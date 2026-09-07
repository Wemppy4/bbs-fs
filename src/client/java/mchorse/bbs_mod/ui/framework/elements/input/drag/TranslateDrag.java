package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoJacobian;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Ray-based translate along one gizmo handle or a two-axis plane: the cursor
 * ray is intersected with a plane through the gizmo origin, and the world
 * delta from the grab point is projected back into translate units.
 */
public class TranslateDrag extends DragStrategy
{
    /**
     * World-space direction (with scale) for one unit of drag along each
     * gizmo handle. Columns correspond to the {@link Axis} the user is
     * dragging. Used to project the cursor's world delta back onto the
     * relevant handle.
     */
    protected final Matrix3f worldBasis = new Matrix3f();

    /**
     * Direction (in {@code transform.translate} units) for one unit of drag
     * along each gizmo handle. Together with {@link #worldBasis} this solves
     * the mapping from world cursor motion to the change of
     * {@code transform.translate} regardless of what units that translate is
     * in (blocks, pixels, ...).
     */
    protected final Matrix3f translateBasis = new Matrix3f();

    protected final Vector3f planeNormal = new Vector3f();
    protected final Vector3d startHit = new Vector3d();
    protected final Vector3f startTranslate = new Vector3f();

    public TranslateDrag(DragContext ctx, Axis axis, Axis axis2)
    {
        super(ctx, TransformOp.TRANSLATE, axis, axis2);
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        GizmoDrag drag = this.ctx.drag();
        Transform transform = this.ctx.transform();

        if (drag == null || transform == null)
        {
            this.hasStart = false;

            return;
        }

        Matrix3f jacobian = new Matrix3f(drag.translateJacobian);

        /* Handles slide along the active space's axes exactly as DRAWN
         * (frameBasis: LOCAL/PARENT = the placed gizmo frame, GLOBAL = the
         * container's frame, WORLD = the map's,
         * VIEW = camera axes), and the inverse Jacobian maps one world unit
         * along a handle into translate-channel units. One formula for every
         * space. LOCAL used to rebuild its axes analytically from the
         * transform's OWN channel rotation (plus the legacy 180° X model
         * correction) — which broke on additive layers like pose overlays: an
         * overlay's own channels sit near identity while the bone (and the
         * drawn gizmo) carry the full pose, so the drag slid along a frame the
         * user wasn't shown. The drawn frame is always the truth on screen. */
        Matrix3f basis = drag.frameBasis(this.ctx.space());

        this.translateBasis.set(GizmoJacobian.inverse(jacobian)).mul(basis);
        this.worldBasis.set(basis);

        if (this.axis2 == null)
        {
            drag.planeNormalForAxis(mouseX, mouseY, this.worldBasis, this.axis, this.planeNormal);
        }
        else
        {
            drag.planeNormalForPlane(this.worldBasis, this.axis, this.axis2, this.planeNormal);
        }

        this.startTranslate.set(transform.translate);
        this.hasStart = drag.intersectPlane(mouseX, mouseY, this.planeNormal, this.startHit);
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        Vector3d hit = new Vector3d();

        if (!this.ctx.drag().intersectPlane(mouseX, mouseY, this.planeNormal, hit))
        {
            return;
        }

        Vector3f delta = new Vector3f(
            (float) (hit.x - this.startHit.x),
            (float) (hit.y - this.startHit.y),
            (float) (hit.z - this.startHit.z)
        );

        Vector3f result = new Vector3f();

        this.accumulateAlongAxis(delta, this.axis, result);

        if (this.axis2 != null)
        {
            this.accumulateAlongAxis(delta, this.axis2, result);
        }

        this.ctx.writeTranslate(
            this.startTranslate.x + result.x,
            this.startTranslate.y + result.y,
            this.startTranslate.z + result.z
        );
    }

    /**
     * Convert the cursor's world-space delta into a contribution to
     * {@code transform.translate} for the given handle.
     *
     * The math is the same for both LOCAL and GLOBAL modes: project the
     * world delta onto the handle's world direction (with scale), then map
     * it back into translate-space using the precomputed translate basis.
     * This automatically handles cases where one unit of {@code translate}
     * is not one block in world (cubic groups, scaled parents, etc.).
     */
    protected void accumulateAlongAxis(Vector3f delta, Axis axis, Vector3f out)
    {
        Vector3f worldAxis = new Vector3f();

        this.worldBasis.getColumn(axis.ordinal(), worldAxis);

        float lenSq = worldAxis.lengthSquared();

        if (lenSq < 1.0E-12F)
        {
            return;
        }

        float t = worldAxis.dot(delta) / lenSq;

        Vector3f translateAxis = new Vector3f();

        this.translateBasis.getColumn(axis.ordinal(), translateAxis);

        /* Snap the distance moved along the handle in translate units, so the
         * step means the same thing whatever frame the handle works in. */
        if (this.ctx.shouldSnap(TransformOp.TRANSLATE))
        {
            float length = translateAxis.length();

            if (length > 1.0E-8F)
            {
                t = (float) (snap(t * length, BBSSettings.snapTranslate.get()) / length);
            }
        }

        out.add(translateAxis.mul(t));
    }

    @Override
    public void applyNumeric(double value)
    {
        this.numericTranslate(value);
    }

    @Override
    public String readout()
    {
        Vector3f delta = new Vector3f(this.ctx.transform().translate).sub(this.startTranslate);

        return this.axisDeltaReadout(delta, false);
    }
}
