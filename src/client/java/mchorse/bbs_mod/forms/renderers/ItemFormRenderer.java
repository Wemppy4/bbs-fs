package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public class ItemFormRenderer extends FormRenderer<ItemForm>
{
    /* Reused per render to avoid per-frame allocation; the form renderers run single-threaded on the
     * client render thread (same assumption as BlockFormRenderer.color). clearAndUpdate() wipes it first. */
    private static final ItemRenderState renderState = new ItemRenderState();

    public ItemFormRenderer(ItemForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* DrawContext.draw() was removed in the 1.21.5 UI rewrite; the engine flushes immediate draws. */
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        /* DrawContext.getMatrices() now returns a 2D Matrix3x2fStack; item rendering needs a 3D
         * MatrixStack, so build a dedicated one and apply the UI matrix to it. */
        MatrixStack matrices = new MatrixStack();

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
        /* 1.21.1 called MinecraftClient.getItemRenderer().renderItem(stack, modelTransform, MAX_BLOCK_LIGHT,
         * DEFAULT_UV, matrices, consumers, world, 0). The 1.21.4 item-model rewrite removed that high-level
         * stack overload; the faithful replacement resolves the stack into per-layer draw commands and feeds
         * them through the surviving VertexConsumerProvider-taking ItemRenderer.renderItem static, so the BBS
         * recolor/picking substitution on `consumers` still intercepts the geometry. */
        renderItem(this.form.stack.get(), this.form.modelTransform.get(), matrices, consumers, MinecraftClient.getInstance().world, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
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
                /* TODO(1.21.11 render): RenderSystem.setShader and ShaderProgram-based setupTarget were
                 * removed in 1.21.5. The picker_models pipeline must be bound via its RenderLayer and the
                 * per-object Target uniform supplied through the pipeline's UBO/DynamicUniforms. */
            });

            light = 0;
        }
        else
        {
            /* TODO(1.21.11 render): RenderSystem.enableBlend() is gone; blend state now lives in each
             * RenderLayer's RenderPipeline. */
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});
        }

        BlockFormRenderer.color.set(context.color);
        FormColorBlend.blend(BlockFormRenderer.color, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(BlockFormRenderer.color));

        /* 1.21.1 called renderItem(stack, modelTransform, light, overlay, context.stack, consumers,
         * entity world, 0). Same faithful command-queue replacement as renderInUI. */
        World world = context.entity == null ? null : context.entity.getWorld();

        renderItem(this.form.stack.get(), this.form.modelTransform.get(), context.stack, consumers, world, light, context.overlay);
        consumers.draw();
        consumers.setSubstitute(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest() was removed in 1.21.5; depth testing is
         * now encoded per RenderLayer via DepthTestFunction on its pipeline. */
    }

    /**
     * Faithful 1.21.11 replacement for the removed high-level {@code ItemRenderer.renderItem(ItemStack, ...,
     * VertexConsumerProvider, ...)} overload used by the 1.21.1 renderer.
     *
     * <p>The 1.21.4 item-model rewrite resolves an {@link ItemStack} into an {@link ItemRenderState} of baked
     * draw layers, then renders it into an {@code OrderedRenderCommandQueue} (bypassing
     * {@link net.minecraft.client.render.VertexConsumerProvider}). To preserve the BBS recolor/picking hook
     * (which works by substituting the {@code VertexConsumerProvider}), we:</p>
     * <ol>
     *     <li>resolve the stack into per-layer item commands via {@link ItemModelManager#clearAndUpdate} +
     *     {@link ItemRenderState#render} into a private {@link OrderedRenderCommandQueueImpl};</li>
     *     <li>replay each queued {@code ItemCommand} through the surviving low-level static
     *     {@link ItemRenderer#renderItem(ItemDisplayContext, MatrixStack, net.minecraft.client.render.VertexConsumerProvider, int, int, int[], java.util.List, net.minecraft.client.render.RenderLayer, ItemRenderState.Glint)}
     *     (which still accepts a {@code VertexConsumerProvider}) feeding the BBS {@code consumers}.</li>
     * </ol>
     * This mirrors what {@link net.minecraft.client.render.command.ItemCommandRenderer} does internally, but
     * routes geometry through the recolor provider instead of the engine's {@code Immediate}.
     */
    private static void renderItem(ItemStack stack, ItemDisplayContext displayContext, MatrixStack matrices, CustomVertexConsumerProvider consumers, World world, int light, int overlay)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        ItemModelManager modelManager = MinecraftClient.getInstance().getItemModelManager();

        /* Resolve the item into baked per-layer geometry (replaces 1.21.1's implicit model resolution inside
         * the old renderItem). seed=0 matches the original; HeldItemContext is null (as updateForNonLivingEntity
         * passes for free-standing items). */
        modelManager.clearAndUpdate(renderState, stack, displayContext, world, null, 0);

        OrderedRenderCommandQueueImpl queue = new OrderedRenderCommandQueueImpl();

        /* light/overlay/outlineColor: outlineColor=0 (no glow outline). render() enqueues one ItemCommand per
         * layer into the queue's batching queues. */
        renderState.render(matrices, queue, light, overlay, 0);

        for (BatchingRenderCommandQueue batch : queue.getBatchingQueues().values())
        {
            for (OrderedRenderCommandQueueImpl.ItemCommand command : batch.getItemCommands())
            {
                /* Replay faithfully (see ItemCommandRenderer#render): push a copy of the captured entry, draw,
                 * pop. The VertexConsumerProvider is the BBS recolor/picking-substituting `consumers`. */
                matrices.push();
                matrices.peek().copy(command.positionMatrix());
                ItemRenderer.renderItem(
                    command.displayContext(),
                    matrices,
                    consumers,
                    command.lightCoords(),
                    command.overlayCoords(),
                    command.tintLayers(),
                    command.quads(),
                    command.renderLayer(),
                    command.glintType()
                );
                matrices.pop();
            }
        }
    }
}
