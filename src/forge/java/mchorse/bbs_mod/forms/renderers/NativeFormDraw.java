package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forge.studio.NativePickingShader;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import mchorse.bbs_mod.graphics.OptiFineModelRenderer;
import mchorse.bbs_mod.graphics.NativeFormVertices;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;

/** GL boundary for vanilla block/item/TESR/font draws: native attributes, original tint and overlay. */
public final class NativeFormDraw implements AutoCloseable
{
    private static int program;
    private final State state = new State();
    private final OptiFineShaders.LocalPass local;
    private final NativePickingShader.Scope picking;
    private final OptiFineModelRenderer.Scope pack;
    private final NativeFormVertices vertices;
    private final OptiFineShaders.OverlayPass overlay;
    private final NativeArrays arrays;

    public NativeFormDraw(FormRenderingContext context, Color tint, Color overlay, boolean vertexLight)
    {
        boolean shaderPack = !context.ui && !context.isPicking() && OptiFineShaders.isWorldPass();
        this.local = shaderPack ? null : OptiFineShaders.localPass();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        NativeTextureRenderer.loadMatrix(context.stack.peek().getPositionMatrix());
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().entityRenderer.enableLightmap();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, context.light & 65535, context.light >>> 16 & 65535);
        this.pack = shaderPack ? OptiFineModelRenderer.beginNative(context.stack.peek().getPositionMatrix(),
            RenderSystem.getProjectionMatrix(),tint.r,tint.g,tint.b,tint.a,context.light) : null;
        this.arrays = shaderPack ? null : new NativeArrays();
        this.overlay = shaderPack ? OptiFineShaders.overlay(overlay.r,overlay.g,overlay.b,overlay.a) : null;
        this.vertices = shaderPack ? new NativeFormVertices(tint,context.light,vertexLight,this.overlay::apply) : null;
        this.picking = NativePickingShader.open(context.isPicking() ? context.getPickingIndex() : -1);
        if (!context.isPicking() && !shaderPack)
        {
            if (program == 0) program = createProgram();
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Texture"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Lightmap"), 1);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "Tint"), tint.r, tint.g, tint.b, tint.a);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "Overlay"), overlay.r, overlay.g, overlay.b, overlay.a);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "VertexLight"), vertexLight ? 1 : 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "Light"), (context.light & 65535) / 256F + 0.03125F, (context.light >>> 16 & 65535) / 256F + 0.03125F);
        }
    }

    private static int createProgram()
    {
        int vertex = shader(GL20.GL_VERTEX_SHADER, "#version 120\nvarying vec2 uv; varying vec2 light; varying vec4 color; void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;uv=gl_MultiTexCoord0.xy;light=gl_MultiTexCoord1.xy/256.0+vec2(0.03125);color=gl_Color;}");
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 120\nuniform sampler2D Texture;uniform sampler2D Lightmap;uniform vec4 Tint;uniform vec4 Overlay;uniform vec2 Light;uniform float VertexLight;varying vec2 uv;varying vec2 light;varying vec4 color;void main(){vec4 c=texture2D(Texture,uv)*color*Tint;if(c.a<0.003)discard;c.rgb=mix(c.rgb,Overlay.rgb,clamp(Overlay.a,0.0,1.0));c.rgb*=texture2D(Lightmap,vec2(mix(Light.x,max(Light.x,light.x),VertexLight),Light.y)).rgb;gl_FragColor=c;}");
        int result = GL20.glCreateProgram();
        GL20.glAttachShader(result, vertex); GL20.glAttachShader(result, fragment); GL20.glLinkProgram(result);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(result, 8192));
        return result;
    }

    private static int shader(int type, String source)
    {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 8192));
        return shader;
    }

    @Override public void close()
    {
        this.picking.close();
        if (this.vertices != null) this.vertices.close();
        if (this.overlay != null) this.overlay.close();
        if (this.pack != null) this.pack.close();
        if (this.arrays != null) this.arrays.close();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix();
        if (this.local != null) this.local.close();
        this.state.close();
    }

    /** Client-memory upload pointers must never be interpreted as offsets into a BBS VBO. */
    private static final class NativeArrays implements AutoCloseable
    {
        private final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int client=GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        NativeArrays()
        {
            GL30.glBindVertexArray(0);GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0);
            for(int i=0,n=GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);i<n;i++)GL20.glDisableVertexAttribArray(i);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            for(int i=0,n=GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS);i<n;i++)
            {GL13.glClientActiveTexture(GL13.GL_TEXTURE0+i);GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);}
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
        public void close()
        {
            GL30.glBindVertexArray(0);GL11.glPopClientAttrib();GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL13.glClientActiveTexture(client);
        }
    }

    /** Restore real GL and the vanilla cache together; glPopAttrib alone desynchronizes the cache. */
    public static final class State implements AutoCloseable
    {
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
        private final int[] textures = new int[2];
        private final boolean[] enabled = new boolean[2];
        public State()
        {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            for (int i=0;i<2;i++) { GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+i); textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); enabled[i]=GL11.glIsEnabled(GL11.GL_TEXTURE_2D); }
            GlStateManager.setActiveTexture(active);
        }
        public void close()
        {
            GL20.glUseProgram(program);
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.depthFunc(depthFunc);GlStateManager.depthMask(mask);GL11.glCullFace(cullFace);
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lx,ly);
            for(int i=0;i<2;i++){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+i);GlStateManager.bindTexture(textures[i]);if(enabled[i])GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();}
            GlStateManager.setActiveTexture(active);GlStateManager.matrixMode(mode);
        }
    }
}
