package mchorse.bbs_mod.client.render.picker;

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
import com.mojang.blaze3d.platform.DepthTestFunction;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
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
    /**
     * POSITION_COLOR triangles into the off-screen highlight target.
     *
     * <p>Blend is OFF, for the same reason the index pickers have it off. The target is cleared to
     * transparent black, so blending premultiplies the colour into it, and the caller's blit then applies
     * alpha a SECOND time — the glow came out dark exactly like the bone highlight used to. Writing straight
     * keeps the colour unpremultiplied, and the single blend at blit time is the correct one.
     */
    private static final RenderPipeline GIZMO_HIGHLIGHT_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/gizmo_sphere_highlight"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .build()
    );

    /** std140 size of the BBSPicker block: vec4 (16) + int (4), rounded up to a 16-byte multiple. */
    private static final int UBO_SIZE = 32;

    /** The active picking index (object/gizmo id), the faithful equivalent of the old Target uniform. */
    private static int target;

    /** Highlight colour for picker_preview's matched-pixel overlay (ARGB); unused by the geometry pickers. */
    private static int highlightColor = Colors.WHITE;

    /**
     * Projection the picker passes should draw with, or null to use whatever the engine has bound.
     *
     * <p>{@link RenderSystem#bindDefaultUniforms} binds the CURRENT Projection UBO. That is right when the
     * picking pass runs inside a render pass that already set the world/preview projection (the form editor's
     * ModelPreviewRenderer does), and wrong when it runs in the GUI phase — the film editor picks from
     * {@code UIFilmController#renderPickingPreview}, where the bound projection is the interface's ortho, so
     * world-space geometry landed nowhere near the cursor and the whole stencil map read empty.
     */
    private static Matrix4f projectionOverride;

    /** Set the projection the following picker passes bind; null restores the engine default. */
    public static void setProjectionOverride(Matrix4f projection)
    {
        projectionOverride = projection == null ? null : new Matrix4f(projection);
    }

    /**
     * Write the override into its UBO, or null when unset. MUST be called BEFORE the render pass is
     * opened: {@link #writeProjection} rotates a {@link MappableRingBuffer}, which issues a GPU fence, and
     * the encoder rejects any command while a pass is open ("Close the existing render pass before
     * performing additional commands"). The resulting slice is then bound inside the pass. This is the same
     * order the screen-space highlight overlay below already uses.
     */
    private static GpuBufferSlice projectionOverrideSlice(CommandEncoder encoder)
    {
        return projectionOverride == null ? null : writeProjection(encoder, projectionOverride);
    }

    /** Bind the pre-written override after the engine defaults, so it wins for this pass. */
    private static void applyProjectionOverride(RenderPass pass, GpuBufferSlice slice)
    {
        if (slice != null)
        {
            pass.setUniform("Projection", slice);
        }
    }

    /** std140 size of the Projection block: a single mat4. */
    private static final int PROJECTION_UBO_SIZE = 64;

    /** Per-frame triple-buffered ring for the BBSPicker UBO. Lazily created (needs the GPU device). */
    private static MappableRingBuffer uboRing;

    /** Per-frame triple-buffered ring for the Projection UBO of the screen-space highlight overlay. */
    private static MappableRingBuffer projectionRing;

    /** Off-screen colour/depth the picker draws render into (StencilFormFramebuffer). Null = the main framebuffer. */
    private static GpuTextureView targetColor;
    private static GpuTextureView targetDepth;

    /**
     * The most recent picking colour target ({@link StencilFormFramebuffer}'s device-owned colour texture that
     * the picker draws render the encoded per-bone/per-form index colours into). Retained separately from
     * {@link #targetColor} because {@link #clearRenderTarget} (called on {@code unbind}) runs BEFORE the hover
     * highlight is drawn later in the same frame's UI {@code render}; the highlight pass samples THIS view.
     * This is the texture the 1.21.1 highlight overlay sampled — {@code getMainTexture()} (the legacy raw-GL
     * framebuffer) is now a stale 2x2 leftover the picker never renders into, which is why the previous attempt
     * recoloured nothing.
     */

    /** NEAREST/clamp sampler for sampling the encoded-index picking texture (must decode index texels exactly). */
    private static GpuSampler pickSampler;

    /* Off-screen output the highlight recolour pass renders into (transparent except the matched/highlighted
     * pixels). Blitted back over the viewport by the call sites through the recorded two-phase-GUI texturedBox
     * path — the immediate recolour pass alone would be overdrawn by the deferred GUI flush. */

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
     * {@link #setSampler0(GpuTextureView, GpuSampler)} for a BBS raw-GL texture: the mapped API cannot bind a
     * bare GL id, so the texture is bridged through {@link AdoptedTexture} (zero-copy) into the vanilla
     * TextureManager first. Every form renderer that picks needs exactly this, so it lives here rather than
     * being spelled out at each call site.
     */
    public static void setSampler0(Texture texture)
    {
        Identifier adopted = AdoptedTexture.identifier(texture);

        if (adopted == null)
        {
            return;
        }

        AbstractTexture at = MinecraftClient.getInstance().getTextureManager().getTexture(adopted);

        setSampler0(at.getGlTextureView(), at.getSampler());
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
     * Write the BBSPicker UBO (with the current {@link #target}/{@link #highlightColor}) and return the buffer
     * to bind. Use when driving a custom picker pass by hand.
     *
     * <p>Call this BEFORE opening the pass and bind the result inside it. It cannot write from within an open
     * pass: {@link #writeUniform} rotates a {@link MappableRingBuffer}, which issues a GPU fence, and the
     * encoder rejects commands while a pass is open. The previous signature took the open {@code RenderPass}
     * and wrote from inside it, so any caller would have thrown — it had none, which is why that never showed.
     */
    public static GpuBuffer prepareUniform()
    {
        GpuDevice device = RenderSystem.getDevice();

        return writeUniform(device, device.createCommandEncoder());
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

        GpuBufferSlice projectionUniform = projectionOverrideSlice(encoder);

        try (RenderPass pass = encoder.createRenderPass(() -> "bbs:picker_draw", color, OptionalInt.empty(), depth, OptionalDouble.empty()))
        {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            applyProjectionOverride(pass, projectionUniform);
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

    /**
     * Draw a plain {@code POSITION_COLOR} {@link BuiltBuffer} into the active off-screen picking colour target
     * (set via {@link #setRenderTarget}), encoding the picking index directly in the vertex colour rather than a
     * {@code Target} uniform. This is the gizmo-handle equivalent of {@link #draw}: the gizmo stencil pass bakes
     * each handle's stencil id into the red channel of its geometry (the faithful 1.21.1 mechanism — the original
     * drew the handles with {@code getPositionColorProgram} at {@code (id/255, 0, 0, 1)}), so it needs neither the
     * {@code BBSPicker} UBO nor a {@code Sampler0} — only the engine builtins + {@code DynamicTransforms}.
     *
     * <p>Renders colour-only (no depth attachment) and LOADs the target (no clear), so — exactly like the
     * original {@code RenderSystem.disableDepthTest()} stencil pass — the handles always win the pick over the
     * form/bone ids already accumulated in the target, regardless of model depth.
     *
     * <p>No-op when no off-screen target is set: drawing the id colours into the main framebuffer would leak the
     * encoded handle colours onto the visible viewport (the exact hazard that kept this pass stubbed). The gizmo
     * stencil is only ever invoked inside a {@code StencilFormFramebuffer.apply()}/{@code unbind()} window, so the
     * target is set whenever this is legitimately reached.
     *
     * @param modelView the global model-view the visual gizmo {@link net.minecraft.client.render.RenderLayer}
     *                  would apply ({@link RenderSystem#getModelViewMatrix()}); the per-vertex stack pose is
     *                  already baked into the geometry, matching the visible gizmo exactly.
     */
    public static void drawColorId(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView)
    {
        if (targetColor == null)
        {
            buffer.close();

            return;
        }

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .write(modelView, new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

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

        GpuBufferSlice projectionUniform = projectionOverrideSlice(encoder);

        try (RenderPass pass = encoder.createRenderPass(() -> "bbs:gizmo_pick", targetColor, OptionalInt.empty()))
        {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            applyProjectionOverride(pass, projectionUniform);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
        }
        finally
        {
            buffer.close();
        }
    }

    /**
     * Map the projection ring's current slot, write a single-mat4 std140 {@code Projection} block holding the
     * given ortho matrix, and return its slice. Mirrors the engine's own {@code PROJECTION_MATRIX_UBO_SIZE}
     * (one mat4) so the picker_preview vertex shader's {@code Projection { mat4 ProjMat; }} binds correctly.
     */
    private static GpuBufferSlice writeProjection(CommandEncoder encoder, Matrix4f projection)
    {
        if (projectionRing == null)
        {
            projectionRing = new MappableRingBuffer(() -> "bbs:picker_projection_ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, PROJECTION_UBO_SIZE);
        }

        projectionRing.rotate();

        GpuBuffer ubo = projectionRing.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo, false, true))
        {
            Std140Builder.intoBuffer(view.data()).putMat4f(projection);
        }

        return ubo.slice(0L, PROJECTION_UBO_SIZE);
    }


    /**
     * Render the hover-highlight into an off-screen texture: recolour, through the {@code picker_preview}
     * pipeline, the pixels of the last picking colour target whose encoded index equals {@code index} with
     * {@code highlightColor}, discarding the rest. The result (transparent except the highlighted bone/form/
     * gizmo) is left in the off-screen target; the caller blits it back over the viewport via the recorded
     * two-phase-GUI {@code texturedBox(getHighlightGlId(), ...)} path.
     *
     * <p>This is the faithful 1.21.11 replacement for the 1.21.1 {@code program.getUniform("Target").set(index)}
     * + {@code "HighlightColor"} set + {@code texturedBox(getPickerPreviewProgram, getMainTexture().id, ...)}.
     * Two things broke the previous attempt and are fixed here:</p>
     * <ul>
     *   <li><b>Source texture.</b> In 1.21.1 the picker drew into {@code getFramebuffer().getMainTexture()},
     *   so the overlay sampled the same texture the index colours lived in. In 1.21.11 the picker draws into
     *   {@link StencilFormFramebuffer}'s device-owned colour texture; the legacy {@code getMainTexture()} is a
     *   stale 2x2 the picker never touches. Sampling it recoloured nothing. The caller hands us ITS OWN picking
     *   texture and size: a single "last bound target" static was wrong as soon as two viewports were alive at
     *   once — opening a form editor on top of the film editor made the form editor's highlight sample the
     *   FILM's picking texture, painting the film's stencils over it. The size must come from the same place,
     *   because the recolour maps the source across the whole target with UV 0..1.</li>
     *   <li><b>Overdraw.</b> Drawing the recolour straight onto the main framebuffer (as the previous attempt
     *   did) is overpainted by the deferred two-phase GUI flush (the viewport's model blit is a recorded GUI
     *   element composited afterwards). So we render into our OWN off-screen texture here and hand it back for
     *   a recorded blit — the same pattern {@code UIModelRenderer} uses for its model preview.</li>
     * </ul>
     *
     * <p>The custom {@code BBSPicker} UBO (Target + HighlightColor) cannot ride the immediate {@code RenderLayer}
     * /{@code texturedBox} path, so — exactly like the pick-read pass in {@link #draw} — this drives a manual
     * {@link RenderPass} that binds it alongside a screen-space ortho {@code Projection} and identity
     * {@code DynamicTransforms}.</p>
     *
     * @param index          the picked stencil index (the hovered bone/form/gizmo); becomes {@code Target}
     * @param highlightColor ARGB colour matched pixels are painted with (BBSSettings.stencilHighlightColor)
     * @param w,h            the off-screen target size in pixels (the viewport area at GUI scale)
     * @return {@code true} when the off-screen highlight was rendered (so the caller should blit it)
     */
    public static boolean drawHighlight(GpuTextureView source, GpuTextureView target, int w, int h, int index, int highlightColor)
    {
        if (source == null || target == null || w <= 0 || h <= 0)
        {
            return false;
        }

        if (pickSampler == null)
        {
            /* NEAREST + clamp: the source carries the encoded index per texel; any filtering would blend
             * indices at bone borders and corrupt the int match. */
            pickSampler = RenderSystem.getSamplerCache().get(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
        }

        setTarget(index);
        setHighlightColor(highlightColor);

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        RenderPipeline pipeline = BBSShaders.getPickerPreviewProgram();

        /* Ortho over the full off-screen target (origin top-left, y-down). picker_preview's vertex shader is
         * ProjMat * ModelViewMat * Position, so ModelViewMat (DynamicTransforms) stays identity. */
        Matrix4f projection = new Matrix4f().ortho(0F, (float) w, (float) h, 0F, -1000F, 1000F);

        /* DynamicTransforms: identity ModelViewMat, ColorModulator (1,1,1,1) — the shader's final
         * color * ColorModulator must be a no-op so HighlightColor passes through unchanged. */
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .write(new Matrix4f(), new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

        GpuBufferSlice projectionUniform = writeProjection(encoder, projection);
        GpuBuffer pickerUniform = writeUniform(device, encoder);

        /* Full-target quad with V-INVERTED UVs. Runtime showed the highlight was vertically mirrored vs the
         * visible model (hover head -> highlight at feet): the source picking texture is actually TOP-DOWN, so the
         * caller's blit-back V-flip (FBO convention) was a net extra flip. We pre-flip V here so recolour-flip +
         * blit-flip cancel to identity -> the highlight matches the model. WHITE vertex colour (* texel = texel). */
        int color = Colors.WHITE;

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        builder.vertex(0F, (float) h, 0F).texture(0F, 0F).color(color);
        builder.vertex((float) w, (float) h, 0F).texture(1F, 0F).color(color);
        builder.vertex((float) w, 0F, 0F).texture(1F, 1F).color(color);
        builder.vertex(0F, 0F, 0F).texture(0F, 1F).color(color);

        BuiltBuffer buffer = builder.endNullable();

        if (buffer == null)
        {
            return false;
        }

        VertexFormat format = pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());

        RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
        GpuBuffer indexBuffer = sequential.getIndexBuffer(buffer.getDrawParameters().indexCount());
        VertexFormat.IndexType indexType = sequential.getIndexType();

        /* Clear-on-load to fully transparent (the OptionalInt clear colour): only the recoloured (matched)
         * pixels end up carrying alpha, so the later GUI_TEXTURED blit (texel.a * vertex.a) composites only the
         * highlight over the viewport. No depth attachment: a flat full-target recolour; with no depth buffer
         * bound the pipeline's LEQUAL test has nothing to cull against, so every matched pixel survives. */
        try (RenderPass pass = encoder.createRenderPass(() -> "bbs:picker_highlight", target, OptionalInt.of(0x00000000)))
        {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projectionUniform);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform(BBSShaders.PICKER_UNIFORM, pickerUniform);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.bindTexture("Sampler0", source, pickSampler);
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
        }
        finally
        {
            buffer.close();
        }

        return true;
    }

    /**
     * Render plain {@code POSITION_COLOR} geometry into the off-screen highlight target, so the caller can
     * blit it over its viewport through the recorded GUI path.
     *
     * <p>The sibling {@link #drawHighlight} recolours the picking texture, which only works for things that
     * carry a picking index. The trackball sphere has none — it is picked by a screen-space disc test, not
     * the stencil — so its hover glow is produced by re-drawing the sphere itself here, at the model-view it
     * was last drawn with and the viewport's projection, giving exactly the same on-screen footprint.
     *
     * <p>Drawn without depth (no depth attachment is bound) and cleared to fully transparent, so only the
     * sphere's own pixels carry alpha and the blit composites just the glow.
     *
     * @return {@code true} when something was rendered and the caller should blit
     */
    public static boolean drawGeometryHighlight(BuiltBuffer buffer, GpuTextureView target, Matrix4f modelView, Matrix4f projection)
    {
        if (buffer == null || target == null)
        {
            return false;
        }

        try
        {
            GpuDevice device = RenderSystem.getDevice();
            CommandEncoder encoder = device.createCommandEncoder();
            RenderPipeline pipeline = GIZMO_HIGHLIGHT_PIPELINE;

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(modelView, new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

            /* Written before the pass opens: rotating the ring issues a GPU fence, which the encoder
             * rejects while a pass is open. */
            GpuBufferSlice projectionUniform = writeProjection(encoder, projection);

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

            try (RenderPass pass = encoder.createRenderPass(() -> "bbs:gizmo_sphere_highlight", target, OptionalInt.of(0x00000000)))
            {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("Projection", projectionUniform);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
            }

            return true;
        }
        finally
        {
            buffer.close();
        }
    }



}
