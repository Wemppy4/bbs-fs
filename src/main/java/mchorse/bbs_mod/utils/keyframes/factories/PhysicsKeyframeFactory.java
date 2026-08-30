package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;

public class PhysicsKeyframeFactory extends ChainKeyframeFactory<PhysicsControl, PhysicsControls>
{
    @Override
    public PhysicsControls createEmpty()
    {
        return new PhysicsControls();
    }
}
