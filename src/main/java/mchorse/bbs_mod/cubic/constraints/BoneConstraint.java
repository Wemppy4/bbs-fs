package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;

/**
 * One bone's rotation limits — the value of the bone's "constraints" property, both as the
 * form's static setting and as a film track's keyframe (one type for both, so no separate
 * animatable mirror is ever needed). The limits interpolate; {@code enabled} steps. An
 * all-default instance is neutral: the bone clamps nothing, as if the value didn't exist.
 */
public class BoneConstraint implements IMapSerializable
{
    public static final float DEFAULT_MIN = -180F;
    public static final float DEFAULT_MAX = 180F;

    public static final BoneConstraint DEFAULT = new BoneConstraint();

    public boolean enabled;
    public float minX = DEFAULT_MIN;
    public float minY = DEFAULT_MIN;
    public float minZ = DEFAULT_MIN;
    public float maxX = DEFAULT_MAX;
    public float maxY = DEFAULT_MAX;
    public float maxZ = DEFAULT_MAX;

    public void identity()
    {
        this.enabled = false;
        this.minX = DEFAULT_MIN;
        this.minY = DEFAULT_MIN;
        this.minZ = DEFAULT_MIN;
        this.maxX = DEFAULT_MAX;
        this.maxY = DEFAULT_MAX;
        this.maxZ = DEFAULT_MAX;
    }

    public void lerp(BoneConstraint preA, BoneConstraint a, BoneConstraint b, BoneConstraint postB, IInterp interp, float x)
    {
        this.minX = (float) interp.interpolate(IInterp.context.set(preA.minX, a.minX, b.minX, postB.minX, x));
        this.minY = (float) interp.interpolate(IInterp.context.set(preA.minY, a.minY, b.minY, postB.minY, x));
        this.minZ = (float) interp.interpolate(IInterp.context.set(preA.minZ, a.minZ, b.minZ, postB.minZ, x));
        this.maxX = (float) interp.interpolate(IInterp.context.set(preA.maxX, a.maxX, b.maxX, postB.maxX, x));
        this.maxY = (float) interp.interpolate(IInterp.context.set(preA.maxY, a.maxY, b.maxY, postB.maxY, x));
        this.maxZ = (float) interp.interpolate(IInterp.context.set(preA.maxZ, a.maxZ, b.maxZ, postB.maxZ, x));
        this.enabled = a.enabled;
    }

    public void autoLerp(BoneConstraint preA, BoneConstraint a, BoneConstraint b, BoneConstraint postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.minX = (float) AutoBezier.get(preA.minX, a.minX, b.minX, postB.minX, pt, at, bt, qt, clamped, x);
        this.minY = (float) AutoBezier.get(preA.minY, a.minY, b.minY, postB.minY, pt, at, bt, qt, clamped, x);
        this.minZ = (float) AutoBezier.get(preA.minZ, a.minZ, b.minZ, postB.minZ, pt, at, bt, qt, clamped, x);
        this.maxX = (float) AutoBezier.get(preA.maxX, a.maxX, b.maxX, postB.maxX, pt, at, bt, qt, clamped, x);
        this.maxY = (float) AutoBezier.get(preA.maxY, a.maxY, b.maxY, postB.maxY, pt, at, bt, qt, clamped, x);
        this.maxZ = (float) AutoBezier.get(preA.maxZ, a.maxZ, b.maxZ, postB.maxZ, pt, at, bt, qt, clamped, x);
        this.enabled = a.enabled;
    }

    public BoneConstraint copy()
    {
        BoneConstraint constraint = new BoneConstraint();

        constraint.copy(this);

        return constraint;
    }

    public void copy(BoneConstraint other)
    {
        this.enabled = other.enabled;
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
    }

    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj instanceof BoneConstraint constraint)
        {
            return this.enabled == constraint.enabled
                && this.minX == constraint.minX
                && this.minY == constraint.minY
                && this.minZ == constraint.minZ
                && this.maxX == constraint.maxX
                && this.maxY == constraint.maxY
                && this.maxZ == constraint.maxZ;
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        data.putBool("enabled", this.enabled);
        data.putDouble("min_x", this.minX);
        data.putDouble("min_y", this.minY);
        data.putDouble("min_z", this.minZ);
        data.putDouble("max_x", this.maxX);
        data.putDouble("max_y", this.maxY);
        data.putDouble("max_z", this.maxZ);
    }

    @Override
    public void fromData(MapType data)
    {
        this.enabled = data.getBool("enabled", DEFAULT.enabled);
        this.minX = (float) data.getDouble("min_x", DEFAULT.minX);
        this.minY = (float) data.getDouble("min_y", DEFAULT.minY);
        this.minZ = (float) data.getDouble("min_z", DEFAULT.minZ);
        this.maxX = (float) data.getDouble("max_x", DEFAULT.maxX);
        this.maxY = (float) data.getDouble("max_y", DEFAULT.maxY);
        this.maxZ = (float) data.getDouble("max_z", DEFAULT.maxZ);
    }
}
