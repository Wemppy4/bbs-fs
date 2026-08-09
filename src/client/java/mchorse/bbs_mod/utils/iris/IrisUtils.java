package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

/**
 * Everything BBS asks of Iris, in one class that is only ever touched when the Iris mod is actually
 * present ({@code BBSRendering.iris}). Loading it without Iris on the classpath would throw, so every
 * caller has to go through {@link mchorse.bbs_mod.client.BBSRendering}, which gates on that flag —
 * class loading is lazy, so a gated call site never resolves these names on a plain install.
 *
 * <p>Deliberately thin. The 1.21.1 integration also carried PBR texture wrappers, custom shader
 * uniforms driven by BBS curves, and the shaderpack option menus mirrored inside BBS's own UI; those
 * lean on Iris internals the 1.21.5+ rewrite reshaped and stay decoupled for now.
 */
public class IrisUtils
{
    public static boolean isShaderPackEnabled()
    {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowPass()
    {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    /**
     * Tell Iris which of the shaderpack's programs a BBS pipeline should be drawn with.
     *
     * <p>Iris keeps a map from {@code RenderPipeline} to shaderpack program and substitutes the pack's
     * program for anything in it; a pipeline that is not there gets no substitution, so with a pack
     * loaded the draw never reaches the pack's G-buffers and the geometry simply is not in the frame.
     * That is what made every form — replays included — disappear the moment shaders were turned on.
     *
     * <p>1.21.1 solved the same problem from the other side: it told Iris the main target was unbound
     * ({@code WorldRenderingPipeline.setIsMainBound(false)}) so Iris would neither override BBS's own
     * program nor mask its writes, and BBS drew with its own GLSL. Assigning is the supported way now,
     * and the better one — the forms come out lit, shadowed and fogged by the pack like any entity,
     * instead of being a flat patch pasted into a shaded world.
     *
     * <p>Iris throws if a pipeline is assigned twice, so assignment happens once per pipeline, at the
     * point each is registered.
     */
    public static void assignPipeline(RenderPipeline pipeline, BBSRendering.IrisProgramKind kind)
    {
        IrisApi.getInstance().assignPipeline(pipeline, translate(kind));
    }

    /**
     * Which of the pack's programs a kind means. Chosen to follow the VERTEX FORMAT, the way vanilla's
     * own pipelines map: a pack's entity program reads colour, both light coordinates and a normal, so
     * handing it geometry that carries only position and a colour would have it read attributes that
     * are not there. Iris guesses this itself for anything shaped like an entity draw ("Found *decent*
     * program match ... ENTITIES_ALPHA" in the log) and gives up on the plainer formats ("Missing
     * program ... in override list") — those are the ones that have to be named here.
     */
    private static IrisProgram translate(BBSRendering.IrisProgramKind kind)
    {
        return switch (kind)
        {
            case ENTITY -> IrisProgram.ENTITIES;
            case ENTITY_TRANSLUCENT -> IrisProgram.ENTITIES_TRANSLUCENT;
            case PARTICLE -> IrisProgram.PARTICLES;
            case TEXTURED -> IrisProgram.TEXTURED;
            case LINES -> IrisProgram.LINES;
            case BASIC -> IrisProgram.BASIC;
        };
    }
}
