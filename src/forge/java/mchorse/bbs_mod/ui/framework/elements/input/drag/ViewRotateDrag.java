package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * View-plane rotation: the axis is the screen's perpendicular (the shared
 * camera basis' forward column, pointing at the viewer &mdash; Blender's view
 * axis), and the angle comes from sweeping the cursor around the projected
 * gizmo center, exactly like a per-axis ring. Unlike a single ring, the
 * resulting world-space turn is spread across all three rotate components,
 * so it stays "common" to the three axes.
 *
 * <p>The rotation is rebuilt every frame from the FIXED start orientation
 * (the cache) plus the total swept angle about the view axis anchored at
 * grab time — a pure function of the gesture, never of the previous frame's
 * euler readback. The euler angles are read out once per frame in the one
 * shared place ({@link RotationDragMath#applyLocalDelta}), with its euler
 * family anchored to the GRAB — a pole passage self-recovers instead of
 * stranding X/Z at ±180 — while the winding keeps counting from the live
 * channels, so a sweep past half a turn stays a growing number.
 */
public class ViewRotateDrag extends DragStrategy
{
    /** The axis points at the camera (out of the screen), so an increasing
     *  screen angle (clockwise, Y down) is a negative turn about it. */
    private static final float ROTATE_SIGN = -1F;

    /** View axis expressed in the bone's parent frame, captured at drag start. */
    private final Vector3f viewLocalAxis = new Vector3f();

    /** Projected gizmo origin in viewport pixels, captured at drag start. */
    private final Vector2f screenCenter = new Vector2f();

    private float lastScreenAngle;

    private float accumulatedDeg;


    public ViewRotateDrag(DragContext ctx)
    {
        super(ctx, TransformOp.ROTATE, null, null);
    }

    @Override
    public boolean isView()
    {
        return true;
    }

    @Override
    public String editingTargetLabel()
    {
        return UIKeys.TRANSFORMS_TARGET_VIEW.get();
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
         * only moves the cursor reference; the anchored axis and the swept
         * angle survive so the gesture continues instead
         * of restarting. */
        if (this.hasStart)
        {
            this.lastScreenAngle = RotationDragMath.screenAngle(this.screenCenter, mouseX, mouseY);

            return;
        }

        /* The screen's perpendicular from the one shared camera basis, pointing
         * at the viewer (same sign convention as before) — so every object
         * turns about the same screen-aligned axis wherever it sits in frame. */
        Matrix3f cameraBasis = drag.cameraBasis();

        if (cameraBasis == null || !drag.projectToScreen(drag.gizmoOrigin, this.screenCenter))
        {
            return;
        }

        Vector3f viewAxis = cameraBasis.getColumn(2, new Vector3f()).normalize();

        /* Express the view axis once in the bone's parent frame (recovered
         * analytically from the bone's world rotation), against the start
         * orientation it will be composed onto; it stays constant for the drag. */
        Matrix3f parentInverse = RotationDragMath.parentInverse(this.ctx, drag);

        if (parentInverse == null)
        {
            return;
        }

        parentInverse.transform(viewAxis, this.viewLocalAxis);

        if (this.viewLocalAxis.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        this.viewLocalAxis.normalize();
        this.lastScreenAngle = RotationDragMath.screenAngle(this.screenCenter, mouseX, mouseY);
        this.accumulatedDeg = 0F;
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

        if (delta == 0F)
        {
            return;
        }

        this.accumulatedDeg += MathUtils.toDeg(delta * ROTATE_SIGN);

        Vector3f base = this.ctx.cache().rotate;

        Matrix3f deltaLocal = new Matrix3f().rotation(MathUtils.toRad(this.accumulatedDeg), this.viewLocalAxis);

        RotationDragMath.applyLocalDelta(this.ctx, deltaLocal, base);
    }

    @Override
    public void applyNumeric(double value)
    {
        if (this.refuseConstrainedRotation())
        {
            return;
        }

        this.numericAxisRotation(value, this.viewLocalAxis);
    }

    @Override
    public String readout()
    {
        return this.freeRotateReadout();
    }
}
