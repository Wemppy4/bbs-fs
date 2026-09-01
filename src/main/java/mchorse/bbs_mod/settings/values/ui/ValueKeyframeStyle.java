package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeStyle;

/**
 * The style handed to every newly created keyframe - shape, colour and fill.
 *
 * <p>It is one value rather than one per field so the settings screen can hand the very same panel
 * that restyles a keyframe over to the default, instead of growing a second row of controls that
 * has to be kept in step with it.</p>
 */
public class ValueKeyframeStyle extends BaseValueBasic<KeyframeStyle>
{
    public ValueKeyframeStyle(String id)
    {
        super(id, new KeyframeStyle());
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        this.value.toData(data);

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.reset();

        if (data.isMap())
        {
            this.value.fromData(data.asMap());
        }
    }

    @Override
    protected KeyframeStyle copyValue(KeyframeStyle value)
    {
        return value == null ? null : value.copy();
    }
}
