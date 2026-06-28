package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.structure.BakedStructure;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.forms.structure.StructureRenderData;
import mchorse.bbs_mod.forms.structure.StructureRenderWorld;
import mchorse.bbs_mod.forms.structure.StructureWorld;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renders a {@link StructureForm}: every block of the structure goes through the vanilla
 * block/fluid renderer against a {@link StructureRenderWorld} (smooth AO + biome tint baked
 * at tesselation time, textures from the active resource pack).
 *
 * <p>Normal rendering replays a {@link BakedStructure} (tesselated once per
 * structure/biome/resource generation). The naive per-frame path is kept only for editor
 * picking, where the hijacked picker shader is applied per draw.</p>
 */
public class StructureFormRenderer extends FormRenderer<StructureForm>
{
    private static final Color COLOR = new Color();

    private String lastStructure;
    private String lastBiome;

    private StructureRenderData data;
    private StructureRenderWorld world;
    private BakedStructure baked;

    private List<BlockEntity> blockEntities;
    private final Set<BlockPos> erroredBlockEntities = new HashSet<>();

    /** Structure-backed world the block entities are bound to (null until built; falls back to mc.world). */
    private World structureWorld;

    public StructureFormRenderer(StructureForm form)
    {
        super(form);
    }

    /** Reload structure/biome when the form properties change. */
    private void ensureData()
    {
        String structure = this.form.structure.get();
        String biome = this.form.biome.get();

        if (!Objects.equals(structure, this.lastStructure))
        {
            this.lastStructure = structure;
            this.data = StructureManager.get(structure);
            this.world = null;
            this.blockEntities = null;
        }

        /* The cache may deliver data later (e.g. after a world is present) */
        if (this.data == null)
        {
            this.data = StructureManager.get(structure);
            this.world = null;
            this.blockEntities = null;
        }

        if (this.data != null && (this.world == null || !Objects.equals(biome, this.lastBiome)))
        {
            this.lastBiome = biome;
            this.world = new StructureRenderWorld(this.data, biome);
        }
    }

    private void ensureBaked()
    {
        if (this.baked == null || !this.baked.isValidFor(this.world))
        {
            this.baked = BakedStructure.bake(this.data, this.world);
        }
    }

    /**
     * Depth guard for nested forms: a BBS model block saved inside a structure renders its own
     * form, which may itself be (or contain) a structure form — without a cap a self-referential
     * structure would recurse forever.
     */
    private static int blockEntityDepth;

    /** Render chests/signs/beds/... through their vanilla block entity renderers (per frame). */
    private void renderBlockEntities(MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        if (blockEntityDepth >= 2)
        {
            return;
        }

        blockEntityDepth += 1;

        try
        {
            this.doRenderBlockEntities(matrices, consumers, light, overlay);
        }
        finally
        {
            blockEntityDepth -= 1;
        }
    }

