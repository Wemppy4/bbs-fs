package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.InterpContext;

/** A camera pass between two percentages of a spline, timed by the clip itself. */
public class SplineClip extends EntityClip
{
    public final ValueString group = new ValueString("group", "");
    public final ValueFloat start = new ValueFloat("start", 0F);
    public final ValueFloat end = new ValueFloat("end", 100F);
    public final Interpolation interp = new Interpolation("interp", Interpolations.MAP);
    /** 0: independent angles, 1: tangent, 2: horizontal tangent. */
    public final ValueInt pathRotation = new ValueInt("path_rotation", 1, 0, 2);
    public final ValuePoint angle = new ValuePoint("angle", new Point(0, 0, 0));
    public final ValueFloat fov = new ValueFloat("fov", 70F);
    public final ValueInt active = new ValueInt("active", 0b1111111, 0, 0b1111111);

    private final InterpContext interpolationContext = new InterpContext();

    public SplineClip()
    {
        this.add(this.group);
        this.add(this.start);
        this.add(this.end);
        this.add(this.interp);
        this.add(this.pathRotation);
        this.add(this.angle);
        this.add(this.fov);
        this.add(this.active);
    }

    public float progress(float tick)
    {
        float factor = Math.max(0F, Math.min(1F, tick / this.duration.get()));
        return (float) this.interp.interpolate(this.interpolationContext
            .set(this.start.get(), this.end.get(), factor).segment(this.duration.get(), 0));
    }

    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);
        SplineClip first = (SplineClip) original;
        float progress = first.progress(offset);
        this.start.set(progress);
        first.end.set(progress);
    }

    @Override
    public boolean placesCamera()
    {
        return true;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new SplineClip();
    }
}
