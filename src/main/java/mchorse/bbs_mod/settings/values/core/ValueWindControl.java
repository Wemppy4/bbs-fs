package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueWindControl extends BaseKeyframeFactoryValue<WindControl>
{
    public ValueWindControl(String id, WindControl value)
    {
        super(id, KeyframeFactories.WIND, value);
    }
}
