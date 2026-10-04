package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Arcball rotation: the point picked on the ball at grab time stays under
 * the fingertip as it drags. Inside the ball the cursor offset lifts onto
 * the sphere's surface, outside it follows Bell's hyperbola, so the turn
 * degrades into a view-axis roll smoothly — no seam at the silhouette.
 *
 * <p>Within one anchor the rotation is a pure function of the cursor
 * position, so backtracking the drag restores the exact starting rotation.
 * A cursor wrap folds the arc swept so far into {@link #accum} first, so
 * the fresh anchor continues from the current orientation instead of
 * jumping back to the start.
 */
public class ArcballDrag extends SphereDrag
{
    /** World-space unit axis from the sphere centre toward the camera, captured at start. */
    private final Vector3f viewWorld = new Vector3f();

    /** World→parent-frame rotation captured at start (constant for the drag). */
    private final Matrix3f parentInverse = new Matrix3f();

    /** Grab direction on the ball (parent frame) at the current anchor. */
    private final Vector3f startLocal = new Vector3f();

    /** Cursor's current direction on the ball (parent frame). */
    private final Vector3f currentLocal = new Vector3f();

    /** Arc segments folded in by re-anchors (cursor wraps), parent frame. */
    private final Quaternionf accum = new Quaternionf();

    /** World-space radius of the rendered sphere, captured at start. */
    private float radius;

    /** Whether the anchor state is valid (guards the re-anchor fold). */
    private boolean anchored;

    public ArcballDrag(DragContext ctx)
    {
        super(ctx);
    }

    @Override
    public String editingTargetLabel()
    {
        return UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_ARCBALL.get();
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        if (this.anchored)
        {
            this.accum.premul(new Quaternionf().rotationTo(this.startLocal, this.currentLocal));
            this.anchored = false;
        }

        GizmoDrag drag = this.ctx.drag();

        if (drag == null || this.ctx.transform() == null || this.refuseConstrainedRotation())
        {
            this.hasStart = false;

            return;
        }


        Matrix3f parentInverse = RotationDragMath.parentInverse(this.ctx, drag);
        float radius = this.ctx.sphereWorldRadius();

        if (parentInverse == null || radius <= 0F)
        {
            this.hasStart = false;

            return;
        }

        Vector3f view = new Vector3f(
            (float) (drag.cameraOrigin.x - drag.gizmoOrigin.x),
            (float) (drag.cameraOrigin.y - drag.gizmoOrigin.y),
            (float) (drag.cameraOrigin.z - drag.gizmoOrigin.z)
        );

        if (view.lengthSquared() < 1.0E-8F)
        {
            this.hasStart = false;

            return;
        }

        this.viewWorld.set(view.normalize());
        this.radius = radius;
        this.parentInverse.set(parentInverse);

        /* Screen axes in the parent frame, for the typed-angle input and the
         * wheel roll — captured exactly like the trackball's. */
        if (!this.captureScreenAxes(drag, parentInverse))
        {
            this.hasStart = false;

            return;
        }

        Vector3f grab = this.mapArcball(mouseX, mouseY, new Vector3f());

        if (grab == null)
        {
            this.hasStart = false;

            return;
        }

        this.parentInverse.transform(grab, this.startLocal).normalize();
        this.currentLocal.set(this.startLocal);
        this.anchored = true;
        this.hasStart = true;
    }

    /**
     * Map the cursor to a unit direction from the sphere's centre, in world
     * space. The cursor ray is dropped onto the camera-facing plane through
     * the centre; inside the ball the offset lifts onto the sphere's surface,
     * outside it follows Bell's hyperbola. Returns {@code null} when the ray
     * misses the plane entirely.
     */
    private Vector3f mapArcball(int mouseX, int mouseY, Vector3f out)
    {
        GizmoDrag drag = this.ctx.drag();
        Vector3d hit = new Vector3d();

        if (!drag.intersectPlane(mouseX, mouseY, this.viewWorld, hit))
        {
            return null;
        }

        float px = (float) (hit.x - drag.gizmoOrigin.x);
        float py = (float) (hit.y - drag.gizmoOrigin.y);
        float pz = (float) (hit.z - drag.gizmoOrigin.z);
        float r = this.radius;
        float dSq = px * px + py * py + pz * pz;
        float z = dSq < r * r / 2F
            ? (float) Math.sqrt(r * r - dSq)
            : r * r / (2F * (float) Math.sqrt(dSq));

        out.set(this.viewWorld).mul(z).add(px, py, pz);

        return out.normalize();
    }

    /** Turn the grabbed point to follow the cursor. */
    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        Vector3f current = this.mapArcball(mouseX, mouseY, new Vector3f());

        if (current == null)
        {
            return;
        }

        this.parentInverse.transform(current, this.currentLocal).normalize();
        this.updateRotation();
    }

    /**
     * Rebuild the rotation from the cached start orientation plus the single
     * shortest arc from the anchored grab direction to the cursor's current
     * one (the folded {@link #accum} segments and the wheel roll on top).
     * Shared by the cursor drag and the mouse-wheel roll.
     */
    @Override
    protected void updateRotation()
    {
        Vector3f source = this.ctx.cache().rotate;

        Quaternionf arc = new Quaternionf()
            .rotationTo(this.startLocal, this.currentLocal)
            .mul(this.accum);

        Matrix3f deltaLocal = new Matrix3f()
            .rotation(MathUtils.toRad(this.rollDeg), this.viewLocal)
            .rotate(arc);

        RotationDragMath.applyLocalDelta(this.ctx, deltaLocal, source);
    }
}
