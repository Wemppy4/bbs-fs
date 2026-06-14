package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.util.HashMap;
import java.util.Map;

public class ValueLinks extends BaseValueBasic<Map<String, Link>>
{
    public ValueLinks(String id)
    {
        super(id, new HashMap<>());
    }

    public Link getLink(String key)
    {
        return this.value.get(key);
    }

    public void setLink(String key, Link link)
    {
        if (link == null)
        {
            this.value.remove(key);
        }
        else
        {
            this.value.put(key, link);
        }

        this.set(this.value);
    }

    @Override
    public BaseType toData()
    {
        if (this.value.isEmpty())
        {
            return null;
        }

        MapType map = new MapType();

        for (Map.Entry<String, Link> entry : this.value.entrySet())
        {
            if (entry.getValue() != null)
            {
                map.put(entry.getKey(), LinkUtils.toData(entry.getValue()));
            }
        }

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (data instanceof MapType map)
        {
            for (String key : map.keys())
            {
                Link link = LinkUtils.create(map.get(key));

                if (link != null)
                {
                    this.value.put(key, link);
                }
            }
        }
    }
}
