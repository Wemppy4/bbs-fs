package mchorse.bbs_mod.forms.renderers;

import com.google.gson.*;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/** Compare native form compositing in both submission orders, without touching the world or input. */
public final class FormsTransparencyProbe
{
    private static final int SIZE=192;
    public static JsonObject run(JsonObject input) throws Exception
    {
        if(Minecraft.getMinecraft().world==null)throw new IllegalStateException("World required");
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean queue=BBSSettings.translucencyQueue.get();BBSSettings.translucencyQueue.set(true);
        JsonObject out=new JsonObject();JsonArray rows=new JsonArray();
        try
        {
            for(String kind:new String[]{"label","block","item","mob"})
            {
                Form near=form(kind,true),far=form(kind,false);
                ByteBuffer forward=render(near,far,false),reverse=render(near,far,true);
                JsonObject row=compare(forward,reverse);row.addProperty("kind",kind);rows.add(row);
            }
            out.add("order",rows);
            LabelForm label=(LabelForm)form("label",true);
            try(NativeOffscreen target=new NativeOffscreen(SIZE,SIZE,-2,2,-1.5,2.5))
            {
                GlStateManager.clearColor(0,1,0,1);target.framebuffer.clear();
                FormUtilsClient.render(label,context(0));
                ByteBuffer pixels=read();int index=(SIZE/2*SIZE+SIZE/2)*4;
                JsonArray center=new JsonArray();for(int c=0;c<4;c++)center.add(pixels.get(index+c)&255);out.add("halfRedOverGreen",center);
            }
            JsonArray cutouts=new JsonArray();java.lang.reflect.Field preview=StructureManager.class.getDeclaredField("preview");preview.setAccessible(true);Object savedPreview=preview.get(null);
            try
            {
                String id=StructureManager.nextPreviewId();
                StructureManager.setPreview(StructureRenderData.create(id,new net.minecraft.util.math.Vec3i(1,1,1),java.util.Collections.singletonMap(net.minecraft.util.math.BlockPos.ORIGIN,Blocks.LEAVES.getDefaultState()),java.util.Collections.emptyMap()));
                for(String kind:new String[]{"block","structure"})for(float scale:new float[]{1F,.2F,.07F})
                {
                    Form leaves;if(kind.equals("block")){BlockForm b=new BlockForm();b.blockState.set(Blocks.LEAVES.getDefaultState());leaves=b;}
                    else{StructureForm s=new StructureForm();s.structure.set(id);leaves=s;}
                    ByteBuffer[] frames=new ByteBuffer[3];
                    for(int background=0;background<3;background++)try(NativeOffscreen target=new NativeOffscreen(SIZE,SIZE,-1,1,-.5,1.5))
                    {
                        if(background>0){float c=background==2?1:0;GlStateManager.clearColor(c,c,c,1);target.framebuffer.clear();}
                        FormRenderingContext context=context(0);context.stack.translate(0,.5,0);context.stack.scale(scale,scale,scale);context.stack.translate(0,-.5,0);
                        context.stack.multiply(new org.joml.Quaternionf().rotationY(.47F));FormUtilsClient.render(leaves,context);frames[background]=read();
                    }
                    int middle=0,visible=0,dependent=0;
                    for(int i=0;i<frames[0].limit();i+=4)
                    {
                        int a=frames[0].get(i+3)&255;if(a==0)continue;visible++;if(a<250)middle++;
                        int max=0;for(int c=0;c<3;c++)max=Math.max(max,Math.abs((frames[1].get(i+c)&255)-(frames[2].get(i+c)&255)));if(max>3)dependent++;
                    }
                    JsonObject row=new JsonObject();row.addProperty("kind",kind);row.addProperty("scale",scale);row.addProperty("visible",visible);row.addProperty("semiAlpha",middle);row.addProperty("backgroundDependent",dependent);cutouts.add(row);
                }
            }
            finally{preview.set(null,savedPreview);}
            out.add("cutout",cutouts);
        }
        finally{BBSSettings.translucencyQueue.set(queue);}
        out.addProperty("stateRestored",fbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        out.addProperty("glError",GL11.glGetError());out.addProperty("ok",true);return out;
    }
    private static Form form(String kind,boolean red)
    {
        float r=red?1:0,g=red?0:1;
        if(kind.equals("label"))
        {
            LabelForm f=new LabelForm();f.text.set("MMMM");f.color.get().set(0,0,0,0);f.background.get().set(r,g,0,.5F);f.offset.set(10F);return f;
        }
        if(kind.equals("block"))
        {
            BlockForm f=new BlockForm();f.blockState.set(Blocks.STAINED_GLASS.getDefaultState());f.color.get().set(r,g,0,1);return f;
        }
        if(kind.equals("item"))
        {
            ItemForm f=new ItemForm();f.stack.set(new ItemStack(Blocks.STAINED_GLASS));f.color.get().set(r,g,0,1);return f;
        }
        MobForm f=new MobForm();f.mobID.set("minecraft:slime");f.mobNBT.set("{Size:2}");return f;
    }
    private static ByteBuffer render(Form near,Form far,boolean reverse)
    {
        try(NativeOffscreen target=new NativeOffscreen(SIZE,SIZE,-2,2,-1.5,2.5))
        {
            FormTranslucentQueue.begin();
            try
            {
                FormRenderingContext front=context(-1),back=context(-3);
                if(near instanceof MobForm){front.color(0xffff0000);back.color(0xff00ff00);}
                if(reverse){FormUtilsClient.render(far,back);FormUtilsClient.render(near,front);}
                else{FormUtilsClient.render(near,front);FormUtilsClient.render(far,back);}
            }
            finally{FormTranslucentQueue.flush();}
            return read();
        }
    }
    private static FormRenderingContext context(float z)
    {
        StubEntity entity=new StubEntity(Minecraft.getMinecraft().world);MatrixStack stack=new MatrixStack();stack.translate(0,0,z);
        return new FormRenderingContext().set(FormRenderType.ENTITY,entity,stack,0x00f000f0,10<<16,0).inUI();
    }
    private static JsonObject compare(ByteBuffer a,ByteBuffer b)
    {
        int changed=0,visible=0;long error=0;
        for(int i=0;i<a.limit();i+=4)
        {
            if((a.get(i+3)&255)>0||(b.get(i+3)&255)>0)visible++;
            int max=0;for(int c=0;c<4;c++){int d=Math.abs((a.get(i+c)&255)-(b.get(i+c)&255));max=Math.max(max,d);error+=d;}
            if(max>3)changed++;
        }
        JsonObject row=new JsonObject();row.addProperty("visible",visible);row.addProperty("changedPixels",changed);row.addProperty("absoluteError",error);return row;
    }
    private static ByteBuffer read()
    {
        ByteBuffer data=BufferUtils.createByteBuffer(SIZE*SIZE*4);int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);return data;}
        finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);}
    }
    private FormsTransparencyProbe(){}
}
