package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The little 3D pictures of forms in lists, palettes and the HUD, kept as textures.
 *
 * <p>Each visible list row used to be a full model render every frame — channels, IK, physics,
 * one draw call per bone and material — fifteen rows, fifteen models. Now a form's picture is
 * rendered once into its own framebuffer and blitted; it is re-rendered when the form changed
 * (its pose version or the box size) and otherwise refreshed in rotation, a settings-bound
 * number of pictures per frame, so the pictures still turn with the mouse and play their idle
 * animations — at a fraction of the rate, for a fraction of the cost.</p>
 *
 * <p>The re-render happens inside the normal UI pass with the screen's own projection and
 * matrix stack: only the viewport moves, placed so the box's on-screen rectangle lands on the
 * framebuffer. Nothing about how a form draws itself changes, which is what keeps every form
 * type (models, mobs, billboards, blocks) on the same path.</p>
 */
public class FormPreviewCache
{
    /** The largest a picture is rendered at, per side, whatever the GUI scale. */
    private static final int MAX_PIXELS = 512;

    private static final long SWEEP_EVERY = 120L;
    private static final long KEEP_EPOCHS = 600L;

    /* Forms compare by identity, so a plain map keys them right; entries are swept by last use. */
    private static final Map<Form, Entry> ENTRIES = new HashMap<>();

    private static long epoch = -1L;
    private static long lastSweep;
    private static int rendersThisFrame;
    private static int requestedThisFrame;
    private static int requestedLastFrame;

    private static class Entry
    {
        Framebuffer framebuffer;
        Texture texture;
        int width;
        int height;
        int pixelWidth;
        int pixelHeight;
        int poseVersion;
        long renderedEpoch = -1L;
        long usedEpoch;
    }

    /** Draw the form's picture into the box — from the cache when it can, live when it cannot. */
    public static void render(FormRenderer<?> renderer, UIContext context, int x1, int y1, int x2, int y2)
    {
        int budget = BBSSettings.previewRefreshBudget == null ? 0 : BBSSettings.previewRefreshBudget.get();
        int w = x2 - x1;
        int h = y2 - y1;
        Form form = renderer.getForm();

        if (budget <= 0 || w <= 0 || h <= 0 || form == null || !RenderFrame.isEnabled())
        {
            renderer.renderLive(context, x1, y1, x2, y2);

            return;
        }

        long now = RenderFrame.getEpoch();

        if (now != epoch)
        {
            epoch = now;
            requestedLastFrame = requestedThisFrame;
            requestedThisFrame = 0;
            rendersThisFrame = 0;

            if (now - lastSweep >= SWEEP_EVERY)
            {
                lastSweep = now;
                sweep(now);
            }
        }

        requestedThisFrame += 1;

        Entry entry = ENTRIES.get(form);

        if (entry == null)
        {
            entry = new Entry();
            ENTRIES.put(form, entry);
        }

        entry.usedEpoch = now;

        /* A changed form (or box) re-renders at once, budget or not — a stale picture of an edit is
         * wrong, an unrefreshed idle animation is merely late. Rotation: with N pictures on screen
         * and K renders a frame, each one comes round every N/K frames. */
        boolean fresh = entry.renderedEpoch < 0 || entry.width != w || entry.height != h || entry.poseVersion != form.getPoseVersion();
        int interval = Math.max(1, (requestedLastFrame + budget - 1) / budget);
        boolean due = rendersThisFrame < budget && now - entry.renderedEpoch >= interval;

        if (fresh || due)
        {
            if (!renderInto(entry, renderer, context, x1, y1, x2, y2))
            {
                renderer.renderLive(context, x1, y1, x2, y2);

                return;
            }

            entry.width = w;
            entry.height = h;
            entry.poseVersion = form.getPoseVersion();
            entry.renderedEpoch = now;
            rendersThisFrame += 1;
        }

        BBSProfiler.count(BBSProfiler.Section.UI_PREVIEWS_CACHED);

        context.batcher.texturedBox(entry.texture.id, Colors.WHITE, x1, y1, w, h, 0, entry.pixelHeight, entry.pixelWidth, 0, entry.pixelWidth, entry.pixelHeight);
    }

    /**
     * Render the form into the entry's framebuffer. The screen projection and matrix stack stay as
     * they are; the viewport is offset so the box's on-screen rectangle maps onto the framebuffer.
     */
    private static boolean renderInto(Entry entry, FormRenderer<?> renderer, UIContext context, int x1, int y1, int x2, int y2)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();
        int menuW = context.menu.width;
        int menuH = context.menu.height;

        if (menuW <= 0 || menuH <= 0)
        {
            return false;
        }

        int w = x2 - x1;
        int h = y2 - y1;
        float scaleX = window.getFramebufferWidth() / (float) menuW;
        float scaleY = window.getFramebufferHeight() / (float) menuH;
        int pw = Math.max(1, Math.min(MAX_PIXELS, Math.round(w * scaleX)));
        int ph = Math.max(1, Math.min(MAX_PIXELS, Math.round(h * scaleY)));

        /* If the cap bit, the picture is rendered smaller than the screen would: scale the whole
         * mapping down with it, so the box still fills the framebuffer edge to edge. */
        scaleX = pw / (float) w;
        scaleY = ph / (float) h;

        ensureFramebuffer(entry, pw, ph);

        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        context.batcher.flush();

        if (scissor)
        {
            GlStateManager._disableScissorTest();
        }

        entry.framebuffer.bind();
        entry.framebuffer.clear();

        /* The box's coordinates are local to a scrolled element (the scroll rides the matrix
         * stack via UIContext#shiftY), so where it actually lands on screen is the global
         * position — that is what the viewport has to be placed by. GUI y runs down, GL y runs
         * up: the box's bottom-left corner has to land at the framebuffer's origin. */
        int screenX = context.globalX(x1);
        int screenY = context.globalY(y1);

        GL11.glViewport(
            Math.round(-screenX * scaleX),
            Math.round(-(menuH - screenY - h) * scaleY),
            Math.round(menuW * scaleX),
            Math.round(menuH * scaleY)
        );

        try
        {
            renderer.renderLive(context, x1, y1, x2, y2);
            context.batcher.flush();
        }
        finally
        {
            mc.getFramebuffer().beginWrite(true);

            if (scissor)
            {
                GlStateManager._enableScissorTest();
            }
        }

        return true;
    }

    private static void ensureFramebuffer(Entry entry, int pw, int ph)
    {
        if (entry.framebuffer == null)
        {
            Texture texture = new Texture();

            texture.setSize(pw, ph);
            texture.setFilter(GL11.GL_LINEAR);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(pw, ph);

            entry.framebuffer = new Framebuffer();
            entry.framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            entry.framebuffer.attach(renderbuffer);
            entry.framebuffer.unbind();
            entry.texture = texture;
        }
        else if (entry.pixelWidth != pw || entry.pixelHeight != ph)
        {
            entry.framebuffer.resize(pw, ph);
        }

        entry.pixelWidth = pw;
        entry.pixelHeight = ph;
    }

    /** Free the pictures of forms nobody asked about for a while (a closed film, a scrolled-away palette). */
    private static void sweep(long now)
    {
        Iterator<Map.Entry<Form, Entry>> it = ENTRIES.entrySet().iterator();

        while (it.hasNext())
        {
            Entry entry = it.next().getValue();

            if (now - entry.usedEpoch > KEEP_EPOCHS)
            {
                if (entry.framebuffer != null)
                {
                    entry.framebuffer.delete();
                }

                it.remove();
            }
        }
    }
}
