package mchorse.bbs_mod.mixin.client.sodium;

import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ColorAttribute.class)
public class ColorAttributeMixin
{
    @ModifyVariable(method = "set", at = @At("HEAD"), ordinal = 0, remap = false)
    private static int onSet(int color)
    {
        return RecolorVertexConsumer.tintPackedABGR(color);
    }
}