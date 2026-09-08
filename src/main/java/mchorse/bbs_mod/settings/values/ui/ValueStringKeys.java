package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.HashSet;
import java.util.Set;

public class ValueStringKeys extends BaseValueBasic<Set<String>>
{
    public ValueStringKeys(String id)
    {
        super(id, new HashSet<>());
    }

    /** With the keys it starts out holding, so a reset goes back to them rather than to nothing. */
    public ValueStringKeys(String id, Set<String> defaultKeys)
    {
        super(id, new HashSet<>(defaultKeys));
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (String s : this.value)
        {
            list.addString(s);
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
            if (type.isString()) this.value.add(type.asString());
        }
    }

    @Override
    protected Set<String> copyValue(Set<String> value)
    {
        return value == null ? null : new HashSet<>(value);
    }
}