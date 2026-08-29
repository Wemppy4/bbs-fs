package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UITrailFormPanel extends UIFormPanel<TrailForm>
{
    public UIButton pick;
    public UITrackpad length;
    public UIToggle loop;
    public UIToggle paused;

    public UITrailFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        this.length = UIValues.trackpad(() -> this.form.length);
        this.loop = UIValues.toggle(UIKeys.FORMS_EDITORS_TRAIL_LOOP, () -> this.form.loop);
        this.paused = UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, () -> this.form.paused);

        this.options.add(this.pick, UI.labelRow(UIKeys.FORMS_EDITORS_TRAIL_LENGTH, this.length), this.loop, this.paused);
    }

    @Override
    public void startEdit(TrailForm form)
    {
        super.startEdit(form);

        this.length.setValue(form.length.get());
        this.loop.setValue(form.loop.get());
        this.paused.setValue(form.paused.get());
    }
}