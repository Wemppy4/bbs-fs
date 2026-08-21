package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;

/**
 * Switchboard for the pixel art seam smoothing.
 *
 * BBS's ui_scale is a float, and at a fractional value nearest sampling
 * duplicates some rows of texels twice and some once, which is what makes text
 * and icons look ragged. The shaders behind this (see the comment in
 * assets/bbs/shaders/include/bbs_pixelart.glsl) spread the seam between texels
 * over one screen pixel and collapse back into plain nearest sampling at an
 * integer scale.
 *
 * <p>1.21.11 note: the GUI is two-phase. BBS's own drawing only RECORDS elements, and vanilla
 * composites them after {@code Screen.render} has returned — which is why the two questions below
 * are asked at different moments and answered differently. Quads are chosen while recording (the
 * batcher passes the pipeline itself); text is chosen while compositing, where nothing is left of
 * "BBS is drawing" but the screen that is open.</p>
 */
public class PixelArt
{
    private static boolean drawingUI;

    public static boolean isEnabled()
    {
        return BBSSettings.pixelArtSmoothing.get();
    }

    /**
     * Vanilla's text programs are shared with the text drawn in the world, so
     * they may only be swapped while BBS's own UI is the thing on screen.
     */
    public static void setDrawingUI(boolean drawing)
    {
        drawingUI = drawing;
    }

    /** Whether BBS's own interface is what is currently being drawn. */
    public static boolean isDrawingUI()
    {
        return drawingUI;
    }

    /**
     * Pipeline for a textured UI quad, or null to leave the caller's own choice alone (smoothing off,
     * or the shader failed to compile).
     */
    public static RenderPipeline getTexturedPipeline()
    {
        if (!drawingUI || !isEnabled())
        {
            return null;
        }

        return BBSShaders.getPixelArtProgram();
    }

    /**
     * Same, but a texture the user asked to be filtered linearly or mipmapped (the toggles in the
     * texture picker) keeps GL's own filtering — the smoothing reads texels of level 0 directly, which
     * would both render those toggles meaningless and lean on a complete mipmap pyramid.
     */
    public static RenderPipeline getTexturedPipeline(Texture texture)
    {
        if (texture != null && (texture.isLinear() || texture.isMipmap()))
        {
            return null;
        }

        return getTexturedPipeline();
    }

    /**
     * The smoothing counterpart of the GUI text pipeline a glyph is about to be drawn with, or null to
     * leave vanilla's in charge.
     *
     * <p>Asked during the GUI composite, long after {@link #isDrawingUI()} has been put down, so the
     * gate here is the screen that is open. That is not a weaker test than 1.21.1's: BBS overrides the
     * window's scale factor for the whole frame a UIScreen is up, so every glyph composited in that
     * frame — the HUD's included — is drawn at the same fractional scale and wants the same treatment.
     * Text in the world never reaches this path at all; it is not part of the GUI state.</p>
     */
    public static RenderPipeline getTextPipeline(RenderPipeline vanilla)
    {
        if (!isEnabled() || !(MinecraftClient.getInstance().currentScreen instanceof UIScreen))
        {
            return null;
        }

        return BBSShaders.getPixelArtTextProgram(vanilla);
    }
}
