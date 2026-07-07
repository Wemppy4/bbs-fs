package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import org.lwjgl.glfw.GLFW;

/**
 * Thin UI wrapper around a {@link PanelVideoExportSession}. Owns the overlay
 * presence (warm-up countdown, ESC-to-cancel) and delegates the export
 * lifecycle to the session.
 */
public class UIFilmRecorder extends UIElement
{
    public UIFilmPanel editor;

    public boolean resetReplays = true;

    private final PanelVideoExportSession session;
    private final UIExit exit = new UIExit(this);

    public UIFilmRecorder(UIFilmPanel editor)
    {
        super();

        this.editor = editor;
        this.session = new PanelVideoExportSession(this, editor);

        this.noCulling();
    }

    public boolean isRecording()
    {
        return this.session.isRecording();
    }

    public boolean isExporting()
    {
        return this.session.isExporting();
    }

    public void setFinishedListener(VideoExportSession.FinishedListener listener)
    {
        this.session.setFinishedListener(listener);
    }

    public void cancel()
    {
        this.session.cancel();
    }

    public void stop()
    {
        this.session.stop();
    }

    public void openMovies()
    {
        UIUtils.openFolder(BBSRendering.getVideoFolder());
    }

    public void startRecording(int duration, Texture texture)
    {
        this.startRecording(duration, texture.id, texture.width, texture.height);
    }

    public void startRecording(int duration, int id, int w, int h)
    {
        if (this.editor.isRunning() || duration <= 0)
        {
            return;
        }

        this.session.start(duration, id, w, h);
    }

    /**
     * Add the recorder to the overlay and disable the main UI. Called by the
     * session as recording is set up.
     */
    void attachOverlay()
    {
        UIContext context = this.editor.getContext();

        context.menu.main.setEnabled(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);
    }

    /**
     * Remove the recorder from the overlay and re-enable the main UI. Called by
     * the session during teardown.
     */
    void detachOverlay()
    {
        UIContext context = this.editor.getContext();

        context.render.postRunnable(this.exit::removeFromParent);
        context.menu.main.setEnabled(true);
        context.render.postRunnable(this::removeFromParent);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.session.isWarmingUp() && BBSSettings.recordingOverlays.get())
        {
            long remainingMs = this.session.getWarmupRemainingMs();
            int countdown = Math.max(0, (int) Math.ceil(remainingMs / 50D));
            Area previewArea = this.editor.preview.getViewport();

            BBSRendering.renderRecordingTimerOverlay(context.batcher, String.valueOf(TimeUtils.toSeconds(countdown)), previewArea.x + 5, previewArea.y + 5);
        }

        this.session.update();
    }

    public static class UIExit extends UIElement
    {
        private UIFilmRecorder recorder;

        public UIExit(UIFilmRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.recorder.cancel();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }
}
