package mchorse.bbs_mod.ui.utils;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Dual Kawase background blur, adapted from quaIett's bbs-refreshed-addon.
 * See assets/bbs/licenses/bbs-refreshed-addon.txt for the MIT license.
 * Downsample with five taps, then upsample with eight taps using linear filtering.
 * KawasePass on 1.20.4 requires programs in the minecraft namespace.
 */
public class InterfaceBlur
{
    private static final String DOWN = "bbs_kawase_down";
    private static final String UP = "bbs_kawase_up";

    private static final int MAX_LEVELS = 5;
    /** A level smaller than this on either side is not worth a pass (and would smear the edges). */
    private static final int MIN_SIZE = 16;

    /** Offsets the {@link #SIGMA} table was sampled at. */
    private static final float[] OFFSETS = {0.5F, 1.0F, 1.5F, 2.0F, 2.5F, 3.0F};
    /** Offsets past this start to show the tap pattern; a stronger blur takes one more level instead. */
    private static final float MAX_CLEAN_OFFSET = 1.5F;
    private static final float MIN_OFFSET = 0.25F;

    /**
     * Blur strength per level count (rows, 1..5) and offset (columns, {@link #OFFSETS}): the standard deviation
     * along one axis of the whole chain's impulse response, in full-resolution pixels. Simulated with bilinear
     * sampling and averaged over two impulse phases.
     */
    private static final float[][] SIGMA = {
        {0.96F, 1.44F, 2.01F, 2.61F, 3.14F, 3.71F},
        {2.14F, 3.23F, 4.50F, 5.85F, 7.04F, 8.35F},
        {4.39F, 6.61F, 9.33F, 11.98F, 14.34F, 17.02F},
        {8.83F, 13.31F, 18.95F, 24.10F, 28.75F, 34.08F},
        {17.68F, 26.65F, 38.17F, 48.27F, 57.48F, 68.07F},
    };

    /** chain[0] is main (not owned); chain[i] is main downsampled i times. */
    private static Framebuffer[] chain;
    /** down[i]: chain[i] into chain[i + 1]; up[i]: chain[i + 1] into chain[i]. */
    private static KawasePass[] down;
    private static KawasePass[] up;
    private static int levels;
    private static int width;
    private static int height;

    /** A failed shader is reported once, rather than once per frame. */
    private static boolean broken;
    private static boolean applied;

    public static void beginFrame()
    {
        applied = false;
    }

    /** Blur once per frame, retaining the existing background and overlay settings. */
    public static void apply()
    {
        if (applied || broken || !BBSSettings.interfaceBlur.get())
        {
            return;
        }

        applied = true;
        render(BBSSettings.interfaceBlurRadius.get());
    }

    /** An overlay over a dashboard panel may blur the panel separately from the world. */
    public static void applyUnder()
    {
        apply();
        applied = false;
    }

