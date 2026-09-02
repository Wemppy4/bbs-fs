package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pre-tesselated structure geometry: all blocks + fluids are run through the vanilla renderer
 * ONCE (smooth AO and biome tint get baked into vertex colors), the resulting vertex data is
 * kept per render layer and replayed every frame with just a matrix transform — the same idea
 * as Create's SuperByteBuffer, minus the dependency.
 *
 * <p>A bake is valid for one (structure, biome) pair — the renderer rebakes when its
 * {@link StructureRenderWorld} instance changes — and for one resource generation:
 * {@link #invalidateAll()} is hooked to Fabric's render-state invalidation (resource pack
 * switch, F3+A), because baked sprite UVs go stale when atlases rebuild.</p>
 */
public class BakedStructure
{
    /**
     * Where the color overlay pass draws. What eliminated the alternatives, in order:
     *
     * <ul>
     * <li>its shader must read the overlay channel. The terrain shaders the structure normally
     * draws through have no such channel at all, and neither does {@code entity_translucent_cull},
     * the layer its own translucent blocks go to;</li>
     * <li>its name must not contain "translucent", or the provider defers it into the frame's
     * sorted translucent queue — which draws it long after the overlay texture was unbound;</li>
     * <li>it must cut out on the raw texel alpha, so leaves and plants keep their shape at any
     * overlay strength (a cutout shader tests the texel before the vertex color reaches it).</li>
     * </ul>
     *
     * <p>Blending and back-face culling are turned back on for the draw by the renderer, through
     * the same per-layer hook that binds the overlay texture: the pass composites rather than
     * replaces, and a plant's double-sided cross must not be painted twice.</p>
     */
    public static final RenderLayer OVERLAY_LAYER = RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    private static int globalGeneration;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Scratch builder reused across bakes (grows once and stays). */

    private final List<BakedLayer> layers = new ArrayList<>();

    /** Sprites referenced by the baked geometry — marked active for Sodium every frame. */
    private final Set<Sprite> sprites = new HashSet<>();

    private final StructureRenderWorld world;
    private final int generation;

    private record BakedLayer(RenderLayer layer, ByteBuffer data, int vertexCount) {}

    private BakedStructure(StructureRenderWorld world)
    {
        this.world = world;
        this.generation = globalGeneration;
    }

    public static void invalidateAll()
    {
        globalGeneration += 1;
    }

    public boolean isValidFor(StructureRenderWorld world)
    {
        return this.world == world && this.generation == globalGeneration;
    }

    public static BakedStructure bake(StructureRenderData data, StructureRenderWorld world)
    {
        BakedStructure result = new BakedStructure(world);

        BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
        Random random = Random.create();
        MatrixStack matrices = new MatrixStack();
        TransformingVertexConsumer fluidConsumer = new TransformingVertexConsumer(new Matrix4f(), new Matrix3f());

        for (Map.Entry<BlockPos, BlockState> e : data.getBlocks().entrySet())
        {
            result.collectSprites(manager, world, e.getKey(), e.getValue(), random);
        }

        for (RenderLayer layer : RenderLayer.getBlockLayers())
        {
            BufferBuilder builder = beginBuffer(layer.getDrawMode(), layer.getVertexFormat());

            for (Map.Entry<BlockPos, BlockState> e : data.getBlocks().entrySet())
            {
                BlockPos pos = e.getKey();
                BlockState state = e.getValue();
                FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty() && RenderLayers.getFluidLayer(fluid) == layer)
                {
                    fluidConsumer.target(builder, pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
                    manager.renderFluid(pos, world, fluidConsumer, state, fluid);
                }

                if (state.getRenderType() == BlockRenderType.MODEL && RenderLayers.getBlockLayer(state) == layer)
                {
                    matrices.push();
                    matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                    manager.renderBlock(state, pos, world, matrices, builder, true, random);
                    matrices.pop();
                }
            }

            /* End + repack into a tight POSITION_COLOR_TEXTURE_LIGHT_NORMAL buffer. */
            BakedBuffer baked = endAndNormalize(builder);

            if (baked == null)
            {
                continue;
            }

            if (baked.data() == null)
            {
                /* Format missed standard block attributes — skip the layer */
                continue;
            }

            ByteBuffer copy = baked.data();
            int vertexCount = baked.vertexCount();

            /* Re-impose vanilla's opaque-block invariant on non-translucent layers (see forceOpaque). */
            if (!isTranslucent(layer))
            {
                forceOpaque(copy, vertexCount);
            }

            result.layers.add(new BakedLayer(layer, copy, vertexCount));
        }

        return result;
    }

