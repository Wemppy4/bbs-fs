package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL13;

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

        direct(horizontal, 1F, 0F, radius);
        direct(vertical, 0F, 1F, radius);

        processor.render(0F);

        /* The last pass leaves no framebuffer bound and the blur program's blend state behind;
         * the interface draws into the main one with the usual blending and texture unit */
        main.beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    /**
     * The world under a panel that paints its own background over it (morphing, the texture
     * manager). Blurred before the panel is drawn, and the frame is left open: an overlay that
     * comes up over the panel later blurs the panel in its turn — that is a different picture,
     * not the same one twice.
     */
    public static void applyUnder()
    {
        apply();
        applied = false;
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

        processor = null;
        horizontal = null;
        vertical = null;
    }
}
