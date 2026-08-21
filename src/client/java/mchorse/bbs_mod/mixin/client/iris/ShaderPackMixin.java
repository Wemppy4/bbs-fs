package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand the pack's properties to {@link IrisUtils} the moment they are read.
 *
 * <p>Timing is the whole point: this same constructor goes on to build the program sets, which
 * preprocess every GLSL source, and {@code ShaderCurves} needs the pack's {@code sliders} list by then.
 * The field is private, final and has no getter, so the write itself is the hook — one PUTFIELD in the
 * whole class, hence no ordinal.</p>
 */
@Mixin(ShaderPack.class)
public class ShaderPackMixin
{
    @Shadow(remap = false) @Final private ShaderProperties shaderProperties;

    @Inject(
        method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/irisshaders/iris/shaderpack/ShaderPack;shaderProperties:Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false,
        require = 0
    )
    private void afterShaderPropertiesRead(CallbackInfo ci)
    {
        IrisUtils.setShaderProperties(this.shaderProperties);
    }
}
