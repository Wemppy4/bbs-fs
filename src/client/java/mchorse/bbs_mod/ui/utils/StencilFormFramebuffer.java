package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The off-screen colour target the picker shaders render the per-form/per-bone index colours into, plus the
 * {@code glReadPixels} read-back that turns the pixel under the cursor into a {@link Pair Pair&lt;Form, bone&gt;}.
 *
 * <p>1.21.11 port: the 1.21.1 code bound this raw-GL framebuffer with {@code glBindFramebuffer} and let an
 * immediate {@code RenderLayer.draw} land in it. In 1.21.5+ the immediate path renders into the GPU render
 * target (a {@link GpuTextureView}), not whatever FBO is bound by hand, so the picker draws are now driven by
 * {@link BBSPickerRenderer} through an explicit {@code CommandEncoder.createRenderPass} whose colour/depth come
 * from a device-owned colour ({@link #colorTexture}) + depth ({@link #depthTexture}) pair built here — the same
 * mechanism {@code ModelPreviewRenderer} uses for the in-panel model preview. For read-back the colour texture's
 * GL id is attached to a private raw-GL framebuffer and {@code glReadPixels} samples the pixel under the cursor
 * (faithful to the original). The legacy raw-GL {@link Framebuffer} is retained only so {@code getMainTexture}
 * keeps working for callers (e.g. the picker-preview highlight overlay).</p>
 */
public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /* GPU render-pass attachments (1.21.11): device-owned colour + depth the picker draws render into. */
    private GpuTexture colorTexture;
    private GpuTextureView colorView;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private int gpuWidth = -1;
    private int gpuHeight = -1;

    /* Raw-GL framebuffer used purely to glReadPixels the colour texture (the mapped API has no 1-pixel read). */
    private int readFbo = -1;

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    public int getIndex()
    {
        return this.index;
    }

    public Map<Integer, Pair<Form, String>> getIndexMap()
    {
        return this.indexMap;
    }

    public Pair<Form, String> getPicked()
    {
        return this.indexMap.get(this.index);
    }

    public void setup(Link id)
    {
        if (this.framebuffer != null)
        {
            return;
        }

        this.framebuffer = BBSModClient.getFramebuffers().getFramebuffer(id, (framebuffer) ->
        {
            Texture texture = new Texture();

            texture.setSize(2, 2);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(2, 2);

            framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            framebuffer.attach(renderbuffer);
            framebuffer.unbind();
        });
    }

    public void resizeGUI(int w, int h)
    {
        this.resize(w, h, BBSModClient.getGUIScale());
    }

    public void resize(int w, int h, int scale)
    {
        this.resize(w * scale, h * scale);
    }

    public void resize(int w, int h)
    {
        if (this.framebuffer != null)
        {
            this.framebuffer.resize(w, h);
        }
    }

    /**
     * (Re)build the device colour/depth render-pass attachments to match the current raw-GL stencil texture
     * size. The picker pass needs depth (nearer bones must occlude farther ones for a correct pick). Cheap
     * no-op while the size is unchanged.
     */
    private void ensureGpuTargets()
    {
        Texture texture = this.framebuffer.getMainTexture();
        int w = Math.max(1, texture.width);
        int h = Math.max(1, texture.height);

        if (this.colorView != null && this.gpuWidth == w && this.gpuHeight == h)
        {
            return;
        }

        this.releaseGpuTargets();

        this.colorTexture = RenderSystem.getDevice().createTexture("bbs_stencil_color",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC,
            TextureFormat.RGBA8, w, h, 1, 1);
        this.colorView = RenderSystem.getDevice().createTextureView(this.colorTexture);

        this.depthTexture = RenderSystem.getDevice().createTexture("bbs_stencil_depth",
            GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, w, h, 1, 1);
        this.depthView = RenderSystem.getDevice().createTextureView(this.depthTexture);

        this.gpuWidth = w;
        this.gpuHeight = h;
    }

    /**
     * Begin a picking pass: clear the colour (transparent black = index 0) and depth, then point
     * {@link BBSPickerRenderer} at this target so the form/model picker draws land here. The render pass itself
     * loads (does not clear), so every form/bone accumulates with depth testing. The 1.21.1 equivalent was
     * {@code framebuffer.applyClear()} + a raw {@code glBindFramebuffer}.
     */
    public void apply()
    {
        this.ensureGpuTargets();

        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(this.colorTexture, 0x00000000, this.depthTexture, 1.0D);

        BBSPickerRenderer.setRenderTarget(this.colorView, this.depthView);
    }

    public void pickGUI(UIContext context, Area area)
    {
        this.pickGUI(context.mouseX - area.x, area.h - context.mouseY + area.y);
    }

    public void pickGUI(int x, int y)
    {
        int scale = BBSModClient.getGUIScale();

        this.pick(x * scale, y * scale);
    }

    public void pick(int x, int y)
    {
        if (this.colorTexture == null)
        {
            this.index = 0;

            return;
        }

        /* The mapped render pass wrote into the device colour texture through the backend's own FBO. Attach
         * that texture's GL id to our private read FBO and glReadPixels the pixel under the cursor. */
        if (this.readFbo < 0)
        {
            this.readFbo = GL30.glGenFramebuffers();
        }

        int glId = ((GlTexture) this.colorTexture).getGlId();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

            /* TODO: make other channels work */
            int r = (int) (floats.get() * 255F);
            int g = (int) (floats.get() * 255F);
            int b = (int) (floats.get() * 255F);
            int a = (int) (floats.get() * 255F);

            this.index = a < 1F ? 0 : r | (g << 8) | (b << 16);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void unbind(StencilMap map)
    {
        this.unbind();

        this.indexMap.clear();
        this.indexMap.putAll(map.indexMap);
    }

    public void unbind()
    {
        BBSPickerRenderer.clearRenderTarget();
    }

    public void clearPicking()
    {
        this.index = 0;
        this.indexMap.clear();
    }

    public boolean hasPicked()
    {
        return this.index > 0;
    }

    private void releaseGpuTargets()
    {
        if (this.colorView != null)
        {
            this.colorView.close();
            this.colorView = null;
        }

        if (this.colorTexture != null)
        {
            this.colorTexture.close();
            this.colorTexture = null;
        }

        if (this.depthView != null)
        {
            this.depthView.close();
            this.depthView = null;
        }

        if (this.depthTexture != null)
        {
            this.depthTexture.close();
            this.depthTexture = null;
        }
    }
}
