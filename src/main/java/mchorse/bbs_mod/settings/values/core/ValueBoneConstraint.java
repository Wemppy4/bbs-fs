package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueBoneConstraint extends BaseKeyframeFactoryValue<BoneConstraint>
{
    public ValueBoneConstraint(String id, BoneConstraint value)
    {
        super(id, KeyframeFactories.BONE_CONSTRAINT, value);

        /* A bone's limits are part of the rig, set once in the form: they are not keyed. */
        this.animatable(false);
    }
}
