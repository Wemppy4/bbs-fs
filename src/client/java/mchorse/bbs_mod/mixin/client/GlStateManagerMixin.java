package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import mchorse.bbs_mod.graphics.AntialiasedLayer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside an {@link AntialiasedLayer} alpha accumulates as coverage
 * ({@code ONE, ONE_MINUS_SRC_ALPHA}) whatever blend function the overlay asks for, so
 * the layer holds premultiplied colour and composites over the frame exactly once.
 */
@Mixin(value = GlStateManager.class, remap = false)
public class GlStateManagerMixin
{
    @Inject(method = "_blendFunc", at = @At("HEAD"), cancellable = true)
    private static void bbs$layerBlendFunc(int srcFactor, int dstFactor, CallbackInfo info)
    {
        if (AntialiasedLayer.isActive())
        {
            GlStateManager._blendFuncSeparate(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            info.cancel();
        }
    }

    @ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private static int bbs$layerSrcAlpha(int srcFactorAlpha)
    {
        return AntialiasedLayer.isActive() ? GL11.GL_ONE : srcFactorAlpha;
    }

    @ModifyVariable(method = "_blendFuncSeparate", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private static int bbs$layerDstAlpha(int dstFactorAlpha)
    {
        return AntialiasedLayer.isActive() ? GL11.GL_ONE_MINUS_SRC_ALPHA : dstFactorAlpha;
    }
}