    /** Remember which sprites the block/fluid at this position uses (for Sodium animation). */
    private void collectSprites(BlockRenderManager manager, StructureRenderWorld world, BlockPos pos, BlockState state, Random random)
    {
        if (state.getRenderType() == BlockRenderType.MODEL)
        {
            BakedModel model = manager.getModel(state);

            random.setSeed(state.getRenderingSeed(pos));

            for (Direction direction : DIRECTIONS)
            {
                for (BakedQuad quad : model.getQuads(state, direction, random))
                {
                    this.sprites.add(quad.getSprite());
                }
            }

            for (BakedQuad quad : model.getQuads(state, null, random))
            {
                this.sprites.add(quad.getSprite());
            }
        }

        FluidState fluid = state.getFluidState();

        if (!fluid.isEmpty())
        {
            FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getFluid());

            if (handler != null)
            {
                for (Sprite sprite : handler.getFluidSprites(world, pos, fluid))
                {
                    if (sprite != null)
                    {
                        this.sprites.add(sprite);
                    }
                }
            }
        }
    }

    /** Re-impose vanilla's opaque-block invariant: set every vertex's color alpha to {@code 0xFF}.
     *
     * <p>Vanilla's {@code BlockModelRenderer} always writes alpha 1.0 there, but with Continuity
     * installed the bake is serviced by an FRAPI renderer (Indium), and under Iris with separate-AO
     * that path stuffs the AO coefficient into the alpha byte instead of opacity. Our blend-enabled
     * entity-layer replay would otherwise read that as transparency and the whole structure turns
     * see-through. The form/film tint alpha is applied separately at replay, so resetting the baked
     * alpha here is safe (opaque layers carry no meaningful per-vertex alpha anyway).</p> */
    private static void forceOpaque(ByteBuffer copy, int count)
    {
        for (int i = 0; i < count; i++)
        {
            copy.put(i * BakedBuffer.STRIDE + 15, (byte) 0xFF);
        }
    }

    /** Layers whose per-vertex alpha is real opacity; everything else is opaque (alpha ignorable). */
    private static boolean isTranslucent(RenderLayer layer)
    {
        return layer == RenderLayer.getTranslucent() || layer == RenderLayer.getTripwire();
    }

    /**
     * Map a terrain block layer to the matching BBS-provider entity layer. Both targets are keyed
     * in {@code FormUtilsClient}'s buffer map, so the provider flushes them in insertion order —
     * {@code getEntityCutout} (opaque) before {@code getEntityTranslucentCull} (translucent). This
     * is the route BBS's own block form takes, and the ordering is what makes semi-transparent
     * blocks/fluids composite over the opaque geometry behind them instead of hiding it.
     *
     * <p>{@code getItemEntityTranslucentCull} is deliberately avoided: it is NOT in the provider's
     * map, so it falls back to the shared buffer that {@code Immediate.draw()} flushes first —
     * which would draw translucent before opaque and bring the bug back.</p>
     */
    /**
     * Whether the structure's own geometry is routed through entity layers instead of the terrain
     * ones (see {@link #render}). The color overlay follows from it: an entity layer's shader
     * mixes the bound overlay into the fragment in place, so {@link #renderOverlay} would double
     * it there, while the terrain layers have no overlay channel at all and the pass is the only
     * way to get one.
     */
    public static boolean usesEntityLayers()
    {
        return BBSRendering.isIrisShadersEnabled();
    }

    private static RenderLayer getEntityLayer(RenderLayer blockLayer)
    {
        if (isTranslucent(blockLayer))
        {
            return TexturedRenderLayers.getEntityTranslucentCull();
        }

        return TexturedRenderLayers.getEntityCutout();
    }

    /**
     * Replay the baked vertices into the provider's layer buffers, transforming positions and
     * normals by the given matrices and multiplying colors by {@code tint} (ARGB; the form/film
     * color — applied here instead of a wrapping consumer, which would also have to survive the
     * substitute wrapper the block entities use).
     *
     * <p>Light: the sky component comes from {@code contextLight} (the form's world/entity
     * light, already modulated by the {@code lighting} form property), the block component is
     * the max of context and baked light — so the structure darkens in caves/at night like any
     * other form, while baked emitters (glowstone, lamps) keep glowing. UI previews pass
     * {@code MAX_LIGHT_COORDINATE} which makes everything full-bright.</p>
     */
    public void render(MatrixStack.Entry entry, CustomVertexConsumerProvider consumers, int contextLight, int tint)
    {
        /* Sodium only animates sprites it saw this frame — baked geometry bypasses it */
        SodiumSpriteHook.markActive(this.sprites);

        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        int contextBlock = contextLight & 0xFFFF;
        int contextSky = (contextLight >> 16) & 0xFFFF;
        boolean shaders = usesEntityLayers();

        /* Transparency only needs the right draw ORDER against the shared depth buffer: opaque must
         * be flushed (writing depth) before translucent draws over it. We exploit that while also
         * keeping the right SHADING:
         *
         * - opaque layers go to the terrain block layers. The vanilla terrain shader applies no
         *   directional diffuse, so the smooth AO / face-shade already baked into the vertex colors
         *   shows as-is (entity layers would re-shade and darken the whole structure). The provider
         *   flushes each terrain layer as it switches, so their depth lands before the translucent
         *   pass.
         * - translucent goes to BBS's KEYED entity translucent-cull layer, which the provider draws
         *   last (after every terrain layer) — so glass/water/ice composite over the opaque blocks
         *   behind them instead of hiding them.
         *
         * Under a shaderpack Iris owns the terrain pipeline and relights everything itself, so the
         * whole structure is fed through entity layers instead (no double-diffuse there). */
        for (BakedLayer baked : this.layers)
        {
            RenderLayer target;

            if (shaders)
            {
                target = getEntityLayer(baked.layer());
            }
            else if (isTranslucent(baked.layer()))
            {
                target = TexturedRenderLayers.getEntityTranslucentCull();
            }
            else
            {
                target = baked.layer();
            }

            replay(consumers.getBuffer(target), baked, pose, normalMatrix, contextBlock, contextSky, tint);
        }
    }

    /**
     * The color overlay pass: the same geometry replayed once more into {@link #OVERLAY_LAYER},
     * where the fragment takes its color entirely from the overlay texture the caller bound (at
     * full strength) and {@code strength} rides in the vertex alpha — so the pass composites into
     * {@code mix(structure, overlay, strength)} over what {@link #render} just drew.
     *
     * <p>A second pass is what the overlay costs here: the terrain shaders have no overlay
     * channel to mix the color in place, and rerouting the structure to an entity layer to get
     * one would re-shade the whole thing (see {@link #render}). The pass itself is immune to that
     * re-shading — the overlay texture overwrites the fragment's RGB, so the entity layer's
     * directional diffuse never reaches it. Only alpha survives, which is why the baked per-vertex
     * alpha (real opacity on the translucent layer) still scales the strength.</p>
     */
    public void renderOverlay(MatrixStack.Entry entry, CustomVertexConsumerProvider consumers, int contextLight, float strength)
    {
        int alpha = (int) (MathUtils.clamp(strength, 0F, 1F) * 255F);

        if (alpha <= 0)
        {
            return;
        }

        SodiumSpriteHook.markActive(this.sprites);

        VertexConsumer out = consumers.getBuffer(OVERLAY_LAYER);
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        int contextBlock = contextLight & 0xFFFF;
        int contextSky = (contextLight >> 16) & 0xFFFF;

        /* White RGB: this pass carries the strength and nothing else — the color arrives through
         * the bound overlay texture, which the layer's shader writes over the fragment */
        int tint = (alpha << 24) | 0xFFFFFF;

        for (BakedLayer baked : this.layers)
        {
            replay(out, baked, pose, normalMatrix, contextBlock, contextSky, tint);
        }
    }

    private static void replay(VertexConsumer out, BakedLayer baked, Matrix4f pose, Matrix3f normalMatrix, int contextBlock, int contextSky, int tint)
    {
        Vector4f position = new Vector4f();
        Vector3f normal = new Vector3f();
        ByteBuffer buf = baked.data();
        int count = baked.vertexCount();

        int tintA = tint >>> 24;
        int tintR = (tint >> 16) & 0xFF;
        int tintG = (tint >> 8) & 0xFF;
        int tintB = tint & 0xFF;

        for (int i = 0; i < count; i++)
        {
            int base = i * BakedBuffer.STRIDE;

            position.set(buf.getFloat(base), buf.getFloat(base + 4), buf.getFloat(base + 8), 1F);
            pose.transform(position);

            int r = (buf.get(base + 12) & 0xFF) * tintR / 255;
            int g = (buf.get(base + 13) & 0xFF) * tintG / 255;
            int b = (buf.get(base + 14) & 0xFF) * tintB / 255;
            int a = (buf.get(base + 15) & 0xFF) * tintA / 255;

            float u = buf.getFloat(base + 16);
            float v = buf.getFloat(base + 20);

            int bakedBlock = buf.getInt(base + 24) & 0xFFFF;

            normal.set(buf.get(base + 28) / 127F, buf.get(base + 29) / 127F, buf.get(base + 30) / 127F);
            normalMatrix.transform(normal);

            emitVertex(out, position.x, position.y, position.z, r, g, b, a, u, v,
                OverlayTexture.DEFAULT_UV, Math.max(bakedBlock, contextBlock), contextSky, normal.x, normal.y, normal.z);
        }
    }

    /** Begin a scratch vertex buffer for the given draw mode + format. */
    private static BufferBuilder beginBuffer(VertexFormat.DrawMode mode, VertexFormat format)
    {
        return Tessellator.getInstance().begin(mode, format);
    }

    /** End the builder and copy its vertices into a tight {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     *  ({@link BakedBuffer#STRIDE}-byte) template. Returns null if the builder was empty; a
     *  {@link BakedBuffer} with null data if the format misses standard block attributes. */
    private static BakedBuffer endAndNormalize(BufferBuilder builder)
    {
        BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return null;
        }

        BuiltBuffer.DrawParameters parameters = built.getDrawParameters();
        int count = parameters.vertexCount();
        ByteBuffer copy = normalize(built.getBuffer(), parameters.format(), count);

        built.close();

        return new BakedBuffer(copy, count);
    }

    /**
     * Copy the built vertex data into a tightly packed {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     * template. With an Iris shaderpack active the builder's actual format is EXTENDED (bigger
     * stride, extra attributes appended), so the vanilla attributes are extracted by their real
     * offsets; returns null if the format misses any of them.
     *
     * <p>The copy lives on the heap. It used to be off-heap for the raw replay path, which
     * bulk-copied it into the builder's own direct buffer; that path is gone and every remaining
     * reader is an absolute {@code get}, which a heap buffer serves just as well. Off-heap would
     * now only buy a second memory budget to exhaust and a {@code Cleaner} to wait on — a bake
     * replaced on every biome change or resource reload is much better left to the GC.</p>
     */
    private static ByteBuffer normalize(ByteBuffer source, VertexFormat format, int count)
    {
        int stride = format.getVertexSizeByte();
        int base = source.position();
        ByteBuffer copy = ByteBuffer.allocate(count * BakedBuffer.STRIDE).order(ByteOrder.nativeOrder());

        if (stride == BakedBuffer.STRIDE && VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.equals(format))
        {
            copy.put(0, source, base, count * BakedBuffer.STRIDE);

            return copy;
        }

        /* Since 1.21.1 the elements are constants on VertexFormatElement and the format
         * hands out their offsets itself (-1 when it has no such element). */
        int posOffset = format.getOffset(VertexFormatElement.POSITION);
        int colorOffset = format.getOffset(VertexFormatElement.COLOR);
        int uvOffset = format.getOffset(VertexFormatElement.UV_0);
        int lightOffset = format.getOffset(VertexFormatElement.UV_2);
        int normalOffset = format.getOffset(VertexFormatElement.NORMAL);

        if (posOffset < 0 || colorOffset < 0 || uvOffset < 0 || lightOffset < 0 || normalOffset < 0)
        {
            return null;
        }

        for (int i = 0; i < count; i++)
        {
            int src = base + i * stride;
            int dst = i * BakedBuffer.STRIDE;

            copy.put(dst, source, src + posOffset, 12);
            copy.put(dst + 12, source, src + colorOffset, 4);
            copy.put(dst + 16, source, src + uvOffset, 8);
            copy.put(dst + 24, source, src + lightOffset, 4);
            copy.put(dst + 28, source, src + normalOffset, 3);
        }

        return copy;
    }

    /** Emit one fully-specified vertex (since 1.21.1 it closes itself at the next one). */
    private static void emitVertex(VertexConsumer out, float x, float y, float z, int r, int g, int b, int a,
        float u, float v, int overlay, int blockLight, int skyLight, float nx, float ny, float nz)
    {
        out.vertex(x, y, z)
            .color(r, g, b, a)
            .texture(u, v)
            .overlay(overlay)
            .light(blockLight, skyLight)
            .normal(nx, ny, nz);
    }

}
