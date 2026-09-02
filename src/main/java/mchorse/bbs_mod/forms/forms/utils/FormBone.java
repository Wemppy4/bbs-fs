package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.JointDoF;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.settings.values.core.ValueBoneConstraint;
import mchorse.bbs_mod.settings.values.core.ValueBoneIK;
import mchorse.bbs_mod.settings.values.core.ValueBonePhysics;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueJointDoF;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
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

    /* The physics chain this bone is the root of ({@code physicsEnd} names the chain's last bone
     * down the hierarchy). The addresses and the simulation's configuration are static; the
     * animatable scalars (weight, gravity, damping, stiffness, enabled) live in {@link #physics}. */
    public final ValueString physicsEnd = new ValueString("physics_end", "");
    public final ValueString physicsTargetBone = new ValueString("physics_target_bone", "");
    public final ValueInt physicsIterations = new ValueInt("physics_iterations", 4);
    public final ValueBoolean physicsCollisions = new ValueBoolean("physics_collisions", false);
    public final ValueFloat physicsRadius = new ValueFloat("physics_radius", 0.1F);
    public final ValueBoolean physicsRelativeGravity = new ValueBoolean("physics_relative_gravity", false);
    public final ValueFloat physicsGravityRotateX = new ValueFloat("physics_gravity_rotate_x", 0F);
    public final ValueFloat physicsGravityRotateY = new ValueFloat("physics_gravity_rotate_y", 0F);
    public final ValueFloat physicsGravityRotateZ = new ValueFloat("physics_gravity_rotate_z", 0F);
    public final ValueBonePhysics physics = new ValueBonePhysics("physics", new PhysicsControl());

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
        this.physicsEnd.invisible();
        this.physicsTargetBone.invisible();
        this.physicsIterations.invisible();
        this.physicsCollisions.invisible();
        this.physicsRadius.invisible();
        this.physicsRelativeGravity.invisible();
        this.physicsGravityRotateX.invisible();
        this.physicsGravityRotateY.invisible();
        this.physicsGravityRotateZ.invisible();
        this.physics.invisible();

        this.add(this.constraints);
        this.add(this.ikTarget);
        this.add(this.ikPoleTarget);
        this.add(this.ikChainLength);
        this.add(this.ikTipRotation);
        this.add(this.ikStretch);
        this.add(this.ikClassic);
        this.add(this.ik);
        this.add(this.joint);
        this.add(this.physicsEnd);
        this.add(this.physicsTargetBone);
        this.add(this.physicsIterations);
        this.add(this.physicsCollisions);
        this.add(this.physicsRadius);
        this.add(this.physicsRelativeGravity);
        this.add(this.physicsGravityRotateX);
        this.add(this.physicsGravityRotateY);
        this.add(this.physicsGravityRotateZ);
        this.add(this.physics);
    }

    /** Whether this bone is the root of a configured physics chain. */
    public boolean hasPhysicsChain()
    {
        return !this.physicsEnd.get().isEmpty();
    }

    /** Whether this bone is the tip of a configured IK chain. */
    public boolean hasChain()
    {
        return !this.ikTarget.get().isEmpty();
    }

    /** Whether every property is neutral — such a bone is skipped when the form persists. */
    @Override
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
            && this.joint.get().isFree()
            && !this.hasPhysicsChain()
            && this.physicsTargetBone.get().isEmpty()
            && this.physicsIterations.get() == 4
            && !this.physicsCollisions.get()
            && this.physicsRadius.get() == 0.1F
            && !this.physicsRelativeGravity.get()
            && this.physicsGravityRotateX.get() == 0F
            && this.physicsGravityRotateY.get() == 0F
            && this.physicsGravityRotateZ.get() == 0F
            && this.physics.get().isDefault();
    }
}
