package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.graphics.texture.Texture;
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
        this.renderModel(null,
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

        /* Picking replaces the visible draw with the picker pipeline, which writes the form's object
         * index instead of the texture. setupTarget records that index into the BBSPicker UBO — the
         * spot where 1.21.1's getShader(...) set the Target uniform. Extrusion always builds the
         * lit vertex layout, so it picks with picker_billboard in both shading modes, exactly as on
         * 1.21.1 (which chose the no-shading picker only for the layout it never produced here). */
        RenderPipeline picker = null;

        if (context.isPicking())
        {
            this.setupTarget(context);

            picker = BBSShaders.getPickerBillboardProgram();
        }

        this.renderModel(picker, context.stack, context.overlay, context.light, context.color, context.getTransition());
    }

    private void renderModel(RenderPipeline picker, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Link texture = this.form.texture.get();
        ModelVAO data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
            if (this.form.billboard.get())
            {
                MatrixStackUtils.billboard(matrices);
            }

            Color color = Colors.COLOR.set(overlayColor, true);
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor, this.form.additiveColor.get());

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(textureObject);

            if (picker != null)
            {
                /* The picker shader still samples Sampler0 for its alpha cutout, so the extruded
                 * silhouette stays pickable exactly where it is visible. */
                BBSPickerRenderer.setSampler0(textureObject);
                ModelVAORenderer.renderPicking(data, matrices, color.r, color.g, color.b, color.a, light, overlay, picker);

                return;
            }

            /* Blend/depth and the lightmap/overlay samplers are encoded by the model RenderLayer; the
             * geometry is baked CPU-side and drawn immediately, the same proven path cubic models use.
             * Culled, like the 1.21.1 draw: extrusion is closed geometry, nothing of it should ever be
             * seen from the inside, and its back faces are pure overdraw (doubled alpha where the
             * texture is translucent). ExtrudedFormRenderer never touched the global GL cull, so on
             * 1.21.1 it drew with vanilla's culling on. */
            ModelVAORenderer.render(data, matrices, color.r, color.g, color.b, color.a, light, overlay, true);
        }
    }
}
