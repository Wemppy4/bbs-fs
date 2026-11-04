package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.client.TerrainCut;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisTerrainPass;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A shaderpack's terrain vertex shaders learn the orbit's wall cut (see {@link TerrainCut}).
 * The shadow passes keep the walls - their shadows stay where they were - and so does a pass
 * the pack gives a geometry or tessellation stage: the clip distance has to come from the last
 * stage before the rasterizer, and the vertex shader is not it there.
 */
@Mixin(IrisChunkProgramOverrides.class)
public class IrisChunkProgramOverridesMixin
{
    @Unique
    private boolean bbs$cutTerrain;

    @Inject(method = "createVertexShader", at = @At("HEAD"), remap = false)
    private void onCreateVertexShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline, CallbackInfoReturnable<?> info)
    {
        /* No switch: one over another mod's enum compiles to a helper class in this package,
         * and mixin packages can't hold classes of their own. */
        if (pass == IrisTerrainPass.GBUFFER_SOLID)
        {
            this.bbs$cutTerrain = pipeline.getTerrainSolidGeometryShaderSource().isEmpty() && pipeline.getTerrainSolidTessEvalShaderSource().isEmpty();
        }
        else if (pass == IrisTerrainPass.GBUFFER_CUTOUT)
        {
            this.bbs$cutTerrain = pipeline.getTerrainCutoutGeometryShaderSource().isEmpty() && pipeline.getTerrainCutoutTessEvalShaderSource().isEmpty();
        }
        else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT)
        {
            this.bbs$cutTerrain = pipeline.getTranslucentGeometryShaderSource().isEmpty() && pipeline.getTranslucentTessEvalShaderSource().isEmpty();
        }
        else
        {
            this.bbs$cutTerrain = false;
        }
    }

    @ModifyVariable(method = "createVertexShader", at = @At("STORE"), ordinal = 0, remap = false)
    private String onVertexSource(String source)
    {
        return this.bbs$cutTerrain ? TerrainCut.patchVertexShader(source) : source;
    }
}
