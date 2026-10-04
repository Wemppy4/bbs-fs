package mchorse.bbs_mod.particles.components.meta;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentLocalSpace extends ParticleComponentBase implements IComponentParticleInitialize
{
    public boolean position;
    public boolean rotation;
    public boolean velocity;
    public boolean textureScale;

    @Override
    protected void toData(MapType data)
    {
        if (this.position) data.putBool("position", true);
        if (this.rotation) data.putBool("rotation", true);
        if (this.velocity) data.putBool("velocity", true);
        if (this.textureScale) data.putBool("texture_scale", true);
    }

    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("position")) this.position = map.getBool("position");
        if (map.has("rotation")) this.rotation = map.getBool("rotation");
        if (map.has("velocity")) this.velocity = map.getBool("velocity");
        if (map.has("texture_scale")) this.textureScale = map.getBool("texture_scale");

        return super.fromData(map, parser);
    }

    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {
        particle.relativePosition = this.position;
        particle.relativeRotation = this.rotation;
        particle.relativeVelocity = this.velocity;
        particle.textureScale = this.textureScale;

        particle.setupMatrix(emitter);
    }

    @Override
    public int getSortingIndex()
    {
        return 1000;
    }
}