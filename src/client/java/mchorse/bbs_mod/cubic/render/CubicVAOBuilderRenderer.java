package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.utils.CollectionUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CubicVAOBuilderRenderer implements ICubicRenderer
{
    private final static Vector3f v1 = new Vector3f();
    private final static Vector3f v2 = new Vector3f();
    private final static Vector3f v3 = new Vector3f();

    private final static Vector3f n1 = new Vector3f();
    private final static Vector3f n2 = new Vector3f();
    private final static Vector3f n3 = new Vector3f();

    private final static Vector2f u1 = new Vector2f();
    private final static Vector2f u2 = new Vector2f();
    private final static Vector2f u3 = new Vector2f();

    private Map<ModelGroup, Map<String, ModelVAO>> model;

    /* Temporary variables to avoid allocating and GC vectors */
    private ModelVertex modelVertex = new ModelVertex();
    private Vector3f normal = new Vector3f();
    private Vector4f vertex = new Vector4f();

    public CubicVAOBuilderRenderer(Map<ModelGroup, Map<String, ModelVAO>> model)
    {
        this.model = model;
    }

    /** Accumulated triangle data for a single material within a group. */
    private static class MaterialBucket
    {
        private final List<Float> vertices = new ArrayList<>();
        private final List<Float> normals = new ArrayList<>();
        private final List<Float> uvs = new ArrayList<>();
    }

    @Override
    public void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {}

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        /* A group with shape-keyed meshes morphs per frame, so it gets no VAO and renders immediate —
         * the rest of the model still bakes and rides the GPU (see ModelInstance#renderHybrid). */
        for (ModelMesh mesh : group.meshes)
        {
            if (!mesh.data.isEmpty())
            {
                return false;
            }
        }

        /* Split a group's geometry by material so each material can be drawn with its own
         * texture: cubes belong to the default material (""), meshes to their own. */
        Map<String, MaterialBucket> buckets = new LinkedHashMap<>();

        for (ModelCube cube : group.cubes)
        {
            this.renderCube(buckets.computeIfAbsent("", (k) -> new MaterialBucket()), stack, group, cube);
        }

        for (ModelMesh mesh : group.meshes)
        {
            String material = mesh.material == null ? "" : mesh.material;

            this.renderMesh(buckets.computeIfAbsent(material, (k) -> new MaterialBucket()), stack, model, group, mesh);
        }

        Map<String, ModelVAO> groupVaos = new HashMap<>();

        for (Map.Entry<String, MaterialBucket> entry : buckets.entrySet())
        {
            MaterialBucket bucket = entry.getValue();

            if (bucket.vertices.isEmpty())
            {
                continue;
            }

            float[] v = CollectionUtils.toArray(bucket.vertices);
            float[] n = CollectionUtils.toArray(bucket.normals);
            float[] u = CollectionUtils.toArray(bucket.uvs);
            float[] t = BBSRendering.calculateTangents(v, n, u);

            groupVaos.put(entry.getKey(), new ModelVAO(new ModelVAOData(v, n, t, u)));
        }

        if (!groupVaos.isEmpty())
        {
            this.model.put(group, groupVaos);
        }

        return false;
    }

    private void renderCube(MaterialBucket bucket, MatrixStack stack, ModelGroup group, ModelCube cube)
    {
        stack.push();
        CubicCubeRenderer.moveToPivot(stack, cube.pivot);
        CubicCubeRenderer.rotate(stack, cube.rotate);
        CubicCubeRenderer.moveBackFromPivot(stack, cube.pivot);

        for (ModelQuad quad : cube.quads)
        {
            int count = quad.vertices.size();

            if (count != 3 && count != 4)
            {
                continue;
            }

            this.writeVertex(bucket, stack, group, quad.vertices.get(0));
            this.writeVertex(bucket, stack, group, quad.vertices.get(1));
            this.writeVertex(bucket, stack, group, quad.vertices.get(2));

            if (count == 4)
            {
                this.writeVertex(bucket, stack, group, quad.vertices.get(0));
                this.writeVertex(bucket, stack, group, quad.vertices.get(2));
                this.writeVertex(bucket, stack, group, quad.vertices.get(3));
            }
        }

        stack.pop();
    }

    private void renderMesh(MaterialBucket bucket, MatrixStack stack, Model model, ModelGroup group, ModelMesh mesh)
    {
        stack.push();
        CubicCubeRenderer.moveToPivot(stack, mesh.origin);
        CubicCubeRenderer.rotate(stack, mesh.rotate);
        CubicCubeRenderer.moveBackFromPivot(stack, mesh.origin);

        ModelData baseData = mesh.baseData;

        for (int i = 0, c = baseData.vertices.size() / 3; i < c; i++)
        {
            v1.set(baseData.vertices.get(i * 3));
            v2.set(baseData.vertices.get(i * 3 + 1));
            v3.set(baseData.vertices.get(i * 3 + 2));

            n1.set(baseData.normals.get(i * 3));
            n2.set(baseData.normals.get(i * 3 + 1));
            n3.set(baseData.normals.get(i * 3 + 2));

            u1.set(baseData.uvs.get(i * 3));
            u2.set(baseData.uvs.get(i * 3 + 1));
            u3.set(baseData.uvs.get(i * 3 + 2));

            /* Write vertices */
            this.normal.set(n1.x, n1.y, n1.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v1, u1, model);
            this.writeVertex(bucket, stack, group, this.modelVertex, this.normal);

            this.normal.set(n2.x, n2.y, n2.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v2, u2, model);
            this.writeVertex(bucket, stack, group, this.modelVertex, this.normal);

            this.normal.set(n3.x, n3.y, n3.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v3, u3, model);
            this.writeVertex(bucket, stack, group, this.modelVertex, this.normal);
        }

        stack.pop();
    }

    /** Write a cube vertex with its own normal (bevel strips shade smooth), transformed per vertex. */
    private void writeVertex(MaterialBucket bucket, MatrixStack stack, ModelGroup group, ModelVertex vertex)
    {
        this.normal.set(vertex.normal.x, vertex.normal.y, vertex.normal.z);
        stack.peek().getNormalMatrix().transform(this.normal);

        this.writeVertex(bucket, stack, group, vertex, this.normal);
    }

    private void writeVertex(MaterialBucket bucket, MatrixStack stack, ModelGroup group, ModelVertex vertex, Vector3f normal)
    {
        this.vertex.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1);
        stack.peek().getPositionMatrix().transform(this.vertex);

        bucket.vertices.add(this.vertex.x);
        bucket.vertices.add(this.vertex.y);
        bucket.vertices.add(this.vertex.z);
        bucket.normals.add(normal.x);
        bucket.normals.add(normal.y);
        bucket.normals.add(normal.z);
        bucket.uvs.add(vertex.uv.x);
        bucket.uvs.add(vertex.uv.y);
    }
}