package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Ray-based scale by ratio of cursor-to-origin distances along the active
 * axis. The "lever" picked at drag start defines 1.0; pulling further
 * multiplies the scale, dragging closer shrinks it. Falls back to additive
 * delta if the starting projection is too small to safely divide by.
 */
public class ScaleDrag extends DragStrategy
{
    private final Matrix3f worldBasis = new Matrix3f();
    private final Vector3f planeNormal = new Vector3f();
    private final Vector3d startHit = new Vector3d();
    private final Vector3f startScale = new Vector3f();

    public ScaleDrag(DragContext ctx, Axis axis, Axis axis2)
    {
        super(ctx, TransformOp.SCALE, axis, axis2);
    }

    /**
     * Anchor the drag on a plane that contains the picked axis and faces
     * the camera. The cursor's projection onto that axis at start defines
     * the "1.0" lever for the ratio.
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

        /* Always the bone's own frame: a per-axis scale in any other frame is a
         * shear on a rotated bone, which the T·R·S channels can't represent, so
         * scale deliberately ignores the space toggle (the editor hides its
         * space chip for the same reason). */
        this.worldBasis.set(drag.frameBasis(TransformSpace.LOCAL));

        if (this.axis2 == null)
        {
            drag.planeNormalForAxis(mouseX, mouseY, this.worldBasis, this.axis, this.planeNormal);
        }
        else
        {
            drag.planeNormalForPlane(this.worldBasis, this.axis, this.axis2, this.planeNormal);
        }

        if (!drag.intersectPlane(mouseX, mouseY, this.planeNormal, this.startHit))
        {
            this.hasStart = false;

            return;
        }

        this.startScale.set(this.ctx.transform().scale);
        this.hasStart = true;
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

        Vector3f s = new Vector3f(this.startScale);

        this.applyAxis(hit, this.axis, s);

        if (this.axis2 != null)
        {
            this.applyAxis(hit, this.axis2, s);
        }

        if (this.ctx.shouldSnap(TransformOp.SCALE))
        {
            float step = BBSSettings.snapScale.get();

            if (this.axis == Axis.X || this.axis2 == Axis.X) s.x = (float) snap(s.x, step);
            if (this.axis == Axis.Y || this.axis2 == Axis.Y) s.y = (float) snap(s.y, step);
            if (this.axis == Axis.Z || this.axis2 == Axis.Z) s.z = (float) snap(s.z, step);
        }

        this.ctx.writeScale(s.x, s.y, s.z);
    }

    private void applyAxis(Vector3d hit, Axis currentAxis, Vector3f s)
    {
        GizmoDrag drag = this.ctx.drag();
        Vector3f axisDir = this.worldBasis.getColumn(currentAxis.ordinal(), new Vector3f());

        if (axisDir.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        axisDir.normalize();

        double rx = hit.x - drag.gizmoOrigin.x;
        double ry = hit.y - drag.gizmoOrigin.y;
        double rz = hit.z - drag.gizmoOrigin.z;
        float currentProj = (float) (rx * axisDir.x + ry * axisDir.y + rz * axisDir.z);

        double srx = this.startHit.x - drag.gizmoOrigin.x;
        double sry = this.startHit.y - drag.gizmoOrigin.y;
        double srz = this.startHit.z - drag.gizmoOrigin.z;
        float startProj = (float) (srx * axisDir.x + sry * axisDir.y + srz * axisDir.z);

        float delta = currentProj - startProj;

        if (Math.abs(startProj) < 1.0E-4F)
        {
            if (currentAxis == Axis.X) s.x += delta;
            if (currentAxis == Axis.Y) s.y += delta;
            if (currentAxis == Axis.Z) s.z += delta;
        }
        else
        {
            float ratio = currentProj / startProj;

            if (currentAxis == Axis.X) s.x *= ratio;
            if (currentAxis == Axis.Y) s.y *= ratio;
            if (currentAxis == Axis.Z) s.z *= ratio;
        }
    }

    @Override
    public void applyNumeric(double value)
    {
        this.numericScale(value, false);
    }

    @Override
    public String readout()
    {
        Vector3f delta = new Vector3f(this.ctx.transform().scale).sub(this.startScale);

        return this.axisDeltaReadout(delta, false);
    }
}
