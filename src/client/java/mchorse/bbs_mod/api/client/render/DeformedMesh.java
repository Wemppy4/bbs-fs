package mchorse.bbs_mod.api.client.render;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.GeometryCache;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * A triangle mesh drawn with a model's bone in place of cubes it hides (see
 * {@link ModelMeshReplacement}): its texture coordinates and triangles are fixed, its positions and
 * normals are set per pose. It is drawn through the model's own material path — the material's
 * texture, the form's and the bone's colour, light and overlay, translucency, picking, shaders — the
 * way the welded band of a model is.
 *
 * <p>Points are in the model's root frame, in blocks: the frame the bones' matrices are composed in
 * from the model's root, before the form's own transform.</p>
 *
 * <p>Several poses of one mesh can be drawn in a frame (a form drawn at more than one moment), so each
 * pose is uploaded into a buffer of its own, named by its key. Client thread only; {@link #delete}
 * frees the GPU buffers.</p>
 */
public final class DeformedMesh
{
    /** The model material the mesh is drawn in: its texture and visibility; "" for the default one. */
    public final String material;

    private final float[] texCoords;
    private final int[] indices;

    private final float[] positions;
    private final float[] normals;
    private final float[] tangents;
    private long key;

    private int texCoordBuffer = -1;
    private int elementBuffer = -1;
    private FloatBuffer staging;

    private final GeometryCache<Entry> cache = new GeometryCache<>(Entry::new);
    private Entry prepared;

    /**
     * @param texCoords two floats a vertex, in the texture's 0..1 space
     * @param indices   three vertex indices a triangle, counter-clockwise from the outside
     */
    public DeformedMesh(String material, float[] texCoords, int[] indices)
    {
        int vertices = texCoords.length / 2;

        this.material = material == null ? "" : material;
        this.texCoords = texCoords.clone();
        this.indices = indices.clone();
        this.positions = new float[vertices * 3];
        this.normals = new float[vertices * 3];
        this.tangents = BBSRendering.isIrisLoaded() ? new float[vertices * 4] : null;
    }

    public int getVertexCount()
    {
        return this.texCoords.length / 2;
    }

    /**
     * Sets this pose: three floats a vertex of positions and of normals, and the key that names the
     * pose. A key already drawn in this frame reuses that upload, so a key names one set of numbers.
     * The arrays are copied.
     */
    public void pose(long key, float[] positions, float[] normals)
    {
        System.arraycopy(positions, 0, this.positions, 0, this.positions.length);
        System.arraycopy(normals, 0, this.normals, 0, this.normals.length);
        this.key = key;
    }

    /* What BBS's model renderer reads; the arrays are the mesh's own, not copies. */

    public float[] positions()
    {
        return this.positions;
    }

    public float[] normals()
    {
        return this.normals;
    }

    public float[] texCoords()
    {
        return this.texCoords;
    }

    public int[] indices()
    {
        return this.indices;
    }

    /** The current pose on the GPU, uploaded first unless this frame already drew it. BBS calls this as it draws the mesh. */
    public IModelVAO prepare()
    {
        if (this.texCoordBuffer == -1)
        {
            this.createStaticBuffers();
        }

        long epoch = RenderFrame.getEpoch();
        Entry entry = RenderFrame.isEnabled() ? this.cache.find(this.key, epoch) : null;

        if (entry == null)
        {
            entry = this.cache.acquire(this.key, epoch);
            entry.upload();
        }

        this.prepared = entry;

        return entry;
    }

    /** The translucent queue replays the last prepared pose at the end of the frame: its buffer must hold until then. */
    public void lend()
    {
        if (this.prepared != null)
        {
            this.prepared.lend(RenderFrame.getEpoch());
        }
    }

    public void delete()
    {
        this.cache.delete();
        this.prepared = null;

        if (this.texCoordBuffer != -1)
        {
            GL30.glDeleteBuffers(this.texCoordBuffer);
            GL30.glDeleteBuffers(this.elementBuffer);

            this.texCoordBuffer = -1;
            this.elementBuffer = -1;
        }
    }

    private void createStaticBuffers()
    {
        int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

        this.texCoordBuffer = GL30.glGenBuffers();
        this.elementBuffer = GL30.glGenBuffers();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.texCoords, GL30.GL_STATIC_DRAW);

        /* Through the array target: binding an element buffer now would rebind it in whatever VAO is current. */
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.elementBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.indices, GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);

        this.staging = BufferUtils.createFloatBuffer(Math.max(this.positions.length, this.tangents == null ? 1 : this.tangents.length));
    }

    /**
     * Per-vertex tangents for the shaders, from each triangle's texture directions: what Iris reads for
     * normal maps. Accumulated over the triangles a vertex is in, then made perpendicular to its normal.
     */
    private void computeTangents()
    {
        float[] t = this.tangents;
        float[] p = this.positions;
        float[] uv = this.texCoords;
        float[] n = this.normals;
        float[] bitangents = new float[p.length];

        java.util.Arrays.fill(t, 0F);

        for (int i = 0; i + 2 < this.indices.length; i += 3)
        {
            int a = this.indices[i];
            int b = this.indices[i + 1];
            int c = this.indices[i + 2];

            float e1x = p[b * 3] - p[a * 3], e1y = p[b * 3 + 1] - p[a * 3 + 1], e1z = p[b * 3 + 2] - p[a * 3 + 2];
            float e2x = p[c * 3] - p[a * 3], e2y = p[c * 3 + 1] - p[a * 3 + 1], e2z = p[c * 3 + 2] - p[a * 3 + 2];
            float du1 = uv[b * 2] - uv[a * 2], dv1 = uv[b * 2 + 1] - uv[a * 2 + 1];
            float du2 = uv[c * 2] - uv[a * 2], dv2 = uv[c * 2 + 1] - uv[a * 2 + 1];
            float det = du1 * dv2 - du2 * dv1;

            if (Math.abs(det) < 1.0e-12F)
            {
                continue;
            }

            float r = 1F / det;
            float tx = (e1x * dv2 - e2x * dv1) * r, ty = (e1y * dv2 - e2y * dv1) * r, tz = (e1z * dv2 - e2z * dv1) * r;
            float bx = (e2x * du1 - e1x * du2) * r, by = (e2y * du1 - e1y * du2) * r, bz = (e2z * du1 - e1z * du2) * r;

            accumulate(t, bitangents, a, tx, ty, tz, bx, by, bz);
            accumulate(t, bitangents, b, tx, ty, tz, bx, by, bz);
            accumulate(t, bitangents, c, tx, ty, tz, bx, by, bz);
        }

        for (int v = 0, c = this.getVertexCount(); v < c; v++)
        {
            float nx = n[v * 3], ny = n[v * 3 + 1], nz = n[v * 3 + 2];
            float tx = t[v * 4], ty = t[v * 4 + 1], tz = t[v * 4 + 2];
            float dot = nx * tx + ny * ty + nz * tz;

            tx -= nx * dot;
            ty -= ny * dot;
            tz -= nz * dot;

            float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);

            if (length < 1.0e-12F)
            {
                /* No texture direction to go by: any axis perpendicular to the normal. */
                tx = Math.abs(nx) < 0.9F ? 1F : 0F;
                ty = Math.abs(nx) < 0.9F ? 0F : 1F;
                tz = 0F;
                dot = nx * tx + ny * ty;
                tx -= nx * dot;
                ty -= ny * dot;
                tz -= nz * dot;
                length = Math.max((float) Math.sqrt(tx * tx + ty * ty + tz * tz), 1.0e-12F);
            }

            tx /= length;
            ty /= length;
            tz /= length;

            /* Handedness: whether the bitangent agrees with normal × tangent. */
            float cx = ny * tz - nz * ty, cy = nz * tx - nx * tz, cz = nx * ty - ny * tx;
            float hand = cx * bitangents[v * 3] + cy * bitangents[v * 3 + 1] + cz * bitangents[v * 3 + 2] < 0F ? -1F : 1F;

            t[v * 4] = tx;
            t[v * 4 + 1] = ty;
            t[v * 4 + 2] = tz;
            t[v * 4 + 3] = hand;
        }
    }

    private static void accumulate(float[] tangents, float[] bitangents, int v, float tx, float ty, float tz, float bx, float by, float bz)
    {
        tangents[v * 4] += tx;
        tangents[v * 4 + 1] += ty;
        tangents[v * 4 + 2] += tz;
        bitangents[v * 3] += bx;
        bitangents[v * 3 + 1] += by;
        bitangents[v * 3 + 2] += bz;
    }

    private void upload(int buffer, float[] data)
    {
        this.staging.clear();
        this.staging.put(data);
        this.staging.flip();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, buffer);
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, this.staging);
    }

    /** One pose on the GPU: dynamic positions, normals and tangents over the mesh's static texture coordinates and triangles. */
    private class Entry extends GeometryCache.Entry implements IModelVAO
    {
        private final int vao;
        private final int vao2;
        private final int position;
        private final int normal;
        private final int tangent;

        private Entry()
        {
            DeformedMesh mesh = DeformedMesh.this;
            int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

            this.vao = GL30.glGenVertexArrays();
            this.vao2 = GL30.glGenVertexArrays();
            this.position = buffer(mesh.positions.length);
            this.normal = buffer(mesh.normals.length);
            this.tangent = buffer(mesh.tangents == null ? 4 : mesh.tangents.length);

            ModelVAO.bindAttributes(this.vao, this.vao2, this.position, this.normal, mesh.texCoordBuffer, this.tangent, mesh.texCoordBuffer, mesh.elementBuffer);

            GL30.glBindVertexArray(currentVAO);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);
        }

        private static int buffer(int floats)
        {
            int buffer = GL30.glGenBuffers();

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, buffer);
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, Math.max(floats, 1) * 4L, GL30.GL_DYNAMIC_DRAW);

            return buffer;
        }

        private void upload()
        {
            DeformedMesh mesh = DeformedMesh.this;
            int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

            mesh.upload(this.position, mesh.positions);
            mesh.upload(this.normal, mesh.normals);

            if (mesh.tangents != null)
            {
                mesh.computeTangents();
                mesh.upload(this.tangent, mesh.tangents);
            }

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);
        }

        @Override
        public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
        {
            ModelVAO.bindForDraw(this.vao, this.vao2, format, r, g, b, a, light, overlay);

            GL30.glDrawElements(GL30.GL_TRIANGLES, DeformedMesh.this.indices.length, GL30.GL_UNSIGNED_INT, 0L);
            GL30.glBindVertexArray(0);
        }

        @Override
        protected void close()
        {
            GL30.glDeleteVertexArrays(this.vao);
            GL30.glDeleteVertexArrays(this.vao2);
            GL30.glDeleteBuffers(this.position);
            GL30.glDeleteBuffers(this.normal);
            GL30.glDeleteBuffers(this.tangent);
        }
    }
}
