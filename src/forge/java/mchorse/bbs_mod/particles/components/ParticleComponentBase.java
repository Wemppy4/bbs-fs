package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;

public abstract class ParticleComponentBase
{
    public BaseType toData()
    {
        MapType data = new MapType();

        this.toData(data);

        return data;
    }

    protected void toData(MapType data)
    {}

    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        return this;
    }

    public boolean canBeEmpty()
    {
        return false;
    }
}