package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public interface IComponentParticleInitialize extends IComponentBase
{
    public void apply(ParticleEmitter emitter, Particle particle);
}