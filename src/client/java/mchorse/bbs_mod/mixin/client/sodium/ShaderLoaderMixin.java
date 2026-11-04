package mchorse.bbs_mod.mixin.client.sodium;

import mchorse.bbs_mod.client.TerrainCut;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium's terrain vertex shader learns the orbit's wall cut (see {@link TerrainCut}). */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin
{
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onGetShaderSource(Identifier name, CallbackInfoReturnable<String> info)
    {
        if (name.getNamespace().equals("sodium") && name.getPath().equals("blocks/block_layer_opaque.vsh"))
        {
            info.setReturnValue(TerrainCut.patchVertexShader(info.getReturnValue()));
        }
    }
}
