package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

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
     * Tell Iris whether the main render target is bound.
     *
     * <p>It gates {@code IrisRenderingPipeline.shouldOverrideShaders()}, which is
     * {@code isRenderingWorld && isMainBound}, and that in turn decides
     * {@code MixinCompiledShaderProgram.iris$shouldSkipThis()}:
     *
     * <pre>return !(this instanceof ExtendedShader) &amp;&amp; !(this instanceof FallbackShader) &amp;&amp; shouldOverrideShaders();</pre>
     *
     * <p>Read it plainly: while the world is being drawn into the main target, Iris SKIPS every
     * program that is not one of its own. BBS draws its forms with its own programs, so under a
     * shaderpack the draws were thrown away — some forms gone, the ones that ride vanilla layers still
     * there, which is what "some show, some look transparent" was.
     *
     * <p>Iris keeps this flag itself from every render-target bind (MixinRenderTarget), so it is only
     * ever ours for the span we set it.
     */
    public static void setMainBound(boolean bound)
    {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

        if (pipeline != null)
        {
            pipeline.setIsMainBound(bound);
        }
    }

    /**
     * Tell Iris which of the shaderpack's programs a BBS pipeline should be drawn with.
     *
     * <p>The substitution itself is dynamic — {@code MixinShaderManager_Overrides} consults the
     * assignment map every time a pass resolves its program — so assigning at registration time works
     * no matter when the pack loads. Iris throws if a pipeline is assigned twice, so assignment
     * happens once per pipeline, at the point each is registered.
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
