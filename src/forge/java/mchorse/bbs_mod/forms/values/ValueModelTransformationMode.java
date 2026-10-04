package mchorse.bbs_mod.forms.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import java.util.Locale;

public class ValueModelTransformationMode extends BaseValueBasic<TransformType>
{
    public ValueModelTransformationMode(String id, TransformType value)
    {
        super(id, value);
    }

    @Override
    public BaseType toData()
    {
        return new StringType(asString(this.value == null ? TransformType.NONE : this.value));
    }

    @Override
    public void fromData(BaseType data)
    {
        String string = data.isString() ? data.asString() : "";

        this.set(TransformType.NONE);

        for (TransformType value : TransformType.values())
        {
            if (asString(value).equals(string))
            {
                this.set(value);

                break;
            }
        }
    }

    public static String asString(TransformType value)
    {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
