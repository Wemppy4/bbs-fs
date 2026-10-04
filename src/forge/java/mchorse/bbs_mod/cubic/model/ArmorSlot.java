package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

public class ArmorSlot implements IDataSerializable
{
    public String group = "";
    public final Transform transform = new Transform();

    @Override
    public BaseType toData()
    {
        /* Unnecessary yet */
        return new MapType();
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isString())
        {
            this.transform.identity();
            this.group = data.asString();
        }
        else if (data.isMap())
        {
            MapType map = data.asMap();

            this.transform.fromData(map.getMap("transform"));
            this.transform.toRad();
            this.group = map.getString("group");
        }
    }
}
