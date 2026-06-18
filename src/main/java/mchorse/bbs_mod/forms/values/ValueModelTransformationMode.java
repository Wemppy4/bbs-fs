package mchorse.bbs_mod.forms.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import net.minecraft.item.ItemDisplayContext;

public class ValueModelTransformationMode extends BaseValueBasic<ItemDisplayContext>
{
    public ValueModelTransformationMode(String id, ItemDisplayContext value)
    {
        super(id, value);
    }

    @Override
    public BaseType toData()
    {
        return new StringType((this.value == null ? ItemDisplayContext.NONE : this.value).asString());
    }

    @Override
    public void fromData(BaseType data)
    {
        String string = data.isString() ? data.asString() : "";

        this.set(ItemDisplayContext.NONE);

        for (ItemDisplayContext value : ItemDisplayContext.values())
        {
            if (value.asString().equals(string))
            {
                this.set(value);

                break;
            }
        }
    }
}
