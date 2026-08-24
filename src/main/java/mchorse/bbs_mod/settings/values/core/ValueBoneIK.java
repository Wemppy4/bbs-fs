package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueBoneIK extends BaseKeyframeFactoryValue<IKControl>
{
    public ValueBoneIK(String id, IKControl value)
    {
        super(id, KeyframeFactories.BONE_IK, value);
    }
}
