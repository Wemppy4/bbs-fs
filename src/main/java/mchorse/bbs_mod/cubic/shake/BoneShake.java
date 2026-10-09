package mchorse.bbs_mod.cubic.shake;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import org.joml.Vector3f;

/**
 * One bone's shake — its character, configured on the bone like the IK chain's structure. The
 * animatable strength is not here: it is the form's {@code shake} track ({@link ShakeControl}),
 * so a take fades the shake in and out without freezing everything else into its keyframes.
 *
 * <p>An axis shakes when its amplitude isn't zero; there are no per-axis switches. Rotation is
 * in degrees, translation and scale in the units of the pose they add onto. An all-default
 * instance is neutral: the bone holds still, as if the value didn't exist.</p>
 */
public class BoneShake implements IMapSerializable
{
    public static final float DEFAULT_FREQUENCY = 5F;
    public static final float DEFAULT_ROUGHNESS = 0.3F;

    /** The tremble a bone starts with when its shake is switched on: one that shows at once. */
    public static final float STARTER_ROTATION = 3F;

    public static final BoneShake DEFAULT = new BoneShake();

    public boolean enabled;
    public final Vector3f translate = new Vector3f();
    public final Vector3f rotate = new Vector3f();
    public final Vector3f scale = new Vector3f();

    /** Swings there and back per second. */
    public float frequency = DEFAULT_FREQUENCY;

    /** How much finer, faster jitter rides on the main swing, 0..1. */
    public float roughness = DEFAULT_ROUGHNESS;

    /** Picks another pattern for the same settings. */
    public int seed;

    /** A switched-on shake with the starter tremble on every rotation axis. */
    public static BoneShake starter()
    {
        BoneShake shake = new BoneShake();

        shake.enabled = true;
        shake.rotate.set(STARTER_ROTATION);

        return shake;
    }

    public void identity()
    {
        this.enabled = false;
        this.translate.set(0F);
        this.rotate.set(0F);
        this.scale.set(0F);
        this.frequency = DEFAULT_FREQUENCY;
        this.roughness = DEFAULT_ROUGHNESS;
        this.seed = 0;
    }

    public BoneShake copy()
    {
        BoneShake shake = new BoneShake();

        shake.copy(this);

        return shake;
    }

    public void copy(BoneShake other)
    {
        this.enabled = other.enabled;
        this.translate.set(other.translate);
        this.rotate.set(other.rotate);
        this.scale.set(other.scale);
        this.frequency = other.frequency;
        this.roughness = other.roughness;
        this.seed = other.seed;
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

        if (obj instanceof BoneShake shake)
        {
            return this.enabled == shake.enabled
                && this.translate.equals(shake.translate)
                && this.rotate.equals(shake.rotate)
                && this.scale.equals(shake.scale)
                && this.frequency == shake.frequency
                && this.roughness == shake.roughness
                && this.seed == shake.seed;
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        /* A neutral shake carries nothing, so every bone that never shook stays as small as it was. */
        if (this.isDefault())
        {
            return;
        }

        data.putBool("enabled", this.enabled);
        data.put("translate", DataStorageUtils.vector3fToData(this.translate));
        data.put("rotate", DataStorageUtils.vector3fToData(this.rotate));
        data.put("scale", DataStorageUtils.vector3fToData(this.scale));
        data.putFloat("frequency", this.frequency);
        data.putFloat("roughness", this.roughness);
        data.putInt("seed", this.seed);
    }

    @Override
    public void fromData(MapType data)
    {
        this.identity();

        this.enabled = data.getBool("enabled", false);
        this.frequency = data.getFloat("frequency", DEFAULT_FREQUENCY);
        this.roughness = data.getFloat("roughness", DEFAULT_ROUGHNESS);
        this.seed = data.getInt("seed", 0);

        if (data.has("translate", BaseType.TYPE_LIST)) this.translate.set(DataStorageUtils.vector3fFromData(data.getList("translate")));
        if (data.has("rotate", BaseType.TYPE_LIST)) this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        if (data.has("scale", BaseType.TYPE_LIST)) this.scale.set(DataStorageUtils.vector3fFromData(data.getList("scale")));
    }
}
