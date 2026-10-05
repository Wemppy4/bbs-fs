package mchorse.bbs_mod.graphics.render;

import mchorse.bbs_mod.graphics.shader.GlUniform;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.function.Supplier;

/** Bridges BBS draw state to Forge's actual fixed-function GL context. */
public final class RenderSystem
{
    private static ShaderProgram shader;
    private static int overlay, defaultOverlay, whiteLight;
    private static final int[] extraTextures = new int[12];
    private static Vector3f[] shaderLights;
    private static final java.lang.reflect.Field LIGHTMAP = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
        net.minecraft.client.renderer.EntityRenderer.class, "lightmapTexture", "field_78513_d");
    private RenderSystem() {}
    public static void setShader(Supplier<ShaderProgram> supplier) { shader=supplier.get(); }
    public static ShaderProgram getShader() { return shader; }
    public static int getShaderTexture(int unit)
    {
        if (unit==1) return overlay != 0 ? overlay : (defaultOverlay != 0 ? defaultOverlay : (defaultOverlay=createAtlas(true)));
        if (unit==0) return binding(0);
        if (unit==2)
        {
            if (extraTextures[2] != 0) return extraTextures[2];
            int texture=nativeLightmap();
            return texture != 0 ? texture : (whiteLight != 0 ? whiteLight : (whiteLight=createAtlas(false)));
        }
        return unit >= 0 && unit < extraTextures.length ? extraTextures[unit] : 0;
    }
    /** Composite passes reuse unit 1; its current binding is not a lightmap identity. */
    public static int nativeLightmap()
    {
        try
        {
            Object renderer = Minecraft.getMinecraft().entityRenderer;
            net.minecraft.client.renderer.texture.DynamicTexture light = renderer == null ? null
                : (net.minecraft.client.renderer.texture.DynamicTexture) LIGHTMAP.get(renderer);
            return light == null ? 0 : light.getGlTextureId();
        }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot access Minecraft lightmap", error); }
    }
    /** Logical override only; zero means the native/default texture is selected. */
    public static int getShaderTextureOverride(int unit)
    {
        return unit == 1 ? overlay : unit >= 0 && unit < extraTextures.length ? extraTextures[unit] : 0;
    }
    /** Unit 2 accepts an explicit lightmap; zero restores the native Minecraft lightmap. */
    public static void setShaderTexture(int unit,int texture)
    {
        if (unit==1) { overlay=texture; return; }
        if (unit==0)
        {
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GlStateManager.bindTexture(texture); GlStateManager.setActiveTexture(active);
        }
        else if (unit>=0 && unit<extraTextures.length) extraTextures[unit]=texture;
    }
    public static void bindTexture(int texture) { GlStateManager.bindTexture(texture); }
    private static int binding(int unit)
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.setActiveTexture(active); return binding;
    }
    private static int createAtlas(boolean hurt)
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpack=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int row=GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), skipRows=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), skipPixels=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int id=GlStateManager.generateTexture();
        try
        {
            ByteBuffer pixels=BufferUtils.createByteBuffer(16*16*4);
            for (int y=0;y<16;y++) for (int x=0;x<16;x++)
            {
                pixels.put((byte)255).put((byte)(hurt&&y<8?0:255)).put((byte)(hurt&&y<8?0:255));
                pixels.put((byte)(hurt?(y<8?178:(int)((1-x/15F*0.75F)*255)):255));
            }
            pixels.flip(); GlStateManager.bindTexture(id);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1); GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,16,16,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            return id;
        }
        catch (RuntimeException error) { GlStateManager.deleteTexture(id); throw error; }
        finally
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,unpack); GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,row);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,skipRows); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,skipPixels);
            GlStateManager.bindTexture(bound); GlStateManager.setActiveTexture(active);
        }
    }
    private static Matrix4f matrix(int parameter)
    {
        FloatBuffer data=BufferUtils.createFloatBuffer(16); GL11.glGetFloat(parameter,data); return new Matrix4f().set(data);
    }
    public static Matrix4f getModelViewMatrix() { return matrix(GL11.GL_MODELVIEW_MATRIX); }
    public static Matrix4f getProjectionMatrix() { return matrix(GL11.GL_PROJECTION_MATRIX); }
    public static Matrix4f getTextureMatrix()
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        Matrix4f matrix=matrix(GL11.GL_TEXTURE_MATRIX); GlStateManager.setActiveTexture(active); return matrix;
    }
    public static Matrix3f getInverseViewRotationMatrix() { return getModelViewMatrix().get3x3(new Matrix3f()).invert(); }
    public static float getShaderFogStart() { return GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetFloat(GL11.GL_FOG_START):Float.MAX_VALUE/4; }
    public static float getShaderFogEnd() { return GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetFloat(GL11.GL_FOG_END):Float.MAX_VALUE/2; }
    public static float[] getShaderFogColor()
    {
        FloatBuffer value=BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_FOG_COLOR,value);
        return new float[]{value.get(0),value.get(1),value.get(2),value.get(3)};
    }
    public enum FogShape { SPHERE; public int getId(){return 0;} }
    public static FogShape getShaderFogShape() { return FogShape.SPHERE; }
    public static float getShaderGameTime()
    {
        Minecraft mc=Minecraft.getMinecraft();
        return mc.world==null?0:(mc.world.getTotalWorldTime()%24000+mc.getRenderPartialTicks())/24000F;
    }
    public static void setupShaderLights(ShaderProgram shader)
    {
        for (int i=0;i<2;i++)
        {
            GlUniform uniform=shader.getUniform("Light"+i+"_Direction"); if(uniform==null)continue;
            Vector3f direction=shaderLight(i);
            uniform.set(direction.x,direction.y,direction.z);
        }
    }
    public static Vector3f shaderLight(int index)
    {
        if(shaderLights!=null)return new Vector3f(shaderLights[index]);
        if(GL11.glIsEnabled(GL11.GL_LIGHT0+index))
        {
            FloatBuffer vector=BufferUtils.createFloatBuffer(4);GL11.glGetLight(GL11.GL_LIGHT0+index,GL11.GL_POSITION,vector);
            return new Vector3f(vector.get(0),vector.get(1),vector.get(2));
        }
        return new Vector3f(index==0?.2F:-.2F,1F,index==0?-.7F:.7F);
    }
    /** The original 1.20 DiffuseLighting GUI matrix, independent of 1.12 item light state. */
    public static LightScope guiLighting(){return new LightScope();}
    /** gui_light:front, used by the original BBS model and gun inventory models. */
    public static LightScope guiFlatLighting()
    {
        Matrix4f transform=new Matrix4f().scaling(1F,-1F,1F).rotateY(-.3926991F).rotateX(2.3561945F);
        return new LightScope(transform.transformDirection(new Vector3f(.2F,1F,-.7F).normalize()),
            transform.transformDirection(new Vector3f(-.2F,1F,.7F).normalize()));
    }
    public static LightScope lighting(Vector3f first,Vector3f second){return new LightScope(first,second);}
    public static final class LightScope implements AutoCloseable
    {
        private final Vector3f[] previous=shaderLights;
        private LightScope()
        {
            Matrix4f transform=new Matrix4f().rotationYXZ(1.0821041F,3.2375858F,0F).rotateYXZ(-.3926991F,2.3561945F,0F);
            shaderLights=new Vector3f[]{transform.transformDirection(new Vector3f(.2F,1F,-.7F).normalize()),transform.transformDirection(new Vector3f(-.2F,1F,.7F).normalize())};
        }
        private LightScope(Vector3f first,Vector3f second){shaderLights=new Vector3f[]{new Vector3f(first),new Vector3f(second)};}
        @Override public void close(){shaderLights=previous;}
    }
    public static void enableBlend(){GlStateManager.enableBlend();}
    public static void disableBlend(){GlStateManager.disableBlend();}
    public static void defaultBlendFunc(){GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);}
    public static void enableDepthTest(){GlStateManager.enableDepth();}
    public static void disableDepthTest(){GlStateManager.disableDepth();}
    public static void enableCull(){GlStateManager.enableCull();}
    public static void disableCull(){GlStateManager.disableCull();}
    public static void depthFunc(int value){GlStateManager.depthFunc(value);}
    public static void depthMask(boolean value){GlStateManager.depthMask(value);}
}
