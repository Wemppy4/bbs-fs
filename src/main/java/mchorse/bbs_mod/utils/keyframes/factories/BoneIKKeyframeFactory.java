package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * One bone's IK scalars ({@link IKControl}) as a keyframe value — the per-bone track that
 * replaced the whole-form {@code ik_controls} container. The value type is the bone
 * property's own, so the track and the form's static setting can never drift apart.
 */
public class BoneIKKeyframeFactory implements IKeyframeFactory<IKControl>
{
    private IKControl i = new IKControl();

    @Override
    public IKControl fromData(BaseType data)
    {
        IKControl control = new IKControl();

        if (data instanceof MapType map)
        {
            control.fromData(map);
        }

        return control;
    }

    @Override
    public BaseType toData(IKControl value)
    {
        return value.toData();
    }

    @Override
    public IKControl createEmpty()
    {
        return new IKControl();
    }

    @Override
    public IKControl copy(IKControl value)
    {
        return value.copy();
    }

    @Override
    public IKControl interpolate(Keyframe<IKControl> preA, Keyframe<IKControl> a, Keyframe<IKControl> b, Keyframe<IKControl> postB, IInterp interpolation, float x)
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
    public IKControl interpolate(IKControl preA, IKControl a, IKControl b, IKControl postB, IInterp interpolation, float x)
    {
        this.i.lerp(preA, a, b, postB, interpolation, x);

        return this.i;
    }
}
