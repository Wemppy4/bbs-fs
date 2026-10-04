package mchorse.bbs_mod.forge.studio;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Exact RGB picking IDs with the original texture alpha mask retained. */
public final class NativePickingShader
{
    private static int program;
    private static int target = -1;
    public static boolean isActive() { return target >= 0; }
    public static Scope open(int index) { return new Scope(index); }

    public static void bind()
    {
        if (program == 0)
        {
            int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nvarying vec2 uv; void main() { gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; uv=gl_MultiTexCoord0.xy; }");
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 120\nuniform sampler2D Texture; uniform vec3 Target; varying vec2 uv; void main() { if(texture2D(Texture, uv).a < 0.1) discard; gl_FragColor=vec4(Target, 1.0); }");
            int linked = GL20.glCreateProgram();
            try
            {
                GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment); GL20.glLinkProgram(linked);
                if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                    throw new IllegalStateException("BBS picking shader: " + GL20.glGetProgramInfoLog(linked, 8192));
                program = linked;
            }
            finally
            {
                GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
                if (program == 0) GL20.glDeleteProgram(linked);
            }
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Texture"), 0);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "Target"), (target & 255) / 255F,
            (target >> 8 & 255) / 255F, (target >> 16 & 255) / 255F);
    }

    private static int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        {
            String error = GL20.glGetShaderInfoLog(shader, 8192); GL20.glDeleteShader(shader);
            throw new IllegalStateException("BBS picking shader: " + error);
        }
        return shader;
    }

    public static final class Scope implements AutoCloseable
    {
        private final int previous = target;
        private final int previousProgram;
        private final boolean dither;
        private final boolean changed;
        private Scope(int index)
        {
            this.changed = index >= 0;
            this.previousProgram = this.changed ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
            this.dither = this.changed && GL11.glIsEnabled(GL11.GL_DITHER);
            if (this.changed)
            {
                target = index;
                GL11.glDisable(GL11.GL_DITHER);
                bind();
            }
        }
        @Override public void close()
        {
            if (!this.changed) return;
            target = this.previous;
            GL20.glUseProgram(this.previousProgram);
            if (this.dither) GL11.glEnable(GL11.GL_DITHER); else GL11.glDisable(GL11.GL_DITHER);
        }
    }

    private NativePickingShader() {}
}
