package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.film.sequence.SequenceExport;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
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

/**
 * A sequence's own export settings, in three parts: the picture (a change there makes every
 * recorded piece stale), the sound, and where the video goes. Named the way the same settings are
 * named in the settings' "Video" category.
 */
public class UISequenceExportOverlayPanel extends UIOverlayPanel
{
    private final SequenceExport export;

    public UISequenceExportOverlayPanel(SequenceExport export)
    {
        super(UIKeys.SEQUENCE_EXPORT_TITLE);

        this.export = export;

        UIScrollView view = new UIScrollView(ScrollDirection.VERTICAL);

        view.full(this.content).column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);

        view.add(this.section(UIKeys.SEQUENCE_EXPORT_PICTURE,
            this.number(export.width),
            this.number(export.height),
            this.number(export.frameRate),
            this.number(export.motionBlur),
            this.number(export.heldFrames),
            this.number(export.shutter),
            this.text(export.arguments),
            this.text(export.argumentsAudio)
        ));
        view.add(this.section(UIKeys.SEQUENCE_EXPORT_SOUND,
            this.toggle(export.audio),
            this.toggle(export.minecraftSounds),
            this.text(export.argumentsMux)
        ));
        view.add(this.section(UIKeys.SEQUENCE_EXPORT_FILE,
            this.path(export.exportPath),
            this.text(export.filenameFormat)
        ));

        this.content.add(view);
    }

    private UISection section(IKey title, UIElement... fields)
    {
        UISection section = new UISection(title);

        section.fields.add(fields);

        return section;
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
        UITrackpad trackpad = new UITrackpad((v) -> value.set(v.intValue()));

        trackpad.limit(value.getMin(), value.getMax(), true);
        trackpad.setValue(value.get());
        trackpad.tooltip(comment(value));

        return UI.labelRow(label(value), trackpad);
    }

    private UIElement toggle(ValueBoolean value)
    {
        UIToggle toggle = new UIToggle(label(value), value.get(), (t) -> value.set(t.getValue()));

        toggle.tooltip(comment(value));

        return toggle;
    }

    /** A text over the full width, its name above it. */
    private UIElement text(ValueString value)
    {
        UITextbox textbox = new UITextbox(10000, value::set);

        textbox.setText(value.get());
        textbox.tooltip(comment(value));

        return this.named(value, textbox);
    }

    /** The folder the video goes into, picked with the button beside it. */
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

        return this.named(value, row);
    }

    private UIElement named(BaseValue value, UIElement control)
    {
        UILabel label = UI.label(label(value)).labelAnchor(0, 0.5F);
        UIElement column = new UIElement();

        label.h(12);
        column.column(1).vertical().stretch();
        column.add(label, control);

        return column;
    }
}
