package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.render.vao.*;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.graphics.*;
import mchorse.bbs_mod.graphics.texture.*;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Actual GPU maps, material isolation and native attributes; fixtures are removed on completion. */
public final class OptiFinePbrProbe
{
    private static OptiFinePbrProbe active;
    private static JsonObject result;
    public static JsonObject handle(JsonObject request)
    {
        if(request.has("action")&&request.get("action").getAsString().equals("start")&&active==null)
        {result=null;active=new OptiFinePbrProbe();MinecraftForge.EVENT_BUS.register(active);}
        JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("pending",active!=null);if(result!=null)out.add("result",result);return out;
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event)
    {
        if(active!=this)return;
        MinecraftForge.EVENT_BUS.unregister(this);active=null;result=new JsonObject();
        try {run(result);result.addProperty("ok",true);}
        catch(Throwable error){result.addProperty("ok",false);result.addProperty("error",error.toString());}
    }
    private static void run(JsonObject out)throws Exception
    {
        check(OptiFineShaders.isWorldPass(),"native world pass");
        mchorse.bbs_mod.utils.iris.OptiFineShaderOptions.Option mode=mchorse.bbs_mod.utils.iris.OptiFineShaderOptions.options().get("RP_MODE");
        if(mode!=null)out.addProperty("RP_MODE",mode.value);
        TextureManager manager=BBSModClient.getTextures();
        String id=".aihelper-pbr-"+UUID.randomUUID();Link source=Link.assets(id+".png"),normal=Link.assets(id+"_n.png"),spec=Link.assets(id+"_s.png");
        List<Path> files=new ArrayList<>();int[] oldTextures=new int[4];int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        FloatBuffer oldColor=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,oldColor);
        for(int i=0;i<4;i++){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+i);oldTextures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        try
        {
            for(int map=0;map<3;map++)
            {
                Path file=BBSMod.getAssetsFolder().toPath().resolve(id+(map==0?"":map==1?"_n":"_s")+".png");files.add(file);
                BufferedImage image=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<4;y++)for(int x=0;x<4;x++)image.setRGB(x,y,map==0?(x%2==0?0xff222222:0xffdddddd):map==1?0xff8080ff:0xc9602243);
                ImageIO.write(image,"png",file.toFile());
            }
            Texture albedo=manager.getTexture(source);check(albedo!=manager.getError(),"fixture texture");
            albedo.bind();GlStateManager.color(1,1,1,1);GL11.glColor4f(0,0,0,1);
            try(OptiFineModelRenderer.Scope scope=OptiFineModelRenderer.begin(new Matrix4f().translate(0,0,-4),RenderSystem.getProjectionMatrix(),1,1,1,1,0x00f000f0))
            {
                FloatBuffer color=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
                check(color.get(0)==1&&color.get(1)==1&&color.get(2)==1,"native constant color bypasses stale Minecraft cache");
                Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
                int width=shaders.getField("atlasSizeX").getInt(null),height=shaders.getField("atlasSizeY").getInt(null);
                check(width==4&&height==4,"per-albedo native atlas dimensions");out.addProperty("atlasWidth",width);out.addProperty("atlasHeight",height);
                out.add("fileNormal",pixels(2));out.add("fileSpecular",pixels(3));
                check(Arrays.equals(read(2),new int[]{128,128,255,255}),"raw normal bytes");
                check(Arrays.equals(read(3),new int[]{96,34,67,201}),"OldPBR/raw specular bytes unchanged");
            }
            ModelForm a=new ModelForm(),b=new ModelForm();
            a.materials.getOrCreate("").smoothness.set(.5F);a.materials.getOrCreate("").metallic.set(1F);
            a.materials.getOrCreate("").sss.set(.5F);a.materials.getOrCreate("").pixelEmission.set(.5F);a.materials.getOrCreate("").relief.set(.8F);
            b.materials.getOrCreate("").smoothness.set(.9F);
            Texture first=FormPbr.resolveAlbedo(a,"body",source,albedo),second=FormPbr.resolveAlbedo(b,"body",source,albedo);
            check(first.id!=albedo.id&&first.id!=second.id,"per-form albedo isolation");
            first.bind();int firstSpec;
            try(OptiFineModelRenderer.Scope scope=OptiFineModelRenderer.begin(new Matrix4f().translate(0,0,-4),RenderSystem.getProjectionMatrix(),1,1,1,1,0x00f000f0))
            {
                check(Arrays.equals(read(3),new int[]{128,255,160,127}),"LabPBR sliders bytes");firstSpec=texture(3);
                out.add("sliderSpecular",pixels(3));out.add("reliefNormal",pixels(2));
                check(read(2)[0]!=128,"height gradient reaches normal map");
                second.bind();check(read(3)[0]==230,"albedo rebound inside native scope refreshes PBR");
                first.bind();check(texture(3)==firstSpec&&read(3)[0]==128,"second form does not overwrite first");
                float[] p={0,0,0,1,0,0,0,1,0},n={0,0,1,0,0,1,0,0,1},uv={0,0,1,0,0,1};
                ModelVAO vao=new ModelVAO(new ModelVAOData(p,n,new float[12],uv));
                try
                {
                    vao.renderOptiFine();int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);out.addProperty("program",program);
                    for(String name:new String[]{"at_tangent","mc_midTexCoord"})
                    {int location=GL20.glGetAttribLocation(program,name);out.addProperty(name,location);if(location>=0){IntBuffer value=BufferUtils.createIntBuffer(4);GL20.glGetVertexAttrib(location,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED,value);check(value.get(0)==1,name+" array enabled");}}
                }
                finally {vao.delete();}
            }
            a.materialPbrOverrides.put("",new HashMap<String,Float>());a.materialPbrOverrides.get("").put(TrackId.MATERIAL_PROP_SMOOTHNESS,.25F);
            Texture animated=FormPbr.resolveAlbedo(a,"body",source,albedo);check(animated.id==first.id,"animated slider keeps stable albedo");animated.bind();
            try(OptiFineModelRenderer.Scope scope=OptiFineModelRenderer.begin(new Matrix4f().translate(0,0,-4),RenderSystem.getProjectionMatrix(),1,1,1,1,0x00f000f0))
            {check(texture(3)==firstSpec&&read(3)[0]==64,"animated material override updates existing GPU map");out.add("animatedSpecular",pixels(3));}
            int[] ids=OptiFinePbr.maps(first.id,0,0);manager.delete(source);
            check(!GL11.glIsTexture(ids[0])&&!GL11.glIsTexture(ids[1]),"generated maps deleted with albedo");
            out.addProperty("isolated",true);out.addProperty("animated",true);out.addProperty("deleted",true);
            int error=GL11.glGetError();check(error==0,"GL error "+error);
        }
        finally
        {
            manager.delete(source);manager.delete(normal);manager.delete(spec);for(Path file:files)Files.deleteIfExists(file);
            for(int i=0;i<4;i++){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+i);GlStateManager.bindTexture(oldTextures[i]);}GlStateManager.setActiveTexture(oldActive);
            GlStateManager.color(oldColor.get(0),oldColor.get(1),oldColor.get(2),oldColor.get(3));GL11.glColor4f(oldColor.get(0),oldColor.get(1),oldColor.get(2),oldColor.get(3));
        }
    }
    private static int texture(int unit)
    {int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);int id=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GlStateManager.setActiveTexture(active);return id;}
    private static JsonArray pixels(int unit){JsonArray out=new JsonArray();for(int b:read(unit))out.add(b);return out;}
    static int[] read(int unit)
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);int[] params={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},old=new int[4];
        for(int i=0;i<4;i++){old[i]=GL11.glGetInteger(params[i]);GL11.glPixelStorei(params[i],i==0?1:0);}
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            ByteBuffer buffer=BufferUtils.createByteBuffer(w*h*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
            int[] out=new int[4];for(int i=0;i<4;i++)out[i]=buffer.get(i)&255;return out;
        }
        finally{for(int i=0;i<4;i++)GL11.glPixelStorei(params[i],old[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GlStateManager.setActiveTexture(active);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
