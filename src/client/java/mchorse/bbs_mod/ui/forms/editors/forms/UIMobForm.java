package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMobFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIMobForm extends UIForm<MobForm>
{
    public UIMobForm()
    {
        super();

        this.defaultPanel = new UIMobFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MOB_TITLE, MobForm.ICON);
        this.registerDefaultPanels();
    }
}