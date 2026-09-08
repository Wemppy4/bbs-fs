package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.UIConstants;

public class UIParticleForm extends UIForm<ParticleForm>
{
    public UIParticleForm()
    {
        super();

        UIElement button = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            Link texture = this.form.texture.get();
            ParticleEmitter emitter = ((ParticleFormRenderer) FormUtilsClient.getRenderer(this.form)).getEmitter();

            if (emitter != null && texture == null)
            {
                texture = emitter.scheme.texture;
            }

            UITexturePicker.open(this.getContext(), texture, (l) -> this.form.texture.set(l));
        }).marginBottom(UIConstants.SECTION_GAP);

        this.registerDefaultPanels();

        this.defaultPanel = this.panels.get(this.panels.size() - 1);
        this.defaultPanel.options.prepend(button);

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, button::clickItself);
    }
}