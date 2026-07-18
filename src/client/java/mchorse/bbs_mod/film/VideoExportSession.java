package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.MinecraftSoundCapture;
import mchorse.bbs_mod.audio.MinecraftSoundMixer;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoMuxer;
import mchorse.bbs_mod.utils.VideoRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Owns the lifecycle of a single video export: the optional warm-up delay,
 * starting and stopping the shared {@link VideoRecorder}, and firing a
 * finished listener once teardown completes.
 *
 * <p>Subclasses fill in the differences between exporting the live world
 * ({@link WorldVideoExportSession}) and exporting the film panel's preview
 * ({@link mchorse.bbs_mod.ui.film.PanelVideoExportSession}) through the
 * template hooks. The common state machine lives here so neither path
 * reimplements the warm-up timer.
 */
public abstract class VideoExportSession
{
    public enum State
    {
        IDLE,
        WARMUP,
        RECORDING
    }

    protected State state = State.IDLE;
    protected long warmupEndsAtMs;

    protected File audioFile;
    protected int textureId;
    protected int width;
    protected int height;

    /** Name (no extension) the recorder writes to; kept for the captured-sounds post pass. */
    private String movieName;
    /** Film audio rendered by prepare() whose muxing is deferred to the post pass (Minecraft sounds capture). */
    private File deferredAudioFile;
    /** Frame rate the frames are actually recorded at (motion blur included), for the audio timeline. */
    private double recordingFrameRate;
    /** When the recorder was started - the post pass ignores files older than this. */
    private long recordingStartedAtMs;

    private FinishedListener finishedListener;

    protected VideoRecorder getRecorder()
    {
        return BBSModClient.getVideoRecorder();
    }

    public boolean isExporting()
    {
        return this.state != State.IDLE;
    }

    public boolean isWarmingUp()
    {
        return this.state == State.WARMUP;
    }

    public boolean isRecording()
    {
        return this.getRecorder().isRecording();
    }

    public long getWarmupRemainingMs()
    {
        if (this.state != State.WARMUP)
        {
            return 0L;
        }

        return Math.max(0L, this.warmupEndsAtMs - System.currentTimeMillis());
    }

    /**
     * Sets a one-shot listener invoked once when the current export finishes,
     * receiving whether the teardown was user-initiated. Cleared after it fires.
     */
    public void setFinishedListener(FinishedListener listener)
    {
        this.finishedListener = listener;
    }

    /**
     * Begin an export against the given capture target. Runs {@link #prepare()}
     * (which may abort or set {@link #audioFile}); on success either records
     * immediately or enters the warm-up delay.
     */
    protected final boolean begin(int textureId, int width, int height, long delayMs)
    {
        if (this.isExporting() || this.getRecorder().isRecording())
        {
            return false;
        }

        this.textureId = textureId;
        this.width = width;
        this.height = height;
        this.audioFile = null;

        if (!this.prepare())
        {
            this.reset();

            return false;
        }

        this.applyExportTarget();

        if (delayMs > 0L)
        {
            this.state = State.WARMUP;
            this.warmupEndsAtMs = System.currentTimeMillis() + delayMs;
            this.onWarmupStarted();
        }
        else
        {
            this.beginRecording();
        }

        return this.isExporting();
    }

    public final void update()
    {
        if (this.state == State.WARMUP)
        {
            if (this.shouldAbortWarmup())
            {
                this.cancel();

                return;
            }

            if (!this.isWarmupReady() || System.currentTimeMillis() < this.warmupEndsAtMs)
            {
                return;
            }

            this.beginRecording();
        }
        else if (this.state == State.RECORDING)
        {
            if (this.isFinished())
            {
                this.stop();
            }
        }
    }

