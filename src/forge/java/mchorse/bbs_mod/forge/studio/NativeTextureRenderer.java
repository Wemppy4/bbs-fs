package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Forge backend for the original textured form triangles. The GLSL 1.20 program
 * keeps the modern shader's tint, alpha cutoff, overlay and lightmap operations;
 * only its vertex input and matrix/lightmap bindings use the 1.12 pipeline.
 */
public final class NativeTextureRenderer
{
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final List<Draw> TRANSLUCENT = new ArrayList<>();
    private static int passDepth;
    private static int program;

    private NativeTextureRenderer() {}

    /** Nestable so a film can sort all actors together and an isolated preview can own its pass. */
    public static void beginPass() { passDepth++; }

    public static void endPass()
    {
        if (--passDepth != 0) return;
        TRANSLUCENT.sort(Comparator.comparingDouble((Draw draw) -> draw.distance).reversed());
        try
        {
            for (Draw draw : TRANSLUCENT) draw.draw();
        }
        finally
        {
            TRANSLUCENT.clear();
        }
    }

    public static Matrix4f currentMatrix()
    {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        return new Matrix4f().set(MATRIX);
    }

    public static void loadMatrix(Matrix4f matrix)
    {
        MATRIX.clear();
        matrix.get(MATRIX);
        GL11.glLoadMatrix(MATRIX);
    }

    /** Original billboard behaviour: preserve translation/scale, replace rotation with camera axes. */
    public static void faceCamera()
    {
        Matrix4f matrix = currentMatrix();
        Vector3f scale = matrix.getScale(new Vector3f());
        matrix.m00(1).m01(0).m02(0).m10(0).m11(1).m12(0).m20(0).m21(0).m22(1);
        loadMatrix(matrix.scale(scale));
    }

    public static void render(NativeTextureMesh mesh, Texture texture, Form form, Color color,
                              boolean shading, boolean linear, boolean mipmap, boolean ui, Matrix3fc normalMatrix)
    {
        if (mesh == null || texture == null) return;
        Draw draw = new Draw(mesh, texture, form, color, shading, linear, mipmap, ui, normalMatrix);
        boolean translucent = texture.hasTranslucency() || color.a < 1F || linear || mipmap;
        if (translucent && FormTranslucentQueue.isActive() && !NativePickingShader.isActive())
        {
            boolean split = color.a >= 1F && !mchorse.bbs_mod.graphics.OptiFineShaders.isWorldPass();
            if (split) { draw.mode = 1; draw.draw(); }
            draw.mode = split ? 2 : 0;
            boolean flat = form instanceof mchorse.bbs_mod.forms.forms.BillboardForm;
            draw.writeDepth = !flat;
            Vector3f origin = draw.matrix.getTranslation(new Vector3f());
            Vector3f normal = flat ? new Vector3f(draw.matrix.m00(), draw.matrix.m01(), draw.matrix.m02())
                .cross(draw.matrix.m10(), draw.matrix.m11(), draw.matrix.m12()) : null;
            FormTranslucentQueue.add(new FormTranslucentQueue.DrawCommand(origin, normal, false, !flat)
            {
                @Override public void draw() { draw.draw(); }
            });
        }
        else if (!ui && translucent && passDepth > 0 && !NativePickingShader.isActive())
        {
            boolean split = color.a >= 1F && !mchorse.bbs_mod.graphics.OptiFineShaders.isWorldPass();
            if (split)
            {
                draw.mode = 1;
                draw.draw();
            }
            draw.mode = split ? 2 : 0;
            draw.writeDepth = false;
            TRANSLUCENT.add(draw);
        }
        else draw.draw();
    }

    private static final class Draw
    {
        final NativeTextureMesh mesh;
        final Texture texture;
        final Matrix4f matrix = currentMatrix();
        final double distance = matrix.m30() * matrix.m30() + matrix.m31() * matrix.m31() + matrix.m32() * matrix.m32();
        final Color color;
        final Color overlay;
        final boolean shading, linear, mipmap, ui;
        final float lightX, lightY;
        final Matrix3f normal;
        final Vector3f light0,light1;
        int mode;
        boolean writeDepth = true;

