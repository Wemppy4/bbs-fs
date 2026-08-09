package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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
    public static void assignPipeline(RenderPipeline pipeline, boolean translucent)
    {
        IrisApi.getInstance().assignPipeline(pipeline, translucent ? IrisProgram.ENTITIES_TRANSLUCENT : IrisProgram.ENTITIES);
    }

    /** Same, for the particle pipeline: the pack's particle program, not its entity one. */
    public static void assignParticlePipeline(RenderPipeline pipeline, boolean translucent)
    {
        IrisApi.getInstance().assignPipeline(pipeline, translucent ? IrisProgram.PARTICLES_TRANSLUCENT : IrisProgram.PARTICLES);
    }
}
