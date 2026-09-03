package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.EnumSet;

/**
 * The gizmo target for a replay's own placement in the world &mdash; where the actor
 * stands and which way it faces &mdash; as opposed to a bone inside its form.
 *
 * <p>A replay has no {@link Transform}: its placement lives in the keyframe channels
 * {@code x/y/z} and {@code yaw/pitch/headYaw/bodyYaw}. This editor is the adapter
 * between the two. It holds a scratch transform for the gizmo to read and turns every
 * write back into keyframes at the playhead, which is what "the pose on the current
 * frame" means for a record.
 *
 * <p>The mapping, and why:
 * <ul>
 * <li>{@code translate} is the world position, one unit per block, so the drag needs no
 *     Jacobian of its own.</li>
 * <li>{@code rotate.y} is {@code -yaw} in radians, because the renderer places the whole
 *     actor with {@code rotateY(-bodyYaw)} &mdash; the sign has to match or the actor
 *     would turn against the ring.</li>
 * <li>{@code rotate.x} is {@code pitch}.</li>
 * <li>{@code rotate.z} stays zero and {@code scale} stays one: a record has neither roll
 *     nor size. {@link #MASK} keeps the handles that would drive them off the screen and
 *     out of the pick stencil entirely, rather than letting a gesture start and quietly
 *     go nowhere.</li>
 * </ul>
 *
 * <p>Turning the Y ring moves all three yaw channels by the same amount, keeping the
 * angles they were offset by when the gesture started. Only {@code bodyYaw} turns the
 * actor's matrix, but moving it alone leaves the head aimed where it was and wrings the
 * neck round; the whole-actor turn is also what {@code LOOK_AT} in
 * {@link ReplayBatchProcessor} does to a whole record.
 */