        Draw(NativeTextureMesh mesh, Texture texture, Form form, Color color,
             boolean shading, boolean linear, boolean mipmap, boolean ui, Matrix3fc normal)
        {
            this.mesh = mesh;
            this.texture = texture;
            this.color = color.copy();
            this.color.r = MathUtils.clamp(this.color.r, 0F, 1F);
            this.color.g = MathUtils.clamp(this.color.g, 0F, 1F);
            this.color.b = MathUtils.clamp(this.color.b, 0F, 1F);
            this.color.a = MathUtils.clamp(this.color.a, 0F, 1F);
            this.overlay = form.overlayColor.get().copy();
            this.shading = shading;
            this.linear = linear;
            this.mipmap = mipmap;
            this.ui = ui;
            this.lightX = ui ? 240F : OpenGlHelper.lastBrightnessX;
            this.lightY = ui ? 240F : OpenGlHelper.lastBrightnessY;
            this.normal = shading ? new Matrix3f(normal) : null;
            this.light0 = shading ? mchorse.bbs_mod.graphics.render.RenderSystem.shaderLight(0) : null;
            this.light1 = shading ? mchorse.bbs_mod.graphics.render.RenderSystem.shaderLight(1) : null;
        }

        void draw()
        {
            if (!this.texture.isValid()) return;
            State state = new State();
            GlStateManager.pushMatrix();
            try
            {
                loadMatrix(this.matrix);
                GlStateManager.enableDepth();
                GlStateManager.depthMask(this.writeDepth);
                GlStateManager.enableCull();
                GlStateManager.disableLighting();
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                Minecraft.getMinecraft().entityRenderer.enableLightmap();
                BBSModClient.getTextures().bindTexture(this.texture);
                int oldMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                int oldMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
                int oldMax = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
                int oldFilter = this.texture.getFilter();
                try
                {
                    this.texture.setFilterMipmap(this.linear, this.mipmap);
                    boolean shaders = !this.ui && !NativePickingShader.isActive() && mchorse.bbs_mod.graphics.OptiFineShaders.isWorldPass();
                    try (mchorse.bbs_mod.graphics.OptiFineModelRenderer.Scope pack = shaders
                        ? mchorse.bbs_mod.graphics.OptiFineModelRenderer.beginNative(this.matrix,
                            mchorse.bbs_mod.graphics.render.RenderSystem.getProjectionMatrix(),1,1,1,1,
                            (int)this.lightX | (int)this.lightY << 16) : null;
                         mchorse.bbs_mod.graphics.OptiFineShaders.LocalPass local = shaders ? null : mchorse.bbs_mod.graphics.OptiFineShaders.localPass())
                    {
                    if (shaders) { GlStateManager.enableAlpha(); GlStateManager.alphaFunc(GL11.GL_GREATER,0.1F); }
                    else useProgram(this);
                    BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                    buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                    for (int i = 0, count = this.mesh.positions.length / 3; i < count; i++)
                    {
                        int p = i * 3, uv = i * 2;
                        buffer.pos(this.mesh.positions[p], this.mesh.positions[p + 1], this.mesh.positions[p + 2])
                            .tex(this.mesh.uvs[uv], this.mesh.uvs[uv + 1])
                            .color(this.color.r, this.color.g, this.color.b, this.color.a)
                            .normal(this.mesh.normals[p], this.mesh.normals[p + 1], this.mesh.normals[p + 2]).endVertex();
                    }
                    Tessellator.getInstance().draw();
                    }
                }
                finally
                {
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
                    GlStateManager.bindTexture(this.texture.id);
                    this.texture.setFilter(oldFilter);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, oldMin);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, oldMag);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, oldMax);
                }
            }
            finally
            {
                GlStateManager.popMatrix();
                state.restore();
            }
        }
    }

    private static void useProgram(Draw draw)
    {
        if (NativePickingShader.isActive())
        {
            NativePickingShader.bind();
            return;
        }
        if (program == 0)
        {
            int vertex = compile(GL20.GL_VERTEX_SHADER,
                "#version 120\n"
                + "uniform int Shading; uniform mat3 NormalMatrix; uniform vec3 Light0; uniform vec3 Light1; varying vec4 vertexColor; varying vec2 uv; varying float distanceToCamera;\n"
                + "void main() { gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;"
                + "vec3 n = normalize(NormalMatrix * gl_Normal);"
                + "float d = max(0.0, dot(normalize(Light0), n)) + max(0.0, dot(normalize(Light1), n));"
                + "vertexColor = vec4(gl_Color.rgb * (Shading != 0 ? min(1.0, d * 0.6 + 0.4) : 1.0), gl_Color.a);"
                + "uv = gl_MultiTexCoord0.xy; distanceToCamera = length((gl_ModelViewMatrix * gl_Vertex).xyz); }\n");
            int fragment = compile(GL20.GL_FRAGMENT_SHADER,
                "#version 120\n"
                + "uniform sampler2D Texture; uniform sampler2D Lightmap; uniform vec2 Light; uniform vec4 Overlay;"
                + "uniform int PassMode; uniform int FogMode; varying vec4 vertexColor; varying vec2 uv; varying float distanceToCamera;\n"
                + "void main() { vec4 color = texture2D(Texture, uv); if(color.a < 0.1) discard; color *= vertexColor;"
                + "if(PassMode == 1 && color.a < 0.999) discard; if(PassMode == 2 && color.a >= 0.999) discard;"
                + "color.rgb = mix(color.rgb, Overlay.rgb, Overlay.a); color *= texture2D(Lightmap, (Light + 8.0) / 256.0);"
                + "float fog = 1.0; if(FogMode == 9729) fog = clamp((gl_Fog.end - distanceToCamera) * gl_Fog.scale, 0.0, 1.0);"
                + "else if(FogMode == 2048) fog = exp(-gl_Fog.density * distanceToCamera);"
                + "else if(FogMode == 2049) { float f = gl_Fog.density * distanceToCamera; fog = exp(-f * f); }"
                + "gl_FragColor = vec4(mix(gl_Fog.color.rgb, color.rgb, fog), color.a); }\n");
            int linked = GL20.glCreateProgram();
            try
            {
                GL20.glAttachShader(linked, vertex);
                GL20.glAttachShader(linked, fragment);
                GL20.glLinkProgram(linked);
                if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                    throw new IllegalStateException("BBS texture form shader: " + GL20.glGetProgramInfoLog(linked, 8192));
                program = linked;
            }
            finally
            {
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(fragment);
                if (program == 0) GL20.glDeleteProgram(linked);
            }
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Texture"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Lightmap"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Shading"), draw.shading ? 1 : 0);
        if(draw.shading)
        {
            FloatBuffer normal=BufferUtils.createFloatBuffer(9); draw.normal.get(normal);
            GL20.glUniformMatrix3(GL20.glGetUniformLocation(program,"NormalMatrix"),false,normal);
            GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light0"),draw.light0.x,draw.light0.y,draw.light0.z);
            GL20.glUniform3f(GL20.glGetUniformLocation(program,"Light1"),draw.light1.x,draw.light1.y,draw.light1.z);
        }
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "PassMode"), draw.mode);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "FogMode"), !draw.ui && GL11.glIsEnabled(GL11.GL_FOG) ? GL11.glGetInteger(GL11.GL_FOG_MODE) : 0);
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "Light"), draw.lightX, draw.lightY);
        GL20.glUniform4f(GL20.glGetUniformLocation(program, "Overlay"), draw.overlay.r, draw.overlay.g, draw.overlay.b,
            draw.shading ? MathUtils.clamp(draw.overlay.a, 0F, 1F) : 0F);
    }

    private static int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        {
            String error = GL20.glGetShaderInfoLog(shader, 8192);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("BBS texture form shader: " + error);
        }
        return shader;
    }

    /** Restore through GlStateManager so its state cache agrees with OpenGL afterwards. */
    private static final class State
    {
        final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), shader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int[] binding = new int[2];
        final boolean[] enabled = new boolean[2];

        State()
        {
            for (int i = 0; i < 2; i++)
            {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
                this.binding[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                this.enabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            }
            GlStateManager.setActiveTexture(this.active);
        }

        void restore()
        {
            GL20.glUseProgram(this.shader);
            if (this.lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (this.cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (this.blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (this.alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (this.depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(this.depthMask);
            GlStateManager.tryBlendFuncSeparate(this.srcRGB, this.dstRGB, this.srcAlpha, this.dstAlpha);
            for (int i = 0; i < 2; i++)
            {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager.bindTexture(this.binding[i]);
                if (this.enabled[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            }
            GlStateManager.setActiveTexture(this.active);
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
