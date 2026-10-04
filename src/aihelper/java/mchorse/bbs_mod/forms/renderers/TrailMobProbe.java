package mchorse.bbs_mod.forms.renderers;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;

/** Isolated production geometry and matrix evaluation; no screen changes or OS input. */
public final class TrailMobProbe
{
    public static JsonObject run(JsonObject input) throws Exception
    {
        if(Minecraft.getMinecraft().world==null)throw new IllegalStateException("World required");
        int beforeFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),beforeProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        JsonObject result=new JsonObject();result.add("mob",mob());result.add("trail",trail());
        result.addProperty("stateRestored",beforeFbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&beforeProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        result.addProperty("glError",GL11.glGetError());result.addProperty("ok",true);return result;
    }
    private static JsonArray mob() throws Exception
    {
        JsonArray result=new JsonArray();StubEntity source=new StubEntity(Minecraft.getMinecraft().world);source.setAge(60);
        MobForm form=new MobForm();form.mobID.set("minecraft:player");form.pose.get().getOrCreate("head").rotate.z=.25F;
        BlockForm child=new BlockForm();BodyPart part=new BodyPart("attached");part.bone.set("right_arm");part.setForm(child);form.parts.addBodyPart(part);
        MobFormRenderer renderer=(MobFormRenderer)FormUtilsClient.getRenderer(form);
        Field cache=MobFormRenderer.class.getDeclaredField("bones");cache.setAccessible(true);
        try
        {
            for(String state:new String[]{"standing","crouching","bow","gliding"})
            {
                source.setSneaking(state.equals("crouching"));source.setFallFlying(state.equals("gliding"));source.setRoll(state.equals("gliding")?40:0);source.setVelocity(.2F,-.03F,.3F);
                source.setEquipmentStack(EntityEquipmentSlot.MAINHAND,state.equals("bow")?new ItemStack(Items.BOW):ItemStack.EMPTY);
                ThirdPersonItemUse.set(source,state.equals("bow")?new ItemUsePose.Use(EnumAction.BOW,12F,source.getEquipmentStack(EntityEquipmentSlot.MAINHAND),72000F):null,null);
                JsonObject row=new JsonObject();row.addProperty("state",state);
                try(NativeOffscreen target=new NativeOffscreen(192,192,-1.5,1.5,-.5,2.5))
                {
                    FormRenderingContext context=new FormRenderingContext().set(FormRenderType.ENTITY,source,new MatrixStack(),0x00f000f0,10<<16,0).inUI();
                    FormUtilsClient.render(form,context);row.addProperty("pixels",pixels());
                    MatrixCache drawn=(MatrixCache)cache.get(renderer),evaluated=renderer.collectMatrices(source,0F);
                    float max=0F;int count=0;
                    for(Map.Entry<String,MatrixCacheEntry> e:drawn.entrySet())
                    {
                        Matrix4f a=e.getValue().matrix(),b=evaluated.get(e.getKey()).matrix();
                        if(b==null)throw new IllegalStateException("Missing matrix "+e.getKey());
                        float[] av=new float[16],bv=new float[16];a.get(av);b.get(bv);for(int i=0;i<16;i++)max=Math.max(max,Math.abs(av[i]-bv[i]));count++;
                    }
                    row.addProperty("matrices",count);row.addProperty("maxMatrixError",max);row.addProperty("attachment",evaluated.has(part.getId()));
                    EntityLivingBase nativeEntity=(EntityLivingBase)renderer.getEntity();row.addProperty("gliding",nativeEntity.isElytraFlying());row.addProperty("roll",nativeEntity.getTicksElytraFlying());
                    row.addProperty("usingItem",nativeEntity.isHandActive());row.addProperty("useElapsed",nativeEntity.getItemInUseMaxCount());
                }
                result.add(row);
            }
            /* A resource reload can retain RenderPlayer while replacing its main model. */
            RenderLivingBase nativeRenderer=(RenderLivingBase)Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(renderer.getEntity());
            Field modelField=ReflectionHelper.findField(RenderLivingBase.class,"mainModel","field_77045_g");Object old=modelField.get(nativeRenderer);ModelPlayer replacement=new ModelPlayer(0,false);
            JsonObject replacementResult=new JsonObject();replacementResult.addProperty("state","modelReplacement");
            try
            {
                modelField.set(nativeRenderer,replacement);renderer.getBones();Field rig=MobFormRenderer.class.getDeclaredField("rig");rig.setAccessible(true);
                replacementResult.addProperty("refreshed",((mchorse.bbs_mod.cubic.jem.VanillaRigs.Rig)rig.get(renderer)).model()==replacement);
            }
            finally{modelField.set(nativeRenderer,old);renderer.getBones();}
            result.add(replacementResult);
            source.setEquipmentStack(EntityEquipmentSlot.MAINHAND,new ItemStack(Items.APPLE,3));
            ThirdPersonItemUse.set(source,new ItemUsePose.Use(EnumAction.EAT,31F,source.getEquipmentStack(EntityEquipmentSlot.MAINHAND),32F),null);
            renderer.collectMatrices(source,0);renderer.tick(source);
            JsonObject item=new JsonObject();item.addProperty("state","useDoesNotConsume");item.addProperty("sourceCount",source.getEquipmentStack(EntityEquipmentSlot.MAINHAND).getCount());item.addProperty("copyCount",((EntityLivingBase)renderer.getEntity()).getHeldItemMainhand().getCount());result.add(item);
            source.setSneaking(false);source.setFallFlying(false);source.setRoll(0);source.setEquipmentStack(EntityEquipmentSlot.MAINHAND,ItemStack.EMPTY);ThirdPersonItemUse.set(source,null,null);
            ByteBuffer[] dyes=new ByteBuffer[2];
            for(int dye=0;dye<2;dye++)
            {
                ItemStack armor=new ItemStack(Items.LEATHER_CHESTPLATE);((ItemArmor)armor.getItem()).setColor(armor,dye==0?0xff0000:0x00ff00);source.setEquipmentStack(EntityEquipmentSlot.CHEST,armor);
                try(NativeOffscreen target=new NativeOffscreen(192,192,-1.5,1.5,-.5,2.5))
                {
                    FormUtilsClient.render(form,new FormRenderingContext().set(FormRenderType.ENTITY,source,new MatrixStack(),0x00f000f0,10<<16,0).inUI());dyes[dye]=read();
                }
            }
            int changed=0;for(int i=0;i<dyes[0].limit();i+=4)if((dyes[0].get(i)&255)-(dyes[1].get(i)&255)>40&&(dyes[1].get(i+1)&255)-(dyes[0].get(i+1)&255)>40)changed++;
            JsonObject armor=new JsonObject();armor.addProperty("state","armorDye");armor.addProperty("changedPixels",changed);result.add(armor);
        }
        finally{ThirdPersonItemUse.set(source,null,null);}
        return result;
    }
    private static JsonObject trail()
    {
        Link link=Link.bbs("ai-trail-"+UUID.randomUUID());ByteBuffer white=BufferUtils.createByteBuffer(4);white.putInt(-1).flip();
        Texture texture=Texture.textureFromPixels(new Pixels(white,1,1),GL11.GL_NEAREST);BBSModClient.getTextures().textures.put(link,texture);
        boolean world=BBSRendering.renderingWorld;TrailForm form=new TrailForm();form.texture.set(link);form.length.set(8F);
        TrailFormRenderer renderer=(TrailFormRenderer)FormUtilsClient.getRenderer(form);StubEntity entity=new StubEntity(Minecraft.getMinecraft().world);JsonObject result=new JsonObject();
        try(NativeOffscreen target=new NativeOffscreen(192,192,-2,2,-2,2))
        {
            BBSRendering.renderingWorld=true;
            FormRenderingContext context=null;
            for(int n=0;n<6;n++)
            {
                renderer.tick(entity);entity.setPosition(-1+n*.4,0,0);entity.setPrevX(entity.getX());
                context=new FormRenderingContext().set(FormRenderType.ENTITY,entity,new MatrixStack(),0,10<<16,0);
                target.framebuffer.clear();FormUtilsClient.render(form,context);
            }
            result.addProperty("pixels",pixels());result.addProperty("samples",renderer.getTrailSamples());result.addProperty("lightRestored",context.light==0);
            int count=renderer.getTrailSamples();form.paused.set(true);FormUtilsClient.render(form,context);result.addProperty("paused",renderer.getTrailSamples()==count);
            target.framebuffer.clear();FormUtilsClient.render(form,context);long dark=rgbSum();
            context.light=0x00f000f0;target.framebuffer.clear();FormUtilsClient.render(form,context);long bright=rgbSum();
            result.addProperty("unlit",dark==bright);result.addProperty("rgb",bright);
            for(int n=0;n<12;n++)renderer.tick(entity);target.framebuffer.clear();FormUtilsClient.render(form,context);result.addProperty("expired",renderer.getTrailSamples()==0&&pixels()==0);
        }
        finally{BBSRendering.renderingWorld=world;BBSModClient.getTextures().textures.remove(link);texture.delete();}
        return result;
    }
    private static ByteBuffer read()
    {
        ByteBuffer data=BufferUtils.createByteBuffer(192*192*4);int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,192,192,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);return data;}
        finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);}
    }
    private static int pixels(){ByteBuffer data=read();int count=0;for(int i=3;i<data.limit();i+=4)if((data.get(i)&255)>0)count++;return count;}
    private static long rgbSum(){ByteBuffer data=read();long sum=0;for(int i=0;i<data.limit();i+=4)sum+=(data.get(i)&255)+(data.get(i+1)&255)+(data.get(i+2)&255);return sum;}
    private TrailMobProbe(){}
}
