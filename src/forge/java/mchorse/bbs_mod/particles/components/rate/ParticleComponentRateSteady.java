package mchorse.bbs_mod.particles.components.rate;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.particles.components.IComponentEmitterUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentRateSteady extends ParticleComponentRate implements IComponentEmitterUpdate
{
    public static final MolangExpression DEFAULT_PARTICLES = new MolangValue(null, new Constant(50));

    public MolangExpression spawnRate = MolangParser.ONE;

    public ParticleComponentRateSteady()
    {
        this.particles = DEFAULT_PARTICLES;
    }

    @Override
    protected void toData(MapType data)
    {
        if (!MolangExpression.isOne(this.spawnRate)) data.put("spawn_rate", this.spawnRate.toData());
        if (!MolangExpression.isConstant(this.particles, 50)) data.put("max_particles", this.particles.toData());
    }

    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("spawn_rate")) this.spawnRate = parser.parseDataSilently(map.get("spawn_rate"), MolangParser.ONE);
        if (map.has("max_particles")) this.particles = parser.parseDataSilently(map.get("max_particles"), MolangParser.ONE);

        return super.fromData(map, parser);
    }

    @Override
    public void update(ParticleEmitter emitter)
    {
        if (emitter.playing && !emitter.paused)
        {
            float spawnRate = (float) (this.spawnRate.get() / 20D);
            int max = (int) this.particles.get();
            int particles = (int) Math.floor(spawnRate);

            while (emitter.spawnRemainder >= 1F)
            {
                emitter.spawnRemainder -= 1;
                particles += 1;
            }

            for (int i = 0; i < particles; i++)
            {
                if (emitter.particles.size() >= max)
                {
                    break;
                }

                emitter.setEmitterVariables(i / (float) particles);
                emitter.spawnParticle(i / (float) particles);
            }

            emitter.spawnRemainder += spawnRate % 1F;
        }
    }
}