    private static void render(int radius)
    {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();

        if (chain == null || chain[0] != main || main.framebufferTextureWidth != width || main.framebufferTextureHeight != height)
        {
            if (!rebuild(mc, main))
            {
                main.bindFramebuffer(true);

                return;
            }
        }

        /* BBS's box of half-width R is 2R + 1 wide: sigma^2 = ((2R + 1)^2 - 1) / 12 */
        float target = (float) Math.sqrt(radius * (radius + 1) / 3D);

        int n = pickLevels(target);
        float offset = pickOffset(n, target);

        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int mainFilter = main.framebufferFilter;

        try
        {
            /* Blur only changes color: the passes clear their output, and that must not wipe main's depth */
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);

            main.setFramebufferFilter(GL11.GL_LINEAR);

            for (int i = 0; i < n; i++)
            {
                run(down[i], offset);

                if (i == 0)
                {
                    main.setFramebufferFilter(mainFilter);
                }
            }

            for (int i = n - 1; i >= 0; i--)
            {
                run(up[i], offset);
            }
        }
        finally
        {
            if (main.framebufferFilter != mainFilter)
            {
                main.setFramebufferFilter(mainFilter);
            }

            /* KawasePass leaves GL_LEQUAL behind, but BBS paints its UI with GL_ALWAYS */
            main.bindFramebuffer(true);
            GlStateManager.depthMask(depthMask);
            GlStateManager.depthFunc(depthFunction);

            if (depthTest)
            {
                GlStateManager.enableDepth();
            }
            else
            {
                GlStateManager.disableDepth();
            }

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    private static void run(KawasePass pass, float offset)
    {
        pass.setOffset(offset);
        pass.render();
    }

    /** The fewest levels that reach {@code target} without pushing the offset past {@link #MAX_CLEAN_OFFSET}. */
    private static int pickLevels(float target)
    {
        for (int n = 1; n < levels; n++)
        {
            if (sigma(n, MAX_CLEAN_OFFSET) >= target)
            {
                return n;
            }
        }

        return levels;
    }

    /** Invert the (piecewise linear) sigma row of {@code n} levels; extrapolates past either end of the table. */
    private static float pickOffset(int n, float target)
    {
        float[] row = SIGMA[n - 1];
        int last = OFFSETS.length - 1;
        int i = 0;

        while (i < last - 1 && row[i + 1] < target)
        {
            i++;
        }

        float t = (target - row[i]) / (row[i + 1] - row[i]);
        float offset = OFFSETS[i] + t * (OFFSETS[i + 1] - OFFSETS[i]);

        return Math.max(MIN_OFFSET, Math.min(OFFSETS[last], offset));
    }

    private static float sigma(int n, float offset)
    {
        float[] row = SIGMA[n - 1];

        for (int i = 0; i < OFFSETS.length - 1; i++)
        {
            if (offset <= OFFSETS[i + 1])
            {
                float t = (offset - OFFSETS[i]) / (OFFSETS[i + 1] - OFFSETS[i]);

                return row[i] + t * (row[i + 1] - row[i]);
            }
        }

        return row[row.length - 1];
    }

    /** The chain holds targets sized off the screen, so a resized window means a new one. */
    private static boolean rebuild(Minecraft mc, Framebuffer main)
    {
        close();

        try
        {
            int w = main.framebufferTextureWidth;
            int h = main.framebufferTextureHeight;
            int count = 0;

            while (count < MAX_LEVELS && w / 2 >= MIN_SIZE && h / 2 >= MIN_SIZE)
            {
                w /= 2;
                h /= 2;
                count++;
            }

            if (count == 0)
            {
                return false;
            }

            chain = new Framebuffer[count + 1];
            down = new KawasePass[count];
            up = new KawasePass[count];
            chain[0] = main;

            w = main.framebufferTextureWidth;
            h = main.framebufferTextureHeight;

            for (int i = 1; i <= count; i++)
            {
                w /= 2;
                h /= 2;

                Framebuffer target = new Framebuffer(w, h, false);

                target.setFramebufferFilter(GL11.GL_LINEAR);
                chain[i] = target;
            }

            for (int i = 0; i < count; i++)
            {
                down[i] = pass(mc, DOWN, chain[i], chain[i + 1]);
                up[i] = pass(mc, UP, chain[i + 1], chain[i]);
            }

            levels = count;
            width = main.framebufferTextureWidth;
            height = main.framebufferTextureHeight;

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

    private static KawasePass pass(Minecraft mc, String program, Framebuffer input, Framebuffer output) throws Exception
    {
        return new KawasePass(program, input, output);
    }

    private static void close()
    {
        if (down != null)
        {
            for (int i = 0; i < down.length; i++)
            {
                if (down[i] != null) down[i].close();
                if (up[i] != null) up[i].close();
            }
        }

        if (chain != null)
        {
            /* chain[0] is main, which belongs to the game */
            for (int i = 1; i < chain.length; i++)
            {
                if (chain[i] != null) chain[i].deleteFramebuffer();
            }
        }

        chain = null;
        down = null;
        up = null;
        levels = 0;
    }
}
