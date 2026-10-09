package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.TrackerClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import org.joml.Vector3d;

import java.util.List;

public class TrackerClientClip extends TrackerClip
{
    /**
     * Position produced by the clips underneath at the tick this clip was last
     * applied at. Relative mode measures the camera's travel against it, and the
     * camera editor needs the same measurement to invert the placement — see
     * {@link mchorse.bbs_mod.ui.film.clips.UITrackerClip}.
     */
    private final Position underneath = new Position();

    private boolean evaluated;

    /**
     * Whether this clip has been applied at least once, i.e. whether
     * {@link #getUnderneath()} holds anything meaningful.
     */
    public boolean isEvaluated()
    {
        return this.evaluated;
    }

    public Position getUnderneath()
    {
        return this.underneath;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<IEntity> entities = this.getEntities(context);

        if (entities.isEmpty())
        {
            return;
        }

        if (!context.applyUnderneath(this.tick.get(), 0F, this.position))
        {
            this.position.copy(position);
        }

        TrackerFrame frame = TrackerFrame.resolve(
            ((CameraClipContext) context).entities,
            entities.get(0),
            this.group.get(),
            position.point.x,
            position.point.y,
            position.point.z,
            context.transition
        );

        if (frame == null)
        {
            return;
        }

        if (this.relative.get())
        {
            frame.relative(position, this.position);
        }

        this.underneath.copy(position);
        this.evaluated = true;

        Point offset = this.offset.get();
        Point angle = this.angle.get();
        Angle newAngle = this.lookAt.get() ? frame.lookAtAngles(offset, angle) : frame.angles(angle);

        if (!this.lookAt.get())
        {
            Vector3d newPosition = frame.position(offset);

            position.point.x = this.isActive(0) ? newPosition.x : position.point.x;
            position.point.y = this.isActive(1) ? newPosition.y : position.point.y;
            position.point.z = this.isActive(2) ? newPosition.z : position.point.z;
        }

        position.angle.yaw = this.isActive(3) ? newAngle.yaw : position.angle.yaw;
        position.angle.pitch = this.isActive(4) ? newAngle.pitch : position.angle.pitch;
        position.angle.roll = this.isActive(5) ? newAngle.roll : position.angle.roll;
        position.angle.fov = this.isActive(6) ? this.fov.get() : position.angle.fov;
    }

    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }

    @Override
    protected Clip create()
    {
        return new TrackerClientClip();
    }
}
