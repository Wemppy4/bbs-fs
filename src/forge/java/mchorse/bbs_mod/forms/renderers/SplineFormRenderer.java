package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.cubic.spline.SplineArray;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.SplineOverlay;
import org.joml.Matrix4f;

/** The curve is editor-only; repeated contents use the ordinary form rendering pipeline. */
public class SplineFormRenderer extends FormRenderer<SplineForm>
{
    public SplineFormRenderer(SplineForm form) { super(form); }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        if (!this.form.repeatEnabled.get())
        {
            super.renderBodyParts(context);
            return;
        }
        try (RepeatedFormRender ignored = new RepeatedFormRender())
        {
            for (Matrix4f frame : RepeatedFormRender.current().frames(this.form))
            {
                context.stack.push();
                if (context.world != null) context.world.push();
                try
                {
                    MatrixStackUtils.multiply(context.stack, frame);
                    if (context.world != null) MatrixStackUtils.multiply(context.world, frame);
                    super.renderBodyParts(context);
                }
                finally
                {
                    context.stack.pop();
                    if (context.world != null) context.world.pop();
                }
            }
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        if (!this.form.repeatEnabled.get())
        {
            super.collectMatrices(entity, stack, matrices, prefix, transition);
            return;
        }
        mchorse.bbs_mod.api.client.events.FormPoseEvents.PARENT_FRAME.invoker().capture(this.form, entity, stack.peek().getPositionMatrix(), prefix, transition);
        stack.push();
        this.applyTransforms(stack, true, transition);
        Matrix4f origin = new Matrix4f(stack.peek().getPositionMatrix());
        stack.pop();
        stack.push();
        try
        {
            this.applyTransforms(stack, false, transition);
            matrices.put(prefix, new Matrix4f(stack.peek().getPositionMatrix()), origin);
            var frames = SplineArray.frames(this.form);
            // There is one editable source. All picks point to it; its gizmo uses the first visible copy.
            if (frames.isEmpty()) return;
            MatrixStackUtils.multiply(stack, frames.get(0));
            for (BodyPart part : this.form.parts.getAllTyped())
            {
                if (part.getForm() == null) continue;
                stack.push();
                MatrixStackUtils.applyTransform(stack, part.transform.get());
                FormUtilsClient.getRenderer(part.getForm()).collectMatrices(part.getRenderEntity(entity), stack, matrices,
                    StringUtils.combinePaths(prefix, part.getId()), transition);
                stack.pop();
            }
        }
        finally { stack.pop(); }
    }
    @Override protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Matrix4f matrix = new Matrix4f(ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2));
        this.applyTransforms(matrix, context.getTransition());
        SplineOverlay.drawPreview(context, this.form, matrix);
    }
}
