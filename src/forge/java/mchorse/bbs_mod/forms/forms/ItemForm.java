package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.forms.values.ValueModelTransformationMode;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;

public class ItemForm extends Form
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.LINE;

    public final ValueItemStack stack = new ValueItemStack("item_stack");
    public final ValueModelTransformationMode modelTransform = new ValueModelTransformationMode("modelTransform", TransformType.NONE);
    public final ValueColor color = new ValueColor("color", Color.white());

    public ItemForm()
    {
        this.add(this.stack);
        this.add(this.modelTransform);
        this.add(this.color);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return this.stack.get().getItem().getRegistryName().toString();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}
