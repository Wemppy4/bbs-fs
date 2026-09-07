package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * TEMPORARY. Dumps what a form nested in a framebuffer form actually draws under - the program
 * that ended up bound, the light directions, the GL state, and what colour landed in the buffer.
 * One burst a second, only while the framebuffer form renders. Delete once the darkening is
 * understood; the whole thing is this file plus the {@code logging} checks that call into it.
 */
public class FramebufferDebug
{
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final long PERIOD = 1000L;

    /** True for the length of one framebuffer render that is being logged. */
    public static boolean logging;

    private static long last;

    public static boolean begin()
    {
        long now = System.currentTimeMillis();

        logging = now - last >= PERIOD;

        if (logging)
        {
            last = now;
        }

        return logging;
    }

    public static void end()
    {
        logging = false;
    }

    public static void log(String tag, String line)
    {
        LOGGER.info("[BBS FB] {}: {}", tag, line);
    }

    /** Whether the pack says it is shading right now, and the two questions it is made of. */
    public static String iris()
    {
        return "irisPack=" + BBSRendering.isIrisShadersEnabled()
            + " irisShadingThisDraw=" + BBSRendering.isIrisWorldShadersEnabled()
            + " renderingWorld=" + BBSRendering.isRenderingWorld();
    }

    /** Everything that scales a fragment's brightness before it reaches the buffer. */
    public static String lights()
    {
        Vector3f l0 = RenderSystem.shaderLightDirections[0];
        Vector3f l1 = RenderSystem.shaderLightDirections[1];
        float[] modulator = RenderSystem.getShaderColor();

        return "light0=" + vec(l0) + " light1=" + vec(l1)
            + " colorModulator=[" + modulator[0] + ", " + modulator[1] + ", " + modulator[2] + ", " + modulator[3] + "]";
    }

    public static String glState()
    {
        return "cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE) + "/" + name(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE))
            + " frontFace=" + name(GL11.glGetInteger(GL11.GL_FRONT_FACE))
            + " blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
            + "/" + name(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)) + "," + name(GL11.glGetInteger(GL14.GL_BLEND_DST_RGB))
            + "/" + name(GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)) + "," + name(GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA))
            + " depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + "/" + name(GL11.glGetInteger(GL11.GL_DEPTH_FUNC))
            + " depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    }

    public static String shader(ShaderProgram program)
    {
        return program == null ? "null" : program.getName();
    }

    public static String texture(Texture texture)
    {
        if (texture == null)
        {
            return "null";
        }

        texture.bind();

        return "id=" + texture.id + " " + texture.width + "x" + texture.height
            + " min=" + name(texture.getParameter(GL11.GL_TEXTURE_MIN_FILTER))
            + " mag=" + name(texture.getParameter(GL11.GL_TEXTURE_MAG_FILTER));
    }

    /**
     * The brightest texel in the middle of the framebuffer, straight out of GL. This is the one
     * line that splits the problem: a bright value here with a dark form on screen means the
     * buffer is fine and the quad showing it is what darkens.
     */
    public static String readCentre(Framebuffer framebuffer)
    {
        Texture texture = framebuffer.getMainTexture();
        int size = Math.min(16, Math.min(texture.width, texture.height));
        int x = Math.max(0, texture.width / 2 - size / 2);
        int y = Math.max(0, texture.height / 2 - size / 2);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer pixels = stack.malloc(size * size * 4);

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL30.glPixelStorei(GL30.GL_PACK_ROW_LENGTH, 0);
            GL11.glReadPixels(x, y, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            int br = 0, bg = 0, bb = 0, ba = 0;
            int best = -1;

            for (int i = 0; i < size * size; i++)
            {
                int r = pixels.get(i * 4) & 0xFF;
                int g = pixels.get(i * 4 + 1) & 0xFF;
                int b = pixels.get(i * 4 + 2) & 0xFF;
                int a = pixels.get(i * 4 + 3) & 0xFF;
                int sum = r + g + b;

                if (a > 0 && sum > best)
                {
                    best = sum;
                    br = r;
                    bg = g;
                    bb = b;
                    ba = a;
                }
            }

            return best < 0
                ? "brightest=(nothing opaque in the middle " + size + "x" + size + ")"
                : "brightest=rgba(" + br + ", " + bg + ", " + bb + ", " + ba + ")";
        }
    }

    private static String vec(Vector3f v)
    {
        return "(" + v.x + ", " + v.y + ", " + v.z + ")";
    }

    private static String name(int constant)
    {
        switch (constant)
        {
            case GL11.GL_NEAREST: return "NEAREST";
            case GL11.GL_LINEAR: return "LINEAR";
            case GL11.GL_FRONT: return "FRONT";
            case GL11.GL_BACK: return "BACK";
            case GL11.GL_FRONT_AND_BACK: return "FRONT_AND_BACK";
            case GL11.GL_CW: return "CW";
            case GL11.GL_CCW: return "CCW";
            case GL11.GL_ZERO: return "ZERO";
            case GL11.GL_ONE: return "ONE";
            case GL11.GL_SRC_ALPHA: return "SRC_ALPHA";
            case GL11.GL_ONE_MINUS_SRC_ALPHA: return "1-SRC_ALPHA";
            case GL11.GL_DST_ALPHA: return "DST_ALPHA";
            case GL11.GL_ONE_MINUS_DST_ALPHA: return "1-DST_ALPHA";
            case GL11.GL_SRC_COLOR: return "SRC_COLOR";
            case GL11.GL_ONE_MINUS_SRC_COLOR: return "1-SRC_COLOR";
            case GL11.GL_ALWAYS: return "ALWAYS";
            case GL11.GL_LEQUAL: return "LEQUAL";
            case GL11.GL_LESS: return "LESS";
            case GL11.GL_EQUAL: return "EQUAL";
            case GL11.GL_GEQUAL: return "GEQUAL";
            case GL11.GL_NEVER: return "NEVER";
            default: return String.valueOf(constant);
        }
    }
}
