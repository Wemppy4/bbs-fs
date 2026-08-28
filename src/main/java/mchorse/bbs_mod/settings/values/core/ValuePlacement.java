package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValuePlacement extends BaseValueBasic<Placement>
{
    public ValuePlacement(String id, Placement placement)
    {
        super(id, placement);
    }

    @Override
    public BaseType toData()
    {
        return this.value.toData();
    }

    @Override
    public void fromData(BaseType data)
    {
        Placement placement = new Placement();

        placement.fromData(data);
        this.value = placement;
    }
}
