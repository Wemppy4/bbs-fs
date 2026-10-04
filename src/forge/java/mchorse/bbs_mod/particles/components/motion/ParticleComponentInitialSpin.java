package mchorse.bbs_mod.particles.components.motion;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentInitialSpin extends ParticleComponentBase implements IComponentParticleInitialize
{
    public MolangExpression rotation = MolangParser.ZERO;
    public MolangExpression rate = MolangParser.ZERO;

    @Override
    protected void toData(MapType data)
    {
        if (!MolangExpression.isZero(this.rotation)) data.put("rotation", this.rotation.toData());
        if (!MolangExpression.isZero(this.rate)) data.put("rotation_rate", this.rate.toData());
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("rotation")) this.rotation = parser.parseDataSilently(map.get("rotation"));
        if (map.has("rotation_rate")) this.rate = parser.parseDataSilently(map.get("rotation_rate"));

        return super.fromData(map, parser);
    }

    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {
        particle.initialRotation = (float) this.rotation.get();
        particle.rotationVelocity = (float) this.rate.get() / 20;
    }
}