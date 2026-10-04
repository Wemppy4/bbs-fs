package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public interface IComponentParticleUpdate extends IComponentBase
{
    public void update(ParticleEmitter emitter, Particle particle);
}