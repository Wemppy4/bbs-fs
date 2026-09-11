package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The welded geometry of one CPU bake, held back as grids until the whole model has been walked. A welded
 * cube tessellates its bands knowing only its own side of every seam — the other side belongs to a cube
 * drawn earlier or later in the tree — so a seam can only be resolved across both of its sides (today:
 * shared shading normals) once everything is in. Pooled: patches, seam edges and their grids are reused
 * across bakes, so a warm buffer allocates nothing per frame.
 */
public class WeldPatchBuffer
{
    private static final float EPS_SQ = 1.0e-12F;

    private final int capacity;
    private final List<Patch> patches = new ArrayList<>();
    private int patchCount;

    private final List<SeamEdge> seamEdges = new ArrayList<>();
    private int seamEdgeCount;

    /* Scratch for resolveSeams: one side's shared normals are built here off the other side's ORIGINAL ones,
     * then written back, so the two sides read each other before either changes. */
    private final Vector3f[] sharedA;
    private final Vector3f[] sharedB;
    private final Vector3f sample = new Vector3f();

    /** @param subdivisions the most segments a grid has along either direction. */
    public WeldPatchBuffer(int subdivisions)
    {
        this.capacity = (subdivisions + 1) * (subdivisions + 1);
        this.sharedA = vectors(subdivisions + 1);
        this.sharedB = vectors(subdivisions + 1);
    }

    /** Take the next free patch for an (nS + 1) x (nT + 1) grid; its arrays are ready to fill. */
    public Patch add(ModelGroup group, int nS, int nT)
    {
        if (this.patchCount == this.patches.size())
        {
            this.patches.add(new Patch(this.capacity));
        }

        Patch patch = this.patches.get(this.patchCount++);

        patch.group = group;
        patch.nS = nS;
        patch.nT = nT;

        return patch;
    }

    /**
     * Note that an edge of a patch lies on a layer's seam, running from seam corner {@code seamA} to
     * {@code seamB} in the edge's own point order — that pair is how the edge finds its other side.
     */
    public void addSeamEdge(Patch patch, WeldBinding.Layer layer, boolean source, Edge edge, int seamA, int seamB)
    {
        if (this.seamEdgeCount == this.seamEdges.size())
        {
            this.seamEdges.add(new SeamEdge());
        }

        SeamEdge seamEdge = this.seamEdges.get(this.seamEdgeCount++);

        seamEdge.patch = patch;
        seamEdge.layer = layer;
        seamEdge.source = source;
        seamEdge.edge = edge;
        seamEdge.seamA = seamA;
        seamEdge.seamB = seamB;
    }

    public int size()
    {
        return this.patchCount;
    }

    public Patch get(int index)
    {
        return this.patches.get(index);
    }

    /** Forget the held geometry; the patches stay allocated for the next bake. */
    public void clear()
    {
        this.patchCount = 0;
        this.seamEdgeCount = 0;
    }

    /**
     * Share shading normals across every smooth seam. For each pair of edges lying on the same seam from its
     * two sides, every point of one takes the average of its own normal and the other side's normal at the
     * same spot along the seam (interpolated — the two sides need not split the edge alike, a twisted band
     * has nine points where a plain one has two). Both sides read the other's ORIGINAL normals, so the result
     * is symmetric: one rounded edge under light. The geometry itself does not move.
     */
    public void resolveSeams()
    {
        for (int i = 0; i < this.seamEdgeCount; i++)
        {
            SeamEdge target = this.seamEdges.get(i);

            if (target.source || !target.layer.smooth)
            {
                continue;
            }

            for (int j = 0; j < this.seamEdgeCount; j++)
            {
                SeamEdge source = this.seamEdges.get(j);

                if (source.source && source.layer == target.layer && target.sameSeam(source))
                {
                    this.shareNormals(target, source);
                }
            }
        }
    }

    private void shareNormals(SeamEdge a, SeamEdge b)
    {
        boolean reversed = a.seamA != b.seamA;
        int segmentsA = a.segments();
        int segmentsB = b.segments();

        for (int i = 0; i <= segmentsA; i++)
        {
            float p = (float) i / segmentsA;

            this.average(a.normal(i), this.sampleNormal(b, reversed ? 1F - p : p), this.sharedA[i]);
        }

        for (int j = 0; j <= segmentsB; j++)
        {
            float p = (float) j / segmentsB;

            this.average(b.normal(j), this.sampleNormal(a, reversed ? 1F - p : p), this.sharedB[j]);
        }

        for (int i = 0; i <= segmentsA; i++)
        {
            a.normal(i).set(this.sharedA[i]);
        }

        for (int j = 0; j <= segmentsB; j++)
        {
            b.normal(j).set(this.sharedB[j]);
        }
    }

    /** The edge's normal at fraction {@code p} of its length, lerped between the two points around it. */
    private Vector3f sampleNormal(SeamEdge edge, float p)
    {
        int segments = edge.segments();
        float x = p * segments;
        int j0 = Math.max(0, Math.min(segments, (int) Math.floor(x)));
        int j1 = Math.min(segments, j0 + 1);

        return this.sample.set(edge.normal(j0)).lerp(edge.normal(j1), x - j0).normalize();
    }

    /** The unit mean of two normals; a pair that cancels out (a fully folded seam) keeps the first as is. */
    private void average(Vector3f own, Vector3f other, Vector3f dest)
    {
        dest.set(own).add(other);

        if (dest.lengthSquared() < EPS_SQ)
        {
            dest.set(own);
        }
        else
        {
            dest.normalize();
        }
    }

    /** The four edges of a grid: the first and last row (t = 0 and t = 1), the first and last column (s = 0 and s = 1). */
    public enum Edge
    {
        ROW_0, ROW_N, COL_0, COL_N
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

        /** Grid index of the i-th point along an edge: rows run along s from column 0, columns along t from row 0. */
        public int edgePoint(Edge edge, int i)
        {
            switch (edge)
            {
                case ROW_0: return this.index(i, 0);
                case ROW_N: return this.index(i, this.nT);
                case COL_0: return this.index(0, i);
                default: return this.index(this.nS, i);
            }
        }

        public int edgeSegments(Edge edge)
        {
            return edge == Edge.ROW_0 || edge == Edge.ROW_N ? this.nS : this.nT;
        }
    }

    /** A patch edge that lies on a layer's seam, between two of the seam's corners. */
    private static class SeamEdge
    {
        private Patch patch;
        private WeldBinding.Layer layer;
        private boolean source;
        private Edge edge;
        private int seamA;
        private int seamB;

        private boolean sameSeam(SeamEdge other)
        {
            return (this.seamA == other.seamA && this.seamB == other.seamB) || (this.seamA == other.seamB && this.seamB == other.seamA);
        }

        private int segments()
        {
            return this.patch.edgeSegments(this.edge);
        }

        private Vector3f normal(int i)
        {
            return this.patch.normal[this.patch.edgePoint(this.edge, i)];
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
