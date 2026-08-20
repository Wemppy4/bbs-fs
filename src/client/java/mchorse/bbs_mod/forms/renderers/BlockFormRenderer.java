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
import mchorse.bbs_mod.forms.renderers.utils.SingleBlockRenderView;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
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
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
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
    public static final Color color = new Color();

    private final SingleBlockRenderView fluidView = new SingleBlockRenderView();

    private BlockEntity blockEntity;
    private BlockState blockEntityState;

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
        this.renderBlock(stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false);
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

        /* TODO(1.21.11 render): RenderSystem.enableBlend() is gone; blend state now lives in each
         * RenderLayer's RenderPipeline. No imperative blend toggle needed here. */
        CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});

        color.set(context.color);
        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)); the
         * picking branch above never publishes, so the stencil keeps every pixel. */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        this.renderBlock(context.stack, consumers, light, context.overlay, context.isPicking());
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);

        CustomVertexConsumerProvider.clearRunnables();

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
