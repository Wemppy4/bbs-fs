package mchorse.bbs_mod.particles.components.lifetime;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.particles.components.IComponentEmitterUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;

public abstract class ParticleComponentLifetime extends ParticleComponentBase implements IComponentEmitterUpdate
{
    public static final MolangExpression DEFAULT_ACTIVE = new MolangValue(null, new Constant(10));

    public MolangExpression activeTime = DEFAULT_ACTIVE;

    @Override
    protected void toData(MapType data)
    {
        if (!MolangExpression.isConstant(this.activeTime, 10))
        {
            data.put(this.getPropertyName(), this.activeTime.toData());
        }
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has(this.getPropertyName()))
        {
            this.activeTime = parser.parseDataSilently(map.get(this.getPropertyName()), MolangParser.ONE);
        }

        return super.fromData(map, parser);
    }

    protected String getPropertyName()
    {
        return "active_time";
    }

    @Override
    public int getSortingIndex()
    {
        return -10;
    }
}