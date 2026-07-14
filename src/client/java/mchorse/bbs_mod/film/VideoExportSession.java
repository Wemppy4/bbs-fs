package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoRecorder;

import java.io.File;

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

        try
        {
            recorder.startRecording(this.getMovieName(), this.audioFile, this.textureId, this.width, this.height);
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

        if (recorder.isRecording())
        {
            try
            {
                recorder.stopRecording();
            }
            catch (Exception e)
            {}
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

    private void reset()
    {
        this.state = State.IDLE;
        this.warmupEndsAtMs = 0L;
        this.audioFile = null;
        this.textureId = 0;
        this.width = 0;
        this.height = 0;
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
