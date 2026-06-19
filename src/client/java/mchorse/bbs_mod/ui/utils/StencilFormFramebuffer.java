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
import net.minecraft.client.texture.GlTextureView;
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
 * from here. The colour attachment is the SAME GL texture this class already owns — adopted (zero-copy) into a
 * render-attachment {@link GlTexture}/{@link GlTextureView} so the mapped pass writes into it, while the raw-GL
 * {@link Framebuffer} is kept purely as the {@code glReadPixels} read target (and for {@code getMainTexture}).
 * Depth is a throwaway device texture (nearer bones must occlude farther ones for a correct pick).</p>
 */
public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /* GPU render-pass attachments (1.21.11): colour wraps the raw-GL texture above; depth is device-owned. */
    private GpuTexture colorTexture;
    private GpuTextureView colorView;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private int gpuWidth = -1;
    private int gpuHeight = -1;
    private int wrappedColorGlId = -1;

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
     * (Re)build the GPU render-pass attachments to match the current raw-GL colour texture. The colour view
     * adopts the raw-GL texture id (zero-copy, render-attachment usage) so the picker pass writes into the same
     * texture the read-back / {@code getMainTexture} blit read; the depth view is a device texture re-allocated
     * on resize. Cheap no-op while the size and adopted id are unchanged.
     */
    private void ensureGpuTargets()
    {
        Texture texture = this.framebuffer.getMainTexture();
        int w = Math.max(1, texture.width);
        int h = Math.max(1, texture.height);

        if (this.colorView != null && this.gpuWidth == w && this.gpuHeight == h && this.wrappedColorGlId == texture.id)
        {
            return;
        }

        this.releaseGpuTargets();

        AdoptedColorTexture color = new AdoptedColorTexture(texture.id, w, h);

        this.colorTexture = color;
        this.colorView = new AdoptedColorTextureView(color);

        this.depthTexture = RenderSystem.getDevice().createTexture("bbs_stencil_depth",
            GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, w, h, 1, 1);
        this.depthView = RenderSystem.getDevice().createTextureView(this.depthTexture);

        this.gpuWidth = w;
        this.gpuHeight = h;
        this.wrappedColorGlId = texture.id;
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
        /* The mapped render pass wrote into the colour texture through the backend's own FBO; bind our raw-GL
         * FBO (same colour texture attached) as the read source so glReadPixels samples the rendered pixels. */
        this.framebuffer.bind();

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

        this.framebuffer.unbind();
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

    /**
     * Render-attachment {@link GlTexture} adopting the raw-GL stencil colour texture id (zero-copy). BBS owns the
     * GL id ({@link Framebuffer}/{@link Texture}), so {@link #close()} must never free it. Same protected-ctor
     * subclass trick {@code AdoptedTexture} uses for the GUI sampling bridge, but with render-attachment usage.
     */
    private static final class AdoptedColorTexture extends GlTexture
    {
        private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC;

        private AdoptedColorTexture(int glId, int width, int height)
        {
            super(USAGE, "bbs_stencil_color_" + glId, TextureFormat.RGBA8, width, height, 1, 1, glId);
        }

        @Override
        public void close()
        {
            /* BBS owns the GL id; do not free it here. */
        }
    }

    private static final class AdoptedColorTextureView extends GlTextureView
    {
        private AdoptedColorTextureView(AdoptedColorTexture texture)
        {
            super(texture, 0, 1);
        }

        @Override
        public void close()
        {
            /* Underlying texture not owned; nothing to release. */
        }
    }
}
