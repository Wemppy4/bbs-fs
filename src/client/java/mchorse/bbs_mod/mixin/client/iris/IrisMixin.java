package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.QueueMap;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * The lifetime of the curve variables: they describe ONE loaded pack, so both loading another pack and
 * turning shaders off have to drop them, or the curve picker keeps offering options of a pack that is
 * no longer there (and the clip writes uniforms nothing reads).
 */
@Mixin(Iris.class)
public class IrisMixin
{
    @Inject(method = "getShaderPackOptionQueue", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onGetShaderPackOptionQueue(CallbackInfoReturnable<Map<String, String>> info)
    {
        Map<String, String> returnValue = info.getReturnValue() == null ? null : new QueueMap<>(info.getReturnValue());

        info.setReturnValue(returnValue);
    }

    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"), remap = false)
    private static void onLoadExternalShaderpack(String name, CallbackInfoReturnable<Boolean> info)
    {
        ShaderCurves.reset();
    }

    @Inject(method = "setShadersDisabled", at = @At("HEAD"), remap = false)
    private static void onSetShadersDisabled(CallbackInfo info)
    {
        ShaderCurves.reset();
    }
}
