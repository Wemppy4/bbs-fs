package mchorse.bbs_mod.cubic.shake;

import mchorse.bbs_mod.cubic.chains.ChainControls;

/**
 * The shake track's keyframe value: {@link ShakeControl} strengths keyed by the shaking bone.
 */
public class ShakeControls extends ChainControls<ShakeControl, ShakeControls>
{
    @Override
    protected ShakeControls createControls()
    {
        return new ShakeControls();
    }

    @Override
    protected ShakeControl createControl()
    {
        return new ShakeControl();
    }

    @Override
    protected String getDataKey()
    {
        return "shake";
    }
}
