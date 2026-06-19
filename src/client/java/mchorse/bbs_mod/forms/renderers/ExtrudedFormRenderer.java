package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    public ExtrudedFormRenderer(ExtrudedForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* List/icon preview: submit a special GUI element so the extruded model draws off-screen during the GUI
         * prepare phase (two-phase GUI drops a direct immediate draw here). BbsFormGuiElementRenderer calls back
         * into renderUIPreview inside the FBO render pass — same path as ModelForm. */
        this.submitUIPreview(context, x1, y1, x2, y2);
    }

    @Override
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        /* The base renderer pre-translated the stack to the cell (centre, 0.85*height down) + scale(f,f,-f);
         * apply the rest of the original getUIMatrix framing here, then the original extruded post-ops + draw
         * (ModelVAORenderer.render via getModelLayer, the same path render3D uses, confirmed working in-world). */
        Matrix4f uiMatrix = getUIPreviewMatrix(angle, y1, y2);

        this.applyTransforms(uiMatrix, transition);

        stack.push();

        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        /* Was: RenderSystem.depthFunc(GL_LEQUAL) ... depthFunc(GL_ALWAYS). Depth test is now
         * per-pipeline (the model pipeline declares LEQUAL_DEPTH_TEST). */
        this.renderModel(BBSShaders::getModel,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            transition
        );

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        /* TODO(1.21.11 render): ShaderProgram and GameRenderer::getRenderTypeEntityTranslucentProgram
         * /getPositionTexColorProgram are removed; the old getShader(...) picking path (which set the
         * Target GlUniform via setupTarget) is gone. For now always select the BBS model
         * RenderPipeline; the picker pipeline (BBSShaders.getPickerBillboard[NoShading]Program) must
         * be selected + its Target UBO uniform supplied once the picking foundation lands. */
        Supplier<RenderPipeline> shader = BBSShaders::getModel;

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition());
    }

    private void renderModel(Supplier<RenderPipeline> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Link texture = this.form.texture.get();
        ModelVAO data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
            if (this.form.billboard.get())
            {
                Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
                Vector3f scale = Vectors.TEMP_3F;

                modelMatrix.getScale(scale);

                modelMatrix.m00(1).m01(0).m02(0);
                modelMatrix.m10(0).m11(1).m12(0);
                modelMatrix.m20(0).m21(0).m22(1);

                modelMatrix.scale(scale);

                matrices.peek().getNormalMatrix().identity();
            }

            Color color = Colors.COLOR.set(overlayColor, true);
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor, this.form.additiveColor.get());

            BBSModClient.getTextures().bindTexture(texture);

            /* Blend/depth/cull and the lightmap/overlay samplers are encoded by the model RenderLayer
             * (BBSShaders.getModelLayer()); the geometry is baked CPU-side and drawn immediately, the
             * same proven path cubic Models/Billboards use. The picker pipeline carried by `shader`
             * will be selected here once the picking foundation lands (see render3D TODO). */
            ModelVAORenderer.render(data, matrices, color.r, color.g, color.b, color.a, light, overlay);
        }
    }
}
