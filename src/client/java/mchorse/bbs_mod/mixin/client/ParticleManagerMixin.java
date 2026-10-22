package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.particles.vanilla.HeldParticles;
import mchorse.bbs_mod.particles.vanilla.IHeldParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the particles a form let into the world on that form's time (see
 * {@link HeldParticles}): they don't tick while it stands still, and are drawn
 * where it stands between two ticks rather than where the world does.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin
{
    @Inject(method = "tick", at = @At("HEAD"))
    private void bbs$advanceHeldClock(CallbackInfo info)
    {
        HeldParticles.advance();
    }

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void bbs$holdStoppedParticle(Particle particle, CallbackInfo info)
    {
        HeldParticles holder = ((IHeldParticle) particle).bbs$getHolder();

        if (holder != null && holder.isStopped())
        {
            info.cancel();
        }
    }

    @WrapOperation(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"))
    private void bbs$drawHeldParticle(Particle particle, VertexConsumer consumer, Camera camera, float tickDelta, Operation<Void> original)
    {
        HeldParticles holder = ((IHeldParticle) particle).bbs$getHolder();

        if (holder == null)
        {
            original.call(particle, consumer, camera, tickDelta);

            return;
        }

        /* Taken back by its form: gone at once, not when the manager clears it
         * out a tick later (two, for one let in during the same tick) */
        if (!particle.isAlive())
        {
            return;
        }

        original.call(particle, consumer, camera, holder.isStopped() ? holder.getTransition() : tickDelta);
    }
}
