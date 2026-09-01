package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Rotate around a single gizmo ring by the angle the cursor sweeps around
 * the projected gizmo center on screen. Unlike a ring-plane projection this
 * stays accurate when the ring faces the camera (where the plane hit
 * degenerates), and the per-frame deltas are unwrapped and accumulated
 * without limit so the user can wind several full turns in either direction.
 *
 * <p>The rotation is rebuilt every frame as the FIXED grab pose plus the total
 * sweep — a pure function of the gesture. EVERY space turns about the axis the
 * ring is DRAWN about: in LOCAL the bone's own tilted axis (true local, not the
 * euler channel's gimbal axis), in PARENT the parent bone's axis the gizmo is
 * placed on — a real parent-frame turn, which generally moves more than one
 * euler channel. It used to bump the driven channel instead, which is Blender's
 * GIMBAL orientation, not its parent one: the red ring simply grew the red
 * number, and the bone left the ring the moment an outer channel tilted it.
 * That channel walk survives only as the euler pole fallback. The readback's
 * live winding keeps &gt;360° sweeps counting in either path.
 */
public class RingRotateDrag extends DragStrategy
{
    /** World-space unit rotation axis of the grabbed ring, captured at drag start. */
    private final Vector3f axisDir = new Vector3f();

    /** Original unit ring direction (perpendicular to the rotation axis) captured at drag start. */
    private final Vector3f initialRingVec = new Vector3f();

    /** Projected gizmo origin in viewport pixels, captured at drag start. */
    private final Vector2f screenCenter = new Vector2f();

    /** Previous cursor angle (radians) around {@link #screenCenter}, unwrapped each frame. */
    private float lastScreenAngle;

    /** Maps screen-space angular motion to a rotation about the handle's axis (+1 or -1). */
    private float rotateSign = 1F;

    private float accumulatedDeg;

    /** Start rotation (degrees) from the cache; the fixed base the sweep adds onto. */
    private final Vector3f startRotateDeg = new Vector3f();

    /** Whether the edited bone stores its rotation as a quaternion. */
    private boolean quatMode;

    /** The space this gesture rotates in, captured at drag start. */
    private TransformSpace space = TransformSpace.LOCAL;

    /** The ring's world axis expressed in the bone's parent frame (constant for
     *  the drag), so the turn composes onto the pose the same gimbal-free way
     *  {@link ViewRotateDrag} does. Unused on the pole fallback. */
    private final Vector3f axisLocalParent = new Vector3f();

    /** Whether this gesture bumps the driven channel directly instead of
     *  composing about a world axis. Only the euler fallback exactly at the
     *  euler pole, where the parent frame can't be recovered and the
     *  world-axis path can't start: LOCAL and PARENT then walk the single
     *  channel (gimbal semantics, but alive) rather than refusing the drag. */
    private boolean channelPath;

    public RingRotateDrag(DragContext ctx, Axis axis)
    {
        super(ctx, TransformOp.ROTATE, axis, null);
    }

    @Override
    public Vector3f initialRingVec()
    {
        return this.initialRingVec;
    }

    @Override
    public Vector3f ringAxisDir()
    {
        return this.hasStart ? new Vector3f(this.axisDir) : null;
    }

