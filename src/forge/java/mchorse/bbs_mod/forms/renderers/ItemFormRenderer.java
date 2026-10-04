package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;

/** Uses the registered native item renderer, preserving model transforms, overrides and TEISR. */
public class ItemFormRenderer extends NativeGeometryFormRenderer<ItemForm>
{
    public ItemFormRenderer(ItemForm form){super(form);}
    @Override public AABB getPreviewBounds(){return new AABB(-.5,-.5,-.5,1,1,1);}
    @Override protected void render3D(FormRenderingContext context)
    {
        if(form.stack.get().isEmpty())return;
        Color tint=form.color.get().copy();tint.mul(context.color);
        if(!context.isPicking()&&mchorse.bbs_mod.forms.FormTranslucentQueue.isActive())
        {
            net.minecraft.item.ItemStack stack=form.stack.get().copy();
            net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType transform=form.modelTransform.get();
            mchorse.bbs_mod.forms.FormTranslucentQueue.add(new NativeFormCommand(context,tint,form.overlayColor.get(),false,true,true,false,()->
                Minecraft.getMinecraft().getRenderItem().renderItem(stack,transform)));return;
        }
        try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),false))
        {Minecraft.getMinecraft().getRenderItem().renderItem(form.stack.get(),form.modelTransform.get());}
    }
}
