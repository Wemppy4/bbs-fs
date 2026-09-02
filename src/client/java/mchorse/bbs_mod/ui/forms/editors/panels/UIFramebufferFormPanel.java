package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIFramebufferFormPanel extends UIFormPanel<FramebufferForm>
{
    public UITrackpad width;
    public UITrackpad height;
    public UITrackpad scale;

    public UIFramebufferFormPanel(UIForm editor)
    {
        super(editor);

        this.width = UIValues.trackpad(() -> this.form.width);
        this.width.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_WIDTH);
        this.height = UIValues.trackpad(() -> this.form.height);
        this.height.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_HEIGHT);
        this.scale = UIValues.trackpad(() -> this.form.scale);

        this.options.add(UI.label(UIKeys.VIDEO_SETTINGS_RESOLUTION), UI.row(this.width, this.height), UI.labelRow(UIKeys.TRANSFORMS_SCALE, this.scale));
    }

    @Override
    public void startEdit(FramebufferForm form)
    {
        super.startEdit(form);

        this.width.setValue(form.width.get());
        this.height.setValue(form.height.get());
        this.scale.setValue(form.scale.get());
    }
}