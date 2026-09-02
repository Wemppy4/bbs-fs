package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValueBonePhysics extends BaseValueBasic<PhysicsControl>
{
    public ValueBonePhysics(String id, PhysicsControl value)
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
        PhysicsControl control = new PhysicsControl();

        if (data instanceof MapType map)
        {
            control.fromData(map);
        }

        this.value = control;
    }

    @Override
    protected PhysicsControl copyValue(PhysicsControl value)
    {
        return value == null ? null : value.copy();
    }
}
