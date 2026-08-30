package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Editor for the {@code physics} keyframe track: chains are keyed by their root bone.
 */
public class UIPhysicsKeyframeFactory extends UIChainKeyframeFactory<PhysicsControl, PhysicsControls>
{
    public UISliderTrackpad weight;
    public UITrackpad gravity;
    public UISliderTrackpad damping;
    public UISliderTrackpad stiffness;
    public UIToggle enabled;

    public UIPhysicsKeyframeFactory(Keyframe<PhysicsControls> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.weight = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.weight = v.floatValue())));
        this.weight.limit(0D, 1D).increment(0.1D).values(0.1D, 0.05D, 0.2D);

        this.gravity = this.input(new UITrackpad((v) -> this.edit((control) -> control.gravity = v.floatValue())));
        this.gravity.increment(0.1D).values(0.1D, 0.05D, 0.2D);

        this.damping = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.damping = v.floatValue())));
        this.damping.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.stiffness = this.input(new UISliderTrackpad((v) -> this.edit((control) -> control.stiffness = v.floatValue())));
        this.stiffness.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.1D);

        this.enabled = this.input(new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) -> this.edit((control) -> control.enabled = b.getValue())));

        this.setup(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY, this.gravity).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING, this.damping).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS, this.stiffness).marginTop(UIConstants.SECTION_GAP),
            this.enabled
        );
    }

    @Override
    protected boolean hasChain(FormBone bone)
    {
        return bone.hasPhysicsChain();
    }

    @Override
    protected void sync(PhysicsControl control)
    {
        this.weight.setValue(control.weight);
        this.gravity.setValue(control.gravity);
        this.damping.setValue(control.damping);
        this.stiffness.setValue(control.stiffness);
        this.enabled.setValue(control.enabled);
    }

    @Override
    protected PhysicsControl configControl(String bone)
    {
        FormBone formBone = this.form == null ? null : this.form.bones.getBone(bone);

        return formBone == null ? new PhysicsControl() : formBone.physics.getOriginalValue().copy();
    }
}
