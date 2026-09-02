package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * One bone's joint freedom for the IK solve — the value of the bone's "joint" property
 * (Blender's bone IK panel). Per axis: {@code lock} removes the axis from the solve entirely
 * (it stays frozen at its FK value, so an authored twist survives); {@code limit} clamps the
 * CHANNEL angle into [min, max] degrees — the same numbers the animator sees on the rotation
 * pads; {@code stiffness} 0..1 makes the axis increasingly reluctant to move, shifting the
 * bend to freer joints. One per bone of the model — a bone shared by several chains has one
 * set of joints, like a Blender pose bone. An all-default instance is neutral.
 *
 * <p>The serialized shape ({@code lock}/{@code limited}/{@code min}/{@code max}/{@code stiffness}
 * lists) is the one the IK blob always used, so presets and old forms read unchanged.</p>
 */
public class JointDoF implements IMapSerializable
{
    public static final float DEFAULT_MIN = -180F;
    public static final float DEFAULT_MAX = 180F;

    public static final JointDoF FREE = new JointDoF();

    public boolean lockX;
    public boolean lockY;
    public boolean lockZ;
    public boolean limitX;
    public boolean limitY;
    public boolean limitZ;
    public float minX = DEFAULT_MIN;
    public float minY = DEFAULT_MIN;
    public float minZ = DEFAULT_MIN;
    public float maxX = DEFAULT_MAX;
    public float maxY = DEFAULT_MAX;
    public float maxZ = DEFAULT_MAX;
    public float stiffnessX;
    public float stiffnessY;
    public float stiffnessZ;

    /** A free joint carries no information — it is not serialized and not handed to the solver. */
    public boolean isFree()
    {
        return !this.lockX && !this.lockY && !this.lockZ
            && !this.limitX && !this.limitY && !this.limitZ
            && this.stiffnessX <= 0F && this.stiffnessY <= 0F && this.stiffnessZ <= 0F;
    }

    public void identity()
    {
        this.lockX = this.lockY = this.lockZ = false;
        this.limitX = this.limitY = this.limitZ = false;
        this.minX = this.minY = this.minZ = DEFAULT_MIN;
        this.maxX = this.maxY = this.maxZ = DEFAULT_MAX;
        this.stiffnessX = this.stiffnessY = this.stiffnessZ = 0F;
    }

    public JointDoF copy()
    {
        JointDoF joint = new JointDoF();

        joint.copy(this);

        return joint;
    }

    public void copy(JointDoF other)
    {
        this.lockX = other.lockX;
        this.lockY = other.lockY;
        this.lockZ = other.lockZ;
        this.limitX = other.limitX;
        this.limitY = other.limitY;
        this.limitZ = other.limitZ;
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
        this.stiffnessX = other.stiffnessX;
        this.stiffnessY = other.stiffnessY;
        this.stiffnessZ = other.stiffnessZ;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj instanceof JointDoF joint)
        {
            return this.lockX == joint.lockX && this.lockY == joint.lockY && this.lockZ == joint.lockZ
                && this.limitX == joint.limitX && this.limitY == joint.limitY && this.limitZ == joint.limitZ
                && this.minX == joint.minX && this.minY == joint.minY && this.minZ == joint.minZ
                && this.maxX == joint.maxX && this.maxY == joint.maxY && this.maxZ == joint.maxZ
                && this.stiffnessX == joint.stiffnessX && this.stiffnessY == joint.stiffnessY && this.stiffnessZ == joint.stiffnessZ;
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        if (this.lockX || this.lockY || this.lockZ)
        {
            ListType lock = new ListType();

            lock.addBool(this.lockX);
            lock.addBool(this.lockY);
            lock.addBool(this.lockZ);
            data.put("lock", lock);
        }

        if (this.limitX || this.limitY || this.limitZ)
        {
            ListType limited = new ListType();

            limited.addBool(this.limitX);
            limited.addBool(this.limitY);
            limited.addBool(this.limitZ);
            data.put("limited", limited);

            ListType min = new ListType();

            min.addFloat(this.minX);
            min.addFloat(this.minY);
            min.addFloat(this.minZ);
            data.put("min", min);

            ListType max = new ListType();

            max.addFloat(this.maxX);
            max.addFloat(this.maxY);
            max.addFloat(this.maxZ);
            data.put("max", max);
        }

        if (this.stiffnessX > 0F || this.stiffnessY > 0F || this.stiffnessZ > 0F)
        {
            ListType stiffness = new ListType();

            stiffness.addFloat(this.stiffnessX);
            stiffness.addFloat(this.stiffnessY);
            stiffness.addFloat(this.stiffnessZ);
            data.put("stiffness", stiffness);
        }
    }

    @Override
    public void fromData(MapType data)
    {
        this.identity();

        if (data.has("lock", BaseType.TYPE_LIST))
        {
            ListType list = data.getList("lock");

            this.lockX = list.getBool(0);
            this.lockY = list.getBool(1);
            this.lockZ = list.getBool(2);
        }

        if (data.has("limited", BaseType.TYPE_LIST))
        {
            ListType list = data.getList("limited");

            this.limitX = list.getBool(0);
            this.limitY = list.getBool(1);
            this.limitZ = list.getBool(2);
        }

        if (data.has("min", BaseType.TYPE_LIST))
        {
            ListType list = data.getList("min");

            this.minX = getFloat(list, 0, DEFAULT_MIN);
            this.minY = getFloat(list, 1, DEFAULT_MIN);
            this.minZ = getFloat(list, 2, DEFAULT_MIN);
        }

        if (data.has("max", BaseType.TYPE_LIST))
        {
            ListType list = data.getList("max");

            this.maxX = getFloat(list, 0, DEFAULT_MAX);
            this.maxY = getFloat(list, 1, DEFAULT_MAX);
            this.maxZ = getFloat(list, 2, DEFAULT_MAX);
        }

        if (data.has("stiffness", BaseType.TYPE_LIST))
        {
            ListType list = data.getList("stiffness");

            this.stiffnessX = MathUtils.clamp(getFloat(list, 0, 0F), 0F, 1F);
            this.stiffnessY = MathUtils.clamp(getFloat(list, 1, 0F), 0F, 1F);
            this.stiffnessZ = MathUtils.clamp(getFloat(list, 2, 0F), 0F, 1F);
        }
    }

    private static float getFloat(ListType list, int index, float def)
    {
        BaseType element = list == null ? null : list.get(index);

        if (BaseType.isNumeric(element))
        {
            return element.asNumeric().floatValue();
        }

        return def;
    }
}
