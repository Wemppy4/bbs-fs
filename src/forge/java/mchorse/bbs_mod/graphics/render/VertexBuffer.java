package mchorse.bbs_mod.graphics.render;

import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** An owned native VAO/VBO, with upload and draw restoring their caller's bindings. */
public final class VertexBuffer implements AutoCloseable
{
    public enum Usage { STATIC(GL15.GL_STATIC_DRAW), DYNAMIC(GL15.GL_DYNAMIC_DRAW); final int gl; Usage(int gl){this.gl=gl;} }
    private final Usage usage;
    private int vao, buffer, count, mode;
    public VertexBuffer(Usage usage) { this.usage=usage; this.vao=GL30.glGenVertexArrays(); this.buffer=GL15.glGenBuffers(); }
    public void bind(){GL30.glBindVertexArray(this.vao);}
    public static void unbind(){GL30.glBindVertexArray(0);}
    public void upload(BufferBuilder.BuiltBuffer built)
    {
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try
        {
            this.bind(); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,built.getBuffer(),this.usage.gl);
            int index=0,offset=0;
            for(VertexFormat.Element element:built.format.elements)
            {
                if(element.integer) GL30.glVertexAttribIPointer(index,element.count,element.type,built.format.getVertexSizeByte(),(long)offset);
                else GL20.glVertexAttribPointer(index,element.count,element.type,element.normalized,built.format.getVertexSizeByte(),(long)offset);
                GL20.glEnableVertexAttribArray(index++); offset+=element.bytes();
            }
            for(;index<6;index++)GL20.glDisableVertexAttribArray(index);
            this.count=built.count; this.mode=built.mode.glMode;
        }
        finally { GL30.glBindVertexArray(oldVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer); }
    }
    public void draw(Matrix4f modelView,Matrix4f projection,ShaderProgram shader)
    {
        this.draw(modelView,projection,shader,modelView.normal(new Matrix3f()));
    }
    public void draw(Matrix4f modelView,Matrix4f projection,ShaderProgram shader,Matrix3f normalMat)
    {
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        ModelVAORenderer.setupUniforms(shader,modelView,normalMat==null?new Matrix3f():normalMat);
        if(shader.projectionMat!=null)shader.projectionMat.set(projection);
        shader.bind();
        try { this.bind(); GL11.glDrawArrays(this.mode,0,this.count); }
        finally { GL30.glBindVertexArray(oldVao); shader.unbind(); }
    }
    @Override public void close()
    {
        if(this.vao!=0)GL30.glDeleteVertexArrays(this.vao);
        if(this.buffer!=0)GL15.glDeleteBuffers(this.buffer);
        this.vao=this.buffer=this.count=0;
    }
}
