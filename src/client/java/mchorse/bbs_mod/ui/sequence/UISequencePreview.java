package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIWorldPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoExportSettings;

/**
 * The sequence editor's preview: the world as the sequence is recorded, at its own export
 * settings, with the playback on its icon bar.
 */
public class UISequencePreview extends UIWorldPreview
{
    public final UISequencePanel panel;

    public UIIcon prev;
    public UIIcon plause;
    public UIIcon next;
    public UIIcon shaders;

    public UISequencePreview(UISequencePanel panel)
    {
        this.panel = panel;

        this.prev = new UIIcon(Icons.FRAME_PREV, (b) -> this.panel.setCursor(this.panel.getCursor() - 1));
        this.prev.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PREV);
        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.next = new UIIcon(Icons.FRAME_NEXT, (b) -> this.panel.setCursor(this.panel.getCursor() + 1));
        this.next.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_NEXT);
        this.shaders = this.createShadersIcon();

        this.addIcon(this.prev, BBSSettings.previewIconPlayback);
        this.addIcon(this.plause, BBSSettings.previewIconPlayback);
        this.addIcon(this.next, BBSSettings.previewIconPlayback);
        this.addIcon(this.shaders, BBSSettings.previewIconShaders);
    }

    @Override
    protected VideoExportSettings getExportSettings()
    {
        Sequence sequence = this.panel.getData();

        return sequence == null ? super.getExportSettings() : sequence.export.toExportSettings();
    }

    @Override
    protected boolean isFramingExport()
    {
        return true;
    }

    /**
     * Over a frame with no film to show, what is missing there.
     */
    @Override
    protected void renderFrame(UIContext context, Area area)
    {
        super.renderFrame(context, area);

        String placeholder = this.panel.getPlaceholder();

        if (placeholder != null)
        {
            FontRenderer font = context.batcher.getFont();
            String text = font.limitToWidth(placeholder, area.w - 20);

            context.batcher.textCard(text, area.mx() - font.getWidth(text) / 2F, area.my() - font.getHeight() / 2F, Colors.WHITE, Colors.A50);
        }
    }
}
