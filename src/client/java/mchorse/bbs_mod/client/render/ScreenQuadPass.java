package mchorse.bbs_mod.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * A textured screen-space quad drawn with a CUSTOM pipeline and (optionally) a custom std140 UBO,
 * into an {@link OffscreenTarget}.
 *
 * <p>This is the per-draw-uniform dispatch 1.21.5 removed: loose {@code program.getUniform(...)}
 * setters became std140 blocks, and the immediate {@code RenderLayer.draw} path binds only the
 * engine builtins — a custom block simply cannot ride it. So every effect that needs one (the
 * marching-ants selection outline, the multilink pixelate/erase preview, the subtitle blur) drives
 * this manual pass instead: engine builtins + its own UBO + its own samplers, quad at an ortho over
 * the target, result composited back through the recorded GUI blit. Generalized from
 * {@code BBSPickerRenderer.drawHighlight}, the first live instance of the pattern.
 */
public class ScreenQuadPass
{
    /** Shared ring for the small custom UBO blocks (largest current block is 32 bytes; 48 headroom). */
    private static final int UBO_SIZE = 48;

    private static MappableRingBuffer uboRing;
    private static MappableRingBuffer projectionRing;
    private static GpuSampler nearestSampler;
    private static GpuSampler linearSampler;

    /**
     * Write a custom std140 block into the shared ring and return the slice to hand to {@link Quad}.
     * Call BEFORE the pass runs (ring rotation fences; an open pass rejects it) — in practice, just
     * before {@link #draw}.
     */
    public static GpuBufferSlice writeUbo(Consumer<Std140Builder> writer)
    {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        if (uboRing == null)
        {
            uboRing = new MappableRingBuffer(() -> "bbs:screen_quad_ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }

        uboRing.rotate();

        GpuBuffer ubo = uboRing.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo, false, true))
        {
            writer.accept(Std140Builder.intoBuffer(view.data()));
        }

        return ubo.slice(0L, UBO_SIZE);
    }

    public static GpuSampler nearest()
    {
        if (nearestSampler == null)
        {
            nearestSampler = RenderSystem.getSamplerCache().get(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
        }

        return nearestSampler;
    }

    public static GpuSampler linear()
    {
        if (linearSampler == null)
        {
            linearSampler = RenderSystem.getSamplerCache().get(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
        }

        return linearSampler;
    }

    /** One quad draw. Coordinates are target-local pixels (ortho over the whole target, y-down). */
    public static class Quad
    {
        public RenderPipeline pipeline;
        public GpuTextureView target;
        public int targetWidth;
        public int targetHeight;

        public float x, y, w, h;
        public float u1, v1, u2, v2;
        public int color = 0xFFFFFFFF;

        public GpuTextureView sampler0;
        public GpuSampler sampler0Sampler;
        public String sampler3Name;
        public GpuTextureView sampler3;
        public GpuSampler sampler3Sampler;

        public String uboName;
        public GpuBufferSlice ubo;

        /** Clear the target to transparent before drawing (first draw of the frame into it). */
        public boolean clear;

        public Quad(RenderPipeline pipeline, GpuTextureView target, int targetWidth, int targetHeight)
        {
            this.pipeline = pipeline;
            this.target = target;
            this.targetWidth = targetWidth;
            this.targetHeight = targetHeight;
        }

        public Quad rect(float x, float y, float w, float h)
        {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;

            return this;
        }

        public Quad uv(float u1, float v1, float u2, float v2)
        {
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;

            return this;
        }

        public Quad texture(GpuTextureView view, GpuSampler sampler)
        {
            this.sampler0 = view;
            this.sampler0Sampler = sampler;

            return this;
        }

        public Quad texture3(String name, GpuTextureView view, GpuSampler sampler)
        {
            this.sampler3Name = name;
            this.sampler3 = view;
            this.sampler3Sampler = sampler;

            return this;
        }

        public Quad ubo(String name, GpuBufferSlice slice)
        {
            this.uboName = name;
            this.ubo = slice;

            return this;
        }

        public Quad clear()
        {
            this.clear = true;

            return this;
        }
    }

    public static boolean draw(String name, Quad quad)
    {
        if (quad.target == null || quad.targetWidth <= 0 || quad.targetHeight <= 0)
        {
            return false;
        }

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        /* Identity model-view, neutral ColorModulator; the vertex colour carries any tint. */
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .write(new Matrix4f(), new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

        if (projectionRing == null)
        {
            projectionRing = new MappableRingBuffer(() -> "bbs:screen_quad_projection", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 64);
        }

        projectionRing.rotate();

        GpuBuffer projection = projectionRing.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(projection, false, true))
        {
            Std140Builder.intoBuffer(view.data())
                .putMat4f(new Matrix4f().ortho(0F, quad.targetWidth, quad.targetHeight, 0F, -1000F, 1000F));
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        builder.vertex(quad.x, quad.y + quad.h, 0F).texture(quad.u1, quad.v2).color(quad.color);
        builder.vertex(quad.x + quad.w, quad.y + quad.h, 0F).texture(quad.u2, quad.v2).color(quad.color);
        builder.vertex(quad.x + quad.w, quad.y, 0F).texture(quad.u2, quad.v1).color(quad.color);
        builder.vertex(quad.x, quad.y, 0F).texture(quad.u1, quad.v1).color(quad.color);

        BuiltBuffer buffer = builder.endNullable();

        if (buffer == null)
        {
            return false;
        }

        VertexFormat format = quad.pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());
        RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
        GpuBuffer indexBuffer = sequential.getIndexBuffer(buffer.getDrawParameters().indexCount());
        VertexFormat.IndexType indexType = sequential.getIndexType();

        try (RenderPass pass = encoder.createRenderPass(() -> name, quad.target,
            quad.clear ? OptionalInt.of(0x00000000) : OptionalInt.empty()))
        {
            pass.setPipeline(quad.pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projection.slice(0L, 64));
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            if (quad.ubo != null)
            {
                pass.setUniform(quad.uboName, quad.ubo);
            }

            if (quad.sampler0 != null)
            {
                pass.bindTexture("Sampler0", quad.sampler0, quad.sampler0Sampler == null ? nearest() : quad.sampler0Sampler);
            }

            if (quad.sampler3 != null)
            {
                pass.bindTexture(quad.sampler3Name, quad.sampler3, quad.sampler3Sampler == null ? nearest() : quad.sampler3Sampler);
            }

            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
        }
        finally
        {
            buffer.close();
        }

        return true;
    }
}
