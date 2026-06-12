package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFramebufferFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIFramebufferForm extends UIForm<FramebufferForm>
{
    public UIFramebufferForm()
    {
        super();

        this.defaultPanel = new UIFramebufferFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_FRAMEBUFFER_TITLE, Icons.CAMERA);
        this.registerDefaultPanels();
    }
}