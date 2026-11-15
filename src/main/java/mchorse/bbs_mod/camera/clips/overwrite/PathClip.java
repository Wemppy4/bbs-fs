package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePositions;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Path camera fixture
 *
 * This fixture is responsible for making smooth camera movements.
 */
public class PathClip extends CameraClip
{
    /**
     * List of points in this fixture
     */
    public final ValuePositions points = new ValuePositions("points");

    public final Interpolation interpolationPoint = new Interpolation("interpPoint", Interpolations.MAP, Interpolations.HERMITE);
    public final Interpolation interpolationAngle = new Interpolation("interpAngle", Interpolations.MAP, Interpolations.HERMITE);

    public PathClip()
    {
        super();

        this.add(this.points);
        this.add(this.interpolationPoint);
        this.add(this.interpolationAngle);
    }

    public Position getPoint(int index)
    {
        int size = this.size();

        if (size == 0)
        {
            return new Position(0, 0, 0, 0, 0);
        }

        if (index >= size)
        {
            return this.points.get(size - 1);
        }

        if (index < 0)
        {
            return this.points.get(0);
        }

        return this.points.get(index);
    }

    public int size()
    {
        return this.points.size();
    }

    /**
     * Return the frame for point at the index   
     */
    public int getTickForPoint(int index)
    {
        return (int) ((index / (float) (this.size() - 1)) * this.duration.get());
    }

    @Override
    public void fromCamera(Camera camera)
    {
        this.points.add(new Position(camera));
    }

    @Override
    public boolean placesCamera()
    {
        return true;
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        int duration = this.duration.get();

        if (this.points.size() == 0 || duration == 0)
        {
            return;
        }

        int length = this.size() - 1;
        int index;
        float x;

        x = (context.relativeTick + context.transition) / (float) duration;
        x = MathUtils.clamp(x * length, 0, length);
        index = (int) Math.floor(x);
        x = x - index;

        this.applyAngle(position.angle, index, x);
        this.applyPoint(position.point, index, x);
    }

    /**
     * Apply point 
     */
    private void applyPoint(Point point, int index, float progress)
    {
        Position p0 = this.getPoint(index - 1);
        Position p1 = this.getPoint(index);
        Position p2 = this.getPoint(index + 1);
        Position p3 = this.getPoint(index + 2);

        double x = this.interpolationPoint.interpolate(IInterp.context.set(p0.point.x, p1.point.x, p2.point.x, p3.point.x, progress));
        double y = this.interpolationPoint.interpolate(IInterp.context.set(p0.point.y, p1.point.y, p2.point.y, p3.point.y, progress));
        double z = this.interpolationPoint.interpolate(IInterp.context.set(p0.point.z, p1.point.z, p2.point.z, p3.point.z, progress));

        point.set(x, y, z);
    }

    /**
     * Apply angle  
     */
    private void applyAngle(Angle angle, int index, float progress)
    {
        Position p0 = this.getPoint(index - 1);
        Position p1 = this.getPoint(index);
        Position p2 = this.getPoint(index + 1);
        Position p3 = this.getPoint(index + 2);

        /* Interpolating the angle */
        float yaw   = (float) this.interpolationAngle.interpolate(IInterp.context.set(p0.angle.yaw, p1.angle.yaw, p2.angle.yaw, p3.angle.yaw, progress));
        float pitch = (float) this.interpolationAngle.interpolate(IInterp.context.set(p0.angle.pitch, p1.angle.pitch, p2.angle.pitch, p3.angle.pitch, progress));
        float roll  = (float) this.interpolationAngle.interpolate(IInterp.context.set(p0.angle.roll, p1.angle.roll, p2.angle.roll, p3.angle.roll, progress));
        float fov   = (float) this.interpolationAngle.interpolate(IInterp.context.set(p0.angle.fov, p1.angle.fov, p2.angle.fov, p3.angle.fov, progress));

        angle.set(yaw, pitch, roll, fov);
    }

    @Override
    public Clip create()
    {
        return new PathClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        if (this.points.size() < 2)
        {
            return;
        }

        PathClip path = (PathClip) original;
        Position position = new Position();

        path.apply(new CameraClipContext().setup(offset, 0), position);

        float factor = (offset / (float) original.duration.get()) * (this.size() - 1);
        int originalPoints = (int) Math.ceil(factor);
        int thisPoints = (int) Math.floor(factor);

        List<Position> oP = new ArrayList<>();
        List<Position> tP = new ArrayList<>();

        for (int i = 0; i < originalPoints; i++)
        {
            oP.add(path.points.get(i).copy());
        }

        oP.add(position.copy());

        for (int i = this.points.size() - 1; i > thisPoints; i--)
        {
            tP.add(this.points.get(i).copy());
        }

        tP.add(position.copy());

        Collections.reverse(tP);

        path.points.set(oP);
        this.points.set(tP);
    }
}