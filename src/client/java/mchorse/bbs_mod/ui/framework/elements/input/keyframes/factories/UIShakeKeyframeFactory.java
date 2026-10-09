package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIShakeControlFields;

/**
 * Editor for the {@code shake} track: shaking bones are keyed by their own name.
 */
public class UIShakeKeyframeFactory extends UIChainKeyframeFactory<ShakeControl, ShakeControls>
{
    public UISliderTrackpad strength;

    public UIShakeKeyframeFactory(UITrackValue<ShakeControls> track, UIKeyframes editor)
    {
        super(track, editor);

        this.strength = this.input(new UIShakeControlFields(this::edit).strength);

        this.setup(UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_STRENGTH, this.strength));
    }

    @Override
    protected boolean hasChain(FormBone bone)
    {
        return bone.hasShake();
    }

    @Override
    protected void sync(ShakeControl control)
    {
        this.strength.setValue(control.strength);
    }

    @Override
    protected ShakeControl configControl(String bone)
    {
        ShakeControl control = this.form == null ? null : this.form.shake.getOriginalValue().controls.get(bone);

        return control == null ? new ShakeControl() : control.copy();
    }
}
