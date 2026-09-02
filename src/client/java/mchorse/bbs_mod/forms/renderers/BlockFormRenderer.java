package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.picker.PickingReplay;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.QueueDispatch;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.renderers.utils.FluidVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.forms.renderers.utils.SingleBlockRenderView;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    /**
     * Stand-in for the translucent layers while a color overlay is on. A block form already draws
     * through entity layers, whose shader mixes the bound overlay into the fragment — except the
     * two translucent ones: vanilla's {@code entity_translucent_cull} and
     * {@code item_entity_translucent_cull} take the UV1 attribute and then never read the overlay
     * texture at all, so glass, ice and water would be the only blocks the overlay skips.
     *
     * <p>{@code entity_translucent} is the same layer with the channel kept — the one difference
     * left is that it draws back faces, which the renderer's per-layer hook culls back out. The
     * swap only happens while an overlay is actually set, so a plain block form keeps the exact
     * layer it always had.</p>
     */
    public static final RenderLayer OVERLAY_TRANSLUCENT_LAYER = RenderLayers.entityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    public static final Color color = new Color();

    private final SingleBlockRenderView fluidView = new SingleBlockRenderView();

    private BlockEntity blockEntity;
    private BlockState blockEntityState;

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    /**
     * The provider the block draws through while a color overlay is on: the two translucent
     * layers that drop the overlay channel are swapped for {@link #OVERLAY_TRANSLUCENT_LAYER},
     * everything else is left exactly where vanilla put it. Both swapped layers are the block
     * atlas ones, so the stand-in samples the same texture.
     */
    /* 1.21.11: fed to CustomVertexConsumerProvider#setLayerMapper rather than wrapping the provider —
     * the block path calls draw() on it, which a bare VertexConsumerProvider lambda cannot answer, and
     * the mapper is the port's own hook for exactly this swap. Null keeps the original layer. */
    private static RenderLayer overlayLayer(RenderLayer layer)
    {
        return layer == TexturedRenderLayers.getItemTranslucentCull() || layer == TexturedRenderLayers.getBlockTranslucentCull()
            ? OVERLAY_TRANSLUCENT_LAYER
            : null;
    }

    /**
     * Bind the color overlay for the layer that is about to draw. The hook fires right after the
     * layer applied its own phases, which is where it bound vanilla's hurt-flash texture over
     * unit 1 — so this has to come after them.
     */
    private static void setupOverlay(RenderLayer layer, Color overlay)
    {
        if (layer == OVERLAY_TRANSLUCENT_LAYER)
        {
            /* The stand-in draws back faces where the layer it replaces did not */
            /* TODO(1.21.11 render): RenderSystem.enableCull() was removed by the GPU-pipeline rewrite; this state is now encoded by the RenderLayer/RenderPipeline. */
        }

        FormOverlay.bind(overlay);
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
        FormColorBlend.blend(set, this.form.color.get());

        Color overlay = this.form.overlayColor.get();
        boolean overlayActive = OverlayBlend.isActive(overlay);
        int previousOverlayTexture = 0;

        if (overlayActive)
        {
            /* Bound up front for the id it hands back: the per-layer binding below cannot restore
             * the unit itself, and not every layer has an overlay phase whose teardown would */
            previousOverlayTexture = FormOverlay.bind(overlay);

            CustomVertexConsumerProvider.hijackVertexFormat((layer) -> setupOverlay(layer, overlay));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);
        consumers.setLayerMapper(overlayActive ? BlockFormRenderer::overlayLayer : null);
        this.renderBlock(stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false);
        consumers.setLayerMapper(null);
        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        if (overlayActive)
        {
            CustomVertexConsumerProvider.clearRunnables();
            FormOverlay.unbind(previousOverlayTexture);
        }

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

        Color overlay = this.form.overlayColor.get();
        boolean overlayActive = !context.isPicking() && OverlayBlend.isActive(overlay);
        /* Bound up front for the id it hands back: the per-layer binding below cannot restore the
         * unit itself, and not every layer has an overlay phase whose teardown would */
        int previousOverlayTexture = overlayActive ? FormOverlay.bind(overlay) : 0;

        if (context.isPicking())
        {
            /* Picking: capture the block's vanilla-layer geometry and replay it through the
             * picker_models pipeline into the stencil (see PickingReplay — the 1.21.1 global-shader
             * swap has no equivalent, the capture+replay does the same job). */
            this.setupTarget(context);

            FormRenderCapture.begin();

            Map<RenderLayer, List<FormRenderCapture.Captured>> captured;

            try
            {
                this.renderBlock(context.stack, consumers, 0, context.overlay, true);
                consumers.draw();
            }
            finally
            {
                captured = FormRenderCapture.end();
            }

            PickingReplay.draw(captured);

            context.stack.pop();

            if (context.world != null)
            {
                context.world.pop();
            }

            return;
        }

        /* RenderSystem.enableBlend() is gone in 1.21.11 — blend state lives in each RenderLayer's
         * RenderPipeline, so only the overlay layer swap is left to install here. */
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            if (overlayActive)
            {
                setupOverlay(l, overlay);
            }
        });

        color.set(context.color);
        FormColorBlend.blend(color, this.form.color.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)); the
         * picking branch above never publishes, so the stencil keeps every pixel. */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        /* The block's translucent layer opts into the deferred pass above, and that draw happens
         * after the per-layer hook is gone — so the overlay travels with the command instead. */
        consumers.setOverlay(overlayActive ? overlay : null);
        consumers.setLayerMapper(overlayActive ? BlockFormRenderer::overlayLayer : null);
        this.renderBlock(context.stack, consumers, light, context.overlay, context.isPicking());
        consumers.setLayerMapper(null);
        consumers.draw();
        consumers.setSubstitute(null);
        consumers.setOverlay(null);
        FormTranslucentQueue.setSortOrigin(null);

        CustomVertexConsumerProvider.clearRunnables();

        if (overlayActive)
        {
            FormOverlay.unbind(previousOverlayTexture);
        }

        context.stack.pop();
        if (context.world != null)
        {
            context.world.pop();
        }

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest() was removed in 1.21.5; depth testing is
         * now encoded per RenderLayer via DepthTestFunction on its pipeline. No restore needed here. */
    }

    /**
     * Draw the block state the way the world would draw it.
     *
     * <p>Vanilla's renderBlockAsEntity() draws a baked block model and nothing else, so
     * everything the world puts on top of that model, or instead of it, was silently missing
     * here: water and lava, whose geometry the fluid renderer generates per chunk section;
     * signs, banners, skulls and the end portal, which render as
     * {@link BlockRenderType#INVISIBLE} and are drawn entirely by a block entity renderer;
     * the bell's body, the campfire's food, the lectern's book, which a block entity renderer
     * adds on top of the model; and marker blocks like the barrier, which only ever exist as
     * an item icon. Each of those gets its own path below.</p>
     */
    private void renderBlock(MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay, boolean picking)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockState state = this.form.blockState.get();
        BlockRenderType type = state.getRenderType();
        FluidState fluidState = state.getFluidState();

        /* Not only water and lava: this is also where a waterlogged block gets its water,
         * on top of its own model below. */
        if (!fluidState.isEmpty())
        {
            RenderLayer layer = BlockRenderLayers.getEntityBlockLayer(fluidState.getBlockState());
            FluidVertexConsumer consumer = new FluidVertexConsumer(consumers.getBuffer(layer), matrices.peek(), overlay);

            mc.getBlockRenderManager().renderFluid(BlockPos.ORIGIN, this.fluidView.set(state, light), consumer, state, fluidState);
        }

        if (type != BlockRenderType.INVISIBLE)
        {
            mc.getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers, light, overlay);
        }

        if (picking)
        {
            /* Picking stays out of the paths below on purpose: they draw through layers of
             * their own, and a sign's text or an end portal's sides are not even in the
             * entity vertex format the picking shader is compiled for. Such a form gets
             * selected from the outliner instead. */
            return;
        }

        /* 1.21.4+ dropped BlockRenderType.ENTITYBLOCK_ANIMATED: a chest, a bed or a shulker box
         * has a plain model like anything else, and everything animated about it comes from its
         * block entity renderer - so there is no longer a family to exclude here. */
        if (this.renderBlockEntity(mc, state, matrices, consumers, light, overlay))
        {
            return;
        }

        if (type == BlockRenderType.INVISIBLE && fluidState.isEmpty())
        {
            /* Barrier, light block, structure void: invisible in the world, but they do have
             * an icon, and a form of one should show something. The item model is centered on
             * the origin, while a block model spans 0..1, hence the half block nudge. */
            ItemStack stack = new ItemStack(state.getBlock());

            if (!stack.isEmpty())
            {
                matrices.push();
                matrices.translate(0.5F, 0.5F, 0.5F);
                ItemFormRenderer.renderItem(stack, ItemDisplayContext.NONE, matrices, consumers, mc.world, light, overlay);
                matrices.pop();
            }
        }
    }

    /**
     * Run the block state's block entity renderer, keeping the block entity itself around
     * between frames: it is an argument the renderer needs, not state of the form.
     *
     * @return whether there was a renderer to run
     */
    private boolean renderBlockEntity(MinecraftClient mc, BlockState state, MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        if (mc.world == null || !(state.getBlock() instanceof BlockEntityProvider provider))
        {
            return false;
        }

        if (this.blockEntity == null || this.blockEntityState != state)
        {
            this.blockEntity = provider.createBlockEntity(BlockPos.ORIGIN, state);
            this.blockEntityState = state;
        }

        if (this.blockEntity == null)
        {
            return false;
        }

        if (this.blockEntity.getWorld() != mc.world)
        {
            /* Renderers of blocks that tick or move (the bell, the beacon) read the world off
             * the block entity, and the client's is the only one a form can offer. */
            this.blockEntity.setWorld(mc.world);
        }

        BlockEntityRenderManager manager = mc.getBlockEntityRenderDispatcher();
        BlockEntityRenderer renderer = manager.get(this.blockEntity);

        if (renderer == null)
        {
            return false;
        }

        /* Not manager.getRenderState(): it refuses anything further than the renderer's render
         * distance from the camera, and a form's block entity always sits at the world origin -
         * a chest form would have stopped opening 64 blocks away from spawn. The state is built
         * by hand instead, and the form's own light replaces the light at that origin. */
        BlockEntityRenderState renderState = renderer.createRenderState();

        renderer.updateRenderState(this.blockEntity, renderState, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false), mc.gameRenderer.getCamera().getCameraPos(), null);

        renderState.lightmapCoordinates = light;

        /* Block entity renderers only submit commands since 1.21.2, so they go through the BBS
         * queue and are flushed right here - the same path the mob form's entity takes. */
        manager.render(renderState, matrices, QueueDispatch.queue(), QueueDispatch.cameraState());
        QueueDispatch.flush();
        consumers.draw();

        return true;
    }
}
