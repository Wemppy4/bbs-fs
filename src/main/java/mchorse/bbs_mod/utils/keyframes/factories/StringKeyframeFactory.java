package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.utils.interps.IInterp;

public class StringKeyframeFactory implements IKeyframeFactory<String>
{
    @Override
    public String fromData(BaseType data)
    {
        return data.isString() ? data.asString() : "";
    }

    @Override
    public BaseType toData(String value)
    {
        return new StringType(value);
    }

    @Override
    public String createEmpty()
    {
        return "";
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public String copy(String value)
    {
        return value;
    }

    @Override
    public String interpolate(String preA, String a, String b, String postB, IInterp interpolation, float x)
    {
        return a;
    }
}