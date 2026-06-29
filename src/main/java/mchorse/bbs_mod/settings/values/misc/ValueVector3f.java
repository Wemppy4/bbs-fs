package mchorse.bbs_mod.settings.values.misc;

import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.joml.Vector3f;

public class ValueVector3f extends BaseKeyframeFactoryValue<Vector3f>
{
    public ValueVector3f(String id, Vector3f value)
    {
        super(id, KeyframeFactories.VECTOR3F, value);
    }
}
