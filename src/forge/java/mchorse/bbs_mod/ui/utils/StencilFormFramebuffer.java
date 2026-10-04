package mchorse.bbs_mod.ui.utils;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;


import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /** Reused readback buffer for the tolerance region pick (grows as needed). */
    private FloatBuffer pickBuffer;

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    /**
     * Draw the stencil buffer over the area with the picked form lit up: the preview shader is
     * told which index it is looking for and what colour to paint it, and the buffer is drawn
     * flipped, the way it was rendered.
     */
    public void renderPreview(UIContext context, Area area)
    {
        this.renderPreview(context, area, this.getIndex());
    }

    /** Same, lighting up {@code index} instead of what is under the cursor — a host pointing at a bone from a list. */
    public void renderPreview(UIContext context, Area area, int index)
    {
        context.batcher.flush();
        Texture texture = this.getFramebuffer().getMainTexture();
        int previous = StencilPreviewProgram.bind(index, BBSSettings.stencilHighlightColor.get());
        try
        {
            GlStateManager.enableBlend();
            context.batcher.texturedBox(texture.id, Colors.WHITE,
                area.x, area.y, area.w, area.h,
                0, texture.height, texture.width, 0, texture.width, texture.height);
        }
        finally
        {
            org.lwjgl.opengl.GL20.glUseProgram(previous);
        }
    }
    public int getIndex()
    {
        return this.index;
    }

    public Pair<Form, String> getPicked()
    {
        return this.indexMap.get(this.index);
    }

    /** The id the last pass drew {@code bone} of {@code form} with, or 0 when it wasn't drawn. */
    public int indexOf(Form form, String bone)
    {
        for (Map.Entry<Integer, Pair<Form, String>> entry : this.indexMap.entrySet())
        {
            Pair<Form, String> pair = entry.getValue();

            if (pair.a == form && pair.b.equals(bone))
            {
                return entry.getKey();
            }
        }

        return 0;
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
            texture.setWrap(GL12.GL_CLAMP_TO_EDGE);

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

    public void apply()
    {
        UIModelRenderer.flushViewportTranslucency();
        this.framebuffer.applyClear();
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
        if (this.framebuffer == null || x < 0 || y < 0
            || x >= this.framebuffer.getMainTexture().width || y >= this.framebuffer.getMainTexture().height)
        {
            this.index = 0;
            return;
        }
        if (this.pickBuffer == null) this.pickBuffer = BufferUtils.createFloatBuffer(4);
        this.pickBuffer.clear();
        readPixels(x, y, 1, 1, this.pickBuffer);
        int r = (int) (this.pickBuffer.get(0) * 255F);
        int g = (int) (this.pickBuffer.get(1) * 255F);
        int b = (int) (this.pickBuffer.get(2) * 255F);
        int a = (int) (this.pickBuffer.get(3) * 255F);
        this.index = a < 1 ? 0 : r | (g << 8) | (b << 16);
    }

    /** Pack state belongs to the caller (Minecraft screenshots also change it). */
    private static void readPixels(int x, int y, int w, int h, FloatBuffer buffer)
    {
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int swapBytes = GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES);
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, 0);
            GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, swapBytes);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        }
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

        Texture texture = this.framebuffer.getMainTexture();
        int x0 = Math.max(0, x - radius);
        int y0 = Math.max(0, y - radius);
        int x1 = Math.min(texture.width - 1, x + radius);
        int y1 = Math.min(texture.height - 1, y + radius);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        if (w <= 0 || h <= 0)
        {
            this.index = 0;

            return;
        }

        int needed = w * h * 4;

        /* A large tolerance × GUI scale can make this region far bigger than the
         * LWJGL frame stack holds, so read into a cached heap buffer instead. */
        if (this.pickBuffer == null || this.pickBuffer.capacity() < needed)
        {
            this.pickBuffer = BufferUtils.createFloatBuffer(needed);
        }

        FloatBuffer floats = this.pickBuffer;

        floats.clear();
        readPixels(x0, y0, w, h, floats);

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
        this.framebuffer.unbind();
    }

    public void clearPicking()
    {
        this.index = 0;
        this.indexMap.clear();
    }

    /** Nothing under the cursor, while what the pass drew stays known (for {@link #indexOf}). */
    public void clearIndex()
    {
        this.index = 0;
    }

    public boolean hasPicked()
    {
        return this.index > 0;
    }
}