    private void beginRecording()
    {
        VideoRecorder recorder = this.getRecorder();

        /* Optimistically enter RECORDING before the start attempt so a failure
         * still routes through teardown and restores whatever prepare() applied. */
        this.state = State.RECORDING;

        String movieName = this.getMovieName();

        if (movieName == null || movieName.isEmpty())
        {
            /* Mirror the recorder's own fallback - the post pass must know the real name */
            movieName = StringUtils.createTimestampFilename();
        }

        File muxAudioFile = this.audioFile;
        boolean captureSounds = BBSSettings.videoExportMinecraftSounds.get();

        if (captureSounds)
        {
            /* Minecraft sounds are only known once the recording ends, so the whole
             * audio track (film audio + captured sounds) is muxed in a post pass
             * instead of being handed to the recording ffmpeg process. */
            this.deferredAudioFile = this.audioFile;
            muxAudioFile = null;
        }

        try
        {
            recorder.startRecording(movieName, muxAudioFile, this.textureId, this.width, this.height);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.cancel();

            return;
        }

        if (!recorder.isRecording())
        {
            this.cancel();

            return;
        }

        this.movieName = movieName;
        this.recordingStartedAtMs = System.currentTimeMillis();

        if (captureSounds)
        {
            this.recordingFrameRate = BBSRendering.getVideoFrameRate();

            BBSModClient.getMinecraftSoundCapture().begin();
        }

        this.onRecordingStarted();
    }

    /**
     * Stop the export naturally (its footage is complete). Fires the finished
     * listener with {@code cancelled == false}.
     */
    public final void stop()
    {
        this.finish(false);
    }

    /**
     * Abort the export (user pressed the key/ESC, an error occurred). Fires the
     * finished listener with {@code cancelled == true} so callers can tell an
     * abort from a natural finish.
     */
    public final void cancel()
    {
        this.finish(true);
    }

    private void finish(boolean cancelled)
    {
        if (this.state == State.IDLE)
        {
            return;
        }

        VideoRecorder recorder = this.getRecorder();
        int recordedFrames = recorder.getCounter();
        MinecraftSoundCapture capture = BBSModClient.getMinecraftSoundCapture();
        boolean postPass = capture.isActive();

        if (recorder.isRecording())
        {
            try
            {
                /* With a post pass ahead the file isn't final yet - the completion
                 * sound and the folder opening wait until the audio is merged */
                recorder.stopRecording(!postPass);
            }
            catch (Exception e)
            {}
        }

        if (postPass)
        {
            this.finishCapturedSounds(capture, recordedFrames);
            recorder.playFinishEffects();
        }
        else if (this.deferredAudioFile != null)
        {
            /* The recording never started after prepare() had rendered the film
             * audio for the post pass - there is no video to merge it into */
            this.deferredAudioFile.delete();
        }

        this.state = State.IDLE;
        this.teardown(cancelled);
        this.reset();

        FinishedListener listener = this.finishedListener;
        this.finishedListener = null;

        if (listener != null)
        {
            listener.onFinished(cancelled);
        }
    }

    /**
     * Post pass of the Minecraft sounds capture: mix the captured sounds with the
     * deferred film audio into a stereo WAV and merge it into the recorded video.
     * Best effort - a failure logs and leaves the recorded video without audio (and
     * keeps the deferred film audio WAV on disk for manual recovery).
     */
    private void finishCapturedSounds(MinecraftSoundCapture capture, int recordedFrames)
    {
        capture.end();

        File deferred = this.deferredAudioFile;

        this.deferredAudioFile = null;

        try
        {
            if (recordedFrames <= 0)
            {
                /* Nothing was recorded - there is no video to merge into */
                if (deferred != null)
                {
                    deferred.delete();
                }

                return;
            }

            File folder = BBSRendering.getVideoFolder();
            File audio = new File(folder, this.movieName + ".wav");

            if (!MinecraftSoundMixer.mixToFile(audio, capture.getSounds(), capture.getFrames(), readWave(deferred), 48000, this.recordingFrameRate, recordedFrames))
            {
                return;
            }

            File video = this.findRecordedVideo(folder);

            if (video != null && VideoMuxer.mux(video, audio, this.movieName) != null && deferred != null)
            {
                /* Only a successful merge makes the deferred film track redundant */
                deferred.delete();
            }
        }
        catch (Throwable e)
        {
            /* Best effort - even an OutOfMemoryError of a huge mix must not take
             * down the game after a completed recording */
            e.printStackTrace();
        }
    }

