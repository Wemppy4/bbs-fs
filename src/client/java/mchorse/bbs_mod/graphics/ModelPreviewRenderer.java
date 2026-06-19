package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Off-screen 3D model preview target for the in-panel viewports (1.21.11 port).
 *
 * <p>The 1.21.6+ GUI is two-phase: {@code Screen.render} only RECORDS into a {@code GuiRenderState} that
 * composites later, and the 1.21.5 GPU rewrite removed imperative {@code RenderSystem.setProjectionMatrix(Matrix4f)}
 * / raw-FBO binding. Vanilla now draws 3D-into-GUI through {@code SpecialGuiElementRenderer}, whose registry is
 * closed (no Fabric hook). So this class reproduces that mechanism standalone: it renders the BBS cubic model
 * through a VANILLA entity {@code RenderLayer} (working shader + std140 UBO + lighting) into our own off-screen
 * colour+depth {@link GpuTexture}s via the {@link RenderSystem#outputColorTextureOverride}/{@code Depth} statics,
 * then the caller blits the colour texture back into the GUI via the existing {@code texturedBox(int)} bridge.</p>
 *
 * <p>Projection is BBS's perspective {@link mchorse.bbs_mod.camera.Camera#projection} uploaded through
 * {@link RawProjectionMatrix} (the same path vanilla uses for the world); the model-view (camera rotation +
 * {@code -position} translation + element transform) is pushed onto {@link RenderSystem#getModelViewStack()};
 * per-model bone transforms are baked into the vertices by {@code CubicCubeRenderer}. Globals are saved/restored
 * around the draw so the surrounding 2D GUI is undisturbed.</p>
 *
 * <p>{@link #ACTIVE}/{@link #TEXTURE} form the gate that {@code ModelInstance.render} reads to route cubic
 * geometry into {@code RenderLayers.entityCutoutNoCull(TEXTURE)} instead of the (not-yet-ported) BBS model layer.
 * {@code ModelFormRenderer.render3D} sets {@link #TEXTURE} to the adopted model texture while {@link #ACTIVE}.</p>
 *
 * TODO(1.21.11 render): verify at runtime (same-frame compositing of the imperative draw, FBO V-orientation,
 * ENTITY_IN_UI light direction vs the old setupLevelDiffuseLighting).
 */
public class ModelPreviewRenderer
{
    /* COPY_DST | COPY_SRC | TEXTURE_BINDING | RENDER_ATTACHMENT (mirrors vanilla Framebuffer's attachments:
     * usable as a render target AND sampled afterwards for the GUI blit). */
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
        | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    /** Set true by the orchestrator around {@code renderUserModel} so the cubic path draws into a vanilla layer. */
    public static boolean ACTIVE = false;

    /** Adopted {@link net.minecraft.util.Identifier} of the current model texture (set by {@code ModelFormRenderer}). */
    public static net.minecraft.util.Identifier TEXTURE = null;

    private final RawProjectionMatrix projection = new RawProjectionMatrix("bbs_model_preview");

    private GpuTexture color;
    private GpuTexture depth;
    private GpuTextureView colorView;
    private GpuTextureView depthView;
    private int width = -1;
    private int height = -1;

    private void resize(int w, int h)
    {
        if (this.color != null && this.width == w && this.height == h)
        {
            return;
        }

        this.releaseTextures();

        GpuDevice device = RenderSystem.getDevice();

        this.color = device.createTexture("bbs_preview_color", USAGE, TextureFormat.RGBA8, w, h, 1, 1);
        this.depth = device.createTexture("bbs_preview_depth", USAGE, TextureFormat.DEPTH32, w, h, 1, 1);
        this.colorView = device.createTextureView(this.color);
        this.depthView = device.createTextureView(this.depth);

        this.width = w;
        this.height = h;
    }

    /**
     * Set up the off-screen 3D state and bind it as the active render target. Caller then draws the model
     * (cubic geometry routed through {@code RenderLayers.entityCutoutNoCull}) and finally calls {@link #end()}.
     */
    public void begin(int w, int h, Matrix4f projectionMatrix, Matrix4f modelView)
    {
        this.resize(w, h);

        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(this.color, 0x00000000, this.depth, 1.0D);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(this.projection.set(projectionMatrix), ProjectionType.PERSPECTIVE);

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        stack.mul(modelView);

        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);

        RenderSystem.outputColorTextureOverride = this.colorView;
        RenderSystem.outputDepthTextureOverride = this.depthView;
    }

    /** Restore all global render state mutated by {@link #begin}. */
    public void end()
    {
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /** Raw GL id of the off-screen colour texture, for blitting via {@code Batcher2D.texturedBox(int,...)}. */
    public int getColorGlId()
    {
        return ((GlTexture) this.color).getGlId();
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }

    private void releaseTextures()
    {
        if (this.colorView != null)
        {
            this.colorView.close();
            this.colorView = null;
        }

        if (this.depthView != null)
        {
            this.depthView.close();
            this.depthView = null;
        }

        if (this.color != null)
        {
            this.color.close();
            this.color = null;
        }

        if (this.depth != null)
        {
            this.depth.close();
            this.depth = null;
        }
    }

    public void close()
    {
        this.releaseTextures();
        this.projection.close();
        this.width = this.height = -1;
    }
}
