package mchorse.bbs_mod.cubic.chains;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.utils.interps.IInterp;

/**
 * The animatable scalars of a single solver chain, layered over the form's own config at
 * playback (the chain structure stays on the config). The element of a {@link ChainControls}
 * keyframe value — see {@link mchorse.bbs_mod.cubic.ik.IKControl} and
 * {@link mchorse.bbs_mod.cubic.physics.PhysicsControl}.
 */
public abstract class ChainControl <C extends ChainControl<C>> implements IMapSerializable
{
    /** Reset to the defaults — what a chain the keyframe doesn't mention behaves like. */
    public abstract void identity();

    public abstract C copy();

    public abstract void copy(C other);

    public abstract boolean isDefault();

    /** Ease this control from {@code a} to {@code b}; the flags step, so they take {@code a}'s. */
    public abstract void lerp(C preA, C a, C b, C postB, IInterp interp, float x);

    public abstract void autoLerp(C preA, C a, C b, C postB, float pt, float at, float bt, float qt, boolean clamped, float x);
}
