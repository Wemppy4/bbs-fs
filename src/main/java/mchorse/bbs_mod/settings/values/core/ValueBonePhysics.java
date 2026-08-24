package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueBonePhysics extends BaseKeyframeFactoryValue<PhysicsControl>
{
    public ValueBonePhysics(String id, PhysicsControl value)
    {
        super(id, KeyframeFactories.BONE_PHYSICS, value);
    }
}
