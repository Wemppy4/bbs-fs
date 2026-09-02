package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIFramebufferFormPanel extends UIFormPanel<FramebufferForm>
{
    public UIFramebufferFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        UITrackpad height = UIValues.trackpad(() -> this.form.height);

        width.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_WIDTH);
        height.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_HEIGHT);

        this.options.add(
            UI.label(UIKeys.VIDEO_SETTINGS_RESOLUTION),
            UI.row(width, height),
            UI.labelRow(UIKeys.TRANSFORMS_SCALE, UIValues.trackpad(() -> this.form.scale))
        );
    }
}
