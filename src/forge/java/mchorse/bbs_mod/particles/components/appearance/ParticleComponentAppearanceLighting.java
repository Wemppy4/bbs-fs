package mchorse.bbs_mod.particles.components.appearance;

import mchorse.bbs_mod.particles.components.IComponentEmitterInitialize;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentAppearanceLighting extends ParticleComponentBase implements IComponentEmitterInitialize
{
    @Override
    public void apply(ParticleEmitter emitter)
    {
        emitter.lit = false;
    }

    @Override
    public boolean canBeEmpty()
    {
        return true;
    }
}