package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;
import java.util.*;

/** Original BBS pivot, cache lifetime, biome and form properties on the native 1.12 renderer. */
public class StructureFormRenderer extends NativeGeometryFormRenderer<StructureForm>
{
    private String lastStructure,lastBiome;
    private int generation=-1;
    private StructureRenderData data;
    private StructureRenderWorld view;
    private StructureWorld world;
    private BakedStructure baked;
    private final Set<BlockPos> errors=new HashSet<>();
    private static int tileDepth;
    public StructureFormRenderer(StructureForm form){super(form);}
    public StructureRenderData getData(){ensureData();return data;}
    public int getBakedVertexCount(){ensureData();ensureBaked();return baked==null?0:baked.getVertexCount();}
    private void ensureData()
    {
        int current=StructureManager.getGeneration();
        if(current!=generation||!Objects.equals(lastStructure,form.structure.get()))
        {generation=current;lastStructure=form.structure.get();data=null;view=null;world=null;baked=null;errors.clear();}
        if(data==null)data=StructureManager.get(lastStructure);
        if(data!=null&&(view==null||!Objects.equals(lastBiome,form.biome.get())))
        {lastBiome=form.biome.get();view=new StructureRenderWorld(data,lastBiome);world=new StructureWorld(view);baked=null;errors.clear();}
    }
    private void ensureBaked(){if(view!=null&&(baked==null||!baked.isValidFor(view)))baked=BakedStructure.bake(data,view);}
    private Vector3f offset(){return new Vector3f(-data.size.getX()/2F,-0F,-data.size.getZ()/2F).sub(form.origin.get());}
    @Override public AABB getPreviewBounds(){ensureData();if(data==null)return null;Vector3f p=offset();return new AABB(p.x,p.y,p.z,data.size.getX(),data.size.getY(),data.size.getZ());}
    @Override protected void render3D(FormRenderingContext context)
    {
        ensureData();if(data==null)return;
        if(mchorse.bbs_mod.api.client.events.StructureRenderEvents.RENDER.invoker().render(this,form,data,context))return;
        ensureBaked();
        context.stack.push();
        try
        {
            Vector3f p=offset();context.stack.translate(p.x,p.y,p.z);
            Color tint=form.color.get().copy();tint.mul(context.color);
            try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),true))
            {
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.enableCull();
                for(BlockRenderLayer layer:BlockRenderLayer.values())
                {
                    if(layer==BlockRenderLayer.TRANSLUCENT){GlStateManager.enableBlend();GlStateManager.depthMask(context.isPicking());}
                    else {GlStateManager.depthMask(true);GlStateManager.enableAlpha();}
                    baked.render(layer);
                }
            }
            if(tileDepth<2)
            {
                tileDepth++;
                try
                {
                    for(TileEntity tile:world.getTiles())
                    {
                        if(errors.contains(tile.getPos()))continue;
                        try(NativeFormDraw draw=new NativeFormDraw(context,tint,form.overlayColor.get(),false))
                        {
                            BlockPos pos=tile.getPos();
                            TileEntityRendererDispatcher.instance.render(tile,pos.getX(),pos.getY(),pos.getZ(),context.transition,-1,1F);
                        }
                        catch(Exception e){errors.add(tile.getPos());BBSMod.LOGGER.error("Cannot draw structure block entity at "+tile.getPos(),e);}
                    }
                }
                finally{tileDepth--;}
            }
        }
        finally{context.stack.pop();}
    }
}
