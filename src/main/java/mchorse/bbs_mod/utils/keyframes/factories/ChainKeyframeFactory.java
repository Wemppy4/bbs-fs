package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.chains.ChainControl;
import mchorse.bbs_mod.cubic.chains.ChainControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.HashSet;
import java.util.Set;

/**
 * Keyframe factory for a solver track's per-chain scalars. Interpolation runs over the union of
 * the chains the surrounding keyframes mention, the way the pose track interpolates over bones,
 * so a chain that appears on only one side still eases in from its defaults.
 */
public abstract class ChainKeyframeFactory <C extends ChainControl<C>, S extends ChainControls<C, S>> implements IKeyframeFactory<S>
{
    private final Set<String> keys = new HashSet<>();

    /** Reused across frames: interpolation happens every frame and its result is read at once. */
    private final S interpolated = this.createEmpty();

    @Override
    public S fromData(BaseType data)
    {
        S controls = this.createEmpty();

        if (data.isMap())
        {
            controls.fromData(data.asMap());
        }

        return controls;
    }

    @Override
    public BaseType toData(S value)
    {
        return value.toData();
    }

    @Override
    public S copy(S value)
    {
        return value.copy();
    }

    @Override
    public S interpolate(Keyframe<S> preA, Keyframe<S> a, Keyframe<S> b, Keyframe<S> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            S preAp = preA.getValue();
            S ap = a.getValue();
            S bp = b.getValue();
            S postBp = postB.getValue();

            this.collect(preAp, ap, bp, postBp);

            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float pt = preA.getTick();
            float at = a.getTick();
            float bt = b.getTick();
            float qt = postB.getTick();

            for (String key : this.keys)
            {
                this.interpolated.get(key).autoLerp(preAp.get(key), ap.get(key), bp.get(key), postBp.get(key), pt, at, bt, qt, clamped, x);
            }

            return this.interpolated;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public S interpolate(S preA, S a, S b, S postB, IInterp interpolation, float x)
    {
        this.collect(preA, a, b, postB);

        for (String key : this.keys)
        {
            this.interpolated.get(key).lerp(preA.get(key), a.get(key), b.get(key), postB.get(key), interpolation, x);
        }

        return this.interpolated;
    }

    private void collect(S preA, S a, S b, S postB)
    {
        this.keys.clear();

        if (preA != a && preA != null) this.keys.addAll(preA.controls.keySet());
        if (a != null) this.keys.addAll(a.controls.keySet());
        if (b != null) this.keys.addAll(b.controls.keySet());
        if (postB != b && postB != null) this.keys.addAll(postB.controls.keySet());

        for (C value : this.interpolated.controls.values())
        {
            value.identity();
        }
    }
}
