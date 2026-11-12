package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;

/**
 * A multisampled layer for editor overlays (the gizmo, the orbit ball): what is drawn
 * between {@link #begin} and {@link #end} goes into an off-screen MSAA buffer the size
 * of the current viewport, which is then resolved and laid over the frame — smooth edges
 * instead of the main framebuffer's stairs, without touching how the overlay draws.
 *
 * <p>The buffer holds premultiplied colour: while the layer is open, every blend function
 * gets its alpha half swapped for {@code ONE, ONE_MINUS_SRC_ALPHA}
 * ({@code GlStateManagerMixin}), so alpha accumulates as coverage the way it would over
 * an opaque frame, and the composite ({@code ONE, ONE_MINUS_SRC_ALPHA}) applies it once.
 * A plain buffer would apply a translucent part's alpha twice (on draw and on composite)
 * and leave the rotation pie nearly invisible.</p>
 *
 * <p>The overlay may set the viewport it found, but not another one: the layer moves the
 * viewport onto its own buffer and back. Scissoring is off inside (the buffer is the
 * clip). Layers don't nest — an inner {@code begin} draws straight into the outer one.</p>
 */
public class AntialiasedLayer
{
    private static final float[] CLEAR_COLOR = {0F, 0F, 0F, 0F};
    private static final float[] CLEAR_DEPTH = {1F};
    private static final int[] SAMPLES = {0, 2, 4};

    private static boolean active;
    private static boolean failed;
    private static int maxSamples = -1;

    private static int msaaFbo;
    private static int msaaColor;
    private static int msaaDepth;
    private static int resolveFbo;
    private static int resolveTexture;
    private static int width;
    private static int height;
    private static int samples;

    /* What begin() found, put back by end() */
    private static int x;
    private static int y;
    private static final int[] viewport = new int[4];
    private static int previousDraw;
    private static int previousRead;
    private static boolean scissor;
    private static boolean blend;
    private static boolean depthWrite;
    private static final int[] blendFunc = new int[4];

    public static boolean isActive()
    {
        return active;
    }

    /**
     * Open a layer over the current viewport.
     *
     * @return whether a layer was opened; {@link #end} must only follow a {@code true}.
     */
    public static boolean begin()
    {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        return begin(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    /**
     * Open a layer over a rectangle of the framebuffer, in framebuffer pixels from the
     * bottom left — for an overlay drawn with the whole window's viewport (the UI) that
     * covers only a small part of it.
     */
    public static boolean begin(int x, int y, int w, int h)
    {
        int wanted = wantedSamples();

        if (active || failed || wanted <= 1 || w <= 0 || h <= 0)
        {
            return false;
        }

        if (!ensure(w, h, wanted))
        {
            return false;
        }

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        AntialiasedLayer.x = x;
        AntialiasedLayer.y = y;
        previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        blendFunc[0] = GL11.glGetInteger(GL30.GL_BLEND_SRC_RGB);
        blendFunc[1] = GL11.glGetInteger(GL30.GL_BLEND_DST_RGB);
        blendFunc[2] = GL11.glGetInteger(GL30.GL_BLEND_SRC_ALPHA);
        blendFunc[3] = GL11.glGetInteger(GL30.GL_BLEND_DST_ALPHA);

        GlStateManager._disableScissorTest();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, msaaFbo);

        /* Clearing obeys the write masks */
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, CLEAR_COLOR);
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, CLEAR_DEPTH);

        /* The same mapping as before, shifted so the rectangle lands on the buffer's origin */
        RenderSystem.viewport(viewport[0] - x, viewport[1] - y, viewport[2], viewport[3]);

        active = true;

