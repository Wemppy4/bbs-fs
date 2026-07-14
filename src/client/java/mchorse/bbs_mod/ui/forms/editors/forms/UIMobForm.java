package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMobFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIMobForm extends UIPoseForm<MobForm>
{
    public UIMobFormPanel mobPanel;

    public UIMobForm()
    {
        super();

        this.mobPanel = new UIMobFormPanel(this);
        this.setupPosePanel(this.mobPanel);
        this.defaultPanel = this.mobPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MOB_TITLE, Icons.MORPH);
        this.registerDefaultPanels();
    }
}
