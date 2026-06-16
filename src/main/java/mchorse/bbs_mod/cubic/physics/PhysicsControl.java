package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;

/**
 * The animatable per-chain physics scalars, layered over the form's physics config at
 * playback (the chain structure — root/end/target bone — stays on the config). Mirrors
 * {@link mchorse.bbs_mod.cubic.ik.IKControl}, the IK track's element. Floats interpolate;
 * the boolean steps.
 */
public class PhysicsControl implements IMapSerializable
{
    /* Mirrors ModelPhysicsIO's defaults; duplicated because that config class lives
     * in the client source set and this keyframe value lives in main. */
    public static final float DEFAULT_WEIGHT = 1F;
    public static final float DEFAULT_GRAVITY = 1F;
    public static final float DEFAULT_DAMPING = 0.15F;
    public static final float DEFAULT_STIFFNESS = 0F;

    public static final PhysicsControl DEFAULT = new PhysicsControl();

    public float weight = DEFAULT_WEIGHT;
    public float gravity = DEFAULT_GRAVITY;
    public float damping = DEFAULT_DAMPING;
    public float stiffness = DEFAULT_STIFFNESS;
    public boolean enabled = true;

    public void identity()
    {
        this.weight = DEFAULT_WEIGHT;
        this.gravity = DEFAULT_GRAVITY;
        this.damping = DEFAULT_DAMPING;
        this.stiffness = DEFAULT_STIFFNESS;
        this.enabled = true;
    }

    public void lerp(PhysicsControl preA, PhysicsControl a, PhysicsControl b, PhysicsControl postB, IInterp interp, float x)
    {
        this.weight = (float) interp.interpolate(IInterp.context.set(preA.weight, a.weight, b.weight, postB.weight, x));
        this.gravity = (float) interp.interpolate(IInterp.context.set(preA.gravity, a.gravity, b.gravity, postB.gravity, x));
        this.damping = (float) interp.interpolate(IInterp.context.set(preA.damping, a.damping, b.damping, postB.damping, x));
        this.stiffness = (float) interp.interpolate(IInterp.context.set(preA.stiffness, a.stiffness, b.stiffness, postB.stiffness, x));
        this.enabled = a.enabled;
    }

    public void autoLerp(PhysicsControl preA, PhysicsControl a, PhysicsControl b, PhysicsControl postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.weight = (float) AutoBezier.get(preA.weight, a.weight, b.weight, postB.weight, pt, at, bt, qt, clamped, x);
        this.gravity = (float) AutoBezier.get(preA.gravity, a.gravity, b.gravity, postB.gravity, pt, at, bt, qt, clamped, x);
        this.damping = (float) AutoBezier.get(preA.damping, a.damping, b.damping, postB.damping, pt, at, bt, qt, clamped, x);
        this.stiffness = (float) AutoBezier.get(preA.stiffness, a.stiffness, b.stiffness, postB.stiffness, pt, at, bt, qt, clamped, x);
        this.enabled = a.enabled;
    }

    public PhysicsControl copy()
    {
        PhysicsControl control = new PhysicsControl();

        control.copy(this);

        return control;
    }

    public void copy(PhysicsControl other)
    {
        this.weight = other.weight;
        this.gravity = other.gravity;
        this.damping = other.damping;
        this.stiffness = other.stiffness;
        this.enabled = other.enabled;
    }

    public boolean isDefault()
    {
        return this.weight == DEFAULT.weight
            && this.gravity == DEFAULT.gravity
            && this.damping == DEFAULT.damping
            && this.stiffness == DEFAULT.stiffness
            && this.enabled == DEFAULT.enabled;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj instanceof PhysicsControl control)
        {
            return this.weight == control.weight
                && this.gravity == control.gravity
                && this.damping == control.damping
                && this.stiffness == control.stiffness
                && this.enabled == control.enabled;
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        data.putDouble("weight", this.weight);
        data.putDouble("gravity", this.gravity);
        data.putDouble("damping", this.damping);
        data.putDouble("stiffness", this.stiffness);
        data.putBool("enabled", this.enabled);
    }

    @Override
    public void fromData(MapType data)
    {
        this.weight = (float) data.getDouble("weight", DEFAULT.weight);
        this.gravity = (float) data.getDouble("gravity", DEFAULT.gravity);
        this.damping = (float) data.getDouble("damping", DEFAULT.damping);
        this.stiffness = (float) data.getDouble("stiffness", DEFAULT.stiffness);
        this.enabled = data.getBool("enabled", DEFAULT.enabled);
    }
}
