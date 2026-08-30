package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFramebufferFormPanel;

public class UIFramebufferForm extends UIForm<FramebufferForm>
{
    public UIFramebufferForm()
    {
        super();

        this.defaultPanel = new UIFramebufferFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_FRAMEBUFFER_TITLE, FramebufferForm.ICON);
        this.registerDefaultPanels();
    }
}