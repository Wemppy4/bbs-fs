package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIBillboardFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIBillboardForm extends UIForm<BillboardForm>
{
    private UIBillboardFormPanel billboardFormPanel;

    public UIBillboardForm()
    {
        super();

        this.billboardFormPanel = new UIBillboardFormPanel(this);
        this.defaultPanel = this.billboardFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, BillboardForm.ICON);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.billboardFormPanel)
            {
                this.setPanel(this.billboardFormPanel);
            }

            this.billboardFormPanel.pick.clickItself();
        });
    }
}