package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CelestialUniforms.class)
public class CelestialUniformsMixin
{
    @ModifyConstant(method = {"getCelestialPosition", "getCelestialPositionInWorldSpace"}, constant = @Constant(floatValue = -90F), remap = false)
    private float rotateSunHorizontally(float rotation)
    {
        return rotation + BBSRendering.getSunHorizontalRotation();
    }
}
