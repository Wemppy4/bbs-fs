package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ItemForm;
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
import org.joml.Vector3f;

public class ItemFormRenderer extends FormRenderer<ItemForm>
{
    public ItemFormRenderer(ItemForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        Color set = Color.white();
        FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);
        MinecraftClient.getInstance().getItemRenderer().renderItem(this.form.stack.get(), this.form.modelTransform.get(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, matrices, consumers, MinecraftClient.getInstance().world, 0);
        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.push();

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }
        else
        {
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());
        }

        BlockFormRenderer.color.set(context.color);
        FormColorBlend.blend(BlockFormRenderer.color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)). */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(BlockFormRenderer.color));
        MinecraftClient.getInstance().getItemRenderer().renderItem(this.form.stack.get(), this.form.modelTransform.get(), light, context.overlay, context.stack, consumers, context.entity.getWorld(), 0);
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();

        RenderSystem.enableDepthTest();
    }
}
