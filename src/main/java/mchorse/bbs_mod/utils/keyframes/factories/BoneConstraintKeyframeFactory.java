package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class BoneConstraintKeyframeFactory implements IKeyframeFactory<BoneConstraint>
{
    private BoneConstraint i = new BoneConstraint();

    @Override
    public BoneConstraint fromData(BaseType data)
    {
        BoneConstraint constraint = new BoneConstraint();

        if (data instanceof MapType map)
        {
            constraint.fromData(map);
        }

        return constraint;
    }

    @Override
    public BaseType toData(BoneConstraint value)
    {
        return value.toData();
    }

    @Override
    public BoneConstraint createEmpty()
    {
        return new BoneConstraint();
    }

    @Override
    public BoneConstraint copy(BoneConstraint value)
    {
        return value.copy();
    }

    @Override
    public BoneConstraint interpolate(Keyframe<BoneConstraint> preA, Keyframe<BoneConstraint> a, Keyframe<BoneConstraint> b, Keyframe<BoneConstraint> postB, IInterp interpolation, float x)
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
    public BoneConstraint interpolate(BoneConstraint preA, BoneConstraint a, BoneConstraint b, BoneConstraint postB, IInterp interpolation, float x)
    {
        this.i.lerp(preA, a, b, postB, interpolation, x);

        return this.i;
    }
}
