package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;

/** Native vanilla equipment boundary. Captures the evaluated attachment frame, never the model's pose. */
public final class NativeEquipmentRenderer
{
    private static int program;
    private NativeEquipmentRenderer() {}

    public static void draw(MatrixStack stack, Color color, int light, Runnable render)
    {
        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix());
        Color tint = color.copy();
        FormTranslucentQueue.DrawCommand command = new FormTranslucentQueue.DrawCommand(matrix.getTranslation(new Vector3f()), true, true)
        {
            @Override public void draw() { drawNow(matrix, tint, light, render); }
        };
        /* Native item renderers choose their own textures and layers while drawing. Replay the
         * complete attachment in sorted order instead of pretending it is a modern RenderLayer. */
        FormTranslucentQueue.add(command);
    }

    private static void drawNow(Matrix4f matrix, Color color, int light, Runnable render)
    {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), shader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL), textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        FloatBuffer rgba = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, rgba);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        try
        {
            NativeTextureRenderer.loadMatrix(matrix);
            GlStateManager.enableDepth(); GlStateManager.enableBlend(); GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 1F / 255F);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xffff, light >>> 16);
            boolean shaders = mchorse.bbs_mod.graphics.OptiFineShaders.isWorldPass();
            try (mchorse.bbs_mod.graphics.OptiFineModelRenderer.Scope pack = shaders
                ? mchorse.bbs_mod.graphics.OptiFineModelRenderer.beginNative(matrix,
                    mchorse.bbs_mod.graphics.render.RenderSystem.getProjectionMatrix(),color.r,color.g,color.b,color.a,light) : null;
                 mchorse.bbs_mod.graphics.OptiFineShaders.LocalPass local = shaders ? null : mchorse.bbs_mod.graphics.OptiFineShaders.localPass())
            {
            if (!shaders)
            {
            ensureProgram(); GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Albedo"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Lightmap"), 1);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "Tint"), color.r, color.g, color.b, color.a);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "HasLightmap"), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == 0 ? 0 : 1);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            render.run();
            }
        }
        finally
        {
            GL20.glUseProgram(shader);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(mode);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); GlStateManager.bindTexture(texture);
            if (textured) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(active);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.depthMask(mask); GlStateManager.depthFunc(depthFunc); GlStateManager.alphaFunc(alphaFunc, alphaRef);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.color(rgba.get(0), rgba.get(1), rgba.get(2), rgba.get(3));
        }
    }

    private static void ensureProgram()
    {
        if (program != 0) return;
        int fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        int candidate = GL20.glCreateProgram();
        try
        {
            /* A fragment-only compatibility program retains vanilla's lighting, texture matrices,
             * baked vertex colours and modded item renderers while applying the form colour once. */
            GL20.glShaderSource(fragment, "#version 120\nuniform sampler2D Albedo; uniform sampler2D Lightmap; uniform vec4 Tint; uniform bool HasLightmap; void main(){ vec4 c=texture2D(Albedo,gl_TexCoord[0].xy)*gl_Color*Tint; if(c.a<0.00392157)discard; if(HasLightmap)c.rgb*=texture2D(Lightmap,gl_TexCoord[1].xy).rgb; gl_FragColor=c; }");
            GL20.glCompileShader(fragment);
            if (GL20.glGetShaderi(fragment, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(fragment, 8192));
            GL20.glAttachShader(candidate, fragment); GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate, 8192));
            program = candidate; candidate = 0;
        }
        finally { GL20.glDeleteShader(fragment); if (candidate != 0) GL20.glDeleteProgram(candidate); }
    }
}