        /* Re-apply the current blend function, now through the alpha swap */
        RenderSystem.blendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);

        return true;
    }

    public static void end()
    {
        if (!active)
        {
            return;
        }

        active = false;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, msaaFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resolveFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        RenderSystem.viewport(x, y, width, height);

        composite();

        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.blendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
        RenderSystem.depthMask(depthWrite);

        if (!blend)
        {
            RenderSystem.disableBlend();
        }

        if (scissor)
        {
            GlStateManager._enableScissorTest();
        }
    }

    /** The resolved buffer over the rectangle, premultiplied. */
    private static void composite()
    {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorter = RenderSystem.getVertexSorting();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        MatrixStack stack = RenderSystem.getModelViewStack();

        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, 1F, 0F, 1F, -1F, 1F), VertexSorter.BY_Z);
        stack.push();
        stack.loadIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, resolveTexture);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(0F, 0F, 0F).texture(0F, 0F).next();
        builder.vertex(1F, 0F, 0F).texture(1F, 0F).next();
        builder.vertex(1F, 1F, 0F).texture(1F, 1F).next();
        builder.vertex(0F, 1F, 0F).texture(0F, 1F).next();
        BufferRenderer.drawWithGlobalProgram(builder.end());

        if (depthTest) RenderSystem.enableDepthTest();
        if (cull) RenderSystem.enableCull();

        stack.pop();
        /* Put back the applied model-view as it was (it isn't necessarily the stack's top) */
        stack.push();
        stack.peek().getPositionMatrix().set(modelView);
        RenderSystem.applyModelViewMatrix();
        stack.pop();
        RenderSystem.setProjectionMatrix(projection, sorter);
    }

    private static int wantedSamples()
    {
        int mode = BBSSettings.gizmoAntialiasing == null ? 0 : BBSSettings.gizmoAntialiasing.get();
        int wanted = SAMPLES[Math.max(0, Math.min(mode, SAMPLES.length - 1))];

        if (maxSamples < 0)
        {
            maxSamples = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
        }

        return Math.min(wanted, maxSamples);
    }

    private static boolean ensure(int w, int h, int wantedSamples)
    {
        if (msaaFbo != 0 && width == w && height == h && samples == wantedSamples)
        {
            return true;
        }

        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        delete();

        width = w;
        height = h;
        samples = wantedSamples;

        msaaColor = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, msaaColor);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, GL11.GL_RGBA8, w, h);

        msaaDepth = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, msaaDepth);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, GL30.GL_DEPTH_COMPONENT24, w, h);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        msaaFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, msaaFbo);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, msaaColor);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, msaaDepth);

        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

        resolveTexture = GlStateManager._genTexture();
        RenderSystem.bindTexture(resolveTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        RenderSystem.bindTexture(0);

        resolveFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, resolveFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, resolveTexture, 0);

        complete &= GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        if (!complete)
        {
            /* The driver can't do it: overlays go back to drawing straight, for the session */
            LoggerFactory.getLogger(AntialiasedLayer.class).warn("Overlay anti-aliasing is unavailable ({}x MSAA, {}x{}); drawing overlays without it.", samples, w, h);
            delete();
            failed = true;
        }

        return complete;
    }

    private static void delete()
    {
        if (msaaFbo != 0) GL30.glDeleteFramebuffers(msaaFbo);
        if (resolveFbo != 0) GL30.glDeleteFramebuffers(resolveFbo);
        if (msaaColor != 0) GL30.glDeleteRenderbuffers(msaaColor);
        if (msaaDepth != 0) GL30.glDeleteRenderbuffers(msaaDepth);
        if (resolveTexture != 0) GlStateManager._deleteTexture(resolveTexture);

        msaaFbo = resolveFbo = msaaColor = msaaDepth = resolveTexture = 0;
        width = height = samples = 0;
    }

    /** The orbit ball and other UI-space overlays: their pixel rectangle, as {@code UIUtils.viewportArea} maps it. */
    public static boolean beginUI(float x, float y, float w, float h)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = (float) mc.getWindow().getScaleFactor();
        int px = (int) Math.floor(x * scale);
        int py = (int) Math.floor(mc.getWindow().getFramebufferHeight() - (y + h) * scale);
        int pw = (int) Math.ceil((x + w) * scale) - px;
        int ph = (int) Math.ceil(mc.getWindow().getFramebufferHeight() - y * scale) - py;

        return begin(px, py, pw, ph);
    }
}
