package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIExtrudedFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIExtrudedForm extends UIForm<ExtrudedForm>
{
    private UIExtrudedFormPanel extrudedFormPanel;

    public UIExtrudedForm()
    {
        super();

        this.extrudedFormPanel = new UIExtrudedFormPanel(this);
        this.defaultPanel = this.extrudedFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_EXTRUDED_TITLE, ExtrudedForm.ICON);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.extrudedFormPanel)
            {
                this.setPanel(this.extrudedFormPanel);
            }

            this.extrudedFormPanel.pick.clickItself();
        });
    }
}