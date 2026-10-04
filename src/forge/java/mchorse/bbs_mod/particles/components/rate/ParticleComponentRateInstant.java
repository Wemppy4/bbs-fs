package mchorse.bbs_mod.particles.components.rate;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.particles.components.IComponentEmitterUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentRateInstant extends ParticleComponentRate implements IComponentEmitterUpdate
{
    public static final MolangExpression DEFAULT_PARTICLES = new MolangValue(null, new Constant(10));

    public ParticleComponentRateInstant()
    {
        this.particles = DEFAULT_PARTICLES;
    }

    @Override
    protected void toData(MapType data)
    {
        if (!MolangExpression.isConstant(this.particles, 10))
        {
            data.put("num_particles", this.particles.toData());
        }
    }

    public ParticleComponentBase fromData(BaseType elem, MolangParser parser) throws MolangException
    {
        if (!elem.isMap())
        {
            return super.fromData(elem, parser);
        }

        MapType map = elem.asMap();

        if (map.has("num_particles"))
        {
            this.particles = parser.parseDataSilently(map.get("num_particles"), MolangParser.ONE);
        }

        return super.fromData(map, parser);
    }

    @Override
    public void update(ParticleEmitter emitter)
    {
        double age = emitter.getAge();

        if (emitter.playing && !emitter.paused && Operation.equals(age, 0))
        {
            emitter.setEmitterVariables(0);

            int particles = (int) this.particles.get();

            for (int i = 0, c = particles; i < c; i ++)
            {
                emitter.spawnParticle(0F);
            }
        }
    }
}