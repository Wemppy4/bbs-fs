package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UITrailFormPanel extends UIFormPanel<TrailForm>
{
    public UITrailFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });

        this.options.add(
            pick,
            UI.labelRow(UIKeys.FORMS_EDITORS_TRAIL_LENGTH, UIValues.trackpad(() -> this.form.length)),
            UIValues.toggle(UIKeys.FORMS_EDITORS_TRAIL_LOOP, () -> this.form.loop),
            UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, () -> this.form.paused)
        );
    }
}
