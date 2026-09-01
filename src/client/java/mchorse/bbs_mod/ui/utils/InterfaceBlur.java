package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Blurs what is on screen under an overlay panel, on top of the dimming — the way the game's
 * own menus do it from 1.21 on. Two passes of vanilla's own blur program over the main
 * framebuffer, run at the moment the first overlay of the frame is about to paint its dim:
 * everything drawn so far, world and interface alike, is what gets blurred, and the overlay
 * lands sharp on top.
 *
 * <p>The passes are added by hand rather than read from a json, so the radius is a uniform
 * set every frame from the settings, not a number baked into a file.</p>
 *
 * <p>A tour wants the opposite for one place: everything blurred but the thing it points at.
 * For that the screen is copied aside first, and after the blur the place is painted back
 * from the copy, sharp.</p>
 *
 * <p>Once per frame, with one exception: a second overlay over the first would only blur the
 * blur, but the world under a panel and the panel under an overlay are two different pictures,
 * and each gets its own pass — see {@link #applyUnder()}.</p>
 */
public class InterfaceBlur
{
    private static final Identifier EFFECT = new Identifier("bbs", "shaders/post/interface_blur.json");

    private static PostEffectProcessor processor;
    private static PostEffectPass horizontal;
    private static PostEffectPass vertical;
    private static int width;
    private static int height;

    /** The screen as it was before the blur, for the place that has to stay sharp. */
    private static Framebuffer copy;

    /** Whether this frame was blurred already. */
    private static boolean applied;

    /** Set when the effect failed to build, so a broken shader costs one stack trace, not one per frame. */
    private static boolean broken;

    /** Called where the frame's interface rendering starts. */
    public static void beginFrame()
    {
        applied = false;
    }

    /** Blur the screen as it stands, unless it was blurred this frame already or the setting is off. */
    public static void apply()
    {
        apply(null, null);
    }

    /**
     * The world under a panel that paints its own background over it (morphing, the texture
     * manager). Blurred before the panel is drawn, and the frame is left open: an overlay that
     * comes up over the panel later blurs the panel in its turn — that is a different picture,
     * not the same one twice.
     */
    public static void applyUnder()
    {
        apply(null, null);
        applied = false;
    }

    /**
     * The same, keeping one place sharp — the one a tour points at. The place is given in
     * interface coordinates, the way the element that owns it reports them.
     */
    public static void apply(UIContext context, Area keep)
    {
        if (applied || broken || !BBSSettings.interfaceBlur.get())
        {
            return;
        }

        applied = true;

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();

        if (processor == null || main.textureWidth != width || main.textureHeight != height)
        {
            if (!rebuild(mc, main))
            {
                return;
            }
        }

        float radius = BBSSettings.interfaceBlurRadius.get();

        if (keep != null)
        {
            snapshot(main);
        }

        direct(horizontal, 1F, 0F, radius);
        direct(vertical, 0F, 1F, radius);

        processor.render(0F);

        /* The last pass leaves no framebuffer bound and the blur program's blend state behind;
         * the interface draws into the main one with the usual blending and texture unit */
        main.beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        if (keep != null && context != null)
        {
            restore(context, main, keep);
        }
    }

    private static void direct(PostEffectPass pass, float x, float y, float radius)
    {
        GlUniform dir = pass.getProgram().getUniformByName("BlurDir");
        GlUniform size = pass.getProgram().getUniformByName("Radius");

        if (dir != null)
        {
            dir.set(x, y);
        }

        if (size != null)
        {
            size.set(radius);
        }
    }

    /** The whole screen, as it stands, into the copy — a blit, so the main framebuffer is untouched. */
    private static void snapshot(Framebuffer main)
    {
        int w = main.textureWidth;
        int h = main.textureHeight;

        if (copy == null || copy.textureWidth != w || copy.textureHeight != h)
        {
            if (copy != null)
            {
                copy.delete();
            }

            copy = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.fbo);
    }

    /**
     * The kept place, painted back from the copy over the blurred screen. Interface units are
     * scaled to framebuffer pixels, and the framebuffer's rows run bottom-up, so the texture
     * is read upside down.
     */
    private static void restore(UIContext context, Framebuffer main, Area keep)
    {
        Window window = MinecraftClient.getInstance().getWindow();
        float sx = main.textureWidth / (float) window.getScaledWidth();
        float sy = main.textureHeight / (float) window.getScaledHeight();
        float u1 = keep.x * sx;
        float u2 = keep.ex() * sx;
        float v1 = main.textureHeight - keep.y * sy;
        float v2 = main.textureHeight - keep.ey() * sy;

        context.batcher.texturedBox(copy.getColorAttachment(), Colors.WHITE, keep.x, keep.y, keep.w, keep.h, u1, v1, u2, v2, main.textureWidth, main.textureHeight);
    }

    /**
     * The processor holds targets of the screen's size, so a resized window means a new one.
     * The json only names the intermediate target; the two passes are added here.
     */
    private static boolean rebuild(MinecraftClient mc, Framebuffer main)
    {
        close();

        try
        {
            processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT);

            Framebuffer swap = processor.getSecondaryTarget("swap");

            horizontal = processor.addPass("blur", main, swap);
            vertical = processor.addPass("blur", swap, main);
            processor.setupDimensions(main.textureWidth, main.textureHeight);

            width = main.textureWidth;
            height = main.textureHeight;

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            close();
            broken = true;

            return false;
        }
    }

    private static void close()
    {
        if (processor != null)
        {
            processor.close();
        }

        if (copy != null)
        {
            copy.delete();
        }

        processor = null;
        horizontal = null;
        vertical = null;
        copy = null;
    }
}
