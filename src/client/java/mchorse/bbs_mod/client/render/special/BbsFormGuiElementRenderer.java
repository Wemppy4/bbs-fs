package mchorse.bbs_mod.client.render.special;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders BBS form thumbnails (model/billboard/extruded/block/particle) into off-screen FBOs and composites them into form-list cells via the
 * vanilla special-element pipeline. Registered into the otherwise-closed special-element registry by
 * {@code GuiRendererMixin}. This is the mechanism vanilla uses for entity/item 3D-in-GUI thumbnails, adopted
 * here because the form list draws each cell in the GUI record phase where a direct immediate 3D draw cannot
 * composite (two-phase GUI).
 *
 * <p><b>Why we override {@code render(T, GuiRenderState, int)} instead of just {@code render(T, MatrixStack)}:</b>
 * the base {@link SpecialGuiElementRenderer} keeps a SINGLE off-screen texture per renderer instance and its
 * composite quads sample it lazily in the deferred GUI. Vanilla only ever submits one element per renderer
 * type per frame (one inventory player, one loom result…), so that single texture is fine. A LIST submits N
 * thumbnails through this one renderer; with the base's single FBO every deferred quad would sample the
 * last-rendered model. So we keep a per-form texture pool: each cell renders into its own persistent texture
 * and its composite quad samples that — otherwise the framing/draw mirrors the base exactly.</p>
 */
public class BbsFormGuiElementRenderer extends SpecialGuiElementRenderer<BbsFormGuiElementRenderState>
{
    /* TODO(strip): throttled diagnostic so a failing renderUIPreview is visible in the log instead of silent. */
    private static int errorLog;

    private final ProjectionMatrix2 projection = new ProjectionMatrix2("PIP - bbs form", -1000.0F, 1000.0F, true);

    /* Per-form (and per-size) persistent off-screen targets. Keyed by renderer identity + dimensions so the
     * same form shown at two sizes (e.g. toolbar 40x40 and grid 60x80) doesn't thrash one texture. The deferred
     * composite quads sample these, so they MUST survive until the GUI is drawn — hence a persistent pool, not
     * the base's single reused texture. TODO: LRU eviction; currently a bounded leak (forms x sizes shown). */
    private final Map<String, Target> targets = new HashMap<>();

    /* Custom diffuse-light UBO holding the EXACT directions the original setupLevelDiffuseLighting used for UI
     * form previews (FormRenderer.renderUI lightA/lightB). Built lazily once and bound via the public
     * RenderSystem.setShaderLights for a 1:1 match — see lights(). */
    private GpuBuffer lightsBuffer;
    private GpuBufferSlice lights;

    public BbsFormGuiElementRenderer(Immediate vertexConsumers)
    {
        super(vertexConsumers);
    }

    @Override
    public Class<BbsFormGuiElementRenderState> getElementClass()
    {
        return BbsFormGuiElementRenderState.class;
    }

