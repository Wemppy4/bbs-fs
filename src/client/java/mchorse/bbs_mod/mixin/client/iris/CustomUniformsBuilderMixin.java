package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Append BBS's curve uniforms to the pack's custom uniforms as they are built — before Iris resolves
 * locations for them ({@code assignTo}) and before it drops the ones no program uses ({@code optimise}),
 * so a curve variable that the rewritten source actually reads is carried like any of the pack's own.
 */
@Mixin(CustomUniforms.Builder.class)
public class CustomUniformsBuilderMixin
{
    @Inject(method = "build(Lnet/irisshaders/iris/uniforms/custom/CustomUniformFixedInputUniformsHolder;)Lnet/irisshaders/iris/uniforms/custom/CustomUniforms;", at = @At("RETURN"), remap = false)
    public void onBuild(CallbackInfoReturnable<CustomUniforms> info)
    {
        if (info.getReturnValue() instanceof CustomUniformsAccessor accessor)
        {
            IrisUtils.addUniforms(accessor.bbs$uniformOrder(), ShaderCurves.variableMap);
        }
    }
}
