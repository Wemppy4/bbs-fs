package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import java.io.InputStream;

/** The original BBS Kawase shaders over Forge's framebuffer, without clearing world depth. */
final class KawasePass
{
    private final Framebuffer input, output;
    private final int program;
    private float offset;

    KawasePass(String name, Framebuffer input, Framebuffer output) throws Exception
    {
        this.input = input;
        this.output = output;
        String fragment;
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager()
            .getResource(new ResourceLocation("minecraft", "shaders/program/" + name + ".fsh")).getInputStream())
        {
            fragment = IOUtils.readText(stream);
        }
        fragment = fragment.replace("#version 150", "#version 120").replace("in vec2", "varying vec2")
            .replace("out vec4 fragColor;", "").replace("fragColor =", "gl_FragColor =").replace("texture(", "texture2D(");
        int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nuniform vec2 InSize; varying vec2 texCoord; varying vec2 oneTexel; void main() { gl_Position = gl_Vertex; texCoord = (gl_Vertex.xy + 1.0) * 0.5; oneTexel = 1.0 / InSize; }");
        int pixel = 0;
        int linked = 0;
        try
        {
            pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, pixel);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, 4096));
            this.program = linked;
        }
        catch (RuntimeException error)
        {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw error;
        }
        finally
        {
            GL20.glDeleteShader(vertex);
            if (pixel != 0) GL20.glDeleteShader(pixel);
        }
    }

    private static int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    void setOffset(float offset) { this.offset = offset; }
    void render()
    {
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        output.bindFramebuffer(true);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(input.framebufferTexture);
        int wrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
        int wrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
        /* 1.12 framebuffers use GL_CLAMP, which adds a black border to a blur.
         * Modern SimpleFramebuffer samples the outermost texels at the edge. */
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "DiffuseSampler"), 0);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "Offset"), offset);
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "InSize"), input.framebufferTextureWidth, input.framebufferTextureHeight);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1, -1); GL11.glVertex2f(1, -1);
        GL11.glVertex2f(1, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
        GL20.glUseProgram(previous);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        if (alpha) GlStateManager.enableAlpha();
        if (cull) GlStateManager.enableCull();
    }
    void close() { GL20.glDeleteProgram(program); }
}
