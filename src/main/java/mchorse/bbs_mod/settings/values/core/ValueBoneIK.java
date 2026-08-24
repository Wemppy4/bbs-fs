package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValueBoneIK extends BaseValueBasic<IKControl>
{
    public ValueBoneIK(String id, IKControl value)
    {
        super(id, value);
    }

    @Override
    public BaseType toData()
    {
        return this.value.toData();
    }

    @Override
    public void fromData(BaseType data)
    {
        IKControl control = new IKControl();

        if (data instanceof MapType map)
        {
            control.fromData(map);
        }

        this.value = control;
    }
}
