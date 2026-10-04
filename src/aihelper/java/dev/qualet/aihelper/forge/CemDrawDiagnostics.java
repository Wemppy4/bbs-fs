package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.render.vao.*;
import mchorse.bbs_mod.graphics.render.VertexFormat;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.lang.reflect.Field;
import java.util.*;

/** Temporary test-only subclasses inspect the real draw scope; original VBOs still draw. */
final class CemDrawDiagnostics
{
    static JsonArray render(ModelInstance model,Runnable draw)
    {
        JsonArray result=new JsonArray();List<Replacement> replacements=new ArrayList<>();
        try
        {
            for(Map<String,ModelVAO> group:model.getVaos().values())for(Map.Entry<String,ModelVAO> entry:group.entrySet())
            {
                Spy spy=new Spy(entry.getValue(),entry.getKey(),result,model);replacements.add(new Replacement(entry,entry.getValue(),spy));entry.setValue(spy);
            }
            draw.run();
        }
        finally{for(Replacement replacement:replacements){replacement.entry.setValue(replacement.original);replacement.spy.delete();}}
        return result;
    }
    private static final class Replacement
    {
        final Map.Entry<String,ModelVAO> entry;final ModelVAO original;final Spy spy;
        Replacement(Map.Entry<String,ModelVAO> entry,ModelVAO original,Spy spy){this.entry=entry;this.original=original;this.spy=spy;}
    }
    private static final class Spy extends ModelVAO
    {
        final ModelVAO original;final String material;final JsonArray result;final ModelInstance model;
        Spy(ModelVAO original,String material,JsonArray result,ModelInstance model)
        {super(new ModelVAOData(new float[0],new float[0],new float[0],new float[0]));this.original=original;this.material=material;this.result=result;this.model=model;}
        @Override public void renderOptiFine()
        {
            JsonObject out=new JsonObject();out.addProperty("material",material);out.addProperty("textureLink",String.valueOf(model.getTexture()));
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try
            {
                int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);out.addProperty("program",program);
                FloatBuffer color=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);out.add("color",floats(color,4));
                for(int unit=0;unit<4;unit++)
                {
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);JsonObject texture=new JsonObject();texture.addProperty("id",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                    texture.addProperty("width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH));texture.addProperty("height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT));
                    out.add("texture"+unit,texture);
                    if(texture.get("width").getAsInt()>0&&texture.get("height").getAsInt()>0)
                    {JsonArray pixel=new JsonArray();for(int value:OptiFinePbrProbe.read(unit))pixel.add(value);texture.add("pixel",pixel);}
                    if(unit==1){FloatBuffer coord=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS,coord);texture.add("coord",floats(coord,4));}
                }
                out.addProperty("lastBound",BBSModClient.getTextures().getLastBound().id);
                for(String name:new String[]{"tex","texture","gtexture","lightmap","normals","specular","entityId"})
                {int location=GL20.glGetUniformLocation(program,name);if(location>=0){IntBuffer data=BufferUtils.createIntBuffer(4);GL20.glGetUniform(program,location,data);out.addProperty(name,data.get(0));}}
                int location=GL20.glGetUniformLocation(program,"entityColor");if(location>=0){FloatBuffer data=BufferUtils.createFloatBuffer(4);GL20.glGetUniform(program,location,data);out.add("entityColor",floats(data,4));}
                for(String name:new String[]{"positions","normals","texCoords"})
                {
                    Field field=ModelVAO.class.getDeclaredField(name);field.setAccessible(true);int old=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                    try{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,field.getInt(original));FloatBuffer data=BufferUtils.createFloatBuffer(name.equals("texCoords")?2:3);GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER,0L,data);out.add("vbo_"+name,floats(data,data.capacity()));}
                    finally{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,old);}
                }
                out.addProperty("glError",GL11.glGetError());
            }
            catch(Throwable error){out.addProperty("error",error.toString());}
            finally{GlStateManager.setActiveTexture(active);result.add(out);}
            original.renderOptiFine();
        }
        @Override public void render(VertexFormat format,float r,float g,float b,float a,int light,int overlay)
        {JsonObject out=new JsonObject();out.addProperty("local",true);result.add(out);original.render(format,r,g,b,a,light,overlay);}
    }
    private static JsonArray floats(FloatBuffer data,int count){JsonArray out=new JsonArray();for(int i=0;i<count;i++)out.add(data.get(i));return out;}
}
