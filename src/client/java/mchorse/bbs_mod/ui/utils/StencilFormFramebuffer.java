package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.PixelPackState;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.BufferUtils;
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

    /** Reused readback buffer for the tolerance region pick (grows as needed). */
    private FloatBuffer pickBuffer;

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    /**
     * Off-screen colour the hover highlight for THIS viewport is rendered into, before the caller blits it
     * back over its area. Owned here rather than by BBSPickerRenderer because the blit is a RECORDED GUI
     * element: with a single shared target, two live viewports (a form editor opened over the film editor)
     * would both write it during the frame and both blits would then composite whatever the second one left.
     */
    private GpuTexture highlightTex;
    private GpuTextureView highlightView;
    private int highlightWidth = -1;
    private int highlightHeight = -1;

    /** (Re)build this viewport's highlight target at {@code w}x{@code h}. Cheap no-op while unchanged. */
    public GpuTextureView ensureHighlightTarget(int w, int h)
    {
        if (this.highlightView != null && this.highlightWidth == w && this.highlightHeight == h)
        {
            return this.highlightView;
        }

        this.releaseHighlightTarget();

        this.highlightTex = RenderSystem.getDevice().createTexture("bbs_stencil_highlight",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC,
            TextureFormat.RGBA8, w, h, 1, 1);
        this.highlightView = RenderSystem.getDevice().createTextureView(this.highlightTex);

        this.highlightWidth = w;
        this.highlightHeight = h;

        return this.highlightView;
    }

    /** Raw GL id of this viewport's highlight texture, for the recorded {@code texturedBox(int,...)} blit. */
    public int getHighlightGlId()
    {
        return this.highlightTex == null ? -1 : ((GlTexture) this.highlightTex).getGlId();
    }

    public int getHighlightWidth()
    {
        return this.highlightWidth;
    }

    public int getHighlightHeight()
    {
        return this.highlightHeight;
    }

    private void releaseHighlightTarget()
    {
        if (this.highlightView != null)
        {
            this.highlightView.close();
            this.highlightView = null;
        }

        if (this.highlightTex != null)
        {
            this.highlightTex.close();
            this.highlightTex = null;
        }

        this.highlightWidth = -1;
        this.highlightHeight = -1;
    }

    /** This target's picking colour texture — the one ITS index colours were rendered into. */
    public GpuTextureView getPickColorView()
    {
        return this.colorView;
    }

    public int getPickWidth()
    {
        return this.gpuWidth;
    }

    public int getPickHeight()
    {
        return this.gpuHeight;
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
        float scale = BBSModClient.getGUIScale();

        this.resize(Math.round(w * scale), Math.round(h * scale));
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

    /** {@link #pickGUI(UIContext, Area)} with a gizmo-handle hover tolerance
     *  ({@code radius} in GUI pixels; ids in {@code [1, handleMax]} grab from nearby). */
    public void pickGUI(UIContext context, Area area, int radius, int handleMax)
    {
        float scale = BBSModClient.getGUIScale();
        int x = Math.round((context.mouseX - area.x) * scale);
        int y = Math.round((area.h - context.mouseY + area.y) * scale);

        this.pick(x, y, Math.round(radius * scale), handleMax);
    }

    public void pickGUI(int x, int y)
    {
        float scale = BBSModClient.getGUIScale();

        this.pick(Math.round(x * scale), Math.round(y * scale));
    }

    public void pick(int x, int y)
    {
        /* Outside the attachment there is nothing to sample, and a degenerate target (the film
         * preview reports 0x0 for a frame while its custom size is being applied) must not reach
         * GL at all. */
        if (this.colorTexture == null
            || x < 0 || y < 0 || x >= this.gpuWidth || y >= this.gpuHeight)
        {
            this.index = 0;

            return;
        }

        this.bindReadFbo();

        try (MemoryStack stack = MemoryStack.stackPush(); PixelPackState pack = PixelPackState.push())
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

    /**
     * The mapped render pass wrote into the device colour texture through the backend's own FBO. Attach that
     * texture's GL id to our private read FBO so {@code glReadPixels} can sample it. Caller unbinds.
     */
    private void bindReadFbo()
    {
        if (this.readFbo < 0)
        {
            this.readFbo = GL30.glGenFramebuffers();
        }

        int glId = ((GlTexture) this.colorTexture).getGlId();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0);
    }

    /**
     * Pick, but let ids in {@code [1, handleMax]} (the gizmo's handles) grab from
     * nearby: search a {@code radius}-pixel disc around the cursor and take the
     * <em>nearest</em> such id, so a thin line captures when the cursor is beside
     * it — the way a typical 3D gizmo hovers. Anything outside that id range (form
     * parts / bones) still resolves at the exact pixel under the cursor, so only
     * the handles get the tolerance. {@code radius} is in framebuffer pixels;
     * {@code radius <= 0} falls back to the plain single-pixel {@link #pick}.
     */
    public void pick(int x, int y, int radius, int handleMax)
    {
        if (radius <= 0 || this.framebuffer == null)
        {
            this.pick(x, y);

            return;
        }

        if (this.colorTexture == null || this.gpuWidth <= 0 || this.gpuHeight <= 0)
        {
            this.index = 0;

            return;
        }

        /* All of this is computed in long and clamped to the attachment before it reaches GL.
         * The int version overflowed: a caller that divides by a zero-sized viewport hands in
         * x/y = (int) Infinity = Integer.MAX_VALUE, and then `x + radius` wraps negative while
         * `x1 - x0 + 1` wraps back to a small POSITIVE width — which slipped past the `w <= 0`
         * guard and asked the driver for a region that did not match the buffer we sized for it,
         * so glReadPixels wrote past the end (EXCEPTION_ACCESS_VIOLATION in the GL driver). */
        long cx = MathUtils.clamp((long) x, 0L, this.gpuWidth - 1L);
        long cy = MathUtils.clamp((long) y, 0L, this.gpuHeight - 1L);
        long r = Math.min((long) radius, Math.max(this.gpuWidth, this.gpuHeight));

        int x0 = (int) Math.max(0L, cx - r);
        int y0 = (int) Math.max(0L, cy - r);
        int x1 = (int) Math.min(this.gpuWidth - 1L, cx + r);
        int y1 = (int) Math.min(this.gpuHeight - 1L, cy + r);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        if (w <= 0 || h <= 0)
        {
            this.index = 0;

            return;
        }

        /* The centre the nearest-handle search measures from, after the same clamping. */
        x = (int) cx;
        y = (int) cy;

        int needed = w * h * 4;

        /* A large tolerance × GUI scale can make this region far bigger than the
         * LWJGL frame stack holds, so read into a cached heap buffer instead. */
        if (this.pickBuffer == null || this.pickBuffer.capacity() < needed)
        {
            this.pickBuffer = BufferUtils.createFloatBuffer(needed);
        }

        FloatBuffer floats = this.pickBuffer;

        floats.clear();

        this.bindReadFbo();

        /* The pack state is ambient and vanilla leaves GL_PACK_ROW_LENGTH set behind it (see
         * PixelPackState) — without pinning it here the driver writes rowLength pixels per row
         * into a buffer sized for w of them and takes the process down with it. */
        try (PixelPackState pack = PixelPackState.push())
        {
            GL11.glReadPixels(x0, y0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, floats);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        {
            int centerId = 0;
            int nearestHandle = 0;
            long nearestDist = Long.MAX_VALUE;
            long radiusSq = (long) radius * radius;

            for (int py = 0; py < h; py++)
            {
                for (int px = 0; px < w; px++)
                {
                    int base = (py * w + px) * 4;

                    if ((int) (floats.get(base + 3) * 255F) < 1)
                    {
                        continue;
                    }

                    int id = (int) (floats.get(base) * 255F)
                        | ((int) (floats.get(base + 1) * 255F) << 8)
                        | ((int) (floats.get(base + 2) * 255F) << 16);
                    int fx = x0 + px;
                    int fy = y0 + py;

                    if (fx == x && fy == y)
                    {
                        centerId = id;
                    }

                    if (id >= 1 && id <= handleMax)
                    {
                        long dx = fx - x;
                        long dy = fy - y;
                        long dist = dx * dx + dy * dy;

                        if (dist <= radiusSq && dist < nearestDist)
                        {
                            nearestDist = dist;
                            nearestHandle = id;
                        }
                    }
                }
            }

            this.index = nearestHandle != 0 ? nearestHandle : centerId;
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
        this.releaseHighlightTarget();

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
