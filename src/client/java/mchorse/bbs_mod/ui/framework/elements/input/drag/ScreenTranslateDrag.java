package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoJacobian;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Screen-space (view-plane) translate: the object moves along the camera's
 * right/up axes in the plane facing the camera, rather than along a gizmo
 * axis. Reuses the translate projection with {@code axis = X},
 * {@code axis2 = Y} addressing those two camera directions; the mouse wheel
 * pushes the object toward or away from the camera.
 */
public class ScreenTranslateDrag extends TranslateDrag
{
    /** Fraction of the camera-to-gizmo distance the object moves in depth per wheel notch
     *  during a screen-space grab (Alt divides it by 5, Ctrl multiplies it by 5). */
    private static final float DEPTH_WHEEL_FACTOR = 0.05F;

    /** World→translate map (inverse of the translate Jacobian) captured for the active
     *  grab, so the mouse-wheel depth move can convert a world step into
     *  {@code transform.translate} units. */
    private final Matrix3f screenInverseJacobian = new Matrix3f();

    public ScreenTranslateDrag(DragContext ctx)
    {
        super(ctx, Axis.X, Axis.Y);
    }

    @Override
    public boolean isScreenTranslate()
    {
        return true;
    }

    /** A single typed scalar is ambiguous across two camera axes; numeric input
     *  resumes once X/Y/Z constrains the grab to an axis. */
    @Override
    public boolean acceptsNumeric()
    {
        return false;
    }

    @Override
    public String editingTargetLabel()
    {
        return UIKeys.TRANSFORMS_TARGET_SCREEN.get();
    }

    /**
     * Anchor the grab. The drag plane is the one facing the camera (normal =
     * camera forward), and the two move directions are the camera's world
     * right/up axes recovered from the rotation-only view matrix. Pushed
     * through the inverse translate Jacobian they map cursor motion in the
     * view plane straight onto {@code transform.translate}, regardless of the
     * local/global toggle (screen space is camera-relative by definition).
     */
    @Override
    public void begin(int mouseX, int mouseY)
    {
        GizmoDrag drag = this.ctx.drag();

        if (drag == null || this.ctx.transform() == null)
        {
            this.hasStart = false;

            return;
        }

        /* The camera's world right/up/forward from the one shared screen-frame
         * source (GizmoDrag.cameraBasis); with a degenerate view there is no
         * screen plane to drag in. */
        Matrix3f cameraBasis = drag.cameraBasis();

        if (cameraBasis == null)
        {
            this.hasStart = false;

            return;
        }

        Vector3f right = cameraBasis.getColumn(0, new Vector3f()).normalize();
        Vector3f up = cameraBasis.getColumn(1, new Vector3f()).normalize();
        Vector3f forward = cameraBasis.getColumn(2, new Vector3f()).normalize();

        cameraBasis.setColumn(0, right);
        cameraBasis.setColumn(1, up);
        cameraBasis.setColumn(2, forward);

        Matrix3f inverse = GizmoJacobian.inverse(drag.translateJacobian);

        this.translateBasis.set(inverse).mul(cameraBasis);
        this.screenInverseJacobian.set(inverse);
        this.worldBasis.set(cameraBasis);
        this.planeNormal.set(forward);

        this.startTranslate.set(this.ctx.transform().translate);
        this.hasStart = drag.intersectPlane(mouseX, mouseY, this.planeNormal, this.startHit);
    }

    @Override
    public String readout()
    {
        Vector3f delta = new Vector3f(this.ctx.transform().translate).sub(this.startTranslate);

        return this.axisDeltaReadout(delta, true);
    }

    /**
     * Push the object toward or away from the camera with the mouse wheel. One
     * notch scales its distance from the camera by a fraction (Alt divides the
     * step by 5 for fine control, Ctrl multiplies it by 5 for coarse).
     *
     * <p>The object moves along its own camera-to-object ray, so its on-screen
     * position is preserved (only the depth/size changes) — moving along the
     * camera forward axis instead would drift the object across the screen. The
     * drag plane is slid with the object and re-anchored, so the in-plane drag
     * keeps tracking the cursor at the new depth without the perspective
     * mismatch that otherwise makes it overshoot/lag.
     */
    @Override
    public boolean scroll(UIContext context)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return false;
        }

        /* Re-acquire the drag from the freshly rendered gizmo (as if the grab were just
         * triggered): its origin is then the object's authoritative current world
         * position, instead of a snapshot reconstruction that drifts. Rebuild the
         * screen-drag basis/anchors on it so the depth ray and the in-plane plane stay
         * aligned with where the object actually is. */
        GizmoDrag fresh = this.ctx.freshHotkeyDrag();

        if (fresh != null)
        {
            this.ctx.setDrag(fresh);
        }

        GizmoDrag drag = this.ctx.drag();

        if (drag == null)
        {
            return true;
        }

        this.begin(context.mouseX, context.mouseY);

        Vector3d ray = new Vector3d(drag.gizmoOrigin).sub(drag.cameraOrigin);
        double distance = ray.length();

        if (distance < 1.0E-4)
        {
            return true;
        }

        ray.div(distance);

        float step = applyStepModifiers((float) (context.mouseWheel * distance * DEPTH_WHEEL_FACTOR));

        Vector3f worldStep = new Vector3f((float) (ray.x * step), (float) (ray.y * step), (float) (ray.z * step));

        /* Move along the camera->object ray (preserves screen position), in translate units. */
        Vector3f translateStep = this.screenInverseJacobian.transform(new Vector3f(worldStep));
        Vector3f translate = this.ctx.transform().translate;

        this.ctx.writeTranslate(
            translate.x + translateStep.x,
            translate.y + translateStep.y,
            translate.z + translateStep.z
        );

        /* Slide the drag plane to the new depth and re-anchor so the in-plane drag continues. */
        drag.gizmoOrigin.add(ray.x * step, ray.y * step, ray.z * step);
        this.startTranslate.set(this.ctx.transform().translate);
        drag.intersectPlane(context.mouseX, context.mouseY, this.planeNormal, this.startHit);

        this.ctx.refreshFields();

        return true;
    }
}
