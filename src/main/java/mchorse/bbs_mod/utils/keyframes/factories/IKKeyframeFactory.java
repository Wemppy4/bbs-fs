package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;

public class IKKeyframeFactory extends ChainKeyframeFactory<IKControl, IKControls>
{
    @Override
    public IKControls createEmpty()
    {
        return new IKControls();
    }
}
