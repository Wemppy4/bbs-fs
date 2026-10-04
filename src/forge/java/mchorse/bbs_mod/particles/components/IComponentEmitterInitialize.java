package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public interface IComponentEmitterInitialize extends IComponentBase
{
    public void apply(ParticleEmitter emitter);
}