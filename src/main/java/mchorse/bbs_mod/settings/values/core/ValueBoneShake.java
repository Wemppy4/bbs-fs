package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.shake.BoneShake;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValueBoneShake extends BaseValueBasic<BoneShake>
{
    public ValueBoneShake(String id, BoneShake value)
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
        BoneShake shake = new BoneShake();

        if (data instanceof MapType map)
        {
            shake.fromData(map);
        }

        this.value = shake;
    }

    @Override
    protected BoneShake copyValue(BoneShake value)
    {
        return value == null ? null : value.copy();
    }
}
