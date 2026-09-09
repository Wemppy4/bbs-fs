package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin
{
    @ModifyConstant(method = "renderCelestialBodies", constant = @Constant(floatValue = -90F))
    private float rotateSunHorizontally(float rotation)
    {
        return rotation + BBSRendering.getSunHorizontalRotation();
    }
}
