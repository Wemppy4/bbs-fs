package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Vanilla's breaking pass on the model body's box, whose block model is invisible. */
public final class ModelBlockBreakingRenderer
{
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[10];
    static { for (int i=0;i<TEXTURES.length;i++) TEXTURES[i]=new ResourceLocation("textures/blocks/destroy_stage_"+i+".png"); }
    public static void render(ModelTileEntity tile,double x,double y,double z,float partial,int stage)
    {
        if(stage<0||stage>=TEXTURES.length)return;
        MatrixStack matrices=new MatrixStack();
        matrices.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix()).translate((float)x,(float)y,(float)z);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        FormRenderingContext context=new FormRenderingContext().set(FormRenderType.MODEL_BLOCK,tile.getRenderEntity(),matrices,light,10<<16,partial);
        boolean polygon=GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float factor=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),units=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        try(NativeFormDraw draw=new NativeFormDraw(context,new Color(1,1,1,1),new Color(0,0,0,0),false))
        {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURES[stage]);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR,GL11.GL_SRC_COLOR,GL11.GL_ONE,GL11.GL_ZERO);
            GlStateManager.depthMask(false);GlStateManager.enablePolygonOffset();GlStateManager.doPolygonOffset(-3,-3);
            GlStateManager.enableCull();
            AxisAlignedBB b=tile.getShape();double a=b.minX,c=b.minY,d=b.minZ,e=b.maxX,f=b.maxY,g=b.maxZ;
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            face(buffer,light,1,a,c,d,e,c,d,e,c,g,a,c,g);
            face(buffer,light,1,a,f,g,e,f,g,e,f,d,a,f,d);
            face(buffer,light,2,a,c,d,a,f,d,e,f,d,e,c,d);
            face(buffer,light,2,e,c,g,e,f,g,a,f,g,a,c,g);
            face(buffer,light,0,a,c,g,a,f,g,a,f,d,a,c,d);
            face(buffer,light,0,e,c,d,e,f,d,e,f,g,e,c,g);
            Tessellator.getInstance().draw();
        }
        finally
        {
            GlStateManager.doPolygonOffset(factor,units);
            if(polygon)GlStateManager.enablePolygonOffset();else GlStateManager.disablePolygonOffset();
        }
    }
    private static void face(BufferBuilder buffer,int light,int axis,double... v)
    {
        for(int i=0;i<12;i+=3)
        {
            double x=v[i],y=v[i+1],z=v[i+2];
            buffer.pos(x,y,z).color(255,255,255,255).tex(axis==0?z:x,axis==1?z:y)
                .lightmap(light&65535,light>>>16&65535).endVertex();
        }
    }
}
