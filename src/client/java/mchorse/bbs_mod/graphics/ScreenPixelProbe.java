package mchorse.bbs_mod.graphics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * One pixel of the finished frame, for the interface to look at — what the colour picker's
 * eyedropper takes its colour from.
 *
 * <p>It cannot simply be read where it is wanted. The interface in 1.21.11 is deferred: an element
 * only records its draws, and the whole picture is composited after the screen is done painting, so
 * at the moment the eyedropper paints, this frame's interface is not on the framebuffer yet. And a
 * bare {@code glReadPixels} would not find it there either — every render pass unbinds itself when
 * it closes, leaving the window's own back buffer as the read source, while the game draws into a
 * texture of its own.</p>
 *
 * <p>So the ask is left here, and answered once the interface really has been composited (see
 * {@code BBSRendering.onRenderAfterInterface}), out of the main framebuffer's colour texture
 * through a private framebuffer of our own — the same read-back the form picker does. The answer
 * is therefore a frame old, which at the speed a cursor moves is nothing.</p>
 */
public class ScreenPixelProbe
{
    /** Private read framebuffer the main colour texture is attached to; the mapped API has no 1-pixel read. */
    private static int fbo = -1;

    private static int requestX = -1;
    private static int requestY = -1;

    private static int sample;
    private static boolean sampled;

    /** Ask for the pixel at framebuffer coordinates, y counted from the bottom the way GL does. */
    public static void request(int x, int y)
    {
        requestX = x;
        requestY = y;
    }

    /** Whether anything has been read yet — false until the first ask has been answered. */
    public static boolean hasSample()
    {
        return sampled;
    }

    /** The last pixel read, opaque ARGB. */
    public static int getSample()
    {
        return sample;
    }

    /** Answer the ask, if there is one: the interface has just been composited into the framebuffer. */
    public static void fulfill()
    {
        if (requestX < 0)
        {
            return;
        }

        int x = requestX;
        int y = requestY;

        requestX = -1;
        requestY = -1;

        net.minecraft.client.gl.Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        if (framebuffer == null || framebuffer.getColorAttachment() == null
            || x < 0 || y < 0 || x >= framebuffer.textureWidth || y >= framebuffer.textureHeight)
        {
            return;
        }

        if (fbo < 0)
        {
            fbo = GL30.glGenFramebuffers();
        }

        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
            ((GlTexture) framebuffer.getColorAttachment()).getGlId(), 0);
        GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        try (MemoryStack stack = MemoryStack.stackPush(); PixelPackState pack = PixelPackState.push())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

            int r = (int) (floats.get(0) * 255F) & 0xFF;
            int g = (int) (floats.get(1) * 255F) & 0xFF;
            int b = (int) (floats.get(2) * 255F) & 0xFF;

            /* Whatever is on screen is opaque — the framebuffer's own alpha is the sky's, not a colour's. */
            sample = 0xFF000000 | (r << 16) | (g << 8) | b;
            sampled = true;
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous);
    }
}
