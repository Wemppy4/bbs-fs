package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValueData extends BaseValueBasic<BaseType>
{
    public ValueData(String id)
    {
        super(id, null);
    }

    @Override
    public BaseType toData()
    {
        return this.value == null ? null : this.value.copy();
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value = data == null ? null : data.copy();
    }

    @Override
    protected BaseType copyValue(BaseType value)
    {
        return value == null ? null : value.copy();
    }
}

