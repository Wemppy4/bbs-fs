package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of links kept in the settings — the texture browser's pins, where the
 * order is the user's own and outlives the session.
 */
public class ValueLinkList extends BaseValueBasic<List<Link>>
{
    public ValueLinkList(String id)
    {
        super(id, new ArrayList<>());
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (Link link : this.value)
        {
            list.addString(link.toString());
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (!data.isList())
        {
            return;
        }

        for (BaseType type : data.asList())
        {
            if (type.isString())
            {
                this.value.add(Link.create(type.asString()));
            }
        }
    }

    @Override
    protected List<Link> copyValue(List<Link> value)
    {
        return value == null ? null : new ArrayList<>(value);
    }
}
