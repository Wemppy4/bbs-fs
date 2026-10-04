package mchorse.bbs_mod.particles.components.lifetime;

import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentLifetimeOnce extends ParticleComponentLifetime
{
    @Override
    public void update(ParticleEmitter emitter)
    {
        double time = this.activeTime.get();

        emitter.lifetime = (int) (time * 20);

        if (emitter.getAge() >= time)
        {
            emitter.stop();
        }
    }
}