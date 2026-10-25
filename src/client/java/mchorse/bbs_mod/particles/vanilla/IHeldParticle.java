package mchorse.bbs_mod.particles.vanilla;

/**
 * A vanilla particle that knows which form let it into the world, if any
 * (see {@link HeldParticles}). Implemented on every particle by {@code ParticleMixin}.
 */
public interface IHeldParticle
{
    public HeldParticles bbs$getHolder();

    public void bbs$setHolder(HeldParticles holder);
}
