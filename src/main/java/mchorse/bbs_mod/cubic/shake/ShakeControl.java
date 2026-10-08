package mchorse.bbs_mod.cubic.shake;

import mchorse.bbs_mod.cubic.chains.ChainControl;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import org.joml.Vector3f;

/**
 * One bone's shake, the element of the shake track the way {@code PhysicsControl} is the physics
 * track's: the form keeps the bone's own setting, a keyframe replaces it for its stretch of the
 * take. Whether the bone shakes at all is the bone's switch, not part of this.
 *
 * <p>An axis shakes when its amplitude isn't zero, so a fresh shake holds still until one is set.
 * Rotation is in degrees, translation and scale in the units of the pose they add onto. Numbers
 * interpolate; the variant steps, since a pattern halfway between two others doesn't exist.</p>
 */
public class ShakeControl extends ChainControl<ShakeControl>
{
    public static final float DEFAULT_STRENGTH = 1F;
    public static final float DEFAULT_FREQUENCY = 5F;
    public static final float DEFAULT_ROUGHNESS = 0.3F;

    public static final ShakeControl DEFAULT = new ShakeControl();

    public float strength = DEFAULT_STRENGTH;
    public final Vector3f translate = new Vector3f();
    public final Vector3f rotate = new Vector3f();
    public final Vector3f scale = new Vector3f();

    /** Swings there and back per second. */
    public float frequency = DEFAULT_FREQUENCY;

    /** How much finer, faster jitter rides on the main swing, 0..1. */
    public float roughness = DEFAULT_ROUGHNESS;

    /** Picks another pattern for the same settings. */
    public int seed;

    @Override
    public void identity()
    {
        this.bindings.clear();
        this.strength = DEFAULT_STRENGTH;
        this.translate.set(0F);
        this.rotate.set(0F);
        this.scale.set(0F);
        this.frequency = DEFAULT_FREQUENCY;
        this.roughness = DEFAULT_ROUGHNESS;
        this.seed = 0;
    }

    @Override
    public void lerp(ShakeControl preA, ShakeControl a, ShakeControl b, ShakeControl postB, IInterp interp, float x)
    {
        this.lerpBindings(preA, a, b, postB, interp, x, 0, 0, 0, 0, false);
        this.strength = lerp(interp, preA.strength, a.strength, b.strength, postB.strength, x);
        this.frequency = lerp(interp, preA.frequency, a.frequency, b.frequency, postB.frequency, x);
        this.roughness = lerp(interp, preA.roughness, a.roughness, b.roughness, postB.roughness, x);
        lerp(this.translate, interp, preA.translate, a.translate, b.translate, postB.translate, x);
        lerp(this.rotate, interp, preA.rotate, a.rotate, b.rotate, postB.rotate, x);
        lerp(this.scale, interp, preA.scale, a.scale, b.scale, postB.scale, x);
        this.seed = a.seed;
    }

    @Override
    public void autoLerp(ShakeControl preA, ShakeControl a, ShakeControl b, ShakeControl postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.lerpBindings(preA, a, b, postB, clamped ? Interpolations.AUTO_CLAMPED : Interpolations.AUTO, x, pt, at, bt, qt, true);
        this.strength = (float) AutoBezier.get(preA.strength, a.strength, b.strength, postB.strength, pt, at, bt, qt, clamped, x);
        this.frequency = (float) AutoBezier.get(preA.frequency, a.frequency, b.frequency, postB.frequency, pt, at, bt, qt, clamped, x);
        this.roughness = (float) AutoBezier.get(preA.roughness, a.roughness, b.roughness, postB.roughness, pt, at, bt, qt, clamped, x);
        autoLerp(this.translate, preA.translate, a.translate, b.translate, postB.translate, pt, at, bt, qt, clamped, x);
        autoLerp(this.rotate, preA.rotate, a.rotate, b.rotate, postB.rotate, pt, at, bt, qt, clamped, x);
        autoLerp(this.scale, preA.scale, a.scale, b.scale, postB.scale, pt, at, bt, qt, clamped, x);
        this.seed = a.seed;
    }

    private static float lerp(IInterp interp, float preA, float a, float b, float postB, float x)
    {
        return (float) interp.interpolate(IInterp.context.set(preA, a, b, postB, x));
    }

    private static void lerp(Vector3f out, IInterp interp, Vector3f preA, Vector3f a, Vector3f b, Vector3f postB, float x)
    {
        out.set(
            lerp(interp, preA.x, a.x, b.x, postB.x, x),
            lerp(interp, preA.y, a.y, b.y, postB.y, x),
            lerp(interp, preA.z, a.z, b.z, postB.z, x)
        );
    }

    private static void autoLerp(Vector3f out, Vector3f preA, Vector3f a, Vector3f b, Vector3f postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        out.set(
            (float) AutoBezier.get(preA.x, a.x, b.x, postB.x, pt, at, bt, qt, clamped, x),
            (float) AutoBezier.get(preA.y, a.y, b.y, postB.y, pt, at, bt, qt, clamped, x),
            (float) AutoBezier.get(preA.z, a.z, b.z, postB.z, pt, at, bt, qt, clamped, x)
        );
    }

    @Override
    public ShakeControl copy()
    {
        ShakeControl control = new ShakeControl();

        control.copy(this);

        return control;
    }

    @Override
    public void copy(ShakeControl other)
    {
        this.copyBindings(other);
        this.strength = other.strength;
        this.translate.set(other.translate);
        this.rotate.set(other.rotate);
        this.scale.set(other.scale);
        this.frequency = other.frequency;
        this.roughness = other.roughness;
        this.seed = other.seed;
    }

    @Override
    public boolean isDefault()
    {
        return !this.hasBindingsOrMetadata() && this.sameValues(DEFAULT);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        return obj instanceof ShakeControl control && this.sameBindings(control) && this.sameValues(control);
    }

    private boolean sameValues(ShakeControl control)
    {
        return this.strength == control.strength
            && this.translate.equals(control.translate)
            && this.rotate.equals(control.rotate)
            && this.scale.equals(control.scale)
            && this.frequency == control.frequency
            && this.roughness == control.roughness
            && this.seed == control.seed;
    }

    @Override
    public void toData(MapType data)
    {
        this.writeBindings(data);
        data.putFloat("strength", this.strength);
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
        this.readBindings(data);
        this.strength = data.getFloat("strength", DEFAULT_STRENGTH);
        this.frequency = data.getFloat("frequency", DEFAULT_FREQUENCY);
        this.roughness = data.getFloat("roughness", DEFAULT_ROUGHNESS);
        this.seed = data.getInt("seed", 0);
        this.translate.set(0F);
        this.rotate.set(0F);
        this.scale.set(0F);

        if (data.has("translate", BaseType.TYPE_LIST)) this.translate.set(DataStorageUtils.vector3fFromData(data.getList("translate")));
        if (data.has("rotate", BaseType.TYPE_LIST)) this.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        if (data.has("scale", BaseType.TYPE_LIST)) this.scale.set(DataStorageUtils.vector3fFromData(data.getList("scale")));
    }
}
