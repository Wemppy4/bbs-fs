package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

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
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        this.renderModel(BBSShaders::getModel,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition(), false
        );
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

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

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        Supplier<ShaderProgram> normal = shading && !BBSRendering.isIrisShadersEnabled()
            ? BBSShaders::getModel
            : (shading ? GameRenderer::getRenderTypeEntityTranslucentProgram : GameRenderer::getPositionTexColorProgram);
        Supplier<ShaderProgram> shader = this.getShader(context,
            normal,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
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
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor);

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(textureObject);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();

            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Same law as model forms under Iris: the draw always runs right now, in the phase
             * its program is meant for (the end-of-frame replay lands after a deferred pack's
             * shading composite — Photon never shades it), and texture translucency at full
             * colour degrades to alpha cutout — transparent texels become holes, semi-transparent
             * ones draw solid. */
            boolean cutout = defer && irisWorld && textureObject != null && textureObject.hasTranslucency()
                && color.a >= 1F;

            ShaderProgram finalShader = cutout ? GameRenderer.getRenderTypeEntityCutoutProgram() : shader.get();
            boolean wasActive = false;

            if (irisWorld)
            {
                wasActive = FormTranslucentQueue.suspend();
            }

            if (cutout)
            {
                RenderSystem.disableBlend();
            }

            try
            {
                if (defer && FormTranslucentQueue.needsSplit(finalShader, null, textureObject, color.a))
                {
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.peek().getNormalMatrix());

                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_OPAQUE);
                    ModelVAORenderer.render(finalShader, data, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay);
                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_SINGLE);

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else if (defer && FormTranslucentQueue.needsWholeDefer(finalShader, null, color.a))
                {
                    /* A uniform colour fade defers the whole draw into the sorted end-of-frame
                     * pass with depth kept on, so the faded form still self-occludes. */
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.peek().getNormalMatrix());

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, () -> finalShader, FormTranslucentQueue.PASS_SINGLE, true, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else
                {
                    ModelVAORenderer.render(finalShader, data, matrices, color.r, color.g, color.b, color.a, light, overlay);
                }
            }
            finally
            {
                if (irisWorld)
                {
                    FormTranslucentQueue.restore(wasActive);
                }
            }

            RenderSystem.disableBlend();

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }
}
