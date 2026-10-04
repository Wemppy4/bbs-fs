package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import java.util.Collections;

/** World geometry includes fluids and TESRs, rather than reducing every block to an item. */
public class BlockFormRenderer extends NativeGeometryFormRenderer<BlockForm>
{
    private IBlockState state;
    private StructureRenderWorld view;
    private StructureWorld world;
    private BakedStructure baked;
    public BlockFormRenderer(BlockForm form){super(form);}
    @Override public AABB getPreviewBounds(){return new AABB(-.5,0,-.5,1,1,1);}
    @Override protected void render3D(FormRenderingContext context)
    {
        IBlockState current=form.blockState.get();
        if(current!=state)
        {
            state=current;
            StructureRenderData data=StructureRenderData.create("single",new Vec3i(1,1,1),Collections.singletonMap(BlockPos.ORIGIN,state),Collections.emptyMap());
            view=new StructureRenderWorld(data,"minecraft:plains");world=new StructureWorld(view);baked=null;
        }
        if(baked==null||!baked.isValidFor(view))baked=BakedStructure.bake(view.getData(),view);
        Color tint=form.color.get().copy();tint.mul(context.color);
        context.stack.push();context.stack.translate(-.5,0,-.5);
        try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),false))
        {
            Minecraft mc=Minecraft.getMinecraft();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableCull();
            for(BlockRenderLayer layer:BlockRenderLayer.values())
            {
                if(layer==BlockRenderLayer.TRANSLUCENT&&!context.isPicking()&&mchorse.bbs_mod.forms.FormTranslucentQueue.isActive())
                {
                    BakedStructure geometry=baked;
                    mchorse.bbs_mod.forms.FormTranslucentQueue.add(new NativeFormCommand(context,tint,form.overlayColor.get(),false,true,true,false,()->
                    {Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);geometry.render(BlockRenderLayer.TRANSLUCENT);}));
                    continue;
                }
                GlStateManager.depthMask(layer!=BlockRenderLayer.TRANSLUCENT||context.isPicking());
                GlStateManager.enableAlpha();baked.render(layer);
            }
            GlStateManager.depthMask(true);
            boolean tileRendered=false;
            for(TileEntity tile:world.getTiles())
            {
                if(TileEntityRendererDispatcher.instance.getRenderer(tile)!=null)
                {TileEntityRendererDispatcher.instance.render(tile,0,0,0,context.transition,-1,1F);tileRendered=true;}
            }
            if(baked.getVertexCount()==0&&!tileRendered)
            {
                ItemStack item=new ItemStack(state.getBlock());
                if(!item.isEmpty()){GlStateManager.translate(.5,.5,.5);mc.getRenderItem().renderItem(item,ItemCameraTransforms.TransformType.NONE);}
            }
        }
        finally{context.stack.pop();}
    }
}
