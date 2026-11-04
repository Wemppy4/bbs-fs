package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.sequence.Sequence;
import mchorse.bbs_mod.film.sequence.SequenceParts;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIWorldPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoExportSettings;

import java.io.File;

/**
 * The sequence editor's preview: the world as the sequence is recorded, at its own export
 * settings, with the playback on its icon bar.
 */
public class UISequencePreview extends UIWorldPreview
{
    public final UISequencePanel panel;

    public UIIcon plause;
    public UIIcon shaders;
    public UIIcon record;

    public UISequencePreview(UISequencePanel panel)
    {
        this.panel = panel;

        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.shaders = this.createShadersIcon();
        this.record = new UIIcon(Icons.VIDEO_CAMERA, (b) -> this.panel.recorder.recordSequence());
        this.record.tooltip(UIKeys.SEQUENCE_RECORD);
        this.record.context((menu) ->
        {
            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () ->
            {
                Sequence sequence = this.panel.getData();

                UIUtils.openFolder(sequence == null ? VideoExportSettings.global().getFolder() : sequence.export.toExportSettings().getFolder());
            });
            menu.action(Icons.FOLDER, UIKeys.SEQUENCE_OPEN_PARTS, () ->
            {
                Sequence sequence = this.panel.getData();
                File folder = sequence == null ? SequenceParts.getFolder() : SequenceParts.getFolder(sequence.getId());

                folder.mkdirs();
                UIUtils.openFolder(folder);
            });
        });

        this.addIcon(this.shaders, BBSSettings.previewIconShaders);
        this.addIcon(this.plause, BBSSettings.previewIconPlayback);
        this.addIcon(this.record, BBSSettings.previewIconExport);
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

    @Override
    protected boolean isSizeLocked()
    {
        return this.panel.recorder.isActive() || super.isSizeLocked();
    }

    /** Nothing on the bar can be used while recording, and the frame is all there is to watch */
    @Override
    protected boolean canShowIcons()
    {
        return !this.panel.recorder.isActive();
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
