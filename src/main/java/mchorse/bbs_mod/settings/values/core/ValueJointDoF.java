package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.ik.JointDoF;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

public class ValueJointDoF extends BaseValueBasic<JointDoF>
{
    public ValueJointDoF(String id, JointDoF value)
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
        JointDoF joint = new JointDoF();

        if (data instanceof MapType map)
        {
            joint.fromData(map);
        }

        this.value = joint;
    }
}
