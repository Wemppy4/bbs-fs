package mchorse.bbs_mod.graphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.lang.reflect.*;
import java.util.*;

/** Model textures have their own dimensions, independent of Minecraft's block atlas. */
final class OptiFineTextureSize implements AutoCloseable
{
    private static final Access API=discover();
    private final int width=(Integer)get(API.width),height=(Integer)get(API.height);
    private final Map<Integer,int[]> previous=new LinkedHashMap<>();

    void bind(int width,int height)
    {
        set(API.width,width);set(API.height,height);
        Object uniform=get(API.uniform);int program=(Integer)invoke(API.getProgram,uniform);
        if(program>0&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM))
        {
            if(!previous.containsKey(program))previous.put(program,((int[])invoke(API.getValue,uniform)).clone());
            invoke(API.setValue,uniform,width,height);
        }
    }
    @Override public void close()
    {
        set(API.width,width);set(API.height,height);
        Object uniform=get(API.uniform);int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),uniformProgram=(Integer)invoke(API.getProgram,uniform);
        for(Map.Entry<Integer,int[]> entry:previous.entrySet())
        {
            GL20.glUseProgram(entry.getKey());invoke(API.setProgram,uniform,entry.getKey());int[] value=entry.getValue();invoke(API.setValue,uniform,value[0],value[1]);
        }
        invoke(API.setProgram,uniform,uniformProgram);GL20.glUseProgram(program);
    }
    private static Access discover()
    {
        try{return new Access(Class.forName("net.optifine.shaders.Shaders"));}
        catch(ReflectiveOperationException error){throw new IllegalStateException("OptiFine texture size API",error);}
    }
    private static Object get(Field field){try{return field.get(null);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void set(Field field,Object value){try{field.set(null,value);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static Object invoke(Method method,Object owner,Object... values){try{return method.invoke(owner,values);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static final class Access
    {
        final Field width,height,uniform;
        final Method getProgram,setProgram,getValue,setValue;
        Access(Class<?> shaders)throws ReflectiveOperationException
        {
            width=shaders.getField("atlasSizeX");height=shaders.getField("atlasSizeY");uniform=shaders.getField("uniform_atlasSize");Class<?> type=uniform.getType();
            getProgram=type.getMethod("getProgram");setProgram=type.getMethod("setProgram",int.class);getValue=type.getMethod("getValue");setValue=type.getMethod("setValue",int.class,int.class);
        }
    }
}
