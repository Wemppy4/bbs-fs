package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's public {@code addParticle} both creates the particle AND files it
 * into the world's particle manager. The form editor's preview needs only the
 * first half: a particle it owns, ticks and draws itself, which must never show
 * up in the world behind the interface.
 */
@Mixin(ParticleManager.class)
public interface ParticleManagerInvoker
{
    @Invoker("createParticle")
    public Particle bbs$createParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
}
