package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public interface IComponentEmitterUpdate extends IComponentBase
{
    public void update(ParticleEmitter emitter);
}