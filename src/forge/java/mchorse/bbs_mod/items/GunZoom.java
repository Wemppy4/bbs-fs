package mchorse.bbs_mod.items;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;

public class GunZoom
{
    public float targetFOV;
    public Interpolation interp;
    public int duration;
    public float factor;
    public boolean lastPressed;

    public GunZoom(float targetFOV, Interpolation interp, int duration)
    {
        this.targetFOV = targetFOV;
        this.interp = interp;
        this.duration = duration;
        this.factor = duration + 1;
    }

    public float getFOV(float initialFOV)
    {
        if (this.duration <= 0) return this.lastPressed ? this.targetFOV : initialFOV;
        return (float) this.interp.interpolate(IInterp.context.set(this.targetFOV, initialFOV, MathUtils.clamp(this.factor / (float) this.duration, 0F, 1F)));
    }

    public void update(boolean pressed, float delta)
    {
        this.lastPressed = pressed;

        this.factor += pressed ? -delta : delta;
        this.factor = MathUtils.clamp(this.factor, 0, this.duration + 1);
    }

    public boolean canBeRemoved()
    {
        return this.factor >= this.duration + 1;
    }
}