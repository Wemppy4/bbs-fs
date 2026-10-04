package mchorse.bbs_mod.particles.components.shape.directions;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.particles.emitter.Particle;

public abstract class ShapeDirection
{
    public abstract void applyDirection(Particle particle, double x, double y, double z);

    public abstract BaseType toData();
}