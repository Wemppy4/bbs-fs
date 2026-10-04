package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.nio.FloatBuffer;

/** Native position/color drawing. CPU-transformed vertices never inherit Minecraft's view twice. */
public final class NativeColorProgram
{
    private static final float[] color = {1F, 1F, 1F, 1F};
    private static final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f identity = new Matrix4f();
    private static int program;
    private static int projectionUniform;
    private static int modelViewUniform;
    private static int colorUniform;

    public static void setColor(float r, float g, float b, float a) { color[0]=r; color[1]=g; color[2]=b; color[3]=a; }
    public static float[] getColor() { return color; }
    public static Matrix4f projection()
    {
        matrix.clear(); GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix);
        return new Matrix4f(matrix);
    }
    public static void draw(UIVertexBuffer builder)
    {
        int previous = bind(identity, projection());
        try { builder.draw(); }
        finally { GL20.glUseProgram(previous); }
    }
    public static int bind(Matrix4f modelView, Matrix4f projection)
    {
        if (program == 0) create();
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        matrix.clear(); projection.get(matrix); GL20.glUniformMatrix4(projectionUniform, false, matrix);
        matrix.clear(); modelView.get(matrix); GL20.glUniformMatrix4(modelViewUniform, false, matrix);
        GL20.glUniform4f(colorUniform, color[0], color[1], color[2], color[3]);
        return previous;
    }
    private static void create()
    {
        int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nuniform mat4 Projection; uniform mat4 ModelView; varying vec4 vertexColor; void main(){gl_Position=Projection*ModelView*gl_Vertex;vertexColor=gl_Color;}");
        int pixel = 0, linked = 0;
        try
        {
            pixel = compile(GL20.GL_FRAGMENT_SHADER, "#version 120\nuniform vec4 Color; varying vec4 vertexColor; void main(){gl_FragColor=vertexColor*Color;}");
            linked = GL20.glCreateProgram(); GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, pixel); GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, 4096));
            program=linked;
            projectionUniform=GL20.glGetUniformLocation(program,"Projection");
            modelViewUniform=GL20.glGetUniformLocation(program,"ModelView");
            colorUniform=GL20.glGetUniformLocation(program,"Color");
        }
        catch (RuntimeException error) { if(linked!=0)GL20.glDeleteProgram(linked); program=0;throw error; }
        finally { GL20.glDeleteShader(vertex);if(pixel!=0)GL20.glDeleteShader(pixel); }
    }
    private static int compile(int kind, String source)
    {
        int shader=GL20.glCreateShader(kind);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)
        {
            String log=GL20.glGetShaderInfoLog(shader,4096);GL20.glDeleteShader(shader);throw new IllegalStateException("BBS color shader: "+log);
        }
        return shader;
    }
    private NativeColorProgram() {}
}