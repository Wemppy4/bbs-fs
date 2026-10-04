package mchorse.bbs_mod.forms.renderers;

import com.google.gson.*;
import mchorse.bbs_mod.blocks.entities.*;
import mchorse.bbs_mod.forge.*;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/** Vanilla baked override -> perspective -> TEISR, plus the real model-block crack pass. */
public final class ModelItemGPUProbe
{
    public static JsonObject run(JsonObject request)
    {
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.player==null)throw new IllegalStateException("World required");
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        JsonObject result=new JsonObject();JsonArray perspectives=new JsonArray(),cracks=new JsonArray();
        ModelTileEntity tile=new ModelTileEntity();tile.setWorld(mc.world);tile.setPos(mc.player.getPosition());ModelProperties p=tile.getProperties();
        p.setForm(block(1,1,0));p.setFormInventory(block(1,0,0));p.setFormFirstPerson(block(0,1,0));p.setFormThirdPerson(block(0,0,1));
        p.getBody().setHitboxMode(ModelBody.HitboxMode.MANUAL);p.getBody().getHitboxMin().set(.1F,0,.1F);p.getBody().getHitboxMax().set(.9F,1.4F,.9F);
        ItemStack stack=new ItemStack(CommonProxy.MODEL_BLOCK);NBTTagCompound root=new NBTTagCompound();root.setTag("BlockEntityTag",tile.writeToNBT(new NBTTagCompound()));stack.setTagCompound(root);
        try(NativeOffscreen target=new NativeOffscreen(192,192,-1.6,1.6,-1.6,1.6))
        {
            for(TransformType type:new TransformType[]{TransformType.GUI,TransformType.FIRST_PERSON_RIGHT_HAND,TransformType.THIRD_PERSON_RIGHT_HAND,TransformType.GROUND})
            {
                target.framebuffer.clear();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(35,0,1,0);
                mc.getRenderItem().renderItem(stack,type);
                JsonObject draw=measure(false);draw.addProperty("perspective",type.name());perspectives.add(draw);
            }
            for(int stage:new int[]{0,9})
            {
                GlStateManager.clearColor(1,1,1,1);target.framebuffer.clear();GlStateManager.loadIdentity();GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(35,0,1,0);
                boolean depth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),polygon=GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
                float factor=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),units=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
                ModelBlockBreakingRenderer.render(tile,-.5,-.6,-.5,0,stage);
                JsonObject draw=measure(true);draw.addProperty("stage",stage);
                draw.addProperty("stateRestored",depth==GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&polygon==GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)&&factor==GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR)&&units==GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS));cracks.add(draw);
            }
        }
        result.add("perspectives",perspectives);result.add("cracks",cracks);result.addProperty("stateRestored",fbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));result.addProperty("glError",GL11.glGetError());result.addProperty("ok",true);return result;
    }
    private static BlockForm block(float r,float g,float b)
    {BlockForm form=new BlockForm();form.blockState.set(Blocks.WOOL.getDefaultState());form.color.set(new Color(r,g,b,1));return form;}
    private static JsonObject measure(boolean white)
    {
        ByteBuffer pixels=BufferUtils.createByteBuffer(192*192*4);int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),align=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,192,192,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            int count=0;long r=0,g=0,b=0;for(int i=0;i<pixels.limit();i+=4)
            {
                int red=pixels.get(i)&255,green=pixels.get(i+1)&255,blue=pixels.get(i+2)&255;
                if(white?red<245||green<245||blue<245:(pixels.get(i+3)&255)>0){count++;r+=red;g+=green;b+=blue;}
            }
            JsonObject result=new JsonObject();result.addProperty("pixels",count);JsonArray rgb=new JsonArray();rgb.add(count==0?0:r/(float)count);rgb.add(count==0?0:g/(float)count);rgb.add(count==0?0:b/(float)count);result.add("rgb",rgb);return result;
        }
        finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,align);}
    }
    private ModelItemGPUProbe(){}
}
