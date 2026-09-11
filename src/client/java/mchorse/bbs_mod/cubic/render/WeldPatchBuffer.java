package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The welded geometry of one CPU bake, held back as grids until the whole model has been walked. A welded
 * cube tessellates its bands knowing only its own side of every seam — the other side belongs to a cube
 * drawn earlier or later in the tree — so a seam can only be resolved across both of its sides (shared
 * shading normals, a fillet) once everything is in. Pooled: patches and their grids are reused across
 * bakes, so a warm buffer allocates nothing per frame.
 */
public class WeldPatchBuffer
{
    private final int capacity;
    private final List<Patch> patches = new ArrayList<>();
    private int count;

    /** @param capacity the most grid points a patch can hold — (subdivisions + 1) squared. */
    public WeldPatchBuffer(int capacity)
    {
        this.capacity = capacity;
    }

    /** Take the next free patch for an (nS + 1) x (nT + 1) grid; its arrays are ready to fill. */
    public Patch add(ModelGroup group, int nS, int nT)
    {
        if (this.count == this.patches.size())
        {
            this.patches.add(new Patch(this.capacity));
        }

        Patch patch = this.patches.get(this.count++);

        patch.group = group;
        patch.nS = nS;
        patch.nT = nT;

        return patch;
    }

    public int size()
    {
        return this.count;
    }

    public Patch get(int index)
    {
        return this.patches.get(index);
    }

    /** Forget the held geometry; the patches stay allocated for the next bake. */
    public void clear()
    {
        this.count = 0;
    }

    /**
     * One subdivided quad: a grid of finished sub-vertices, row-major with s along the columns and t along
     * the rows, so point (col, row) sits at {@code row * (nS + 1) + col}.
     */
    public static class Patch
    {
        public ModelGroup group;
        public int nS;
        public int nT;

        public final Vector3f[] pos;
        public final Vector3f[] normal;
        public final float[] u;
        public final float[] v;

        private Patch(int capacity)
        {
            this.pos = vectors(capacity);
            this.normal = vectors(capacity);
            this.u = new float[capacity];
            this.v = new float[capacity];
        }

        public int index(int col, int row)
        {
            return row * (this.nS + 1) + col;
        }
    }

    private static Vector3f[] vectors(int count)
    {
        Vector3f[] vectors = new Vector3f[count];

        for (int i = 0; i < count; i++)
        {
            vectors[i] = new Vector3f();
        }

        return vectors;
    }
}
