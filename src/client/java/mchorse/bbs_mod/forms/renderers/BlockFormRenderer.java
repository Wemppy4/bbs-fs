package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    public static final Color color = new Color();

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* List/icon preview: submit a special GUI element so the block draws off-screen during the GUI prepare
         * phase (two-phase GUI drops a direct immediate draw here). BbsFormGuiElementRenderer calls back into
         * renderUIPreview inside the FBO render pass — same path as ModelForm. */
        this.submitUIPreview(context, x1, y1, x2, y2);
    }

    @Override
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        /* The base renderer pre-translated the stack to the cell (centre, 0.85*height down) + scale(f,f,-f);
         * apply the rest of the original getUIMatrix framing here, then the original block post-ops + draw
         * (renderBlockAsEntity + consumers.draw, the same path render3D uses, confirmed working in-world). */
        Matrix4f uiMatrix = getUIPreviewMatrix(angle, y1, y2);

        stack.push();
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());
        stack.translate(-0.5F, 0F, -0.5F);

        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        Color set = Color.white();
        FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(this.form.blockState.get(), stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }
        context.stack.translate(-0.5F, 0F, -0.5F);
        if (context.world != null)
        {
            context.world.translate(-0.5F, 0F, -0.5F);
        }

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                /* TODO(1.21.11 render): RenderSystem.setShader and ShaderProgram-based setupTarget were
                 * removed in 1.21.5. The picker_models pipeline must be bound via its RenderLayer and the
                 * per-object Target uniform supplied through the pipeline's UBO/DynamicUniforms. Neutralized
                 * here so the block still renders (vanilla pipeline); picking selection needs runtime wiring. */
            });

            light = 0;
        }
        else
        {
            /* TODO(1.21.11 render): RenderSystem.enableBlend() is gone; blend state now lives in each
             * RenderLayer's RenderPipeline. No imperative blend toggle needed here. */
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});
        }

        color.set(context.color);
        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(this.form.blockState.get(), context.stack, consumers, light, context.overlay);
        consumers.draw();
        consumers.setSubstitute(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();
        if (context.world != null)
        {
            context.world.pop();
        }

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest() was removed in 1.21.5; depth testing is
         * now encoded per RenderLayer via DepthTestFunction on its pipeline. No restore needed here. */
    }
}
