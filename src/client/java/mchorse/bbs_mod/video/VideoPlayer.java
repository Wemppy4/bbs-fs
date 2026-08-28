package mchorse.bbs_mod.video;

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

    /* A forward jump longer than this restarts with a seek instead of decoding through */
    private static final float MAX_DECODE_AHEAD_SECONDS = 2F;

    private final File file;

    private int width;
    private int height;
    private float fps;
    private float duration;
    private boolean valid;

    private Process process;
    private ReadableByteChannel channel;
    private ByteBuffer frameBuffer;
    private Texture texture;

    /* Frame index currently uploaded to the texture, and the index the next read returns */
    private int currentFrame = -1;
    private int streamFrame;
    private boolean ended;

    public VideoPlayer(File file)
    {
        this.file = file;

        this.probe();
    }

    public boolean isValid()
    {
        return this.valid;
    }

    public float getDuration()
    {
        return this.duration;
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
                return;
            }

            this.frameBuffer = MemoryUtil.memAlloc(this.width * this.height * 4);
            this.valid = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Get the texture holding the frame at given time, advancing or restarting
     * the decode stream as needed. Returns null when the video can't be decoded.
     * Must be called on the render thread.
     */
    public Texture getFrame(float seconds)
    {
        if (!this.valid)
        {
            return null;
        }

        seconds = MathUtils.clamp(seconds, 0F, this.duration);

        int target = Math.min((int) (seconds * this.fps), (int) (this.duration * this.fps));

        if (this.texture != null && target == this.currentFrame)
        {
            return this.texture;
        }

        if (this.process == null || !this.process.isAlive() || target < this.streamFrame || target > this.streamFrame + (int) (this.fps * MAX_DECODE_AHEAD_SECONDS))
        {
            if (this.ended && this.texture != null && target >= this.streamFrame)
            {
                /* Past the last decoded frame - keep showing it */
                return this.texture;
            }

            this.restart(target / this.fps, target);
        }

        while (this.streamFrame <= target)
        {
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

    private void restart(float seconds, int frame)
    {
        this.stop();

        try
        {
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
            this.valid = false;
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
        this.stop();

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

        this.valid = false;
    }
}
