package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class DoubleKeyframeFactory implements IKeyframeFactory<Double>
{
    @Override
    public Double fromData(BaseType data)
    {
        return data.isNumeric() ? data.asNumeric().doubleValue() : 0D;
    }

    @Override
    public BaseType toData(Double value)
    {
        return new DoubleType(value);
    }

    @Override
    public Double createEmpty()
    {
        return 0D;
    }

    @Override
    public Double copy(Double value)
    {
        return value;
    }

    @Override
    public Double interpolate(Keyframe<Double> preA, Keyframe<Double> a, Keyframe<Double> b, Keyframe<Double> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.BEZIER))
        {
            return BezierUtils.get(
                a.getValue(), b.getValue(),
                a.getTick(), b.getTick(),
                a.rx, a.ry,
                b.lx, b.ly,
                x
            );
        }

        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            return AutoBezier.get(
                preA.getValue(), a.getValue(), b.getValue(), postB.getValue(),
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                interpolation.has(Interpolations.AUTO_CLAMPED), x
            );
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Double interpolate(Double preA, Double a, Double b, Double postB, IInterp interpolation, float x)
    {
        return interpolation.interpolate(IInterp.context.set(preA, a, b, postB, x));
    }

    @Override
    public double getY(Double value)
    {
        return value;
    }

    @Override
    public Object yToValue(double y)
    {
        return y;
    }
}