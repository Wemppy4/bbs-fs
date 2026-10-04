package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.graphics.render.VertexFormat;
import mchorse.bbs_mod.graphics.render.VertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Original rigid model layouts, with buffers owned and released alongside their VAOs. */
public class ModelVAO implements IModelVAO
{
    private int vao, vao2, positions, normals, texCoords, tangents, midUvs, count;

    public ModelVAO(ModelVAOData data) { this.upload(data); }

    public void delete()
    {
        if (this.vao != 0) GL30.glDeleteVertexArrays(this.vao);
        if (this.vao2 != 0) GL30.glDeleteVertexArrays(this.vao2);
        if (this.positions != 0) GL15.glDeleteBuffers(this.positions);
        if (this.normals != 0) GL15.glDeleteBuffers(this.normals);
        if (this.texCoords != 0) GL15.glDeleteBuffers(this.texCoords);
        if (this.tangents != 0) GL15.glDeleteBuffers(this.tangents);
        if (this.midUvs != 0) GL15.glDeleteBuffers(this.midUvs);
        this.vao = this.vao2 = this.positions = this.normals = this.texCoords = this.tangents = this.midUvs = this.count = 0;
    }

    public void upload(ModelVAOData data)
    {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        if (previousVao == this.vao || previousVao == this.vao2) previousVao = 0;
        if (previousBuffer == this.positions || previousBuffer == this.normals || previousBuffer == this.texCoords || previousBuffer == this.tangents || previousBuffer == this.midUvs) previousBuffer = 0;
        this.delete();
        try
        {
            this.vao = GL30.glGenVertexArrays();
            this.vao2 = GL30.glGenVertexArrays();
            this.positions = upload(data.vertices());
            this.normals = upload(data.normals());
            this.texCoords = upload(data.texCoords());
            this.tangents = upload(mchorse.bbs_mod.graphics.ModelTangents.calculate(data.vertices(),data.normals(),data.texCoords()));
            this.midUvs = upload(mchorse.bbs_mod.graphics.ModelTangents.midUvs(data.texCoords()));
            GL30.glBindVertexArray(this.vao);
            pointer(this.positions, Attributes.POSITION, 3);
            pointer(this.normals, Attributes.NORMAL, 3);
            pointer(this.texCoords, Attributes.TEXTURE_UV, 2);
            GL20.glDisableVertexAttribArray(Attributes.COLOR);
            GL20.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
            GL20.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);

            GL30.glBindVertexArray(this.vao2);
            pointer(this.positions, 0, 3);
            pointer(this.texCoords, 1, 2);
            GL20.glDisableVertexAttribArray(2);
            GL20.glDisableVertexAttribArray(3);
            this.count = data.vertices().length / 3;
        }
        catch (RuntimeException error)
        {
            this.delete();
            throw error;
        }
        finally
        {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    private static int upload(float[] data)
    {
        int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        try { GL15.glBufferData(GL15.GL_ARRAY_BUFFER, ModelBuffers.wrap(data), GL15.GL_STATIC_DRAW); }
        catch (RuntimeException error) { GL15.glDeleteBuffers(buffer); throw error; }
        return buffer;
    }

    private static void pointer(int buffer, int attribute, int components)
    {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL20.glVertexAttribPointer(attribute, components, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(attribute);
    }

    @Override
    public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
    {
        boolean modelLayout = format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(modelLayout ? this.vao : this.vao2);
        try
        {
            if (modelLayout)
            {
                GL20.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
                GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & 0xffff, overlay >> 16 & 0xffff);
                GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & 0xffff, light >> 16 & 0xffff);
            }
            else
            {
                GL30.glVertexAttribI2i(2, light & 0xffff, light >> 16 & 0xffff);
                GL20.glVertexAttrib4f(3, r, g, b, a);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.count);
        }
        finally { GL30.glBindVertexArray(previousVao); }
    }

    public void renderOptiFine()
    {
        mchorse.bbs_mod.graphics.OptiFineModelRenderer.arrays(this.positions, this.normals, this.texCoords, this.tangents, this.midUvs);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.count);
    }
}
