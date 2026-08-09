package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSSettings;

/**
 * Switchboard for the pixel art seam smoothing.
 *
 * BBS's ui_scale is a float, and at a fractional value nearest sampling
 * duplicates some rows of texels twice and some once, which is what makes text
 * and icons look ragged. The shaders behind this (see the comment in
 * assets/bbs/shaders/include/bbs_pixelart.glsl) spread the seam between texels
 * over one screen pixel and collapse back into plain nearest sampling at an
 * integer scale.
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

    /* TODO(1.21.11 render): the program picker. 1.21.1 answered "which program draws this text" with
     * BBSShaders.getPixelArtText[Intensity]Program() and let GameRendererMixin hand it to
     * GameRenderer.getRenderTypeText*Program. On 1.21.11 BBSShaders builds RenderPipelines rather than
     * ShaderPrograms, and GameRenderer has no get*Program left to inject into, so the shaders shipped
     * with this merge (assets/bbs/shaders/core/pixelart*) are on disk but unused, and ui_scale keeps
     * quantising to whole steps. Re-port as a pipeline variant chosen while {@link #isDrawingUI()}. */
}
