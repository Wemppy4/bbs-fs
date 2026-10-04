package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import java.nio.ByteBuffer;

/** Cached native VBO for the original tessellated gizmo rings and sphere. */
public final class NativeColorBuffer
{
    private int id = GL15.glGenBuffers();
    private final int usage;
    private int count;

    public NativeColorBuffer(boolean dynamic) { this.usage=dynamic?GL15.GL_DYNAMIC_DRAW:GL15.GL_STATIC_DRAW; }
    public void upload(UIVertexBuffer builder)
    {
        if (!builder.getVertexFormat().equals(DefaultVertexFormats.POSITION_COLOR))
            throw new IllegalArgumentException("Color VBO requires POSITION_COLOR vertices");
        builder.finishDrawing();
        this.count=builder.getVertexCount();
        ByteBuffer bytes=builder.getByteBuffer().duplicate();
        bytes.position(0);bytes.limit(this.count*DefaultVertexFormats.POSITION_COLOR.getSize());
        int previous=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.id);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,bytes,this.usage); }
        finally { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previous);builder.reset(); }
    }
    public void draw(Matrix4f modelView, Matrix4f projection)
    {
        int previous=NativeColorProgram.bind(modelView,projection);
        int buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,this.id);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glVertexPointer(3,GL11.GL_FLOAT,16,0L);GL11.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,16,12L);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,this.count);
        }
        finally
        {
            GL11.glPopClientAttrib();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL20.glUseProgram(previous);
        }
    }
    public void close() { if(this.id!=0){GL15.glDeleteBuffers(this.id);this.id=0;} }
}