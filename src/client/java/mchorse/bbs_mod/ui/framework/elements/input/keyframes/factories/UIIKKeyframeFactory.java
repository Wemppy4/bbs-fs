package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Editor for the {@code ik} keyframe track: chains are keyed by their tip bone.
 */
public class UIIKKeyframeFactory extends UIChainKeyframeFactory<IKControl, IKControls>
{
    public UISliderTrackpad weight;
    public UISliderTrackpad softness;
    public UISliderTrackpad poleAngle;
    public UIToggle enabled;
    public UIToggle pole;

    public UIIKKeyframeFactory(Keyframe<IKControls> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.weight = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.weight = v.floatValue())));
        this.weight.limit(0D, 1D).increment(0.1D).values(0.1D, 0.05D, 0.2D);

        this.softness = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.softness = v.floatValue())));
        this.softness.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.poleAngle = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.poleAngle = v.floatValue())));
        this.poleAngle.limit(-180D, 180D).increment(5D).values(1D, 0.5D, 5D);

        this.enabled = this.input(new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) -> this.edit((control) -> control.enabled = b.getValue())));
        this.pole = this.input(new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) -> this.edit((control) -> control.pole = b.getValue())));

        this.setup(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.softness).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.poleAngle).marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            this.pole
        );
    }

    @Override
    protected boolean hasChain(FormBone bone)
    {
        return bone.hasChain() && bone.ik.getOriginalValue().enabled;
    }

    @Override
    protected void sync(IKControl control)
    {
        this.weight.setValue(control.weight);
        this.softness.setValue(control.softness);
        this.poleAngle.setValue(control.poleAngle);
        this.enabled.setValue(control.enabled);
        this.pole.setValue(control.pole);
    }

    @Override
    protected IKControl configControl(String bone)
    {
        FormBone formBone = this.form == null ? null : this.form.bones.getBone(bone);

        return formBone == null ? new IKControl() : formBone.ik.getOriginalValue().copy();
    }
}
