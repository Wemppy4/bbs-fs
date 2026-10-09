package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;

public class ShakeKeyframeFactory extends ChainKeyframeFactory<ShakeControl, ShakeControls>
{
    @Override
    public ShakeControls createEmpty()
    {
        return new ShakeControls();
    }
}
