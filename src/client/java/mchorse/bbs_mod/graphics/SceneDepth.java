package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The world's depth, kept past the world pass for the interface: Minecraft clears the depth
 * before it draws the screen, so overlays drawn there (the camera's frustum, the motion path)
 * couldn't otherwise tell what stands in front of them. {@link #capture} copies the main
 * framebuffer's depth at the end of the editor's world pass along with the projection it was
 * drawn with, which turns a stored depth back into a distance from the eye, and the viewport it
 * was drawn into. The film editor draws its world into a framebuffer of the export's size and
 * shows it scaled in the preview, so an overlay maps its pixel through its own frame (the
 * world's whole picture on screen) onto that viewport, not straight onto the texture.
 */
public class SceneDepth
{
    /** A capture older than this (no world drawn since) no longer stands for the frame. */
    private static final long FRESH_MS = 250L;

    private static int fbo;
    private static int texture;
    private static int width;
    private static int height;
    private static long capturedAt;
    private static boolean failed;
    private static boolean warned;

    /* The world projection's depth terms: distance = b / (ndcZ + a) */
    private static float a;
    private static float b;

    /* Where in the texture the world was drawn, as fractions of it */
    private static final float[] viewport = new float[4];
    private static final int[] glViewport = new int[4];

    /** Copy the main framebuffer's depth now; call at the end of a world pass. */
    public static void capture()
    {
        if (failed)
        {
            return;
        }

        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        int w = main.textureWidth;
        int h = main.textureHeight;

        if (w <= 0 || h <= 0)
        {
            return;
        }

        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        if (!ensure(w, h))
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

            return;
        }

        /* Earlier errors aren't this copy's */
        while (GL11.glGetError() != GL11.GL_NO_ERROR);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        int error = GL11.glGetError();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        if (error != GL11.GL_NO_ERROR)
        {
            /* A depth of another format (a shader pack's, say): no fresh capture, so the
             * overlays stay on top this frame; it may well copy again once that's gone. */
            if (!warned)
            {
                org.slf4j.LoggerFactory.getLogger(SceneDepth.class).warn("Scene depth can't be copied (GL error {}); overlays won't hide behind the world.", error);
                warned = true;
            }

            return;
        }

        Matrix4f projection = RenderSystem.getProjectionMatrix();

        a = projection.m22();
        b = projection.m32();

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, glViewport);
        viewport[0] = glViewport[0] / (float) w;
        viewport[1] = glViewport[1] / (float) h;
        viewport[2] = glViewport[2] / (float) w;
        viewport[3] = glViewport[3] / (float) h;

        capturedAt = System.currentTimeMillis();
    }

    /** Whether a capture stands for the frame being drawn. */
    public static boolean isFresh()
    {
        return texture != 0 && System.currentTimeMillis() - capturedAt < FRESH_MS;
    }

    public static int getTexture()
    {
        return texture;
    }

    /** Where the world was drawn in the texture (x, y, width, height as fractions of it, from the bottom left). */
    public static float[] getViewport()
    {
        return viewport;
    }

    public static float getA()
    {
        return a;
    }

    public static float getB()
    {
        return b;
    }

    private static boolean ensure(int w, int h)
    {
        if (texture != 0 && width == w && height == h)
        {
            return true;
        }

        delete();

        width = w;
        height = h;

        /* The same storage the main framebuffer's depth has, which a depth blit requires */
        texture = GlStateManager._genTexture();
        RenderSystem.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        RenderSystem.bindTexture(0);

        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

        if (complete)
        {
            /* Far everywhere until the first copy lands */
            boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

            RenderSystem.depthMask(true);
            GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1F});
            RenderSystem.depthMask(depthWrite);
        }
        else
        {
            org.slf4j.LoggerFactory.getLogger(SceneDepth.class).warn("Scene depth capture is unavailable; overlays won't hide behind the world.");
            delete();
            failed = true;
        }

        return complete;
    }

    private static void delete()
    {
        if (fbo != 0) GL30.glDeleteFramebuffers(fbo);
        if (texture != 0) GlStateManager._deleteTexture(texture);

        fbo = texture = 0;
        width = height = 0;
    }
}
