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
    private int vao, buffer, tangents, midUvs, count, mode;
    private boolean hasTangents;
    private VertexFormat format;
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
            this.format=built.format;
            this.uploadTangents(built);
        }
        finally { GL30.glBindVertexArray(oldVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer); }
    }

    /** Shape keys and cloth use this immediate stream, after their final deformation. */
    private void uploadTangents(BufferBuilder.BuiltBuffer built)
    {
        this.hasTangents=false;
        if(built.mode!=VertexFormat.DrawMode.TRIANGLES||built.count%3!=0)return;
        int position=-1,normal=-1,texture=-1,offset=0;
        for(VertexFormat.Element element:built.format.elements)
        {
            if(element==VertexFormat.Element.POSITION)position=offset;
            if(element==VertexFormat.Element.NORMAL)normal=offset;
            if(element==VertexFormat.Element.TEXTURE)texture=offset;
            offset+=element.bytes();
        }
        if(position<0||normal<0||texture<0)return;
        float[] positions=new float[built.count*3],normals=new float[built.count*3],uvs=new float[built.count*2];
        java.nio.ByteBuffer bytes=built.getBuffer();int stride=built.format.getVertexSizeByte();
        for(int i=0;i<built.count;i++)
        {
            for(int j=0;j<3;j++){positions[i*3+j]=bytes.getFloat(i*stride+position+j*4);normals[i*3+j]=bytes.getFloat(i*stride+normal+j*4);}
            for(int j=0;j<2;j++)uvs[i*2+j]=bytes.getFloat(i*stride+texture+j*4);
        }
        float[] values=mchorse.bbs_mod.graphics.ModelTangents.calculate(positions,normals,uvs);
        java.nio.FloatBuffer data=org.lwjgl.BufferUtils.createFloatBuffer(values.length);data.put(values).flip();
        if(this.tangents==0)this.tangents=GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.tangents);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,this.usage.gl);this.hasTangents=true;
        float[] mid=mchorse.bbs_mod.graphics.ModelTangents.midUvs(uvs);data=org.lwjgl.BufferUtils.createFloatBuffer(mid.length);data.put(mid).flip();
        if(this.midUvs==0)this.midUvs=GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.midUvs);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,this.usage.gl);
    }
    public void draw(Matrix4f modelView,Matrix4f projection,ShaderProgram shader)
    {
        this.draw(modelView,projection,shader,modelView.normal(new Matrix3f()));
    }
    public void draw(Matrix4f modelView,Matrix4f projection,ShaderProgram shader,Matrix3f normalMat)
    {
        if (shader.isWorldModel())
        {
            try (mchorse.bbs_mod.graphics.OptiFineModelRenderer.Scope pass = mchorse.bbs_mod.graphics.OptiFineModelRenderer.begin(modelView,projection,1,1,1,1,0x00f000f0))
            {
                mchorse.bbs_mod.graphics.OptiFineModelRenderer.interleaved(this.buffer,this.format);
                if(this.hasTangents)mchorse.bbs_mod.graphics.OptiFineModelRenderer.tangents(this.tangents,this.midUvs);
                GL11.glDrawArrays(this.mode,0,this.count);
            }
            return;
        }
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
        if(this.tangents!=0)GL15.glDeleteBuffers(this.tangents);
        if(this.midUvs!=0)GL15.glDeleteBuffers(this.midUvs);
        this.vao=this.buffer=this.tangents=this.midUvs=this.count=0;
    }
}
