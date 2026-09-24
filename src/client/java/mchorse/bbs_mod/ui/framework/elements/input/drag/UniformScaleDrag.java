package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Uniform scale along the screen line from the projected pivot to the grab. */
public class UniformScaleDrag extends DragStrategy
{
    private final Vector2f center = new Vector2f();
    private final Vector2f direction = new Vector2f();
    private final Vector3f baseScale = new Vector3f();
    private float lever;
    private float offset;
    private float factor = 1F;

    public UniformScaleDrag(DragContext ctx)
    {
        super(ctx, TransformOp.SCALE, Axis.X, null);
    }

    @Override
    public boolean isScaleAll()
    {
        return true;
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        if (this.ctx.drag() == null || this.ctx.transform() == null)
        {
            return;
        }

        if (!this.hasStart)
        {
            if (!this.ctx.drag().projectToScreen(this.ctx.drag().gizmoOrigin, this.center))
            {
                return;
            }

            this.baseScale.set(this.ctx.cache().scale);
            this.direction.set(mouseX - this.center.x, mouseY - this.center.y);
            this.lever = this.direction.length();

            /* A centre-handle grab has no usable radial lever. Give that tiny
             * region a stable horizontal lever instead of dividing by zero. */
            if (this.lever < 8F)
            {
                this.direction.set(1F, 0F);
                this.lever = 64F;
            }
            else
            {
                this.direction.div(this.lever);
            }

            this.hasStart = true;
        }

        /* Re-anchor on cursor wraps and after numeric input without losing the
         * original proportions, including when the live scale is exactly zero. */
        this.offset = this.factor - this.projection(mouseX, mouseY) / this.lever;
    }

    private float projection(int mouseX, int mouseY)
    {
        return (mouseX - this.center.x) * this.direction.x
            + (mouseY - this.center.y) * this.direction.y;
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        this.factor = this.offset + this.projection(mouseX, mouseY) / this.lever;
        float value = this.factor;

        if (this.ctx.shouldSnap(TransformOp.SCALE))
        {
            value = (float) snap(value, BBSSettings.snapScale.get());
        }

        this.ctx.writeScale(this.baseScale.x * value, this.baseScale.y * value, this.baseScale.z * value);
    }

    @Override
    public void applyNumeric(double value)
    {
        this.numericScale(value, true);
        /* Emptying the numeric buffer restores the cached transform before begin(). */
        this.factor = 1F;
    }

    @Override
    public String readout()
    {
        return this.axisDeltaReadout(new Vector3f(this.ctx.transform().scale).sub(this.ctx.cache().scale), true);
    }
}
