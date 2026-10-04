package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import mchorse.bbs_mod.graphics.window.InputCodes;

/**
 * Shared machinery of the rotate sphere's free-rotation modes (trackball and
 * arcball): the screen right/up/view axes captured once in the bone's parent
 * frame, the wheel-driven view-axis roll, the X/Y typed-angle aiming, and the
 * per-axis readout. Subclasses supply only their own cursor→rotation mapping
 * via {@link #updateRotation()}.
 */
public abstract class SphereDrag extends DragStrategy
{
    /** Screen right/up/view axes in the bone's parent frame, captured at drag start. */
    protected final Vector3f rightLocal = new Vector3f();
    protected final Vector3f upLocal = new Vector3f();
    protected final Vector3f viewLocal = new Vector3f();

    /** Accumulated wheel-driven view-axis roll (degrees). */
    protected float rollDeg;


    /** Typed-angle target: {@link Axis#X} = horizontal (screen-up axis),
     *  {@link Axis#Y} = vertical (screen-right axis). */
    protected Axis numericAxis = Axis.X;

    protected SphereDrag(DragContext ctx)
    {
        super(ctx, TransformOp.ROTATE, null, null);
    }

    @Override
    public boolean isSphere()
    {
        return true;
    }

    /** Rebuild the rotation from the start orientation plus this mode's accumulated amounts. */
    protected abstract void updateRotation();

    /**
     * Map the screen's right/up/view axes once into the bone's parent frame
     * (constant for the drag). Returns {@code false} when the mapped axes are
     * degenerate and the drag shouldn't start.
     */
    protected boolean captureScreenAxes(GizmoDrag drag, Matrix3f parentInverse)
    {
        Matrix3f cameraBasis = drag.cameraBasis();

        if (cameraBasis == null)
        {
            return false;
        }

        Vector3f right = cameraBasis.getColumn(0, new Vector3f()).normalize();
        Vector3f up = cameraBasis.getColumn(1, new Vector3f()).normalize();
        Vector3f view = cameraBasis.getColumn(2, new Vector3f()).normalize();

        parentInverse.transform(right, this.rightLocal);
        parentInverse.transform(up, this.upLocal);
        parentInverse.transform(view, this.viewLocal);

        if (this.rightLocal.lengthSquared() < 1.0E-8F || this.upLocal.lengthSquared() < 1.0E-8F)
        {
            return false;
        }

        this.rightLocal.normalize();
        this.upLocal.normalize();
        this.viewLocal.normalize();

        return true;
    }

    /**
     * Mouse-wheel roll: each notch rolls the object about the view axis
     * (toward the camera), with Alt for fine (÷5) and Ctrl for coarse (×5)
     * steps.
     */
    @Override
    public boolean scroll(UIContext context)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return false;
        }

        this.rollDeg += applyStepModifiers((float) (context.mouseWheel * TRACKBALL_WHEEL_DEG));
        this.updateRotation();

        return true;
    }

    @Override
    public boolean handleNumericAxisKey(int key)
    {
        if (key == InputCodes.KEY_X || key == InputCodes.KEY_Y)
        {
            this.numericAxis = key == InputCodes.KEY_Y ? Axis.Y : Axis.X;

            return true;
        }

        return false;
    }

    @Override
    public String numericPrefix()
    {
        return this.numericAxis == Axis.Y ? "X" : "Y";
    }

    @Override
    public void applyNumeric(double value)
    {
        if (this.refuseConstrainedRotation())
        {
            return;
        }

        this.numericAxisRotation(value, this.numericAxis == Axis.Y ? this.rightLocal : this.upLocal);
    }

    @Override
    public String readout()
    {
        return this.freeRotateReadout();
    }
}
