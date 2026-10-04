package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.ResourceLocation;

public class ParticleSettings implements IMapSerializable
{
    public ResourceLocation particle = new ResourceLocation("minecraft", "flame");
    public String arguments = "";

    @Override
    public void toData(MapType data)
    {
        data.putString("particle", this.particle.toString());
        data.putString("args", this.arguments);
    }

    @Override
    public void fromData(MapType data)
    {
        this.particle = new ResourceLocation(data.getString("particle"));
        this.arguments = data.getString("args");
    }
}
