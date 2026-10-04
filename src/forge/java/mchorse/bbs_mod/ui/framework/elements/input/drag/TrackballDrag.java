package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Free rotation driven purely by cursor motion (Blender-style): horizontal
 * travel turns the bone about the screen's vertical axis, vertical travel
 * about the screen's horizontal axis, like spinning a ball under the
 * fingertip. Roll comes only from the mouse wheel.
 *
 * <p>The rotation is rebuilt every frame from the fixed start orientation
 * plus the absolute accumulated cursor offset — never from the previous
 * frame's result. Because the outcome is a pure function of those amounts
 * (and addition commutes, unlike rotation composition), a back-and-forth
 * drag returns to the exact starting rotation, eliminating the classic
 * incremental-trackball roll drift.
 */
public class TrackballDrag extends SphereDrag
{
    /** Previous cursor position, for the frame-to-frame mouse delta. */
    private int lastX;
    private int lastY;

    /** Net cursor offset (pixels) since the drag began, carried across cursor
     *  wraps (telescoping the per-frame deltas keeps it correct). */
    private float accumX;
    private float accumY;

    public TrackballDrag(DragContext ctx)
    {
        super(ctx);
    }

    @Override
    public String editingTargetLabel()
    {
        return UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_TRACKBALL.get();
    }

    /**
     * Anchor the drag: all that's captured is the screen's right/up axes
     * mapped once into the bone's parent frame (constant for the drag) and
     * the starting cursor position.
     *
     * <p>Axes come from the cached start orientation, not the live one, so the
     * screen right/up directions stay fixed for the whole drag. This also
     * makes the call idempotent: a cursor wrap re-invokes it, and rebuilding
     * from the unchanged cache yields the same axes (and never disturbs the
     * accumulated offset).
     */
    @Override
    public void begin(int mouseX, int mouseY)
    {
        GizmoDrag drag = this.ctx.drag();

        if (drag == null || this.ctx.transform() == null || this.refuseConstrainedRotation())
        {
            this.hasStart = false;

            return;
        }


        Matrix3f parentInverse = RotationDragMath.parentInverse(this.ctx, drag);

        if (parentInverse == null || !this.captureScreenAxes(drag, parentInverse))
        {
            this.hasStart = false;

            return;
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
        this.hasStart = true;
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        int dx = mouseX - this.lastX;
        int dy = mouseY - this.lastY;

        this.lastX = mouseX;
        this.lastY = mouseY;

        if (dx == 0 && dy == 0)
        {
            return;
        }

        this.accumX += dx;
        this.accumY += dy;

        this.updateRotation();
    }

    /**
     * Rebuild the rotation from the FIXED start orientation plus the absolute
     * cursor offset (yaw/pitch) and the wheel-driven view-axis roll. Shared by
     * the cursor drag and the mouse-wheel roll.
     */
    @Override
    protected void updateRotation()
    {
        float sensitivity = BBSSettings.trackballSensitivity.get();
        float yaw = MathUtils.toRad(this.accumX * sensitivity);
        float pitch = MathUtils.toRad(this.accumY * sensitivity);
        float roll = MathUtils.toRad(this.rollDeg);

        Vector3f source = this.ctx.cache().rotate;

        Matrix3f deltaLocal = new Matrix3f()
            .rotation(roll, this.viewLocal)
            .rotate(yaw, this.upLocal.x, this.upLocal.y, this.upLocal.z)
            .rotate(pitch, this.rightLocal.x, this.rightLocal.y, this.rightLocal.z);

        RotationDragMath.applyLocalDelta(this.ctx, deltaLocal, source);
    }

    /**
     * The gesture's OWN sweeps, not the shared euler-channel diff: this drag is
     * defined in screen axes and its travel is unbounded, while
     * {@link #freeRotateReadout} reads the bone's channels back through a
     * compatible-euler anchored at the grab — which by construction lands within
     * ±180° of it, so the display folded at half a turn and crawled back while
     * the bone kept spinning. The arcball never showed it: its arc is the angle
     * between two directions on the ball and can't exceed half a turn in one
     * grab. Axis naming matches the typed-angle aiming ({@link #numericPrefix}):
     * X turns about the screen's right axis (vertical travel), Y about its up
     * axis (horizontal travel), Z about the view axis (the wheel roll).
     */
    @Override
    public String readout()
    {
        float sensitivity = BBSSettings.trackballSensitivity.get();

        return String.format("X %+.1f°  Y %+.1f°  Z %+.1f°",
            this.accumY * sensitivity,
            this.accumX * sensitivity,
            this.rollDeg
        );
    }
}