    @Override
    public float accumulatedRotateDeg()
    {
        return this.accumulatedDeg;
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        GizmoDrag drag = this.ctx.drag();

        if (drag == null || this.ctx.transform() == null || this.refuseConstrainedRotation())
        {
            this.hasStart = false;

            return;
        }

        /* A re-anchor (cursor wrap, cursor control resumed after typed input)
         * only moves the cursor reference; the axis, the base angles, the
         * pie's start edge and the swept angle survive so the gesture
         * continues instead of restarting. */
        if (this.hasStart)
        {
            this.lastScreenAngle = RotationDragMath.screenAngle(this.screenCenter, mouseX, mouseY);

            return;
        }

        this.space = this.ctx.space();
        this.quatMode = this.ctx.transform().rotationMode == Transform.RotationMode.QUATERNION;
        this.channelPath = false;

        /* The world axis this ring turns around: the axis the ring is DRAWN
         * about (frameBasis) in EVERY space — in LOCAL the bone's own axis with
         * all of its rotation applied, in PARENT the parent bone's own axis the
         * gizmo is placed on. Never the euler channel's response axis, so the
         * bone follows the grabbed ring even when inner channels tilt it, and it
         * stays sign-safe on cubics: the delta is DEFINED by the world axis, and
         * parentInverse folds in the Ry(180) post-flip that makes the drawn
         * parent X/Z point opposite the channels' response. That measured axis
         * (rotateAxes) is the cursor coupling of the CHANNEL path only, which
         * now survives solely as the euler pole fallback below. */
        Vector3f axisDir = drag.frameBasis(this.space).getColumn(this.axis.ordinal(), new Vector3f());

        if (axisDir.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        axisDir.normalize();
        this.axisDir.set(axisDir);

        /* Screen-space ring rotation pivots around the gizmo origin projected to
         * the viewport. If the origin can't be projected (behind the camera)
         * there's nothing sensible to orbit around, so bail. */
        if (!drag.projectToScreen(drag.gizmoOrigin, this.screenCenter))
        {
            return;
        }

        this.lastScreenAngle = RotationDragMath.screenAngle(this.screenCenter, mouseX, mouseY);

        /* A positive rotation about +axis shows up as an increasing screen angle
         * (atan2 with Y pointing down) when the axis points away from the camera
         * (into the screen), and a decreasing one when it points back at it. */
        Vector3f intoScreen = new Vector3f(
            (float) (drag.gizmoOrigin.x - drag.cameraOrigin.x),
            (float) (drag.gizmoOrigin.y - drag.cameraOrigin.y),
            (float) (drag.gizmoOrigin.z - drag.cameraOrigin.z)
        );

        this.rotateSign = Math.signum(axisDir.dot(intoScreen));

        if (this.rotateSign == 0F)
        {
            this.rotateSign = 1F;
        }

        this.initialRingVec.set(this.computeStartRingVec(mouseX, mouseY, axisDir));
        this.accumulatedDeg = 0F;

        /* The channel path adds its sweep onto the start angles; the world-axis
         * path reads this as the pose the parent frame is recovered at (mode-aware,
         * so a quaternion bone uses its ZYX equivalent, not the stale channels). */
        Vector3f source = RotationDragMath.cacheSourceEuler(this.ctx);

        this.startRotateDeg.set(
            MathUtils.toDeg(source.x),
            MathUtils.toDeg(source.y),
            MathUtils.toDeg(source.z)
        );

        /* The world-axis path maps its axis once into the bone's parent frame
         * (constant for the drag) and applies the turn as a parent-frame delta
         * — ViewRotateDrag's gimbal-free path, just axis-locked. Built from the
         * measured rotateAxes (which fold in the parent AND the cubic Ry(180)
         * post-flip), NOT the raw bone orientation: that flip is post-multiplied
         * after the bone's own rotation, so the raw orientation would leave X/Z
         * turning backwards. Exactly at the euler pole the recovery degenerates;
         * the euler LOCAL and PARENT rings then fall back to the channel path
         * (gimbal semantics, but alive) and re-anchor their cursor coupling to
         * the channel's measured response axis, the frames with no channel of
         * their own (GLOBAL/VIEW/WORLD) skip the gesture as before. */
        /* A gimbal-angle target (a replay's root: yaw and pitch, no roll) takes the channel
         * path outright, in every space. The gimbal-free path would decompose its turn back
         * into ZYX and can put part of it in the roll channel, which such a target has to
         * drop — the actor would then settle somewhere the sweep never pointed. */
        boolean channelOnly = this.ctx.rotationChannelOnly() && !this.quatMode;

        if (!this.channelPath)
        {
            Matrix3f parentInverse = channelOnly ? null : RotationDragMath.parentInverse(this.ctx, drag);

            if (parentInverse != null)
            {
                parentInverse.transform(this.axisDir, this.axisLocalParent);
            }

            if (parentInverse == null || this.axisLocalParent.lengthSquared() < 1.0E-8F)
            {
                if (!channelOnly && ((this.space != TransformSpace.LOCAL && this.space != TransformSpace.PARENT) || this.quatMode))
                {
                    return;
                }

                this.channelPath = true;

                Vector3f channelAxis = drag.rotateAxes.getColumn(this.axis.ordinal(), new Vector3f());

                if (channelAxis.lengthSquared() >= 1.0E-8F)
                {
                    channelAxis.normalize();
                    this.axisDir.set(channelAxis);
                    this.rotateSign = Math.signum(channelAxis.dot(intoScreen));

                    if (this.rotateSign == 0F)
                    {
                        this.rotateSign = 1F;
                    }

                    this.initialRingVec.set(this.computeStartRingVec(mouseX, mouseY, channelAxis));
                }
            }
            else
            {
                this.axisLocalParent.normalize();
            }
        }

        this.hasStart = true;
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        float current = RotationDragMath.screenAngle(this.screenCenter, mouseX, mouseY);
        float delta = RotationDragMath.wrapSeamRad(current - this.lastScreenAngle);

        this.lastScreenAngle = current;
        this.accumulatedDeg += MathUtils.toDeg(delta) * this.rotateSign;

        /* Apply the swept turn about the ring's world axis as a parent-frame
         * delta onto the grab pose (gimbal-free, quat- and euler-aware through
         * applyLocalDelta, which keeps the >360° winding this ring is built for
         * and anchors the euler family at the grab). */
        if (!this.channelPath)
        {
            float sweepDeg = (float) this.snapValue(this.accumulatedDeg);
            Matrix3f deltaLocal = new Matrix3f().rotation(MathUtils.toRad(sweepDeg), this.axisLocalParent);

            RotationDragMath.applyLocalDelta(this.ctx, deltaLocal, this.ctx.cache().rotate);

            return;
        }

        /* Channel path (euler pole fallback): bump the single driven channel
         * on top of the grab angles. Snap only the driven axis, and only in the
         * written value — the raw accumulation stays smooth, and the other two
         * axes carry their start values and must not be rounded out from under
         * the user. startRotateDeg is mode-aware, so a quaternion bone runs the
         * same single-parameter walk on its decomposed ZYX and recomposes. */
        float rx = this.startRotateDeg.x;
        float ry = this.startRotateDeg.y;
        float rz = this.startRotateDeg.z;

        switch (this.axis)
        {
            case X: rx = (float) this.snapValue(rx + this.accumulatedDeg); break;
            case Y: ry = (float) this.snapValue(ry + this.accumulatedDeg); break;
            case Z: rz = (float) this.snapValue(rz + this.accumulatedDeg); break;
        }

        if (this.quatMode)
        {
            this.ctx.writeRotationQuat(Matrices.toQuaternionZYXDegrees(rx, ry, rz));
        }
        else
        {
            this.ctx.writeRotateDeg(rx, ry, rz);
        }
    }

    private double snapValue(double value)
    {
        if (!this.ctx.shouldSnap(TransformOp.ROTATE))
        {
            return value;
        }

        return snap(value, BBSSettings.snapRotate.get());
    }

    /**
     * World-space ring direction (perpendicular to the rotation axis) at the
     * point where the drag started. Only feeds the rotation pie preview, so a
     * perpendicular fallback is acceptable when the cursor ray runs parallel to
     * the ring plane.
     */
    private Vector3f computeStartRingVec(int mouseX, int mouseY, Vector3f axisDir)
    {
        GizmoDrag drag = this.ctx.drag();
        Vector3f ring = new Vector3f();
        Vector3d hit = new Vector3d();

        if (drag.intersectPlane(mouseX, mouseY, axisDir, hit))
        {
            ring.set(
                (float) (hit.x - drag.gizmoOrigin.x),
                (float) (hit.y - drag.gizmoOrigin.y),
                (float) (hit.z - drag.gizmoOrigin.z)
            );

            float along = ring.dot(axisDir);

            ring.sub(new Vector3f(axisDir).mul(along));
        }

        if (ring.lengthSquared() < 1.0E-8F)
        {
            Vector3f fallback = Math.abs(axisDir.y) < 0.9F ? new Vector3f(0F, 1F, 0F) : new Vector3f(1F, 0F, 0F);

            axisDir.cross(fallback, ring);
        }

        return ring.normalize();
    }

    @Override
    public void applyNumeric(double value)
    {
        if (this.refuseConstrainedRotation())
        {
            return;
        }

        /* A numeric turn rotates about the ring's world axis mapped to the
         * parent frame — same as the drag; the channel path (the euler pole
         * fallback) bumps the single parameter instead, mode-aware. */
        if (!this.channelPath)
        {
            this.numericAxisRotation(value, this.axisLocalParent);

            return;
        }

        this.numericRotate(value);
    }

    @Override
    public String readout()
    {
        return String.format("%.1f°", this.accumulatedDeg);
    }
}
