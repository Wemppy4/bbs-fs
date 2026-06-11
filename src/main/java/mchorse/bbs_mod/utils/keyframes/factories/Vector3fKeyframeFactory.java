package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector3f;

public class Vector3fKeyframeFactory implements IKeyframeFactory<Vector3f>
{
    private final Vector3f empty;
    private Vector3f i = new Vector3f();

    public Vector3fKeyframeFactory(Vector3f empty)
    {
        this.empty = new Vector3f(empty);
    }

    @Override
    public Vector3f fromData(BaseType data)
    {
        return data.isList() ? DataStorageUtils.vector3fFromData(data.asList()) : new Vector3f(this.empty);
    }

    @Override
    public BaseType toData(Vector3f value)
    {
        return DataStorageUtils.vector3fToData(value);
    }

    @Override
    public Vector3f createEmpty()
    {
        return new Vector3f(this.empty);
    }

    @Override
    public Vector3f copy(Vector3f value)
    {
        return new Vector3f(value);
    }

    @Override
    public Vector3f interpolate(Keyframe<Vector3f> preA, Keyframe<Vector3f> a, Keyframe<Vector3f> b, Keyframe<Vector3f> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.BEZIER))
        {
            this.i.x = (float) BezierUtils.get(
                a.getValue().x, b.getValue().x,
                a.getTick(), b.getTick(),
                a.getRx(0), a.getRy(0),
                b.getLx(0), b.getLy(0),
                x
            );
            this.i.y = (float) BezierUtils.get(
                a.getValue().y, b.getValue().y,
                a.getTick(), b.getTick(),
                a.getRx(1), a.getRy(1),
                b.getLx(1), b.getLy(1),
                x
            );
            this.i.z = (float) BezierUtils.get(
                a.getValue().z, b.getValue().z,
                a.getTick(), b.getTick(),
                a.getRx(2), a.getRy(2),
                b.getLx(2), b.getLy(2),
                x
            );

            return this.i;
        }

        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);

            this.i.x = (float) AutoBezier.get(
                preA.getValue().x, a.getValue().x, b.getValue().x, postB.getValue().x,
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                clamped, x
            );
            this.i.y = (float) AutoBezier.get(
                preA.getValue().y, a.getValue().y, b.getValue().y, postB.getValue().y,
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                clamped, x
            );
            this.i.z = (float) AutoBezier.get(
                preA.getValue().z, a.getValue().z, b.getValue().z, postB.getValue().z,
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                clamped, x
            );

            return this.i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Vector3f interpolate(Vector3f preA, Vector3f a, Vector3f b, Vector3f postB, IInterp interpolation, float x)
    {
        this.i.x = (float) interpolation.interpolate(IInterp.context.set(preA.x, a.x, b.x, postB.x, x));
        this.i.y = (float) interpolation.interpolate(IInterp.context.set(preA.y, a.y, b.y, postB.y, x));
        this.i.z = (float) interpolation.interpolate(IInterp.context.set(preA.z, a.z, b.z, postB.z, x));

        return this.i;
    }
}

