package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.settings.values.core.ValueBoneConstraint;
import mchorse.bbs_mod.settings.values.core.ValueGroup;

/**
 * One bone of a model form, as a group of the bone's own properties (the "Constraints" tab's
 * bone level — IK, physics and the pose are set to move in here too). Mirrors
 * {@link FormMaterial}: every default is neutral — an untouched bone behaves exactly as if
 * this object didn't exist — and {@link ValueBones} creates it lazily on the first edit.
 *
 * <p>Each property is one compound animatable value: its type serves both as the form's
 * static setting and as a film track's keyframe, which is what keeps a separate "animatable
 * mirror" class from ever existing.</p>
 */
public class FormBone extends ValueGroup
{
    public final ValueBoneConstraint constraints = new ValueBoneConstraint("constraints", new BoneConstraint());

    public FormBone(String id)
    {
        super(id);

        this.constraints.invisible();

        this.add(this.constraints);
    }

    /** Whether every property is neutral — such a bone is skipped when the form persists. */
    public boolean isDefault()
    {
        return this.constraints.get().isDefault();
    }
}
