package mchorse.bbs_mod.video;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streams frames out of a video file through an ffmpeg process.
 *
 * Frames are decoded sequentially: while the timeline moves forward the next
 * frames are simply read off the pipe; a jump backward (or far forward) restarts
 * ffmpeg with a seek. While the target frame doesn't change, nothing is read at
 * all, so a paused editor costs nothing.
 */
public class VideoPlayer
{
    private static final Pattern SIZE_PATTERN = Pattern.compile("Video:.*?(\\d{2,5})x(\\d{2,5})");
    private static final Pattern FPS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?) fps");
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");

    /* A forward step within this still counts as sequential playback and is decoded
     * straight off the pipe; anything else is a jump that needs a seek */
    private static final float SEQUENTIAL_AHEAD_SECONDS = 0.5F;

    /* A jump is only acted upon after the requested frame stops changing for this long.
     * While the timeline is being scrubbed the target moves every frame, and restarting
     * ffmpeg on each move would freeze the editor - so the last decoded frame stays up
     * until the cursor settles. */
    private static final long JUMP_SETTLE_MS = 150;

    /* At most this many frames are decoded synchronously per render frame. Real-time
     * playback needs one or two; a forward scrub over a PLAYING film asks for a dozen
     * at once, and decoding them all in one go stutters the editor - the catch-up gets
     * spread over render frames instead (recording is exempt and decodes everything). */
    private static final int MAX_CATCH_UP_FRAMES = 4;

    private static final int STATE_UNPROBED = 0;
    private static final int STATE_VALID = 1;
    private static final int STATE_INVALID = 2;

    private final File file;

    private int width;
    private int height;
    private float fps;
    private float duration;

    /* Probing spawns ffmpeg and parses its output - too slow for the render thread,
     * so it normally runs on the seek worker; only recording and explicit UI asks
     * (ensureProbed) do it synchronously. */
    private volatile int state = STATE_UNPROBED;

    private Process process;
    private ReadableByteChannel channel;
    private ByteBuffer frameBuffer;
    private Texture texture;

    /* Frame index currently uploaded to the texture, and the index the next read returns.
     * streamFrame and ended are also written by the seek thread. */
    private int currentFrame = -1;
    private volatile int streamFrame;
    private volatile boolean ended;

    /* Jump settling state - see JUMP_SETTLE_MS */
    private int settlingTarget = -1;
    private long settlingSince;

    /* A settled jump runs on this thread so the editor never blocks on ffmpeg: the
     * worker restarts the process and decodes the frame into frameBuffer; the render
     * thread only uploads the result. While seeking is true the render thread stays
     * away from the process and the buffer. */
    private Thread seekThread;
    private volatile boolean seeking;
    private volatile boolean pendingReady;
    private volatile int pendingFrame = -1;

    public VideoPlayer(File file)
    {
        this.file = file;
    }

    public boolean isValid()
    {
        return this.state == STATE_VALID;
    }

    public boolean isInvalid()
    {
        return this.state == STATE_INVALID;
    }

    public float getDuration()
    {
        return this.duration;
    }

    /**
     * Probe synchronously if it hasn't happened yet - for UI code that needs the
     * metadata (duration) right now and can afford the wait.
     */
    public void ensureProbed()
    {
        if (this.state == STATE_UNPROBED)
        {
            this.finishSeek();

            if (this.state == STATE_UNPROBED)
            {
                this.probe();
            }
        }
    }

    /**
     * Parse the video's size, frame rate and duration out of ffmpeg's info output
     * ({@code ffmpeg -i} exits with an error but prints the stream data).
     */
    private void probe()
    {
        try
        {
            ProcessBuilder builder = new ProcessBuilder(FFMpegUtils.getFFMPEG(), "-i", this.file.getAbsolutePath());

            builder.redirectErrorStream(true);

            Process probe = builder.start();
            String output;

            try (InputStream stream = probe.getInputStream())
            {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            finally
            {
                probe.destroy();
            }

            Matcher size = SIZE_PATTERN.matcher(output);
            Matcher fps = FPS_PATTERN.matcher(output);
            Matcher duration = DURATION_PATTERN.matcher(output);

            if (!size.find() || !duration.find())
            {
                this.state = STATE_INVALID;

                return;
            }

            this.width = Integer.parseInt(size.group(1));
            this.height = Integer.parseInt(size.group(2));
            this.fps = fps.find() ? Float.parseFloat(fps.group(1)) : 30F;
            this.duration = Integer.parseInt(duration.group(1)) * 3600
                + Integer.parseInt(duration.group(2)) * 60
                + Float.parseFloat(duration.group(3));

            if (this.width <= 0 || this.height <= 0 || this.fps <= 0 || this.duration <= 0)
            {
                this.state = STATE_INVALID;

                return;
            }

            this.state = STATE_VALID;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.state = STATE_INVALID;
        }
    }

    /**
     * Get the texture holding the frame at given time, advancing or restarting
     * the decode stream as needed. Returns null when the video can't be decoded.
     * Must be called on the render thread.
     */
    public Texture getFrame(float seconds)
    {
        if (this.state == STATE_INVALID)
        {
            return null;
        }

        boolean recording = BBSModClient.getVideoRecorder().isRecording();

        if (recording)
        {
            /* Exported frames must be exact right away - wait out an in-flight
             * seek and probe on the spot if it hasn't happened yet */
            this.finishSeek();

            if (this.state == STATE_UNPROBED)
            {
                this.probe();
            }
        }
        else if (this.state == STATE_UNPROBED)
        {
            /* First contact: the worker probes and decodes the first frame */
            if (!this.seeking)
            {
                this.startSeek(seconds);
            }

            return null;
        }

        if (this.state != STATE_VALID)
        {
            return null;
        }

        seconds = MathUtils.clamp(seconds, 0F, this.duration);

        int target = Math.min((int) (seconds * this.fps), (int) (this.duration * this.fps));

        if (this.texture != null && target == this.currentFrame)
        {
            this.settlingTarget = -1;

            return this.texture;
        }

        if (this.seeking)
        {
            return this.texture;
        }

        if (this.pendingReady)
        {
            this.pendingReady = false;

            if (this.pendingFrame == target)
            {
                this.upload(target);

                return this.texture;
            }
        }

        boolean sequential = this.process != null && this.process.isAlive()
            && target >= this.streamFrame && target <= this.streamFrame + (int) (this.fps * SEQUENTIAL_AHEAD_SECONDS);

        if (!sequential)
        {
            if (this.ended && this.texture != null && target >= this.streamFrame)
            {
                /* Past the last decoded frame - keep showing it */
                return this.texture;
            }

            if (!recording)
            {
                /* A jump: keep the old frame up while the scrub is still moving, then
                 * hand the seek to the worker thread - the editor never blocks on it.
                 * Only a LEAP of the target re-arms the timer: during playback the
                 * target advances a frame at a time, and that steady drift must not
                 * keep the seek waiting forever. */
                long now = System.currentTimeMillis();
                int window = (int) (this.fps * SEQUENTIAL_AHEAD_SECONDS);

                if (this.settlingTarget < 0 || Math.abs(target - this.settlingTarget) > window)
                {
                    this.settlingTarget = target;
                    this.settlingSince = now;

                    return this.texture;
                }

                this.settlingTarget = target;

                if (now - this.settlingSince < JUMP_SETTLE_MS)
                {
                    return this.texture;
                }

                this.settlingTarget = -1;
                this.startSeek(seconds);

                return this.texture;
            }

            this.settlingTarget = -1;
            this.restart(target / this.fps, target);
        }

        int read = 0;

        while (this.streamFrame <= target)
        {
            if (!recording && read++ >= MAX_CATCH_UP_FRAMES)
            {
                /* Keep the editor smooth - the rest catches up on the next render frames */
                break;
            }

            if (!this.readFrame())
            {
                this.ended = true;

                break;
            }

            this.streamFrame++;

            if (this.streamFrame - 1 == target)
            {
                this.upload(target);
            }
        }

        return this.texture;
    }

    private void startSeek(float seconds)
    {
        this.seeking = true;
        this.seekThread = new Thread(() ->
        {
            try
            {
                if (this.state == STATE_UNPROBED)
                {
                    this.probe();
                }

                if (this.state != STATE_VALID)
                {
                    return;
                }

                float clamped = MathUtils.clamp(seconds, 0F, this.duration);
                int target = Math.min((int) (clamped * this.fps), (int) (this.duration * this.fps));

                this.restart(target / this.fps, target);

                if (this.readFrame())
                {
                    this.streamFrame = target + 1;
                    this.pendingFrame = target;
                    this.pendingReady = true;
                }
                else
                {
                    /* Nothing at the target - the stream ends THERE, not at whatever
                     * frame the previous process stopped on. Otherwise the "past the
                     * end, keep the last frame" branch would swallow later requests
                     * for perfectly reachable frames. */
                    this.streamFrame = target;
                    this.ended = true;
                }
            }
            finally
            {
                this.seeking = false;
            }
        }, "BBS Video Seek");

        this.seekThread.setDaemon(true);
        this.seekThread.start();
    }

    private void finishSeek()
    {
        Thread thread = this.seekThread;

        if (thread != null && thread.isAlive())
        {
            try
            {
                thread.join(3000);
            }
            catch (InterruptedException e)
            {}
        }
    }

    private void restart(float seconds, int frame)
    {
        this.stop();

        try
        {
            /* Allocated on the first decode, not on probing: a player asked only for
             * metadata (a clip's duration) would otherwise hold a full frame of pixels
             * - up to 33 MB for a 4K file - without ever decoding anything. */
            if (this.frameBuffer == null)
            {
                this.frameBuffer = MemoryUtil.memAlloc(this.width * this.height * 4);
            }

            ProcessBuilder builder = new ProcessBuilder(
                FFMpegUtils.getFFMPEG(),
                "-ss", String.valueOf(seconds),
                "-i", this.file.getAbsolutePath(),
                "-an", "-sn", "-dn",
                "-f", "rawvideo", "-pix_fmt", "rgba",
                "pipe:1"
            );

            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            this.process = builder.start();
            this.channel = Channels.newChannel(this.process.getInputStream());
            this.streamFrame = frame;
            this.ended = false;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.stop();
            this.state = STATE_INVALID;
        }
    }

    private boolean readFrame()
    {
        if (this.channel == null)
        {
            return false;
        }

        try
        {
            this.frameBuffer.clear();

            while (this.frameBuffer.hasRemaining())
            {
                if (this.channel.read(this.frameBuffer) < 0)
                {
                    return false;
                }
            }

            this.frameBuffer.flip();

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private void upload(int frame)
    {
        if (this.texture == null)
        {
            this.texture = new Texture();

            this.texture.setFilter(GL11.GL_LINEAR);
            this.texture.setWrap(GL13.GL_CLAMP_TO_EDGE);
        }

        this.texture.bind();
        this.texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, this.width, this.height, this.frameBuffer);
        this.texture.unbind();

        this.currentFrame = frame;
    }

    /**
     * Kill the decoding process (the texture keeps the last frame; the stream
     * restarts on the next {@link #getFrame(float)}).
     */
    public void stop()
    {
        if (this.process != null)
        {
            this.process.destroy();
            this.process = null;
        }

        if (this.channel != null)
        {
            try
            {
                this.channel.close();
            }
            catch (Exception e)
            {}

            this.channel = null;
        }

        this.ended = false;
    }

    public void delete()
    {
        /* The worker writes into frameBuffer - it must be done before the buffer is freed */
        this.stop();
        this.finishSeek();

        if (this.texture != null)
        {
            this.texture.delete();
            this.texture = null;
        }

        if (this.frameBuffer != null)
        {
            MemoryUtil.memFree(this.frameBuffer);
            this.frameBuffer = null;
        }

        this.state = STATE_INVALID;
    }
}
