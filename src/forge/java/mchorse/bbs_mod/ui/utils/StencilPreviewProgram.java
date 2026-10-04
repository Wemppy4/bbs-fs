package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** The original picker_preview shader translated to the native GLSL 1.20 ABI. */
final class StencilPreviewProgram
{
    private static int program;
    private static int target;
    private static int highlight;

    static int bind(int index, int color)
    {
        if (program == 0) create();
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(target, index);
        GL20.glUniform4f(highlight, Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        return previous;
    }

    private static void create()
    {
        int vertex = compile(GL20.GL_VERTEX_SHADER,
            "#version 120\nvarying vec2 texCoord0; varying vec4 vertexColor;\n"
            + "void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;texCoord0=gl_MultiTexCoord0.xy;vertexColor=gl_Color;}");
        int fragment = 0;
        int linked = 0;
        try
        {
            fragment = compile(GL20.GL_FRAGMENT_SHADER,
                "#version 120\nuniform sampler2D Sampler0; uniform int Target; uniform vec4 HighlightColor;\n"
                + "varying vec2 texCoord0; varying vec4 vertexColor;\n"
                + "void main(){vec4 c=texture2D(Sampler0,texCoord0)*vertexColor;if(c.a<1.0)discard;"
                + "vec3 bytes=floor(c.rgb*255.0+0.5);float id=dot(bytes,vec3(1.0,256.0,65536.0));"
                + "if(id!=float(Target))discard;gl_FragColor=HighlightColor;}");
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException("BBS stencil preview: " + GL20.glGetProgramInfoLog(linked, 4096));
            program = linked;
            target = GL20.glGetUniformLocation(program, "Target");
            highlight = GL20.glGetUniformLocation(program, "HighlightColor");
            int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);
            GL20.glUseProgram(previous);
        }
        catch (RuntimeException error)
        {
            if (linked != 0) GL20.glDeleteProgram(linked);
            program = 0;
            throw error;
        }
        finally
        {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
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
            throw new IllegalStateException("BBS stencil preview: " + log);
        }
        return shader;
    }
}