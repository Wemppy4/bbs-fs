package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.LongType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class LongKeyframeFactory implements IKeyframeFactory<Long>
{
    @Override
    public Long fromData(BaseType data)
    {
        return data.isNumeric() ? data.asNumeric().longValue() : 0L;
    }

    @Override
    public BaseType toData(Long value)
    {
        return new LongType(value);
    }

    @Override
    public Long createEmpty()
    {
        return 0L;
    }

    @Override
    public Long copy(Long value)
    {
        return value;
    }

    @Override
    public Long interpolate(Keyframe<Long> preA, Keyframe<Long> a, Keyframe<Long> b, Keyframe<Long> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.BEZIER))
        {
            return (long) BezierUtils.get(
                a.getValue(), b.getValue(),
                a.getTick(), b.getTick(),
                a.rx, a.ry,
                b.lx, b.ly,
                x
            );
        }

        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            return (long) AutoBezier.get(
                preA.getValue(), a.getValue(), b.getValue(), postB.getValue(),
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                interpolation.has(Interpolations.AUTO_CLAMPED), x
            );
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Long interpolate(Long preA, Long a, Long b, Long postB, IInterp interpolation, float x)
    {
        return (long) interpolation.interpolate(IInterp.context.set(preA, a, b, postB, x));
    }

    @Override
    public double getY(Long value)
    {
        return value;
    }

    @Override
    public Object yToValue(double y)
    {
        return (long) y;
    }
}
