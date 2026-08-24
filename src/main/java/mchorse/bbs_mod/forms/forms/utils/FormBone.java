package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.JointDoF;
import mchorse.bbs_mod.settings.values.core.ValueBoneConstraint;
import mchorse.bbs_mod.settings.values.core.ValueBoneIK;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueJointDoF;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * One bone of a model form, as a group of the bone's own properties (the pose is set to move in
 * here too). Mirrors {@link FormMaterial}: every default is neutral — an untouched bone behaves
 * exactly as if this object didn't exist — and {@link ValueBones} creates it lazily on the first
 * edit.
 *
 * <p>An animatable property is one compound value whose type serves both as the form's static
 * setting and as a film track's keyframe, which is what keeps a separate "animatable mirror"
 * class from ever existing. What refers to other bones — the IK chain's target, pole and length —
 * and what configures the solver's character — the chain's mode toggles — are plain static
 * settings next to it: numbers animate, addresses and modes configure.</p>
 */
public class FormBone extends ValueGroup
{
    public final ValueBoneConstraint constraints = new ValueBoneConstraint("constraints", new BoneConstraint());

    /* The IK chain this bone is the tip of. The addresses and modes are the chain's structure;
     * the animatable scalars (weight, softness, pole angle, enabled) live in {@link #ik}. */
    public final ValueString ikTarget = new ValueString("ik_target", "");
    public final ValueString ikPoleTarget = new ValueString("ik_pole_target", "");
    public final ValueInt ikChainLength = new ValueInt("ik_chain_length", 0);
    public final ValueBoolean ikTipRotation = new ValueBoolean("ik_tip_rotation", false);
    public final ValueBoolean ikStretch = new ValueBoolean("ik_stretch", false);
    public final ValueBoolean ikClassic = new ValueBoolean("ik_classic", false);
    public final ValueBoneIK ik = new ValueBoneIK("ik", new IKControl());

    /** The bone's joint freedom for any IK chain that solves through it (locks, limits, stiffness). */
    public final ValueJointDoF joint = new ValueJointDoF("joint", new JointDoF());

    public FormBone(String id)
    {
        super(id);

        this.constraints.invisible();
        this.ikTarget.invisible();
        this.ikPoleTarget.invisible();
        this.ikChainLength.invisible();
        this.ikTipRotation.invisible();
        this.ikStretch.invisible();
        this.ikClassic.invisible();
        this.ik.invisible();
        this.joint.invisible();

        this.add(this.constraints);
        this.add(this.ikTarget);
        this.add(this.ikPoleTarget);
        this.add(this.ikChainLength);
        this.add(this.ikTipRotation);
        this.add(this.ikStretch);
        this.add(this.ikClassic);
        this.add(this.ik);
        this.add(this.joint);
    }

    /** Whether this bone is the tip of a configured IK chain. */
    public boolean hasChain()
    {
        return !this.ikTarget.get().isEmpty();
    }

    /** Whether every property is neutral — such a bone is skipped when the form persists. */
    public boolean isDefault()
    {
        return this.constraints.get().isDefault()
            && !this.hasChain()
            && this.ikPoleTarget.get().isEmpty()
            && this.ikChainLength.get() == 0
            && !this.ikTipRotation.get()
            && !this.ikStretch.get()
            && !this.ikClassic.get()
            && this.ik.get().isDefault()
            && this.joint.get().isFree();
    }
}
