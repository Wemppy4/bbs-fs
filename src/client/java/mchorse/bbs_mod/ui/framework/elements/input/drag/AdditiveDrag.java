package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

/**
 * The plain left/right additive drag: horizontal travel nudges the channel by a per-pixel
 * step, no 3D ray. Serves the hotkey operations without a rendered gizmo, and the uniform
 * scale, whose centre grab reads wildly through a single-axis ray lever.
 */
public class AdditiveDrag extends DragStrategy
{
    /** Uniform (three-axis) scale: one lever drives every axis, like Ctrl. */
    private final boolean scaleAll;

    private int lastX;

    /* A quaternion bone's euler channels are stale, so the rotate lever can't
     * walk them like the euler path does (reading them would snap the bone to
     * whatever the channels last held). Instead the gesture anchors its base to
     * the cache quaternion's ZYX equivalent once and accumulates its own sweep —
     * the same total-from-cache semantics as numericRotate — and stores the
     * result back as a quaternion. */
    private final Vector3f quatBaseDeg = new Vector3f();
    private float quatSweepDeg;

    public AdditiveDrag(DragContext ctx, TransformOp op, Axis axis, Axis axis2, boolean scaleAll)
    {
        super(ctx, op, axis, axis2);

        this.scaleAll = scaleAll;
    }

    @Override
    public boolean isScaleAll()
    {
        return this.scaleAll;
    }

    /** Shift is damped through the step factor instead of the virtual cursor. */
    @Override
    public boolean usesFineCursor()
    {
        return false;
    }

    @Override
    public boolean acceptsNumeric()
    {
        return this.op != TransformOp.ROTATE || this.axis != null;
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        /* First anchor only — begin() re-fires on cursor wraps and numeric
         * resume, and the accumulated sweep must survive those. */
        if (!this.hasStart && this.op == TransformOp.ROTATE && this.isQuatRotate())
        {
            Vector3f base = Matrices.toEulerZYXRadians(this.ctx.cache().quat, new Vector3f());

            this.quatBaseDeg.set(MathUtils.toDeg(base.x), MathUtils.toDeg(base.y), MathUtils.toDeg(base.z));
            this.quatSweepDeg = 0F;
        }

        this.lastX = mouseX;
        this.hasStart = true;
    }

    private boolean isQuatRotate()
    {
        Transform transform = this.ctx.transform();

        return transform != null && transform.rotationMode == Transform.RotationMode.QUATERNION;
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        Transform transform = this.ctx.transform();

        if (transform == null)
        {
            return;
        }

        int dx = mouseX - this.lastX;

        this.lastX = mouseX;

        if (this.refuseConstrainedRotation())
        {
            return;
        }

        boolean all = this.op == TransformOp.SCALE && (this.scaleAll || Window.isCtrlPressed());
        float factor = this.ctx.additiveFactor(this.op) * (Window.isShiftPressed() ? FINE_DRAG_FACTOR : 1F);

        /* Translate lever: step along the active space's axes as drawn, mapped
         * to channel units the same way the ray drag does (LOCAL included —
         * the drawn frame is the truth even for additive layers like pose
         * overlays). Fallbacks without a drag snapshot: the analytic local
         * vector for LOCAL, the raw channel lever otherwise. */
        if (this.op == TransformOp.TRANSLATE)
        {
            Vector3f offset = this.spaceTranslateOffset(factor * dx, this.axis, this.axis2);

            if (offset == null && this.ctx.space().isLocal())
            {
                offset = this.ctx.localTranslateVector(factor * dx, this.axis);

                if (this.axis2 != null)
                {
                    offset.add(this.ctx.localTranslateVector(factor * dx, this.axis2));
                }
            }

            if (offset != null)
            {
                Vector3f live = transform.translate;

                this.ctx.writeTranslate(live.x + offset.x, live.y + offset.y, live.z + offset.z);

                return;
            }
        }

        if (this.op == TransformOp.ROTATE && this.isQuatRotate())
        {
            this.quatSweepDeg += factor * dx;

            Vector3f rotated = new Vector3f(this.quatBaseDeg);

            if (this.axis == Axis.X || this.axis2 == Axis.X) rotated.x += this.quatSweepDeg;
            if (this.axis == Axis.Y || this.axis2 == Axis.Y) rotated.y += this.quatSweepDeg;
            if (this.axis == Axis.Z || this.axis2 == Axis.Z) rotated.z += this.quatSweepDeg;

            this.ctx.writeRotationQuat(Matrices.toQuaternionZYXDegrees(rotated.x, rotated.y, rotated.z));

            return;
        }

        Vector3f live = this.liveValue(transform);
        Vector3f value = new Vector3f(live);

        if (this.op == TransformOp.ROTATE)
        {
            value.mul(180F / MathUtils.PI);
        }

        if (this.axis == Axis.X || all) value.x += factor * dx;
        if (this.axis == Axis.Y || all) value.y += factor * dx;
        if (this.axis == Axis.Z || all) value.z += factor * dx;
        if (!all && this.axis2 == Axis.X) value.x += factor * dx;
        if (!all && this.axis2 == Axis.Y) value.y += factor * dx;
        if (!all && this.axis2 == Axis.Z) value.z += factor * dx;

        switch (this.op)
        {
            case TRANSLATE:
                this.ctx.writeTranslate(value.x, value.y, value.z);
                break;
            case SCALE:
                this.ctx.writeScale(value.x, value.y, value.z);
                break;
            default:
                this.ctx.writeRotateDeg(value.x, value.y, value.z);
                break;
        }
    }

    private Vector3f liveValue(Transform transform)
    {
        if (this.op == TransformOp.SCALE)
        {
            return transform.scale;
        }

        if (this.op == TransformOp.ROTATE)
        {
            return transform.rotate;
        }

        return transform.translate;
    }

    @Override
    public void applyNumeric(double value)
    {
        if (this.refuseConstrainedRotation())
        {
            return;
        }

        switch (this.op)
        {
            case TRANSLATE:
                this.numericTranslate(value);
                break;
            case SCALE:
                this.numericScale(value, this.scaleAll || Window.isCtrlPressed());
                break;
            default:
                this.numericRotate(value);
                break;
        }
    }

    @Override
    public String readout()
    {
        Transform transform = this.ctx.transform();
        Transform cache = this.ctx.cache();

        if (this.op == TransformOp.TRANSLATE)
        {
            return this.axisDeltaReadout(new Vector3f(transform.translate).sub(cache.translate), false);
        }

        if (this.op == TransformOp.SCALE)
        {
            return this.axisDeltaReadout(new Vector3f(transform.scale).sub(cache.scale), this.scaleAll);
        }

        if (this.axis == null)
        {
            return null;
        }

        /* Quaternion rotate accumulates its own sweep (the channels are stale). */
        if (this.isQuatRotate())
        {
            return String.format("%.1f°", this.quatSweepDeg);
        }

        Vector3f now = transform.rotate;
        Vector3f start = cache.rotate;
        float delta = this.axis == Axis.X ? now.x - start.x : (this.axis == Axis.Y ? now.y - start.y : now.z - start.z);

        return String.format("%.1f°", MathUtils.toDeg(delta));
    }
}
