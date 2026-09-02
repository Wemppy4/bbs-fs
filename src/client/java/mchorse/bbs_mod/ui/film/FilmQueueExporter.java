package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a sequential export of multiple film tabs.
 *
 * <p>The exporter is a state machine ticked once per frame from
 * {@link UIFilmPanel}'s render. For each queued film id it:
 * <ol>
 *   <li>Switches the film panel to that tab and waits for the asynchronous
 *       data load to settle.</li>
 *   <li>Applies the export resolution and waits one rendered frame so the
 *       backing texture is correct.</li>
 *   <li>Starts a recording via {@link UIFilmRecorder}, which honors the
 *       configured export delay independently for each film.</li>
 *   <li>Resumes on the recorder's finished callback, advancing to the next
 *       queued film or finalizing the queue.</li>
 * </ol>
 *
 * <p>Cancellation (ESC during a recording, errors, or {@link #cancel()})
 * stops the queue and restores the originally selected tab.
 */
public class FilmQueueExporter
{
    /** Hard upper bound for how long to wait for a film tab's data to load. */
    private static final long LOAD_TIMEOUT_MS = 20_000L;

    private final UIFilmPanel panel;
    private final List<String> filmIds;
    private final String returnToFilmId;

    private State state = State.IDLE;
    private int currentIndex = -1;
    private long waitStartedAtMs;

    private FilmQueueExporter(UIFilmPanel panel, List<String> filmIds, String returnToFilmId)
    {
        this.panel = panel;
        this.filmIds = filmIds;
        this.returnToFilmId = returnToFilmId;
    }

    /**
     * Build a queue from the currently-open tabs in the panel. Tabs without
     * a bound film id (the "new tab" placeholders) are ignored. Returns
     * {@code null} when no exportable tabs are present.
     */
    public static FilmQueueExporter fromOpenTabs(UIFilmPanel panel)
    {
        List<String> ids = new ArrayList<>();

        for (DataTab tab : panel.tabs)
        {
            if (tab != null && tab.dataId != null && !tab.dataId.isEmpty())
            {
                ids.add(tab.dataId);
            }
        }

        if (ids.isEmpty())
        {
            return null;
        }

        Film current = panel.getData();
        String returnTo = current == null ? null : current.getId();

        return new FilmQueueExporter(panel, ids, returnTo);
    }

    public boolean isActive()
    {
        return this.state != State.IDLE && this.state != State.FINISHED;
    }

    public int totalCount()
    {
        return this.filmIds.size();
    }

    /**
     * Begin the queue. Loads and switches to the first queued tab. Subsequent
     * progress is driven by {@link #tick(UIContext)}.
     */
    public void start()
    {
        if (this.state != State.IDLE)
        {
            return;
        }

        if (this.filmIds.isEmpty())
        {
            this.finish(false);
            return;
        }

        this.advanceToNextFilm();
    }

    /**
     * Abort the queue. Cancels any in-flight recording and restores the
     * originally selected film tab. Safe to call from any state.
     */
    public void cancel()
    {
        if (this.state == State.FINISHED)
        {
            return;
        }

        UIFilmRecorder recorder = this.panel.recorder;

        recorder.setFinishedListener(null);

        if (recorder.isExporting())
        {
            recorder.cancel();
        }

        this.finish(true);
    }

    /**
     * Per-frame tick. Called from {@link UIFilmPanel#render(UIContext)}.
     */
    public void tick(UIContext context)
    {
        if (this.state != State.WAITING_FOR_FILM)
        {
            return;
        }

        String wantedId = this.filmIds.get(this.currentIndex);
        Film loaded = this.panel.getData();

        if (loaded != null && wantedId.equals(loaded.getId()) && loaded.camera != null)
        {
            this.beginRecordingCurrent(context);
            return;
        }

        if (System.currentTimeMillis() - this.waitStartedAtMs > LOAD_TIMEOUT_MS)
        {
            this.notify(context, UIKeys.FILM_RENDER_QUEUE_LOAD_TIMEOUT, true);
            this.finish(true);
        }
    }

    private void advanceToNextFilm()
    {
        this.currentIndex++;

        if (this.currentIndex >= this.filmIds.size())
        {
            this.notify(this.panel.getContext(), UIKeys.FILM_RENDER_QUEUE_FINISHED, false);
            this.finish(false);
            return;
        }

        String targetId = this.filmIds.get(this.currentIndex);
        int tabIndex = this.findTabIndex(targetId);

        if (tabIndex < 0)
        {
            /* Tab was closed mid-queue — skip and advance. */
            this.advanceToNextFilm();
            return;
        }

        this.state = State.WAITING_FOR_FILM;
        this.waitStartedAtMs = System.currentTimeMillis();

        /* The recorder re-enables main on each stop(); re-disable so the user
         * cannot interact (tab switching, button presses) between films. */
        this.setMainInteractive(false);
        this.panel.tabs.switchTab(tabIndex);
    }

    private void beginRecordingCurrent(UIContext context)
    {
        Film film = this.panel.getData();
        int duration = film.camera.calculateDuration();

        if (duration <= 0)
        {
            /* Empty film — nothing meaningful to record; skip to next. */
            this.advanceToNextFilm();
            return;
        }

        this.state = State.RECORDING;
        this.notifyProgress(context);

        UIFilmRecorder recorder = this.panel.recorder;

        recorder.setFinishedListener(this::onRecordingFinished);

        UIFilmPanel.applyExportSizeToBBS();
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            if (this.state != State.RECORDING)
            {
                return;
            }

            recorder.startRecording(
                duration,
                BBSRendering.getTexture().id,
                BBSRendering.getVideoWidth(),
                BBSRendering.getVideoHeight()
            );

            /* startRecording can decline silently (e.g. concurrent recording).
             * In that case our finished listener never fires, so we bail out
             * here instead of stalling the queue. */
            if (!recorder.isExporting())
            {
                recorder.setFinishedListener(null);
                this.notify(this.panel.getContext(), UIKeys.FILM_RENDER_QUEUE_CANCELLED, true);
                this.finish(true);
            }
        });
    }

    private void onRecordingFinished(boolean cancelled)
    {
        if (this.state != State.RECORDING)
        {
            return;
        }

        if (cancelled)
        {
            this.notify(this.panel.getContext(), UIKeys.FILM_RENDER_QUEUE_CANCELLED, true);
            this.finish(true);
            return;
        }

        this.advanceToNextFilm();
    }

    private void finish(boolean aborted)
    {
        this.state = State.FINISHED;

        this.setMainInteractive(true);

        if (this.returnToFilmId != null)
        {
            Film current = this.panel.getData();
            String currentId = current == null ? null : current.getId();

            if (!this.returnToFilmId.equals(currentId))
            {
                int idx = this.findTabIndex(this.returnToFilmId);

                if (idx >= 0)
                {
                    this.panel.tabs.switchTab(idx);
                }
            }
        }

        this.panel.clearQueueExporter(this);
    }

    private void setMainInteractive(boolean interactive)
    {
        UIContext context = this.panel.getContext();

        if (context != null && context.menu != null && context.menu.main != null)
        {
            context.menu.main.setEnabled(interactive);
        }
    }

    private int findTabIndex(String filmId)
    {
        for (int i = 0; i < this.panel.tabs.size(); i++)
        {
            DataTab tab = this.panel.tabs.get(i);

            if (tab != null && filmId.equals(tab.dataId))
            {
                return i;
            }
        }

        return -1;
    }

    private void notifyProgress(UIContext context)
    {
        if (context == null)
        {
            return;
        }

        String filmId = this.filmIds.get(this.currentIndex);
        IKey message = UIKeys.FILM_RENDER_QUEUE_PROGRESS.format(
            this.currentIndex + 1,
            this.filmIds.size(),
            filmId
        );

        context.notifyInfo(message);
    }

    private void notify(UIContext context, IKey key, boolean error)
    {
        if (context == null)
        {
            return;
        }

        if (error)
        {
            context.notifyError(key);
        }
        else
        {
            context.notifySuccess(key);
        }
    }

    private enum State
    {
        IDLE,
        WAITING_FOR_FILM,
        RECORDING,
        FINISHED
    }
}
