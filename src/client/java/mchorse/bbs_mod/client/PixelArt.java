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
     * ShaderPrograms and GameRenderer has no get*Program left to inject into, so ui_scale keeps
     * quantising to whole steps. Re-port as a pipeline variant chosen while {@link #isDrawingUI()}.
     *
     * The GLSL itself is NOT in this branch: assets/bbs/shaders/core/pixelart* and the
     * shaders/include/bbs_pixelart.glsl they share came over with the merge and were taken back out,
     * because vanilla's ShaderLoader eagerly resolves every shipped shader's imports at reload and
     * `#moj_import <bbs_pixelart.glsl>` resolves in the MINECRAFT namespace (Identifier.of(name) with
     * "shaders/include/" prefixed — checked against ShaderLoader$1.loadImport), so a BBS-namespace
     * include NPEs the whole resource reload and leaves the game on a black screen. 1.21.1 got away
     * with it through BBSShaders' own ProxyResourceFactory, which the port has no use for. Bring them
     * back with `git checkout 1.21.1 -- src/main/resources/assets/bbs/shaders`, and give the include
     * an explicit namespace (`<bbs:...>`, like every other BBS shader here already does) or make the
     * import relative. */
}
