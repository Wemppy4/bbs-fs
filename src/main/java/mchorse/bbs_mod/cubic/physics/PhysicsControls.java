package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.chains.ChainControls;

/**
 * The physics track's keyframe value: {@link PhysicsControl} scalars keyed by the chain's root bone.
 */
public class PhysicsControls extends ChainControls<PhysicsControl, PhysicsControls>
{
    @Override
    protected PhysicsControls createControls()
    {
        return new PhysicsControls();
    }

    @Override
    protected PhysicsControl createControl()
    {
        return new PhysicsControl();
    }

    @Override
    protected String getDataKey()
    {
        return "physics";
    }
}
