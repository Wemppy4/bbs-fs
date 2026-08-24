package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * One bone's physics scalars ({@link PhysicsControl}) as a keyframe value — the per-bone track
 * that replaced the whole-form {@code physics_controls} container. The value type is the bone
 * property's own, so the track and the form's static setting can never drift apart.
 */
public class BonePhysicsKeyframeFactory implements IKeyframeFactory<PhysicsControl>
{
    private PhysicsControl i = new PhysicsControl();

    @Override
    public PhysicsControl fromData(BaseType data)
    {
        PhysicsControl control = new PhysicsControl();

        if (data instanceof MapType map)
        {
            control.fromData(map);
        }

        return control;
    }

    @Override
    public BaseType toData(PhysicsControl value)
    {
        return value.toData();
    }

    @Override
    public PhysicsControl createEmpty()
    {
        return new PhysicsControl();
    }

    @Override
    public PhysicsControl copy(PhysicsControl value)
    {
        return value.copy();
    }

    @Override
    public PhysicsControl interpolate(Keyframe<PhysicsControl> preA, Keyframe<PhysicsControl> a, Keyframe<PhysicsControl> b, Keyframe<PhysicsControl> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            this.i.autoLerp(
                preA.getValue(), a.getValue(), b.getValue(), postB.getValue(),
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                interpolation.has(Interpolations.AUTO_CLAMPED), x
            );

            return this.i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public PhysicsControl interpolate(PhysicsControl preA, PhysicsControl a, PhysicsControl b, PhysicsControl postB, IInterp interpolation, float x)
    {
        this.i.lerp(preA, a, b, postB, interpolation, x);

        return this.i;
    }
}
