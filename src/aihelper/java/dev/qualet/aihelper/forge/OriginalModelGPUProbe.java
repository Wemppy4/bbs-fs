package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.render.BufferBuilder;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.graphics.render.VertexBuffer;
import mchorse.bbs_mod.graphics.render.VertexFormat;
import mchorse.bbs_mod.graphics.render.VertexFormats;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

/** Actual original GLSL model/picker programs, native integer attributes, and caller-state checks. */
public final class OriginalModelGPUProbe
{
    private static final int SIZE = 64;
    private static JsonObject last;
    private static final int[] PIXEL_STORE = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
        GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_UNPACK_ALIGNMENT,
        GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS};
    private static final int[] ENABLES = {GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_ALPHA_TEST,
        GL11.GL_FOG, GL11.GL_SCISSOR_TEST, GL11.GL_DITHER, GL30.GL_FRAMEBUFFER_SRGB,
        GL11.GL_LIGHT0, GL11.GL_LIGHT1};

    public static JsonObject handle(JsonObject request)
    {
        if (request.has("run") && request.get("run").getAsBoolean()) last = run();
        if (last == null) throw new IllegalStateException("Run the GPU probe first");
        return last;
    }

    private static JsonObject run()
    {
        JsonObject out = new JsonObject();
        State original = new State();
        int originalLightmap = RenderSystem.getShaderTextureOverride(2);
        int originalOverlay = RenderSystem.getShaderTextureOverride(1);
        Map<String,Integer> originalBindings = bindings();
        List<Integer> textures = new ArrayList<>();
        int fbo = 0, sentinelVao = 0, sentinelBuffer = 0;
        VertexBuffer vao = null;
        boolean scopesRestored = true;
        try
        {
            /* Lazy access compiles the catalog once and preserves an existing caller program. */
            ShaderProgram model = BBSShaders.getModel(), picker = BBSShaders.getPickerModelsProgram();
            out.addProperty("modelLinked", GL20.glGetProgrami(model.getId(), GL20.GL_LINK_STATUS) != 0);
            out.addProperty("pickerLinked", GL20.glGetProgrami(picker.getId(), GL20.GL_LINK_STATUS) != 0);
            out.addProperty("integerUV2Location", GL20.glGetAttribLocation(picker.getId(), "UV2"));
            out.addProperty("pixelArtAvailable", BBSShaders.getPixelArtProgram() != null);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for (int parameter : PIXEL_STORE) GL11.glPixelStorei(parameter, parameter == GL11.GL_PACK_ALIGNMENT || parameter == GL11.GL_UNPACK_ALIGNMENT ? 1 : 0);
            for (int capability : ENABLES) GL11.glDisable(capability);
            /* Keep GlStateManager's state cache coherent for the states production renderers use. */
            GlStateManager.disableDepth(); GlStateManager.disableCull(); GlStateManager.disableAlpha(); GlStateManager.disableFog();
            GlStateManager.disableLight(0); GlStateManager.disableLight(1);
            GlStateManager.colorMask(true,true,true,true);
            RenderSystem.setShaderTexture(1,0);
            int output = texture(SIZE,255,255,255,0,textures);
            int white = texture(16,255,255,255,255,textures);
            int coloredLight = texture(16,64,255,128,255,textures);
            fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.viewport(0,0,SIZE,SIZE);
            out.addProperty("framebufferStatus",GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GlStateManager.bindTexture(white);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1); GlStateManager.bindTexture(white);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE2); GlStateManager.bindTexture(coloredLight);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE3); GlStateManager.bindTexture(white);
            RenderSystem.setShaderTexture(2,0);
            sentinelVao=GL30.glGenVertexArrays(); sentinelBuffer=GL15.glGenBuffers();
            GL30.glBindVertexArray(sentinelVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,sentinelBuffer);
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_DST_ALPHA,GL11.GL_ONE_MINUS_DST_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT,GL14.GL_FUNC_SUBTRACT);
            BufferBuilder builder = new BufferBuilder();
            BufferBuilder.BuiltBuffer ordinary = quad(builder,-1,240,240,1F);
            int hash = hash(ordinary.getBuffer());
            BufferBuilder.BuiltBuffer hurt = quad(builder,3<<16,240,240,1F);
            out.addProperty("immutableBuilderReuse",hash==hash(ordinary.getBuffer()) && ordinary.getBuffer().isReadOnly());
            out.addProperty("vertexStride",ordinary.format.getVertexSizeByte());
            ByteBuffer vertex=ordinary.getBuffer();
            out.addProperty("defaultOverlayRow",vertex.getInt(40));
            out.addProperty("defaultLightU",vertex.getInt(44));
            vao = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            Map<String,Integer> before=bindings(); vao.upload(ordinary);
            out.addProperty("uploadStateRestored",before.equals(bindings()));
            before=bindings(); clear(); vao.draw(new Matrix4f(),new Matrix4f(),model);
            scopesRestored &= before.equals(bindings());
            int[] normal=pixel(); out.add("normal",rgba(normal));
            save("original-model-gpu-normal.png");
            vao.upload(hurt); before=bindings(); clear(); vao.draw(new Matrix4f(),new Matrix4f(),model);
            scopesRestored &= before.equals(bindings());
            out.add("hurt",rgba(pixel()));
            save("original-model-gpu-hurt.png");
            RenderSystem.setShaderTexture(2,coloredLight);
            out.addProperty("explicitLightmapOverride",RenderSystem.getShaderTexture(2)==coloredLight);
            vao.upload(ordinary); before=bindings(); clear(); vao.draw(new Matrix4f(),new Matrix4f(),model);
            scopesRestored &= before.equals(bindings()); out.add("coloredLight",rgba(pixel()));
            RenderSystem.setShaderTexture(2,0);
            out.addProperty("nativeLightmapRestored",RenderSystem.getShaderTexture(2)==white);
            /* >65535 catches short truncation; the nontrivial Target also checks addition across bytes. */
            int boneId=0x12345, target=0x100321;
            vao.upload(quad(builder,-1,boneId,0,1F)); picker.getUniform("Target").set(target);
            before=bindings(); clear(); vao.draw(new Matrix4f(),new Matrix4f(),picker);
            scopesRestored &= before.equals(bindings());
            int[] picked=pixel(); out.add("picker",rgba(picked));
            out.addProperty("pickerExpected",target+boneId);
            out.addProperty("pickerActual",picked[0]|picked[1]<<8|picked[2]<<16);
            save("original-model-gpu-picker.png");
            /* Integer uniforms must retain bits above the exact float range. RGB intentionally wraps at 24 bits. */
            int largeTarget=0x01000003;
            picker.getUniform("Target").set(largeTarget);
            before=bindings(); clear(); vao.draw(new Matrix4f(),new Matrix4f(),picker);
            scopesRestored &= before.equals(bindings());
            IntBuffer targetValue=BufferUtils.createIntBuffer(16);
            GL20.glGetUniform(picker.getId(),GL20.glGetUniformLocation(picker.getId(),"Target"),targetValue);
            out.addProperty("largeTargetUniform",targetValue.get(0));
            out.addProperty("largeTargetExpected",largeTarget);
            int[] largePicked=pixel();
            out.addProperty("largePickerExpected",(largeTarget+boneId)&0xffffff);
            out.addProperty("largePickerActual",largePicked[0]|largePicked[1]<<8|largePicked[2]<<16);
            vao.upload(quad(builder,-1,boneId,0,0.05F)); clear(); vao.draw(new Matrix4f(),new Matrix4f(),picker);
            out.addProperty("alphaDiscard",pixel()[3]==0);
            /* Different nested programs must restore the enclosing program and its overlay/lightmap bindings. */
            before=bindings(); model.bind(); Map<String,Integer> nested=bindings();
            picker.bind(); picker.unbind(); out.addProperty("nestedShaderRestored",nested.equals(bindings()));
            model.unbind(); scopesRestored &= before.equals(bindings());
            out.addProperty("scopedStateRestored",scopesRestored);
            out.addProperty("glError",GL11.glGetError());
        }
        catch (Exception error)
        {
            out.addProperty("failure",error.toString());
            error.printStackTrace();
        }
        finally
        {
            RenderSystem.setShaderTexture(2,originalLightmap);
            RenderSystem.setShaderTexture(1,originalOverlay);
            if (vao!=null) vao.close();
            if (sentinelVao!=0) GL30.glDeleteVertexArrays(sentinelVao);
            if (sentinelBuffer!=0) GL15.glDeleteBuffers(sentinelBuffer);
            if (fbo!=0) GL30.glDeleteFramebuffers(fbo);
            for (int texture:textures) GlStateManager.deleteTexture(texture);
            original.restore();
            out.addProperty("outerStateRestored",originalBindings.equals(bindings()) && original.matches());
        }
        out.addProperty("ok",!out.has("failure"));
        return out;
    }

    private static BufferBuilder.BuiltBuffer quad(BufferBuilder builder,int overlay,int lightU,int lightV,float alpha)
    {
        builder.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        float[][] vertices={{-.8F,-.8F},{.8F,-.8F},{.8F,.8F},{-.8F,-.8F},{.8F,.8F},{-.8F,.8F}};
        for (float[] point:vertices)
        {
            builder.vertex(point[0],point[1],0).color(.2F,.4F,.8F,alpha).texture(.5F,.5F).normal(0,1,0).light(lightU,lightV);
            if (overlay>=0) builder.overlay(overlay);
            builder.next();
        }
        return builder.end();
    }
    private static int hash(ByteBuffer bytes) { int value=1; while(bytes.hasRemaining()) value=31*value+bytes.get(); return value; }
    private static int texture(int size,int r,int g,int b,int a,List<Integer> textures)
    {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int id=GlStateManager.generateTexture(); textures.add(id); GlStateManager.bindTexture(id);
        ByteBuffer pixels=BufferUtils.createByteBuffer(size*size*4);
        for(int i=0;i<size*size;i++)pixels.put((byte)r).put((byte)g).put((byte)b).put((byte)a);
        pixels.flip(); GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return id;
    }
    private static void clear() { GlStateManager.clearColor(0,0,0,0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); }
    private static int[] pixel()
    {
        ByteBuffer bytes=BufferUtils.createByteBuffer(4); GL11.glReadPixels(SIZE/2,SIZE/2,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        return new int[]{bytes.get(0)&255,bytes.get(1)&255,bytes.get(2)&255,bytes.get(3)&255};
    }
    private static JsonArray rgba(int[] value) { JsonArray array=new JsonArray();for(int channel:value)array.add(channel);return array; }
    private static void save(String name) throws Exception
    {
        ByteBuffer bytes=BufferUtils.createByteBuffer(SIZE*SIZE*4); GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int at=(y*SIZE+x)*4;image.setRGB(x,SIZE-1-y,(bytes.get(at+3)&255)<<24|(bytes.get(at)&255)<<16|(bytes.get(at+1)&255)<<8|(bytes.get(at+2)&255));}
        File folder=new File(Minecraft.getMinecraft().gameDir,"screenshots/ai");folder.mkdirs();ImageIO.write(image,"png",new File(folder,name));
    }
    private static Map<String,Integer> bindings()
    {
        Map<String,Integer> values=new LinkedHashMap<>();
        int[] query={GL20.GL_CURRENT_PROGRAM,GL30.GL_VERTEX_ARRAY_BINDING,GL15.GL_ARRAY_BUFFER_BINDING,
            GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING,GL13.GL_ACTIVE_TEXTURE,GL20.GL_BLEND_EQUATION_RGB,
            GL20.GL_BLEND_EQUATION_ALPHA,GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,
            GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL30.GL_READ_FRAMEBUFFER_BINDING};
        for(int parameter:query)values.put(Integer.toString(parameter),GL11.glGetInteger(parameter));
        values.put("blend",GL11.glIsEnabled(GL11.GL_BLEND)?1:0);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for(int unit=0;unit<4;unit++){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);values.put("texture"+unit,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));}
        GlStateManager.setActiveTexture(active);
        return values;
    }
    private static final class State
    {
        final Map<String,Integer> bindings=bindings();
        final IntBuffer viewport=BufferUtils.createIntBuffer(16);
        final FloatBuffer clear=BufferUtils.createFloatBuffer(16);
        final ByteBuffer mask=BufferUtils.createByteBuffer(16);
        final int[] pixelStore=new int[PIXEL_STORE.length];
        final boolean[] enabled=new boolean[ENABLES.length];
        final int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        State()
        {
            GL11.glGetInteger(GL11.GL_VIEWPORT,viewport); GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,clear); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,mask);
            for(int i=0;i<PIXEL_STORE.length;i++)pixelStore[i]=GL11.glGetInteger(PIXEL_STORE[i]);
            for(int i=0;i<ENABLES.length;i++)enabled[i]=GL11.glIsEnabled(ENABLES[i]);
        }
        int get(int parameter){return bindings.get(Integer.toString(parameter));}
        boolean matches()
        {
            if(pack!=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)||unpack!=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING))return false;
            for(int i=0;i<PIXEL_STORE.length;i++)if(pixelStore[i]!=GL11.glGetInteger(PIXEL_STORE[i]))return false;
            for(int i=0;i<ENABLES.length;i++)if(enabled[i]!=GL11.glIsEnabled(ENABLES[i]))return false;
            IntBuffer currentViewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,currentViewport);
            FloatBuffer currentClear=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,currentClear);
            ByteBuffer currentMask=BufferUtils.createByteBuffer(16);GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,currentMask);
            for(int i=0;i<4;i++)if(viewport.get(i)!=currentViewport.get(i)||clear.get(i)!=currentClear.get(i)||mask.get(i)!=currentMask.get(i))return false;
            return true;
        }
        void restore()
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,get(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,get(GL30.GL_READ_FRAMEBUFFER_BINDING));
            GL30.glBindVertexArray(get(GL30.GL_VERTEX_ARRAY_BINDING)); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,get(GL15.GL_ARRAY_BUFFER_BINDING));
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,get(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)); GL20.glUseProgram(get(GL20.GL_CURRENT_PROGRAM));
            for(int unit=0;unit<4;unit++){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);GlStateManager.bindTexture(bindings.get("texture"+unit));}
            GlStateManager.setActiveTexture(get(GL13.GL_ACTIVE_TEXTURE));
            GL20.glBlendEquationSeparate(get(GL20.GL_BLEND_EQUATION_RGB),get(GL20.GL_BLEND_EQUATION_ALPHA));
            GlStateManager.tryBlendFuncSeparate(get(GL14.GL_BLEND_SRC_RGB),get(GL14.GL_BLEND_DST_RGB),get(GL14.GL_BLEND_SRC_ALPHA),get(GL14.GL_BLEND_DST_ALPHA));
            if(bindings.get("blend")!=0)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            for(int i=0;i<ENABLES.length;i++)if(enabled[i])GL11.glEnable(ENABLES[i]);else GL11.glDisable(ENABLES[i]);
            /* Repair cached enable flags as well as the actual context. */
            GlStateManager.enableDepth();if(!enabled[0])GlStateManager.disableDepth();
            GlStateManager.enableCull();if(!enabled[1])GlStateManager.disableCull();
            GlStateManager.enableAlpha();if(!enabled[2])GlStateManager.disableAlpha();
            GlStateManager.enableFog();if(!enabled[3])GlStateManager.disableFog();
            GlStateManager.enableLight(0);if(!enabled[7])GlStateManager.disableLight(0);
            GlStateManager.enableLight(1);if(!enabled[8])GlStateManager.disableLight(1);
            for(int i=0;i<PIXEL_STORE.length;i++)GL11.glPixelStorei(PIXEL_STORE[i],pixelStore[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack); GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
            GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
            GlStateManager.colorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
        }
    }
    private OriginalModelGPUProbe() {}
}