    @Override
    public void render(BbsFormGuiElementRenderState state, GuiRenderState guiState, int windowScaleFactor)
    {
        int w = (state.x2() - state.x1()) * windowScaleFactor;
        int h = (state.y2() - state.y1()) * windowScaleFactor;

        if (w <= 0 || h <= 0)
        {
            return;
        }

        Target target = this.acquire(state.renderer(), w, h);

        RenderSystem.outputColorTextureOverride = target.colorView;
        RenderSystem.outputDepthTextureOverride = target.depthView;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
        RenderSystem.setProjectionMatrix(this.projection.set(w, h), ProjectionType.ORTHOGRAPHIC);

        MatrixStack matrices = new MatrixStack();

        matrices.translate(w / 2.0F, this.getYOffset(h, windowScaleFactor), 0.0F);

        float f = windowScaleFactor * state.scale();

        matrices.scale(f, f, -f);

        this.render(state, matrices);
        this.vertexConsumers.draw();

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        /* Composite THIS form's texture into the cell (V-flipped 0,1,1,0 + premultiplied alpha, exactly like
         * the base's renderElement). The pose carries the list's scroll translate. addSimpleElementToCurrentLayer
         * adds directly to the current layer. */
        guiState.addSimpleElementToCurrentLayer(new TexturedQuadGuiElementRenderState(
            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.of(target.colorView, RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST)),
            state.pose(),
            state.x1(), state.y1(), state.x2(), state.y2(),
            0.0F, 1.0F, 1.0F, 0.0F,
            -1,
            state.scissorArea()));
    }

    @Override
    protected void render(BbsFormGuiElementRenderState state, MatrixStack matrices)
    {
        /* 1:1 with the original: bind the same two diffuse-light directions setupLevelDiffuseLighting used,
         * NOT the vanilla ENTITY_IN_UI (inventory) preset which lights from below. */
        RenderSystem.setShaderLights(this.lights());

        boolean prevActive = ModelPreviewRenderer.ACTIVE;

        ModelPreviewRenderer.ACTIVE = true;

        try
        {
            state.renderer().renderUIPreview(matrices, state.angle(), state.transition(),
                state.x1(), state.y1(), state.x2(), state.y2());
        }
        catch (Exception e)
        {
            /* A single bad form preview must not abort the GUI prepare pass / the other list cells. */
            if (errorLog++ % 120 == 0)
            {
                System.out.println("[BBS list preview] renderUIPreview failed: " + e);
            }
        }
        finally
        {
            ModelPreviewRenderer.TEXTURE = null;
            ModelPreviewRenderer.ACTIVE = prevActive;
        }
    }

    /**
     * The two diffuse-light directions the original {@code FormRenderer.renderUI} fed to
     * {@code RenderSystem.setupLevelDiffuseLighting} (lightA/lightB), as a one-slice Lighting UBO. The 1.21.5
     * Lighting UBO is exactly two std140 vec3s and the vanilla {@code DiffuseLighting.Type} is only a slice
     * index ({@code DiffuseLighting.updateBuffer} writes just the two vectors), so we build our own buffer and
     * bind it through the public {@code RenderSystem.setShaderLights(GpuBufferSlice)} — an exact reproduction.
     */
    private GpuBufferSlice lights()
    {
        if (this.lights == null)
        {
            Vector3f lightA = new Vector3f(0F, 1F, -0.2F).normalize();
            Vector3f lightB = new Vector3f(-0.85F, 0.85F, 1F).normalize();

            try (MemoryStack stack = MemoryStack.stackPush())
            {
                ByteBuffer data = Std140Builder.onStack(stack, DiffuseLighting.UBO_SIZE)
                    .putVec3(lightA)
                    .putVec3(lightB)
                    .get();

                /* usage 136 = UNIFORM | COPY_DST, mirroring DiffuseLighting's own Lighting UBO. */
                this.lightsBuffer = RenderSystem.getDevice().createBuffer(() -> "BBS form preview lights UBO", 136, data);
                this.lights = this.lightsBuffer.slice(0, DiffuseLighting.UBO_SIZE);
            }
        }

        return this.lights;
    }

    private Target acquire(FormRenderer<?> key, int w, int h)
    {
        String id = System.identityHashCode(key) + "_" + w + "x" + h;
        Target target = this.targets.get(id);

        if (target != null)
        {
            return target;
        }

        GpuDevice device = RenderSystem.getDevice();

        target = new Target();
        /* usage 12 = RENDER_ATTACHMENT | TEXTURE_BINDING (render target + sampled); depth 8 = RENDER_ATTACHMENT. */
        target.color = device.createTexture(() -> "BBS form thumbnail", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        target.colorView = device.createTextureView(target.color);
        target.depth = device.createTexture(() -> "BBS form thumbnail depth", GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, w, h, 1, 1);
        target.depthView = device.createTextureView(target.depth);

        this.targets.put(id, target);

        return target;
    }

    @Override
    protected float getYOffset(int height, int windowScaleFactor)
    {
        /* Anchor the model ~85% down the cell (feet near the bottom), matching the original getUIMatrix
         * vertical placement (y1 + 0.85*(y2-y1)). */
        return 0.85F * height;
    }

    @Override
    protected String getName()
    {
        return "bbs form";
    }

    @Override
    public void close()
    {
        super.close();

        for (Target target : this.targets.values())
        {
            target.colorView.close();
            target.color.close();
            target.depthView.close();
            target.depth.close();
        }

        this.targets.clear();
        this.projection.close();

        if (this.lightsBuffer != null)
        {
            this.lightsBuffer.close();
            this.lightsBuffer = null;
            this.lights = null;
        }
    }

    private static final class Target
    {
        private GpuTexture color;
        private GpuTextureView colorView;
        private GpuTexture depth;
        private GpuTextureView depthView;
    }
}
