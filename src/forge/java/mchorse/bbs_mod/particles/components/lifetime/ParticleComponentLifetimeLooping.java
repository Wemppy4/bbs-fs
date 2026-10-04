package mchorse.bbs_mod.particles.components.lifetime;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

public class ParticleComponentLifetimeLooping extends ParticleComponentLifetime
{
    public MolangExpression sleepTime = MolangParser.ZERO;

    @Override
    protected void toData(MapType data)
    {
        super.toData(data);

        if (!MolangExpression.isZero(this.sleepTime))
        {
            data.put("sleep_time", this.sleepTime.toData());
        }
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType element = data.asMap();

        if (element.has("sleep_time"))
        {
            this.sleepTime = parser.parseDataSilently(element.get("sleep_time"));
        }

        return super.fromData(element, parser);
    }

    @Override
    public void update(ParticleEmitter emitter)
    {
        double active = this.activeTime.get();
        double sleep = this.sleepTime.get();
        double age = emitter.getAge();

        emitter.lifetime = (int) (active * 20);

        if (age >= active && emitter.playing)
        {
            emitter.stop();
        }

        if (age >= sleep && !emitter.playing)
        {
            emitter.start();
        }
    }
}