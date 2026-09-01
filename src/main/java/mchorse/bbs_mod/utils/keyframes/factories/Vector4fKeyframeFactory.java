package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import org.joml.Vector4f;

public class Vector4fKeyframeFactory implements IKeyframeFactory<Vector4f>
{
    /* JOML vectors hash by content. */
    @Override
    public int contentHash(Vector4f value)
    {
        return value.hashCode();
    }

    private Vector4f i = new Vector4f();

    @Override
    public Vector4f fromData(BaseType data)
    {
        return data.isList() ? DataStorageUtils.vector4fFromData(data.asList()) : new Vector4f();
    }

    @Override
    public BaseType toData(Vector4f value)
    {
        return DataStorageUtils.vector4fToData(value);
    }

    @Override
    public Vector4f createEmpty()
    {
        return new Vector4f();
    }

    @Override
    public Vector4f copy(Vector4f value)
    {
        return new Vector4f(value);
    }

    @Override
    public Vector4f interpolate(Vector4f preA, Vector4f a, Vector4f b, Vector4f postB, IInterp interpolation, float x)
    {
        this.i.x = (float) interpolation.interpolate(IInterp.context.set(preA.x, a.x, b.x, postB.x, x));
        this.i.y = (float) interpolation.interpolate(IInterp.context.set(preA.y, a.y, b.y, postB.y, x));
        this.i.z = (float) interpolation.interpolate(IInterp.context.set(preA.z, a.z, b.z, postB.z, x));
        this.i.w = (float) interpolation.interpolate(IInterp.context.set(preA.w, a.w, b.w, postB.w, x));

        return this.i;
    }
}