package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import net.irisshaders.iris.compat.sodium.impl.vertex_format.entity_xhfp.EntityVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityVertex.class)
public class EntityVertexMixin
{
    @ModifyVariable(method = "write2", at = @At("HEAD"), ordinal = 0, remap = false)
    private static int onWrite2(int color)
    {
        return RecolorVertexConsumer.tintPackedABGR(color);
    }
}