    private void doRenderBlockEntities(MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        if (this.blockEntities == null)
        {
            this.blockEntities = new ArrayList<>();
            this.erroredBlockEntities.clear();

            Map<BlockPos, BlockEntity> byPos = new HashMap<>();

            for (Map.Entry<BlockPos, NbtCompound> e : this.data.getBlockEntities().entrySet())
            {
                try
                {
                    BlockEntity blockEntity = BlockEntity.createFromNbt(e.getKey(), this.data.getBlockState(e.getKey()), e.getValue());

                    if (blockEntity != null)
                    {
                        this.blockEntities.add(blockEntity);
                        byPos.put(e.getKey(), blockEntity);
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }

            /* Bind them to a structure-backed world so neighbor/light queries resolve within the
             * structure (double chests pair, etc.); null falls back to mc.world below */
            this.structureWorld = StructureWorld.create(this.data, byPos);
        }

        if (this.blockEntities.isEmpty())
        {
            return;
        }

        BlockEntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity blockEntity : this.blockEntities)
        {
            BlockPos pos = blockEntity.getPos();

            if (this.erroredBlockEntities.contains(pos))
            {
                continue;
            }

            /* Renderers may query the world (light, double chest neighbors, BBS model blocks). The
             * structure-backed world resolves those against the structure itself; if it could not be
             * built (no client world to borrow registries from), fall back to the real client world */
            blockEntity.setWorld(this.structureWorld != null ? this.structureWorld : MinecraftClient.getInstance().world);

            /* Isolated stack: if the renderer throws mid-render, its unbalanced pushes must not
             * corrupt the shared pose stack ("Pose stack not empty" crash) */
            MatrixStack local = new MatrixStack();

            local.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
            local.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
            local.translate(pos.getX(), pos.getY(), pos.getZ());

            try
            {
                dispatcher.renderEntity(blockEntity, local, consumers, light, overlay);
            }
            catch (Exception ex)
            {
                /* Renderer incompatible with detached block entities — skip it from now on */
                this.erroredBlockEntities.add(pos);
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureData();

        if (this.world == null)
        {
            /* No structure picked (or not loadable) — render a structure block as the
             * placeholder, the same way the block form renders its preview */
            this.renderPlaceholderBlock(context, x1, y1, x2, y2);

            return;
        }

        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();
        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();

        try
        {
            MatrixStackUtils.multiply(matrices, uiMatrix);

            Vec3i size = this.data.size;
            float max = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
            float scale = (max > 0 ? 1F / max : 1F) * this.form.uiScale.get();

            matrices.scale(scale, scale, scale);
            matrices.translate(-size.getX() / 2F, 0F, -size.getZ() / 2F);

            matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.ensureBaked();

            Color set = Color.white();
            FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

            consumers.setUI(true);
            /* UI preview always uses the correct (non-fast) path */
            this.baked.render(matrices.peek(), consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, set.getARGBColor(), false);

            consumers.setSubstitute(BBSRendering.getColorConsumer(set));
            this.renderBlockEntities(matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            consumers.setUI(false);

            matrices.pop();
        }
    }

    private void renderPlaceholderBlock(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();
        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();

        try
        {
            MatrixStackUtils.multiply(matrices, uiMatrix);
            matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());
            matrices.translate(-0.5F, 0F, -0.5F);

            matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            consumers.setUI(true);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(Blocks.STRUCTURE_BLOCK.getDefaultState(), matrices, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            consumers.draw();
        }
        finally
        {
            consumers.setUI(false);

            matrices.pop();
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureData();

        if (this.world == null)
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        Vec3i size = this.data.size;

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }

        /* finally guarantees pops/state reset: a renderer failure must degrade to a log line,
         * not corrupt the frame ("Pose stack not empty" + profiler cascade) */
        try
        {
            context.stack.translate(-size.getX() / 2F, 0F, -size.getZ() / 2F);
            if (context.world != null)
            {
                context.world.translate(-size.getX() / 2F, 0F, -size.getZ() / 2F);
            }

            COLOR.set(context.color);
            FormColorBlend.blend(COLOR, this.form.color.get(), this.form.additiveColor.get());

            this.ensureBaked();

            if (context.isPicking())
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                    RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
                });

                /* Picking replays the geometry through the picker shader per layer — force the
                 * correct (non-fast) path so the raw-byte route never bypasses it */
                this.baked.render(context.stack.peek(), consumers, context.light, 0xFFFFFFFF, false);
            }
            else
            {
                /* Force blend only when the form is actually faded (tint alpha < 1). For an opaque
                 * structure the terrain layers keep their native state — critical for cutout layers
                 * (leaves, plants): with blend forced on, their mipmapped edges turn semi-transparent
                 * and reveal the back faces behind them ("tearing" at grazing angles). Translucent
                 * blocks (glass/water) still blend through their own entity translucent-cull layer. */
                boolean faded = COLOR.a < 1F;

                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    if (faded)
                    {
                        RenderSystem.enableBlend();
                    }
                });

                this.baked.render(context.stack.peek(), consumers, context.light, COLOR.getARGBColor(), this.form.fastRender.get());

                /* Block entities still go through the consumer interface — tint them via substitute */
                consumers.setSubstitute(BBSRendering.getColorConsumer(COLOR));
                this.renderBlockEntities(context.stack, consumers, context.light, context.overlay);
            }

            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            CustomVertexConsumerProvider.clearRunnables();

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }

            RenderSystem.enableDepthTest();
        }
    }
}
