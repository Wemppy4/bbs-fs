package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.film.sequence.SequenceExport;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIResolutionRow;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.VideoExportSettings;

import java.util.Arrays;

/**
 * A sequence's export settings: one switch on top, and while it is on, the sequence's own size
 * (with the swap and the resolution presets, as in the settings),
 * frame rate, motion blur, held frames, shutter, sound and folder. While it is off, the sequence
 * records with the settings' "Video" category. Named the way the same settings are named there.
 */
public class UISequenceExportOverlayPanel extends UIOverlayPanel
{
    private final SequenceExport export;
    private UIElement fields;
    private UITrackpad width;
    private UITrackpad height;
    private UITrackpad frameRate;

    public UISequenceExportOverlayPanel(SequenceExport export)
    {
        super(UIKeys.SEQUENCE_EXPORT_TITLE);

        this.wh(260, 270);

        this.export = export;

        UIScrollView view = new UIScrollView(ScrollDirection.VERTICAL);

        view.full(this.content).column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);

        UIToggle custom = new UIToggle(UIKeys.SEQUENCE_EXPORT_CUSTOM, export.custom.get(), (t) ->
        {
            export.custom.set(t.getValue());
            this.fields.setVisible(t.getValue());
            this.resize();
        });

        custom.tooltip(UIKeys.SEQUENCE_EXPORT_CUSTOM_TOOLTIP);

        this.fields = new UIElement();
        this.fields.column(UIConstants.MARGIN).vertical().stretch();
        this.fields.add(
            this.resolution(),
            this.number(export.frameRate),
            this.number(export.motionBlur),
            this.number(export.heldFrames),
            this.number(export.shutter),
            this.toggle(export.audio),
            this.toggle(export.minecraftSounds),
            this.path(export.exportPath)
        );
        this.fields.setVisible(export.custom.get());

        view.add(custom, this.fields);
        this.content.add(view);
    }

    private static IKey label(BaseValue value)
    {
        return L10n.lang("bbs.config.video." + value.getId());
    }

    private static IKey comment(BaseValue value)
    {
        return L10n.lang("bbs.config.video." + value.getId() + "-comment");
    }

    private UIElement number(ValueInt value)
    {
        UITrackpad trackpad = this.trackpad(value);

        trackpad.tooltip(comment(value));

        if (value == this.export.frameRate)
        {
            this.frameRate = trackpad;
        }

        return UI.labelRow(label(value), trackpad);
    }

    private UITrackpad trackpad(ValueInt value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> value.set(v.intValue()));

        trackpad.limit(value.getMin(), value.getMax(), true);
        trackpad.setValue(value.get());

        return trackpad;
    }

    /**
     * Width and height as one strip, as in the settings' "Video" category: the swap between
     * them, and the resolution presets on right click.
     */
    private UIElement resolution()
    {
        this.width = this.trackpad(this.export.width);
        this.height = this.trackpad(this.export.height);
        this.width.tooltip(IKey.comp(Arrays.asList(label(this.export.width), IKey.constant("\n"), comment(this.export.width))));
        this.height.tooltip(IKey.comp(Arrays.asList(label(this.export.height), IKey.constant("\n"), comment(this.export.height))));
        this.width.context((menu) -> UISettingsOverlayPanel.addVideoPresets(menu, this::applyPreset));
        this.height.context((menu) -> UISettingsOverlayPanel.addVideoPresets(menu, this::applyPreset));

        UIIcon swap = new UIIcon(Icons.REFRESH, (b) -> this.setResolution(this.export.height.get(), this.export.width.get()));

        swap.tooltip(UIKeys.VIDEO_SETTINGS_SWAP);
        swap.wh(Icons.REFRESH.w, UIConstants.CONTROL_HEIGHT);

        UIElement row = new UIElement();

        row.row(4).height(UIConstants.CONTROL_HEIGHT);
        row.add(new UIResolutionRow.UIIconMark(Icons.HORIZONTAL), this.width, swap, this.height, new UIResolutionRow.UIIconMark(Icons.VERTICAL));

        return row;
    }

    /** Presets promise a resolution and 60 FPS, like the global ones */
    private void applyPreset(int width, int height)
    {
        this.setResolution(width, height);
        this.export.frameRate.set(60);
        this.frameRate.setValue(60);
    }

    private void setResolution(int width, int height)
    {
        this.export.width.set(width);
        this.export.height.set(height);
        this.width.setValue(width);
        this.height.setValue(height);
    }

    private UIElement toggle(ValueBoolean value)
    {
        UIToggle toggle = new UIToggle(label(value), value.get(), (t) -> value.set(t.getValue()));

        toggle.tooltip(comment(value));

        return toggle;
    }

    /** The folder the video goes into, picked with the button beside it, its name above it. */
    private UIElement path(ValueString value)
    {
        UITextbox textbox = new UITextbox(10000, value::set);
        UIIcon pick = new UIIcon(Icons.FOLDER, (b) -> UIFileDialogs.pickFolder(UIKeys.GENERAL_DIALOG_EXPORT_FOLDER, VideoExportSettings.resolveFolder(value.get()), (file) ->
        {
            value.set(file.getAbsolutePath());
            textbox.setText(value.get());
        }));

        textbox.setText(value.get());
        textbox.tooltip(comment(value));
        pick.tooltip(UIKeys.CAMERA_TOOLTIPS_PICK_EXPORT_FOLDER);
        pick.wh(Icons.FOLDER.w, UIConstants.CONTROL_HEIGHT);
        pick.context((menu) -> menu.action(Icons.FOLDER, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> UIUtils.openFolder(VideoExportSettings.resolveFolder(value.get()))));

        UIElement row = new UIElement();

        row.row(4).height(UIConstants.CONTROL_HEIGHT);
        row.add(textbox, pick);

        UILabel label = UI.label(label(value)).labelAnchor(0, 0.5F);
        UIElement column = new UIElement();

        label.h(12);
        column.column(1).vertical().stretch();
        column.add(label, row);

        return column;
    }
}
