package mchorse.bbs_mod.client.render.picker;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The Target-index uniform upload + draw foundation for the migrated picker shaders.
 *
 * <p>In 1.21.1 each picker effect set a loose {@code uniform int Target} (and, for picker_preview,
 * {@code uniform vec4 HighlightColor}) per draw via {@code program.getUniform("Target").set(index)}.
 * 1.21.5+ removed mutable GLSL uniforms: the only per-draw data the immediate {@link
 * net.minecraft.client.render.RenderLayer#draw} path uploads is the engine builtin set
 * (DynamicTransforms/Projection/Fog/Lighting/Globals), and it hardcodes ColorModulator to
 * {@code (1,1,1,1)}. There is no hook to inject a custom UBO into that path, so a shader that needs
 * one cannot be drawn through a {@link net.minecraft.client.render.RenderLayer} — it must be driven
 * by a manual {@link CommandEncoder#createRenderPass} + {@link RenderPass#setUniform} pass.
 *
 * <p>The migrated picker GLSL packs the two custom uniforms into a single std140 block named
 * {@link BBSShaders#PICKER_UNIFORM} ({@code vec4 HighlightColor; int Target;}, vec4-first for 16-byte
 * alignment). This class owns the per-frame ring buffer that block is written into and a
 * {@link #draw} that replicates {@code RenderLayer.draw(BuiltBuffer)} step-for-step while binding the
 * extra {@code BBSPicker} UBO — the faithful 1.21.5 equivalent of the old picker draw + Target set.
 *
 * <p>The active picking index is recorded by {@code FormRenderer.setupTarget} (the same call site the
 * 1.21.1 code set the {@code Target} uniform at) via {@link #setTarget(int)}; {@link #draw} uploads it.
 *
 * <p>TODO(1.21.11 render): {@link #draw} is the complete, ready foundation but is not yet invoked by a
 * live path. End-to-end form/gizmo picking additionally needs (a) the picking target framebuffer
 * (StencilFormFramebuffer) ported so the index colours render into a readable off-screen target rather
 * than the world framebuffer, and (b) a {@link GpuTextureView} bridge for the mod's raw-GL {@code
 * Texture} (the mapped API cannot wrap a bare GL id; the form texture must come through an
 * AbstractTexture / GpuTexture). Both are tracked as separate picking-subsystem ports.
 */
public class BBSPickerRenderer
{
    /** std140 size of the BBSPicker block: vec4 (16) + int (4), rounded up to a 16-byte multiple. */
    private static final int UBO_SIZE = 32;

    /** The active picking index (object/gizmo id), the faithful equivalent of the old Target uniform. */
    private static int target;

    /** Highlight colour for picker_preview's matched-pixel overlay (ARGB); unused by the geometry pickers. */
    private static int highlightColor = Colors.WHITE;

    /** Per-frame triple-buffered ring for the BBSPicker UBO. Lazily created (needs the GPU device). */
    private static MappableRingBuffer uboRing;

    /** Off-screen colour/depth the picker draws render into (StencilFormFramebuffer). Null = the main framebuffer. */
    private static GpuTextureView targetColor;
    private static GpuTextureView targetDepth;

    /** Sampler0 (albedo) bound for the next picker draw — the form/model texture, for the alpha cutout. */
    private static GpuTextureView sampler0View;
    private static GpuSampler sampler0;

    private BBSPickerRenderer()
    {}

    /**
     * Point subsequent picker draws at an off-screen colour/depth pair (the picking framebuffer) instead of
     * the world framebuffer. {@code StencilFormFramebuffer} sets this around the picking render pass so the
     * index colours land in a readable target. Pass {@code null}/{@code null} (or {@link #clearRenderTarget})
     * to restore the default (main framebuffer) behaviour.
     */
    public static void setRenderTarget(GpuTextureView color, GpuTextureView depth)
    {
        BBSPickerRenderer.targetColor = color;
        BBSPickerRenderer.targetDepth = depth;
    }

    public static void clearRenderTarget()
    {
        BBSPickerRenderer.targetColor = null;
        BBSPickerRenderer.targetDepth = null;
    }

    /**
     * Record the Sampler0 albedo texture to bind on the next picker draw. The picker shaders sample it for the
     * alpha cutout ({@code color.a < 0.1 -> discard}); the form/model renderer resolves it from the (adopted)
     * vanilla texture right before issuing the draw.
     */
    public static void setSampler0(GpuTextureView view, GpuSampler sampler)
    {
        BBSPickerRenderer.sampler0View = view;
        BBSPickerRenderer.sampler0 = sampler;
    }

    /**
     * Record the picking index to upload on the next picker draw. Replaces the 1.21.1
     * {@code program.getUniform("Target").set(getPickingIndex())}.
     */
    public static void setTarget(int target)
    {
        BBSPickerRenderer.target = target;
    }

    public static int getTarget()
    {
        return target;
    }

    /** Set the ARGB highlight colour picker_preview paints matched pixels with. */
    public static void setHighlightColor(int highlightColor)
    {
        BBSPickerRenderer.highlightColor = highlightColor;
    }

    /**
     * Map the ring's current slot and write the BBSPicker std140 block (HighlightColor vec4, Target int)
     * with the active {@link #target}/{@link #highlightColor}. Returns the GpuBuffer to bind.
     */
    private static GpuBuffer writeUniform(GpuDevice device, CommandEncoder encoder)
    {
        if (uboRing == null)
        {
            uboRing = new MappableRingBuffer(() -> "bbs:picker_ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }

        uboRing.rotate();

        GpuBuffer ubo = uboRing.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo, false, true))
        {
            Std140Builder.intoBuffer(view.data())
                .putVec4(Colors.getR(highlightColor), Colors.getG(highlightColor), Colors.getB(highlightColor), Colors.getA(highlightColor))
                .putInt(target);
        }

        return ubo;
    }

    /**
     * Bind the BBSPicker UBO (with the current {@link #target}/{@link #highlightColor}) on an
     * already-open render pass. Use when driving a custom picker pass by hand; {@link #draw} calls it.
     */
    public static void bind(RenderPass pass)
    {
        GpuDevice device = RenderSystem.getDevice();

        pass.setUniform(BBSShaders.PICKER_UNIFORM, writeUniform(device, device.createCommandEncoder()));
    }

    /**
     * Draw a {@link BuiltBuffer} with a picker {@link RenderPipeline}, into the active picking target (the
     * off-screen colour/depth set via {@link #setRenderTarget}, or the main framebuffer when none is set),
     * binding the engine builtins (Projection/Fog/Globals/Lighting via {@link RenderSystem#bindDefaultUniforms},
     * DynamicTransforms via the dynamic-uniform ring), the custom {@code BBSPicker} UBO carrying the Target
     * index, and Sampler0 (the albedo set via {@link #setSampler0}, for the shader's alpha cutout). Faithful
     * replication of {@code RenderLayer.draw(BuiltBuffer)} plus the one extra custom-UBO bind.
     *
     * <p>The render pass loads (does not clear) the target, so consecutive draws accumulate with depth testing
     * — the picking framebuffer is cleared once up-front by {@code StencilFormFramebuffer.apply}.</p>
     *
     * @param modelView the pose model-view (typically {@link RenderSystem#getModelViewMatrix()} with the
     *                  form's stack folded in; for the in-panel preview it is identity, the camera being
     *                  baked into the vertices)
     */
    public static void draw(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView)
    {
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        /* DynamicTransforms: modelView + identity colorModulator/offset/textureMatrix, like RenderLayer.draw. */
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .write(modelView, new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

        /* BBSPicker: the Target/HighlightColor block. */
        GpuBuffer pickerUniform = writeUniform(device, encoder);

        VertexFormat format = pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());

        GpuBuffer indexBuffer;
        VertexFormat.IndexType indexType;

        if (buffer.getSortedBuffer() == null)
        {
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());

            indexBuffer = sequential.getIndexBuffer(buffer.getDrawParameters().indexCount());
            indexType = sequential.getIndexType();
        }
        else
        {
            indexBuffer = format.uploadImmediateIndexBuffer(buffer.getSortedBuffer());
            indexType = buffer.getDrawParameters().indexType();
        }

        GpuTextureView color;
        GpuTextureView depth;

        if (targetColor != null)
        {
            color = targetColor;
            depth = targetDepth;
        }
        else
        {
            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

            color = framebuffer.getColorAttachmentView();
            depth = framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null;
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "bbs:picker_draw", color, OptionalInt.empty(), depth, OptionalDouble.empty()))
        {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform(BBSShaders.PICKER_UNIFORM, pickerUniform);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.bindTexture("Sampler0", sampler0View, sampler0);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
        }
        finally
        {
            buffer.close();
        }
    }
}
