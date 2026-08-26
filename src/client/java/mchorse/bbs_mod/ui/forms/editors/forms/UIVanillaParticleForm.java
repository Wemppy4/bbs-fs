package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIVanillaParticleFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIVanillaParticleForm extends UIForm<VanillaParticleForm>
{
    public UIVanillaParticleForm()
    {
        super();

        this.defaultPanel = new UIVanillaParticleFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_TITLE, VanillaParticleForm.ICON);
        this.registerDefaultPanels();
    }
}