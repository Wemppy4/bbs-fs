package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the BBS mob-form pose to a queued model command.
 *
 * <p>On 1.21.1 the pose hook lived in {@code LivingEntityRenderer.render} right after its
 * immediate {@code setAngles} call. The 1.21.2+ command queue moved that call to FLUSH time:
 * {@link ModelCommandRenderer} calls {@code Model.setAngles(state)} when the command renders
 * (verified against the 1.21.11 bytecode), so any part mutation made at submit time would be
 * overwritten. This hook fires right after that setAngles and restores the parts when the
 * command is done, so the shared vanilla model instances stay clean.
 *
 * <p>Active only while {@code MobFormRenderer} is mid-flush ({@code currentPose != null});
 * the world's own dispatcher never runs inside that window, so vanilla mobs are unaffected.
 */
@Mixin(ModelCommandRenderer.class)
public class ModelCommandRendererMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setAngles(Ljava/lang/Object;)V", shift = At.Shift.AFTER)
    )
    private void bbs$applyMobPose(OrderedRenderCommandQueueImpl.ModelCommand<?> command, RenderLayer layer, VertexConsumer consumer, OutlineVertexConsumerProvider outline, VertexConsumerProvider.Immediate crumbling, CallbackInfo info)
    {
        MobFormRenderer.applyCurrentPose();
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        at = @At("TAIL")
    )
    private void bbs$restoreMobPose(OrderedRenderCommandQueueImpl.ModelCommand<?> command, RenderLayer layer, VertexConsumer consumer, OutlineVertexConsumerProvider outline, VertexConsumerProvider.Immediate crumbling, CallbackInfo info)
    {
        MobFormRenderer.restorePosedParts();
    }
}
