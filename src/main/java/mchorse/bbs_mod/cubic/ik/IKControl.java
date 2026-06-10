package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;

/**
 * The animatable per-chain IK scalars, layered over the form's IK config at
 * playback (the chain structure — tip/target/pole bone — stays on the config).
 * Mirrors {@link mchorse.bbs_mod.utils.pose.PoseTransform} as the element of the
 * {@link IKControls} keyframe container. Floats interpolate; the booleans step.
 */
public class IKControl implements IMapSerializable
{
    /* Mirrors ModelIKConfig's defaults; duplicated because that config class lives
     * in the client source set and this keyframe value lives in main. */
    public static final float DEFAULT_WEIGHT = 1F;
    public static final float DEFAULT_SOFTNESS = 0.05F;

    public static final IKControl DEFAULT = new IKControl();

    public float weight = DEFAULT_WEIGHT;
    public float softness = DEFAULT_SOFTNESS;
    public boolean enabled = true;
    public boolean pole = true;

    public void identity()
    {
        this.weight = DEFAULT_WEIGHT;
        this.softness = DEFAULT_SOFTNESS;
        this.enabled = true;
        this.pole = true;
    }

    public void lerp(IKControl preA, IKControl a, IKControl b, IKControl postB, IInterp interp, float x)
    {
        this.weight = (float) interp.interpolate(IInterp.context.set(preA.weight, a.weight, b.weight, postB.weight, x));
        this.softness = (float) interp.interpolate(IInterp.context.set(preA.softness, a.softness, b.softness, postB.softness, x));
        this.enabled = a.enabled;
        this.pole = a.pole;
    }

    public void autoLerp(IKControl preA, IKControl a, IKControl b, IKControl postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.weight = (float) AutoBezier.get(preA.weight, a.weight, b.weight, postB.weight, pt, at, bt, qt, clamped, x);
        this.softness = (float) AutoBezier.get(preA.softness, a.softness, b.softness, postB.softness, pt, at, bt, qt, clamped, x);
        this.enabled = a.enabled;
        this.pole = a.pole;
    }

    public IKControl copy()
    {
        IKControl control = new IKControl();

        control.copy(this);

        return control;
    }

    public void copy(IKControl other)
    {
        this.weight = other.weight;
        this.softness = other.softness;
        this.enabled = other.enabled;
        this.pole = other.pole;
    }

    public boolean isDefault()
    {
        return this.weight == DEFAULT.weight
            && this.softness == DEFAULT.softness
            && this.enabled == DEFAULT.enabled
            && this.pole == DEFAULT.pole;
    }

    @Override
    public void toData(MapType data)
    {
        data.putDouble("weight", this.weight);
        data.putDouble("softness", this.softness);
        data.putBool("enabled", this.enabled);
        data.putBool("pole", this.pole);
    }

    @Override
    public void fromData(MapType data)
    {
        this.weight = (float) data.getDouble("weight", DEFAULT.weight);
        this.softness = (float) data.getDouble("softness", DEFAULT.softness);
        this.enabled = data.getBool("enabled", DEFAULT.enabled);
        this.pole = data.getBool("pole", DEFAULT.pole);
    }
}
