package mchorse.bbs_mod.cubic.data.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModelQuad
{
    public List<ModelVertex> vertices = new ArrayList<>();
    public Vector3f normal = new Vector3f();

    /** Set the quad's flat normal, stamping it on the vertices added so far — call after the vertices. */
    public ModelQuad normal(float x, float y, float z)
    {
        this.normal.set(x, y, z);

        for (ModelVertex vertex : this.vertices)
        {
            vertex.normal.set(x, y, z);
        }

        return this;
    }

    public ModelQuad vertex(float x, float y, float z, float u, float v)
    {
        ModelVertex vertex = new ModelVertex();

        vertex.vertex.set(x, y, z);
        vertex.uv.set(u, v);
        this.vertices.add(vertex);

        return this;
    }

    public ModelQuad vertex(float x, float y, float z, float u, float v, Vector3f normal)
    {
        this.vertex(x, y, z, u, v);
        this.vertices.get(this.vertices.size() - 1).normal.set(normal);

        return this;
    }
}