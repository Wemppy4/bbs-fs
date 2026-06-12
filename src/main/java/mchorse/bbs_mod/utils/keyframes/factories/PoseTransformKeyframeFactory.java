package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;

public class PoseTransformKeyframeFactory implements IKeyframeFactory<PoseTransform>
{
    private PoseTransform i = new PoseTransform();

    @Override
    public PoseTransform fromData(BaseType data)
    {
        PoseTransform transform = new PoseTransform();

        if (data.isMap())
        {
            transform.fromData(data.asMap());
        }

        return transform;
    }

    @Override
    public BaseType toData(PoseTransform value)
    {
        return value.toData();
    }

    @Override
    public PoseTransform createEmpty()
    {
        return new PoseTransform();
    }

    @Override
    public PoseTransform copy(PoseTransform value)
    {
        return (PoseTransform) value.copy();
    }

    @Override
    public PoseTransform interpolate(Keyframe<PoseTransform> preA, Keyframe<PoseTransform> a, Keyframe<PoseTransform> b, Keyframe<PoseTransform> postB, IInterp interpolation, float x)
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
    public PoseTransform interpolate(PoseTransform preA, PoseTransform a, PoseTransform b, PoseTransform postB, IInterp interpolation, float x)
    {
        this.i.lerp(preA, a, b, postB, interpolation, x);

        return this.i;
    }
}
