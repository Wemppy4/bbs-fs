package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.IRenderStateEntityHolder;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Morph entry hook (1.21.11 re-port). The original player + mob morph entries
 * (PlayerEntityRendererMixin @Inject render HEAD; EntityRenderDispatcherMixin @WrapOperation of
 * EntityRenderer.render) both targeted the removed immediate-mode render(Entity, ...) signature. In
 * 1.21.2+ PlayerEntityRenderer inherits LivingEntityRenderer.render(state, matrices, queue, camera),
 * so a single HEAD-cancellable inject here covers BOTH players (Morph) and selector mobs
 * (SelectorOwner).
 *
 * <p>render() is a BUILD phase (it only enqueues into the OrderedRenderCommandQueue; the GL draw
 * happens later at flush), so the BBS immediate form pipeline cannot draw correctly here. Instead we
 * SNAPSHOT the camera-relative pose + suppress the vanilla render, then {@link MorphRenderer} draws
 * the queued forms in WorldRenderEvents.AFTER_ENTITIES (where the camera model-view is still active —
 * the same proven context as replay/film forms).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMorphMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbs$onRenderMorph(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo info)
    {
        if (!(state instanceof IRenderStateEntityHolder holder))
        {
            return;
        }

        Entity entity = holder.bbs$getRenderedEntity();

        if (entity == null)
        {
            return;
        }

        float tickDelta = holder.bbs$getRenderedTickDelta();

        if (entity instanceof AbstractClientPlayerEntity player)
        {
            int overlay = LivingEntityRenderer.getOverlay(state, 0F);

            if (MorphRenderer.collectPlayer(player, state.light, overlay, tickDelta))
            {
                info.cancel();
            }
        }
        else if (entity instanceof LivingEntity living)
        {
            float counter = ((LivingEntityRendererInvoker) (Object) this).bbs$getAnimationCounter(state);
            int overlay = LivingEntityRenderer.getOverlay(state, counter);

            if (MorphRenderer.collectLivingEntity(living, state.light, overlay, tickDelta))
            {
                info.cancel();
            }
        }
    }
}
