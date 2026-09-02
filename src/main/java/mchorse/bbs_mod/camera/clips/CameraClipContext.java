package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public class CameraClipContext extends ClipContext<CameraClip, Position>
{
    /** The film's entities keyed by their replay's stable id, in replay-list order. */
    public Map<String, IEntity> entities = new LinkedHashMap<>();
    private Position lastPosition = new Position();
    private Map<Clip, Position> snapshots = new HashMap<>();
    private boolean captureSnapshots;

    public void captureSnapshots()
    {
        this.captureSnapshots = true;
    }

    public Map<Clip, Position> getSnapshots()
    {
        return this.snapshots;
    }

    @Override
    public ClipContext setup(int ticks, int relativeTick, float transition, int currentLayer)
    {
        this.snapshots.clear();

        return super.setup(ticks, relativeTick, transition, currentLayer);
    }

    @Override
    public boolean applyUnderneath(int ticks, float transition, Position position, Predicate<Clip> filter)
    {
        boolean capture = this.captureSnapshots;

        if (capture) this.captureSnapshots = false;

        boolean result = super.applyUnderneath(ticks, transition, position, filter);

        if (capture) this.captureSnapshots = true;

        return result;
    }

    @Override
    public boolean apply(Clip clip, Position position)
    {
        if (clip instanceof CameraClip)
        {
            this.currentLayer = clip.layer.get();
            this.relativeTick = this.ticks - clip.tick.get();

            ((CameraClip) clip).apply(this, position);

            if (this.captureSnapshots)
            {
                Position snapshot = new Position();

                snapshot.copy(position);
                this.snapshots.put(clip, snapshot);
            }

            double dx = position.point.x - this.lastPosition.point.x;
            double dy = position.point.y - this.lastPosition.point.y;
            double dz = position.point.z - this.lastPosition.point.z;

            if (Double.isNaN(this.distance))
            {
                this.distance = 0;
            }

            this.velocity = Math.sqrt(dx * dx + dy * dy + dz * dz);
            this.distance += this.velocity;

            this.lastPosition.copy(position);

            this.count += 1;

            return true;
        }

        return false;
    }

    public void shutdown()
    {
        if (this.clips == null)
        {
            return;
        }

        for (Clip clip : this.clips.get())
        {
            if (clip instanceof CameraClip cameraClip)
            {
                cameraClip.shutdown(this);
            }
        }
    }
}