package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flat {@code String -> String} map serialized straight to a {@link MapType} (the {@code key: value}
 * shape used by config blocks like {@code flipped_parts} and {@code picking_overrides}). The map-valued
 * sibling of {@link ValueStringKeys}.
 */
public class ValueStringMap extends BaseValueBasic<Map<String, String>>
{
    public ValueStringMap(String id)
    {
        super(id, new LinkedHashMap<>());
    }

    @Override
    public BaseType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, String> entry : this.value.entrySet())
        {
            map.putString(entry.getKey(), entry.getValue());
        }

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (!data.isMap())
        {
            return;
        }

        for (Map.Entry<String, BaseType> entry : data.asMap())
        {
            if (entry.getValue().isString())
            {
                this.value.put(entry.getKey(), entry.getValue().asString());
            }
        }
    }
}
