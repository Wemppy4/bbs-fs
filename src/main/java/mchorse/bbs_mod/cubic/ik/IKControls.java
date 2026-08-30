package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.chains.ChainControls;

/**
 * The IK track's keyframe value: {@link IKControl} scalars keyed by the chain's tip bone.
 */
public class IKControls extends ChainControls<IKControl, IKControls>
{
    @Override
    protected IKControls createControls()
    {
        return new IKControls();
    }

    @Override
    protected IKControl createControl()
    {
        return new IKControl();
    }

    @Override
    protected String getDataKey()
    {
        return "ik";
    }
}
