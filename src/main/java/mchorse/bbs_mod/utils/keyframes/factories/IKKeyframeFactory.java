package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.HashSet;
import java.util.Set;

public class IKKeyframeFactory implements IKeyframeFactory<IKControls>
{
    private static Set<String> keys = new HashSet<>();

    private IKControls i = new IKControls();

    @Override
    public IKControls fromData(BaseType data)
    {
        IKControls controls = new IKControls();

        if (data.isMap())
        {
            controls.fromData(data.asMap());
        }

        return controls;
    }

    @Override
    public BaseType toData(IKControls value)
    {
        return value.toData();
    }

    @Override
    public IKControls createEmpty()
    {
        return new IKControls();
    }

    @Override
    public IKControls copy(IKControls value)
    {
        return value.copy();
    }

    @Override
    public IKControls interpolate(Keyframe<IKControls> preA, Keyframe<IKControls> a, Keyframe<IKControls> b, Keyframe<IKControls> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            IKControls preAp = preA.getValue();
            IKControls ap = a.getValue();
            IKControls bp = b.getValue();
            IKControls postBp = postB.getValue();

            this.collect(preAp, ap, bp, postBp);

            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float pt = preA.getTick();
            float at = a.getTick();
            float bt = b.getTick();
            float qt = postB.getTick();

            for (String key : keys)
            {
                this.i.get(key).autoLerp(preAp.get(key), ap.get(key), bp.get(key), postBp.get(key), pt, at, bt, qt, clamped, x);
            }

            return this.i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public IKControls interpolate(IKControls preA, IKControls a, IKControls b, IKControls postB, IInterp interpolation, float x)
    {
        this.collect(preA, a, b, postB);

        for (String key : keys)
        {
            this.i.get(key).lerp(preA.get(key), a.get(key), b.get(key), postB.get(key), interpolation, x);
        }

        return this.i;
    }

    private void collect(IKControls preA, IKControls a, IKControls b, IKControls postB)
    {
        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.controls.keySet());
        if (a != null) keys.addAll(a.controls.keySet());
        if (b != null) keys.addAll(b.controls.keySet());
        if (postB != b && postB != null) keys.addAll(postB.controls.keySet());

        for (IKControl value : this.i.controls.values())
        {
            value.identity();
        }
    }
}