public class UIReplayPropTransform extends UIPropTransform
{
    /**
     * What a record can be driven by: the move bars, planes and screen cube, plus the
     * rotation rings for yaw (Y) and pitch (X). No scale, no roll ring, no trackball or
     * view ring &mdash; those all need a third rotational degree of freedom to land in.
     */
    public static final Gizmo.HandleMask MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN, Gizmo.Op.ROTATE),
        EnumSet.of(Axis.X, Axis.Y)
    );

    private final Transform scratch = new Transform();

    private Replay replay;
    private IEntity entity;
    private int tick;

    /** Angles the head and body were offset from {@code yaw} when the record was loaded,
     *  kept across a turn so the actor rotates as one piece. */
    private double headYawOffset;
    private double bodyYawOffset;

    public UIReplayPropTransform()
    {
        /* One undo entry per gesture rather than per written channel: every write is
         * bracketed on the whole keyframes group, and the gesture's end closes the block
         * so consecutive drags stay separately undoable. This is also what tells
         * UIFilmUndoHandler to sync the change to the server. */
        this.callbacks(
            () -> this.notifyKeyframes(0),
            () -> { if (this.replay != null) this.replay.keyframes.postNotify(); },
            () -> this.notifyKeyframes(IValueListener.FLAG_UNMERGEABLE)
        );

        this.setVisible(false);
    }

    private void notifyKeyframes(int flag)
    {
        if (this.replay != null)
        {
            this.replay.keyframes.preNotify(flag);
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    /**
     * Load the record's placement at the playhead into the scratch transform, ready for a
     * gesture. Read from the live entity rather than the channels: that is where the actor
     * is actually drawn (and so where the gizmo sits), it already accounts for looping and
     * for a channel that has no keyframes at all, and it is the only reading that cannot
     * drift from the picture.
     */
    public void syncFromReplay(Replay replay, IEntity entity, int tick)
    {
        /* A running gesture owns these values: it rebuilds them every frame from the pose it
         * grabbed at. Re-reading the entity here would fight it — the film re-resolves the
         * gizmo target on every frame, so this is the common path, not an edge case. */
        if (this.isEditing())
        {
            return;
        }

        this.replay = replay;
        this.entity = entity;
        this.tick = tick;

        if (replay == null || entity == null)
        {
            this.setTransform(null);

            return;
        }

        double yaw = entity.getYaw();

        this.headYawOffset = entity.getHeadYaw() - yaw;
        this.bodyYawOffset = entity.getBodyYaw() - yaw;

        this.scratch.rotationMode = Transform.RotationMode.EULER;
        this.scratch.translate.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
        this.scratch.rotate.set(MathUtils.toRad(entity.getPitch()), MathUtils.toRad((float) -yaw), 0F);
        this.scratch.scale.set(1F, 1F, 1F);

        this.setTransform(this.scratch);
    }

    /*
     * Why every write also pushes its value straight onto the live entity:
     *
     * a bone's edit is re-applied to the form on EVERY rendered frame (the render pass calls
     * replay.properties.applyProperties), so its gizmo tracks the cursor exactly. A record's
     * placement isn't: the entity only reads its channels back in updateEntities, which runs
     * on the client tick — 20 times a second. Writing the keyframe alone therefore leaves the
     * actor, and the gizmo drawn on it, stepping along at 20 Hz behind a cursor moving at the
     * frame rate, which reads as the whole drag being choppy.
     *
     * So the derived state is refreshed at the same moment the source of truth changes. The
     * next tick recomputes exactly these values from the very keyframes just written, so this
     * only moves the update earlier — it is not a second, competing source of position.
     * Deliberately not ReplayKeyframes#apply: that would re-interpolate thirty-odd channels
     * and rebuild fourteen equipment stacks per frame to restate what is already in hand.
     */

    /** The tick a write lands on. Looping replays read their channels through
     *  {@link Replay#getTick(int)}, so a write has to go to the same place playback
     *  will look, not to the raw playhead. */
    private int writeTick()
    {
        return this.replay.getTick(this.tick);
    }

    @Override
    public boolean isRotationChannelOnly()
    {
        return true;
    }

    /**
     * Name the record in the drag readout. This editor draws nothing of its own — the chip
     * row that names a bone drag's operation and axis lives in the editor's render, which
     * never runs here — so the readout floating over the gizmo is the only place that can
     * say which of the two things under the cursor is being moved.
     */
    @Override
    public String readoutPrefix()
    {
        return this.replay == null ? null : this.replay.getName() + ": ";
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        if (this.replay == null)
        {
            return;
        }

        this.preCallback();

        this.scratch.translate.set((float) x, (float) y, (float) z);

        int tick = this.writeTick();
        ReplayKeyframes keyframes = this.replay.keyframes;

        /* All three, even for a single-axis drag: a position half-keyed at this tick would
         * read one axis from here and the other two from whatever the neighbouring
         * keyframes interpolate to, which is not the place the user just dropped the actor.
         * The existing "Move replay here" and player-teleport insertions do the same. */
        keyframes.x.insert(tick, x);
        keyframes.y.insert(tick, y);
        keyframes.z.insert(tick, z);

        if (this.entity != null)
        {
            this.entity.setPosition(x, y, z);
            this.entity.setPrevX(x);
            this.entity.setPrevY(y);
            this.entity.setPrevZ(z);
        }

        this.postCallback();
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        /* A record has no size. */
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.replay == null)
        {
            return;
        }

        this.preCallback();

        /* Roll is dropped rather than stored: the rings that could produce one are masked
         * off, but the hotkey walk and typed input still hand over a full euler triple. */
        this.scratch.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), 0F);

        int tick = this.writeTick();
        ReplayKeyframes keyframes = this.replay.keyframes;
        double yaw = -y;

        keyframes.yaw.insert(tick, yaw);
        keyframes.headYaw.insert(tick, yaw + this.headYawOffset);
        keyframes.bodyYaw.insert(tick, yaw + this.bodyYawOffset);
        keyframes.pitch.insert(tick, x);

        if (this.entity != null)
        {
            this.entity.setYaw((float) yaw);
            this.entity.setHeadYaw((float) (yaw + this.headYawOffset));
            this.entity.setBodyYaw((float) (yaw + this.bodyYawOffset));
            this.entity.setPitch((float) x);
            this.entity.setPrevYaw((float) yaw);
            this.entity.setPrevHeadYaw((float) (yaw + this.headYawOffset));
            this.entity.setPrevBodyYaw((float) (yaw + this.bodyYawOffset));
            this.entity.setPrevPitch((float) x);
        }

        this.postCallback();
    }
}
