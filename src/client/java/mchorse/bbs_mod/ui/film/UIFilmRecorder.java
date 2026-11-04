package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Thin UI wrapper around a {@link PanelVideoExportSession}. Owns the overlay
 * presence (the dimmed editor, ESC-to-cancel) and the
 * export's marks on the preview, and delegates the export lifecycle to the
 * session.
 */
public class UIFilmRecorder extends UIElement
{
    /** How dark the editor around the preview gets while exporting. */
    private static final float DIM_ALPHA = 0.6F;
    private static final long DIM_FADE_MS = 300L;

    private static final int PROGRESS_HEIGHT = 2;
    private static final long PULSE_MS = 1400L;

    /** How long the saved file's mark stays up after a finished export, then fades out. */
    private static final long FINISHED_HOLD_MS = 2000L;
    private static final long FINISHED_FADE_MS = 500L;

    public UIFilmPanel editor;

    public boolean resetReplays = true;

    private final PanelVideoExportSession session;
    private final UIExit exit = new UIExit(this);

    private long attachedAtMs;
    private long finishedAtMs;
    private String finishedName;

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
        UIUtils.openFolder(VideoExportSettings.global().getFolder());
    }

    public void startRecording(VideoExportSettings settings, int duration, int id, int w, int h)
    {
        if (this.editor.isRunning() || duration <= 0)
        {
            return;
        }

        this.session.start(settings, duration, id, w, h);
    }

    /**
     * Add the recorder to the overlay and disable the main UI. Called by the
     * session as recording is set up.
     */
    void attachOverlay()
    {
        UIContext context = this.editor.getContext();

        this.attachedAtMs = System.currentTimeMillis();
        this.finishedAtMs = 0L;

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

    /**
     * Mark the export as finished with the given file for a moment. Called by the session
     * during the teardown of an export that ran to its end.
     */
    void showFinished(String name)
    {
        this.finishedAtMs = System.currentTimeMillis();
        this.finishedName = name == null ? "" : name;
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        Area viewport = this.editor.preview.getViewport();

        this.renderDim(context, viewport);
        this.session.update();
    }

    /**
     * Everything but the video frame goes dark: the editor can't be used while exporting,
     * and the frame is the one thing left worth watching. The fade in is also all there is
     * to show of the warm-up - it's a short pause for the scene, not a countdown.
     */
    private void renderDim(UIContext context, Area viewport)
    {
        float fade = Math.min(1F, (System.currentTimeMillis() - this.attachedAtMs) / (float) DIM_FADE_MS);
        int color = Colors.setA(0, DIM_ALPHA * fade);
        int w = context.menu.width;
        int h = context.menu.height;

        context.batcher.box(0, 0, w, viewport.y, color);
        context.batcher.box(0, viewport.ey(), w, h, color);
        context.batcher.box(0, viewport.y, viewport.x, viewport.ey(), color);
        context.batcher.box(viewport.ex(), viewport.y, w, viewport.ey(), color);
    }

    /**
     * The export's marks on the preview: while recording, a progress line along the frame's
     * bottom edge and a tally (percent and time left) in the top left corner; for a moment
     * after a finished export, the saved file's name.
     */
    public void renderHud(UIContext context, PreviewHud hud)
    {
        if (this.session.isExporting() && !this.session.isWarmingUp())
        {
            float progress = this.session.getProgress(context.getTransition());
            long remaining = this.session.getRemainingMs(progress);
            double phase = (System.currentTimeMillis() % PULSE_MS) / (double) PULSE_MS * Math.PI * 2D;
            float pulse = 0.6F + 0.4F * (float) Math.cos(phase);

            this.renderProgressLine(hud, progress, Colors.RED, 1F);
            this.renderTally(context, hud, Icons.SPHERE, Colors.setA(Colors.RED, pulse), (int) (progress * 100F) + "%", remaining < 0L ? "" : formatRemaining(remaining), 1F);

            return;
        }

        long since = System.currentTimeMillis() - this.finishedAtMs;

        if (this.finishedAtMs <= 0L || since >= FINISHED_HOLD_MS + FINISHED_FADE_MS)
        {
            return;
        }

        float alpha = since < FINISHED_HOLD_MS ? 1F : 1F - (since - FINISHED_HOLD_MS) / (float) FINISHED_FADE_MS;

        if (alpha >= 0.05F)
        {
            this.renderProgressLine(hud, 1F, Colors.GREEN, alpha);
            this.renderTally(context, hud, Icons.CHECKMARK, Colors.setA(Colors.GREEN, alpha), this.finishedName, "", alpha);
        }
    }

    private void renderProgressLine(PreviewHud hud, float progress, int color, float alpha)
    {
        Area frame = hud.getFrame();
        Area line = new Area();

        line.set(frame.x, frame.ey() - PROGRESS_HEIGHT, frame.w, PROGRESS_HEIGHT);

        hud.include(PreviewHud.Anchor.BOTTOM_CENTER, line, (c) ->
        {
            c.batcher.box(line.x, line.y, line.ex(), line.ey(), Colors.setA(0, 0.25F * alpha));
            c.batcher.box(line.x, line.y, line.x + Math.round(line.w * progress), line.ey(), Colors.setA(color, alpha));
        });
    }

    /** Laid out like the take's recording tally: an icon, then the text by its shadow. */
    private void renderTally(UIContext context, PreviewHud hud, Icon icon, int iconColor, String label, String secondary, float alpha)
    {
        FontRenderer font = context.batcher.getFont();
        int labelW = font.getWidth(label);
        int textW = labelW + (secondary.isEmpty() ? 0 : PreviewHud.GAP * 2 + font.getWidth(secondary));
        int textY = font.getHeight() / 2;
        Area block = hud.push(PreviewHud.Anchor.TOP_LEFT, icon.w + PreviewHud.GAP + textW, icon.h);

        hud.include(PreviewHud.Anchor.TOP_LEFT, block, (c) ->
        {
            int x = block.x + icon.w + PreviewHud.GAP;
            int y = block.my() - textY;

            c.batcher.icon(icon, iconColor, block.x, block.y);
            c.batcher.text(label, x, y, Colors.setA(Colors.WHITE, alpha), true);

            if (!secondary.isEmpty())
            {
                c.batcher.text(secondary, x + labelW + PreviewHud.GAP * 2, y, Colors.setA(Colors.LIGHTER_GRAY, alpha), true);
            }
        });
    }

    private static String formatRemaining(long ms)
    {
        long seconds = (ms + 999L) / 1000L;
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;

        return hours > 0L
            ? String.format(Locale.ROOT, "~%d:%02d:%02d", hours, minutes, seconds % 60L)
            : String.format(Locale.ROOT, "~%d:%02d", minutes, seconds % 60L);
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
