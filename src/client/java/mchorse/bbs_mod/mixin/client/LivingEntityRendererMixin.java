package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.mob.MobRenderContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", ordinal = 0, shift = At.Shift.AFTER))
    public void onSetAngles(LivingEntity livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info)
    {
        MobRenderContext context = MobRenderContext.current();

        if (context != null)
        {
            context.applyPose();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(LivingEntity livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info)
    {
        MobRenderContext context = MobRenderContext.current();

        if (context != null)
        {
            context.restorePose();
        }
    }
}