    /**
     * Read back the film audio track that prepare() rendered but that wasn't handed
     * to the recorder because the whole track is muxed in the post pass.
     */
    private static Wave readWave(File file)
    {
        if (file == null || !file.isFile())
        {
            return null;
        }

        try (InputStream stream = new FileInputStream(file))
        {
            return new WaveReader().read(stream);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return null;
        }
    }

    /**
     * The video file this session's recording produced: its base name is known, but the
     * extension is up to the user's encoder arguments.
     */
    private File findRecordedVideo(File folder)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        String prefix = this.movieName + ".";
        /* Slack for coarse file system timestamps (e.g. FAT's 2 second resolution) */
        long notBefore = this.recordingStartedAtMs - 10_000L;
        File found = null;

        for (File file : files)
        {
            if (!file.isFile() || !file.getName().startsWith(prefix))
            {
                continue;
            }

            if (isExportArtifact(file.getName().substring(prefix.length())))
            {
                continue;
            }

            /* A same-named file from an older export must never be picked up */
            if (file.lastModified() < notBefore)
            {
                continue;
            }

            if (found == null || file.lastModified() > found.lastModified())
            {
                found = file;
            }
        }

        return found;
    }

    /**
     * Whether a file named {@code <movie name>.<rest>} is a side product of an export -
     * the audio track, an encoder log or the leftover of a failed merge - rather than the
     * video itself. Shared so picking the recorded video and allocating a free movie name
     * agree on what counts as an export's video.
     */
    protected static boolean isExportArtifact(String rest)
    {
        rest = rest.toLowerCase();

        return rest.equals("wav") || rest.equals("log") || rest.endsWith(".log") || rest.startsWith("tmp.");
    }

    private void reset()
    {
        this.state = State.IDLE;
        this.warmupEndsAtMs = 0L;
        this.audioFile = null;
        this.textureId = 0;
        this.width = 0;
        this.height = 0;
        this.movieName = null;
        this.deferredAudioFile = null;
        this.recordingFrameRate = 0D;
        this.recordingStartedAtMs = 0L;
    }

    /* Hooks */

    /**
     * The base filename (no extension) the recorder should write to. Defaults to a timestamp;
     * the film-panel export overrides this to honour the user's filename format setting.
     */
    protected String getMovieName()
    {
        return StringUtils.createTimestampFilename();
    }

    /**
     * Gather everything needed to record (size already stored, plus audio,
     * cursor, entities, UI). May set {@link #audioFile}. Return {@code false}
     * to abort before anything is applied.
     */
    protected abstract boolean prepare();

    /**
     * Apply the capture target (e.g. switch the renderer to the export size).
     * Called for both the immediate and the delayed path.
     */
    protected void applyExportTarget()
    {}

    /** Invoked when the warm-up delay begins (e.g. pause the editor). */
    protected void onWarmupStarted()
    {}

    /**
     * Whether the warm-up should be aborted before it ever records (e.g. the
     * film we were about to record vanished). Distinct from a natural finish.
     */
    protected boolean shouldAbortWarmup()
    {
        return false;
    }

    /**
     * Whether the warm-up may complete once its timer elapses. Lets a subclass
     * hold the warm-up open until an external condition is met (e.g. the film
     * has produced its first tick).
     */
    protected boolean isWarmupReady()
    {
        return true;
    }

    /** Invoked right after the recorder starts (e.g. resume playback). */
    protected abstract void onRecordingStarted();

    /** Whether the recording has reached its natural end. */
    protected abstract boolean isFinished();

    /** Restore whatever {@link #prepare()}/{@link #applyExportTarget()} changed. */
    protected abstract void teardown(boolean cancelled);

    @FunctionalInterface
    public interface FinishedListener
    {
        void onFinished(boolean cancelled);
    }
}
