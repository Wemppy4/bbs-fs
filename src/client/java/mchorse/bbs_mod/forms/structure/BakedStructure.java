package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
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
    private static int globalGeneration;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Scratch builder reused across bakes (grows once and stays). */
    private static BufferBuilder scratch;

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
     * color — applied here instead of a wrapping consumer so the fast path can write raw bytes).
     *
     * <p>Light: the sky component comes from {@code contextLight} (the form's world/entity
     * light, already modulated by the {@code lighting} form property), the block component is
     * the max of context and baked light — so the structure darkens in caves/at night like any
     * other form, while baked emitters (glowstone, lamps) keep glowing. UI previews pass
     * {@code MAX_LIGHT_COORDINATE} which makes everything full-bright.</p>
     *
     * <p>When the target consumer is a plain {@link BufferBuilder} (no substitute wrapper is
     * active) and fast render is on, the whole layer is bulk-copied into the builder's memory and
     * fixed up in place ({@link #replayRaw}) — no per-vertex virtual calls. Otherwise the
     * per-vertex fallback is used.</p>
     */
    public void render(MatrixStack.Entry entry, CustomVertexConsumerProvider consumers, int contextLight, int tint, boolean fastRender)
    {
        /* Sodium only animates sprites it saw this frame — baked geometry bypasses it */
        SodiumSpriteHook.markActive(this.sprites);

        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        int contextBlock = contextLight & 0xFFFF;
        int contextSky = (contextLight >> 16) & 0xFFFF;
        boolean shaders = BBSRendering.isIrisShadersEnabled();
        boolean fast = fastRender && !shaders;

        /* Transparency only needs the right draw ORDER against the shared depth buffer: opaque must
         * be flushed (writing depth) before translucent draws over it. We exploit that while also
         * keeping the right SHADING:
         *
         * - opaque layers go to the terrain block layers. The vanilla terrain shader applies no
         *   directional diffuse, so the smooth AO / face-shade already baked into the vertex colors
         *   shows as-is (entity layers would re-shade and darken the whole structure). The provider
         *   flushes each terrain layer as it switches, so their depth lands before the translucent
         *   pass. With "fast render" on (no shaderpack) these are bulk-copied as raw bytes — opaque
         *   layers carry no per-vertex sort, so the raw copy is safe.
         * - translucent goes to BBS's KEYED entity translucent-cull layer, which the provider draws
         *   last (after every terrain layer) — so glass/water/ice composite over the opaque blocks
         *   behind them instead of hiding them. It is ALWAYS replayed per vertex: the terrain
         *   translucent layer sorts its quads at draw time from sort state that only the per-vertex
         *   path builds, so a raw byte copy there corrupts the shared buffer and crashes the frame.
         *
         * Under a shaderpack Iris owns the terrain pipeline and relights everything itself, so the
         * whole structure is fed through entity layers per vertex (no double-diffuse there). */
        for (BakedLayer baked : this.layers)
        {
            if (shaders)
            {
                replaySlow(consumers.getBuffer(getEntityLayer(baked.layer())), baked, pose, normalMatrix, contextBlock, contextSky, tint);
            }
            else if (isTranslucent(baked.layer()))
            {
                replaySlow(consumers.getBuffer(TexturedRenderLayers.getEntityTranslucentCull()), baked, pose, normalMatrix, contextBlock, contextSky, tint);
            }
            else
            {
                VertexConsumer out = consumers.getBuffer(baked.layer());

                if (!fast || !(out instanceof BufferBuilder builder)
                        || !replayRaw(builder, baked.data(), baked.vertexCount(), pose, normalMatrix, contextBlock, contextSky, tint))
                {
                    replaySlow(out, baked, pose, normalMatrix, contextBlock, contextSky, tint);
                }
            }
        }
    }

    /** Per-vertex fallback for wrapped consumers (e.g. a color substitute is active). */
    private static void replaySlow(VertexConsumer out, BakedLayer baked, Matrix4f pose, Matrix3f normalMatrix, int contextBlock, int contextSky, int tint)
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

    /** Begin a scratch vertex buffer for the given draw mode + format (a single reused builder). */
    private static BufferBuilder beginBuffer(VertexFormat.DrawMode mode, VertexFormat format)
    {
        if (scratch == null)
        {
            scratch = new BufferBuilder(786432);
        }

        scratch.begin(mode, format);

        return scratch;
    }

    /** End the builder and copy its vertices into a tight {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     *  ({@link BakedBuffer#STRIDE}-byte) template. Returns null if the builder was empty; a
     *  {@link BakedBuffer} with null data if the format misses standard block attributes. */
    private static BakedBuffer endAndNormalize(BufferBuilder builder)
    {
        BufferBuilder.BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return null;
        }

        BufferBuilder.DrawParameters parameters = built.getParameters();
        int count = parameters.vertexCount();
        ByteBuffer copy = normalize(built.getVertexBuffer(), parameters.format(), count);

        built.release();

        return new BakedBuffer(copy, count);
    }

    /**
     * Copy the built vertex data into a tightly packed {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     * template. With an Iris shaderpack active the builder's actual format is EXTENDED (bigger
     * stride, extra attributes appended), so the vanilla attributes are extracted by their real
     * offsets; returns null if the format misses any of them.
     */
    private static ByteBuffer normalize(ByteBuffer source, VertexFormat format, int count)
    {
        int stride = format.getVertexSizeByte();
        int base = source.position();
        ByteBuffer copy = ByteBuffer.allocateDirect(count * BakedBuffer.STRIDE).order(ByteOrder.nativeOrder());

        if (stride == BakedBuffer.STRIDE && VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.equals(format))
        {
            copy.put(0, source, base, count * BakedBuffer.STRIDE);

            return copy;
        }

        int posOffset = -1;
        int colorOffset = -1;
        int uvOffset = -1;
        int lightOffset = -1;
        int normalOffset = -1;
        int offset = 0;

        for (VertexFormatElement element : format.getElements())
        {
            if (element == VertexFormats.POSITION_ELEMENT) posOffset = offset;
            else if (element == VertexFormats.COLOR_ELEMENT) colorOffset = offset;
            else if (element == VertexFormats.TEXTURE_ELEMENT) uvOffset = offset;
            else if (element == VertexFormats.LIGHT_ELEMENT) lightOffset = offset;
            else if (element == VertexFormats.NORMAL_ELEMENT) normalOffset = offset;

            offset += element.getByteLength();
        }

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

    /** Emit one fully-specified vertex (the explicit {@code next()} terminator finishes it). */
    private static void emitVertex(VertexConsumer out, float x, float y, float z, int r, int g, int b, int a,
        float u, float v, int overlay, int blockLight, int skyLight, float nx, float ny, float nz)
    {
        out.vertex(x, y, z)
            .color(r, g, b, a)
            .texture(u, v)
            .overlay(overlay)
            .light(blockLight, skyLight)
            .normal(nx, ny, nz)
            .next();
    }

    /** Bulk copy the baked layer into the builder's internal buffer, then transform/tint/relight the
     *  copied bytes in place — no per-vertex virtual calls. */
    private static boolean replayRaw(BufferBuilder builder, ByteBuffer src, int count, Matrix4f pose,
        Matrix3f normalMatrix, int contextBlock, int contextSky, int tint)
    {
        if (count == 0)
        {
            return true;
        }

        int bytes = count * BakedBuffer.STRIDE;

        builder.grow(bytes);

        ByteBuffer dst = builder.buffer;
        int start = builder.elementOffset;

        dst.put(start, src, 0, bytes);

        float m00 = pose.m00(), m01 = pose.m01(), m02 = pose.m02();
        float m10 = pose.m10(), m11 = pose.m11(), m12 = pose.m12();
        float m20 = pose.m20(), m21 = pose.m21(), m22 = pose.m22();
        float m30 = pose.m30(), m31 = pose.m31(), m32 = pose.m32();

        float n00 = normalMatrix.m00(), n01 = normalMatrix.m01(), n02 = normalMatrix.m02();
        float n10 = normalMatrix.m10(), n11 = normalMatrix.m11(), n12 = normalMatrix.m12();
        float n20 = normalMatrix.m20(), n21 = normalMatrix.m21(), n22 = normalMatrix.m22();

        boolean hasTint = tint != 0xFFFFFFFF;
        int tintA = tint >>> 24;
        int tintR = (tint >> 16) & 0xFF;
        int tintG = (tint >> 8) & 0xFF;
        int tintB = tint & 0xFF;

        int skyBits = contextSky << 16;

        for (int i = 0, vbase = start; i < count; i++, vbase += BakedBuffer.STRIDE)
        {
            float x = dst.getFloat(vbase);
            float y = dst.getFloat(vbase + 4);
            float z = dst.getFloat(vbase + 8);

            dst.putFloat(vbase, m00 * x + m10 * y + m20 * z + m30);
            dst.putFloat(vbase + 4, m01 * x + m11 * y + m21 * z + m31);
            dst.putFloat(vbase + 8, m02 * x + m12 * y + m22 * z + m32);

            if (hasTint)
            {
                dst.put(vbase + 12, (byte) ((dst.get(vbase + 12) & 0xFF) * tintR / 255));
                dst.put(vbase + 13, (byte) ((dst.get(vbase + 13) & 0xFF) * tintG / 255));
                dst.put(vbase + 14, (byte) ((dst.get(vbase + 14) & 0xFF) * tintB / 255));
                dst.put(vbase + 15, (byte) ((dst.get(vbase + 15) & 0xFF) * tintA / 255));
            }

            int bakedBlock = dst.getInt(vbase + 24) & 0xFFFF;

            dst.putInt(vbase + 24, Math.max(bakedBlock, contextBlock) | skyBits);

            float nx = dst.get(vbase + 28) / 127F;
            float ny = dst.get(vbase + 29) / 127F;
            float nz = dst.get(vbase + 30) / 127F;

            dst.put(vbase + 28, packNormal(n00 * nx + n10 * ny + n20 * nz));
            dst.put(vbase + 29, packNormal(n01 * nx + n11 * ny + n21 * nz));
            dst.put(vbase + 30, packNormal(n02 * nx + n12 * ny + n22 * nz));
        }

        builder.vertexCount += count;
        builder.elementOffset += bytes;

        return true;
    }

    private static byte packNormal(float value)
    {
        return (byte) ((int) (Math.min(1F, Math.max(-1F, value)) * 127F) & 0xFF);
    }
}
