package mchorse.bbs_mod.particles.components.shape;

import mchorse.bbs_mod.particles.components.shape.directions.ShapeDirectionVector;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentShapePoint extends ParticleComponentShapeBase
{
    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {
        particle.position.x = (float) this.offset[0].get();
        particle.position.y = (float) this.offset[1].get();
        particle.position.z = (float) this.offset[2].get();

        if (this.direction instanceof ShapeDirectionVector)
        {
            this.direction.applyDirection(particle, particle.position.x, particle.position.y, particle.position.z);
        }
    }
}