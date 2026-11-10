package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.render.vao.WeldBandVAO;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The welded bands of a model, laid out once and only re-posed per frame — the way skinning works, and
 * the way the rest of the model already rides its VAOs.
 *
 * <p>A welded cube bends only near its seam: every quad within a seam's band (the welded face itself and
 * the sides running into it) is replaced by a grid whose topology, texture coordinates and falloff
 * weights depend on the model and the weld settings alone, so they are built here once. A frame then
 * walks the pose for the welded cubes' matrices, captures the seams and moves the grid points — loops
 * over arrays, with no per-quad decisions and no allocations. Every other quad of a welded bone stays
 * rigid and bakes into the bone's VAO like any unwelded geometry ({@link #isBand} tells the bakers which
 * quads to leave out).</p>
 *
 * <p>The grid is computed in the model's root frame, camera-independent, so one result serves every pass
 * of a frame. It reaches the GPU through {@link WeldBandVAO} — indexed, drawn per bone and material like
 * the VAOs — or, on a model with no VAOs, goes into its CPU bake.</p>
 */
public class WeldMesh
{
    /* Segments a band quad is cut into, in both directions, once a seam bends it. Along the bone they carry
     * the falloff curve; across it they keep a twisted joint smooth — any bend that is not a pure one-axis
     * hinge leaves the face plane unevenly, and two triangles per long cell crease every cell the same way,
     * so the band reads as a sawtooth. */
    private static final int SUBDIVISIONS = 8;

    /* Coordinates are in /16 model units, so this catches vertices lying on a welded plane without
     * grabbing anything a texel away. */
    private static final float PLANE_EPS = 1.0e-4F;

    /* A collapsed grid edge (a triangle's doubled corner) gives a zero cross product; any real cell, even a
     * texel-sized one split eight ways, lands orders of magnitude above this. */
    private static final float NORMAL_EPS_SQ = 1.0e-12F;

    /* A pair of normals that cancels out (a fully folded seam) keeps its own instead of averaging to nothing. */
    private static final float SHARE_EPS_SQ = 1.0e-12F;

    /* Which grid lines of a quad carry the bend (see Quad#rails): none (resolve every point), the first and
     * last row, or the first and last column. */
    private static final int RAILS_NONE = 0;
    private static final int RAILS_ROWS = 1;
    private static final int RAILS_COLUMNS = 2;

    public final List<WeldBinding> bindings;

    /** Every bone with a welded cube. */
    public final Set<ModelGroup> weldedGroups = new LinkedHashSet<>();

    /** The bands in walk order (bone tree, then cubes, then quads) — the order the CPU bake writes them in. */
    public final List<Quad> quads = new ArrayList<>();

    /** Index ranges of {@link #indices}, one per bone and material, for drawing the band like the VAOs. */
    public final List<Draw> draws = new ArrayList<>();

    /** Grid points: static texture coordinates, and the positions, normals and tangents of the last {@link #deform}. */
    public final int pointCount;
    public final float[] texCoords;
    public final float[] positions;
    public final float[] normals;

    /** Null when Iris isn't loaded: nothing could read them. */
    public final float[] tangents;

    /** Two triangles per grid cell, grouped by {@link #draws}. */
    public final int[] indices;

    private final Set<ModelQuad> bandQuads = Collections.newSetFromMap(new IdentityHashMap<>());

    /* Bones with nothing outside the band: no VAO and no CPU geometry of their own, only band draws. */
    private final Set<ModelGroup> bandOnly = new HashSet<>();

    /* What the band's shape depends on: the welded bones and every bone above them. */
    private final List<ModelGroup> keyGroups;

    private final Map<ModelCube, Cube> cubes = new IdentityHashMap<>();
    private final Map<ModelGroup, List<Cube>> cubesByGroup = new HashMap<>();
    private final List<SeamPair> seamPairs = new ArrayList<>();

    private final MatrixStack stack = new MatrixStack();
    private final Walker walker = new Walker();

    /* Per-quad scratch for deform. */
    private final Vector3f[] rigid = vectors(4);
    private final Vector3f[] cornerNormals = vectors(4);
    private final Vector3f seamPosition = new Vector3f();
    private final Vector3f restShift = new Vector3f();
    private final Vector3f tangentS = new Vector3f();
    private final Vector3f tangentT = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private final Vector3f sample = new Vector3f();
    private final float[] sharedA = new float[(SUBDIVISIONS + 1) * 3];
    private final float[] sharedB = new float[(SUBDIVISIONS + 1) * 3];

    private WeldBandVAO vao;

    /** A welded cube and its root-frame matrices, refreshed by every walk. */
    private static class Cube
    {
        private final ModelCube cube;
        private final List<SnapDef> snaps = new ArrayList<>();

        private final Matrix4f bone = new Matrix4f();
        private final Matrix4f position = new Matrix4f();
        private final Matrix3f normal = new Matrix3f();
        private boolean visible;

        private Cube(ModelCube cube)
        {
            this.cube = cube;
        }
    }

    /** One seam the cube takes part in: the layer, the cube's role in it, and that role's local plane and band. */
    private record SnapDef(WeldBinding.Layer layer, boolean source, Vector3f faceNormal, float weldPlane, float band)
    {}

    /** A seam a band quad bends toward, with the quad's static per-corner and per-point data for it. */
    private static class Snap
    {
        private final SnapDef def;
        private final float[] cornerDistance = new float[4];
        private final boolean[] onPlane = new boolean[4];

        /* The falloff weight at every grid point: full at the seam, none a band away. */
        private float[] weights;

        /* A curved seam on the same plane of the cube as an earlier one (a torso's bottom welded to both
         * legs): the earlier takes the face, the way it takes the corners on the shear. */
        private boolean shadowed;

        /* A curved seam's source side, at rest: how far each corner on the plane goes to meet the target's
         * face (zero elsewhere) — what the shear moves it by at rest, faded across the quad the same way. */
        private final Vector3f[] restGap = vectors(4);

        /* Per deform: whether the seam is built, whether it bends along its curve, and its displacement at
         * each corner (zero off its plane, and unused by a curve). */
        private boolean active;
        private boolean curve;
        private final Vector3f[] displacement = vectors(4);

        private Snap(SnapDef def)
        {
            this.def = def;
        }
    }

    /** A quad replaced by an (nS + 1) x (nT + 1) grid of points, s along corners 0 to 1, t along 0 to 3. */
    public static class Quad
    {
        public final ModelGroup group;
        public final String material;
        public final int nS;
        public final int nT;
        public final int offset;

        private final Cube cube;
        private final Snap[] snaps;
        private final Vector3f[] corners = vectors(4);
        private final Vector3f[] normals = vectors(4);
        private final float[] cornerU = new float[4];
        private final float[] cornerV = new float[4];
        private final float[] tangent = new float[4];

        /* A side face (a box face, one normal) bent only along the bone: every seam's falloff depends on the
         * along-bone coordinate alone, so each grid line across the bone is straight — its points are exact
         * lerps between the two edge lines along the bone (the rails), which are all that needs resolving. */
        private int rails = RAILS_NONE;

        private Quad(ModelGroup group, Cube cube, Snap[] snaps, int nS, int nT, int offset)
        {
            this.group = group;
            this.material = cube.cube.material == null ? "" : cube.cube.material;
            this.cube = cube;
            this.snaps = snaps;
            this.nS = nS;
            this.nT = nT;
            this.offset = offset;
        }

        public int point(int col, int row)
        {
            return this.offset + row * (this.nS + 1) + col;
        }

        public int pointCount()
        {
            return (this.nS + 1) * (this.nT + 1);
        }
    }

    /** One bone's band in one material: a range of {@link #indices}. */
    public record Draw(ModelGroup group, String material, int offset, int count)
    {}

    /* The four edges of a grid: the first and last row (t = 0 and t = 1), the first and last column (s = 0 and s = 1). */
    private static final int ROW_0 = 0;
    private static final int ROW_N = 1;
    private static final int COL_0 = 2;
    private static final int COL_N = 3;

    /** A grid edge lying on a seam, between two of the seam's corners. */
    private record SeamEdge(Quad quad, WeldBinding.Layer layer, boolean source, int edge, int seamA, int seamB)
    {
        private int segments()
        {
            return this.edge == ROW_0 || this.edge == ROW_N ? this.quad.nS : this.quad.nT;
        }

        /** Grid point of the i-th point along the edge: rows run along s from column 0, columns along t from row 0. */
        private int point(int i)
        {
            return switch (this.edge)
            {
                case ROW_0 -> this.quad.point(i, 0);
                case ROW_N -> this.quad.point(i, this.quad.nT);
                case COL_0 -> this.quad.point(0, i);
                default -> this.quad.point(this.quad.nS, i);
            };
        }

        private boolean sameSeam(SeamEdge other)
        {
            return (this.seamA == other.seamA && this.seamB == other.seamB) || (this.seamA == other.seamB && this.seamB == other.seamA);
        }
    }

    /** The two sides of one smooth seam, which share their shading normals. */
    private record SeamPair(SeamEdge target, SeamEdge source, boolean reversed)
    {}

    public WeldMesh(Model model, List<WeldBinding> bindings)
    {
        this.bindings = bindings;

        /* Target layers first, then source layers — so the first seam to claim a corner lying on two planes
         * is the same one whatever the order the cube shows up in. */
        for (WeldBinding binding : bindings)
        {
            this.weldedGroups.add(binding.sourceGroup);
            this.weldedGroups.add(binding.targetGroup);

            for (WeldBinding.Layer layer : binding.layers)
            {
                this.cube(binding.targetGroup, layer.targetCube).snaps.add(new SnapDef(layer, false, layer.targetFaceNormal, layer.targetWeldPlane, layer.falloff * layer.targetAxisExtent));
            }
        }

        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                this.cube(binding.sourceGroup, layer.sourceCube).snaps.add(new SnapDef(layer, true, layer.sourceFaceNormal, layer.sourceWeldPlane, layer.falloff * layer.sourceAxisExtent));
            }
        }

        Set<ModelGroup> keyGroups = new LinkedHashSet<>();

        for (ModelGroup group : this.weldedGroups)
        {
            for (ModelGroup g = group; g != null; g = g.parent)
            {
                keyGroups.add(g);
            }
        }

        this.keyGroups = new ArrayList<>(keyGroups);

        int points = 0;

        for (ModelGroup group : model.topGroups)
        {
            points = this.layOut(group, points);
        }

        this.pointCount = points;
        this.texCoords = new float[points * 2];
        this.positions = new float[points * 3];
        this.normals = new float[points * 3];
        this.tangents = BBSRendering.isIrisLoaded() ? new float[points * 4] : null;

        for (Quad quad : this.quads)
        {
            this.fillTexCoords(quad);
        }

        this.indices = this.buildIndices();
        this.buildSeamPairs();
    }

    private Cube cube(ModelGroup group, ModelCube cube)
    {
        return this.cubes.computeIfAbsent(cube, (k) ->
        {
            Cube created = new Cube(k);

            this.cubesByGroup.computeIfAbsent(group, (g) -> new ArrayList<>()).add(created);

            return created;
        });
    }

    /** Find the band quads of a bone and its children, in walk order; returns the next free point. */
    private int layOut(ModelGroup group, int points)
    {
        boolean allBand = !group.cubes.isEmpty() && group.meshes.isEmpty();

        for (ModelCube modelCube : group.cubes)
        {
            Cube cube = this.cubes.get(modelCube);

            for (ModelQuad quad : modelCube.quads)
            {
                int count = quad.vertices.size();

                if (cube == null || (count != 3 && count != 4) || !this.nearSeam(quad, cube))
                {
                    allBand = false;

                    continue;
                }

                points = this.addQuad(group, cube, quad, points);
            }
        }

        if (allBand)
        {
            this.bandOnly.add(group);
        }

        for (ModelGroup child : group.children)
        {
            points = this.layOut(child, points);
        }

        return points;
    }

    /**
     * Whether any of the quad's corners sits within a seam's bend band; on-plane vertices always count.
     * Checking corners is exact, not approximate: a cube sits entirely on one side of its own welded
     * plane, so the distance over a planar quad is affine and takes its minimum at a corner.
     */
    private boolean nearSeam(ModelQuad quad, Cube cube)
    {
        for (ModelVertex vertex : quad.vertices)
        {
            for (SnapDef snap : cube.snaps)
            {
                if (Math.abs(vertex.vertex.dot(snap.faceNormal()) - snap.weldPlane()) <= snap.band() + PLANE_EPS)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private int addQuad(ModelGroup group, Cube cube, ModelQuad modelQuad, int points)
    {
        int count = modelQuad.vertices.size();
        Vector3f c0 = modelQuad.vertices.get(0).vertex;
        Vector3f cS = modelQuad.vertices.get(1).vertex;
        Vector3f cT = modelQuad.vertices.get(Math.min(3, count - 1)).vertex;
        boolean bends = false;

        /* A quad bends when one of its edges runs along a seam's bone axis: the side faces. The welded face
         * itself and faces across the bone only ride the seam rigidly, so two triangles stay exact there. */
        for (SnapDef snap : cube.snaps)
        {
            Vector3f axis = snap.faceNormal();
            float alongS = Math.abs((cS.x - c0.x) * axis.x + (cS.y - c0.y) * axis.y + (cS.z - c0.z) * axis.z);
            float alongT = Math.abs((cT.x - c0.x) * axis.x + (cT.y - c0.y) * axis.y + (cT.z - c0.z) * axis.z);

            if (Math.max(alongS, alongT) > PLANE_EPS)
            {
                bends = true;
            }
        }

        int n = bends ? SUBDIVISIONS : 1;
        Snap[] snaps = new Snap[cube.snaps.size()];
        Quad quad = new Quad(group, cube, snaps, n, n, points);

        for (int i = 0; i < 4; i++)
        {
            /* A triangle rides through as a quad with its last corner doubled. */
            ModelVertex corner = modelQuad.vertices.get(Math.min(i, count - 1));

            quad.corners[i].set(corner.vertex);
            quad.normals[i].set(corner.normal);
            quad.cornerU[i] = corner.uv.x;
            quad.cornerV[i] = corner.uv.y;
        }

        for (int k = 0; k < snaps.length; k++)
        {
            Snap snap = snaps[k] = new Snap(cube.snaps.get(k));

            for (int i = 0; i < 4; i++)
            {
                snap.cornerDistance[i] = Math.abs(quad.corners[i].dot(snap.def.faceNormal()) - snap.def.weldPlane());
                snap.onPlane[i] = snap.cornerDistance[i] < PLANE_EPS;
            }

            snap.weights = new float[quad.pointCount()];

            if (snap.def.layer().curve)
            {
                for (int j = 0; j < k; j++)
                {
                    SnapDef earlier = snaps[j].def;

                    snap.shadowed |= earlier.faceNormal().equals(snap.def.faceNormal()) && Math.abs(earlier.weldPlane() - snap.def.weldPlane()) < PLANE_EPS;
                }

                for (int i = 0; i < 4 && snap.def.source(); i++)
                {
                    if (snap.onPlane[i])
                    {
                        snap.def.layer().restGapAt(quad.corners[i], snap.restGap[i]);
                    }
                }
            }

            for (int row = 0; row <= quad.nT; row++)
            {
                for (int col = 0; col <= quad.nS; col++)
                {
                    float distance = bilerp(snap.cornerDistance, (float) col / quad.nS, (float) row / quad.nT);

                    snap.weights[row * (quad.nS + 1) + col] = falloffWeight(distance, snap.def.band());
                }
            }
        }

        /* A box face: four corners with one normal. */
        boolean flat = count == 4 && quad.normals[0].equals(quad.normals[1]) && quad.normals[0].equals(quad.normals[2]) && quad.normals[0].equals(quad.normals[3]);

        if (n > 1 && flat)
        {
            quad.rails = rails(snaps);
        }

        this.localTangent(quad, modelQuad);
        this.quads.add(quad);
        this.bandQuads.add(modelQuad);

        return points + quad.pointCount();
    }

    /**
     * Which edge lines of the quad its seams bend: the rows when every seam's distance changes along s only
     * (corners 0-3 and 1-2 equally far from each plane), the columns when along t only, none when the seams
     * pull in both directions (a cube welded on two axes).
     */
    private static int rails(Snap[] snaps)
    {
        boolean rows = true;
        boolean columns = true;

        for (Snap snap : snaps)
        {
            float[] d = snap.cornerDistance;

            rows &= Math.abs(d[0] - d[3]) < PLANE_EPS && Math.abs(d[1] - d[2]) < PLANE_EPS;
            columns &= Math.abs(d[0] - d[1]) < PLANE_EPS && Math.abs(d[3] - d[2]) < PLANE_EPS;
        }

        return rows ? RAILS_ROWS : columns ? RAILS_COLUMNS : RAILS_NONE;
    }

    /** The quad's tangent in cube space, the way the VAO baker computes it — it only turns with the cube. */
    private void localTangent(Quad quad, ModelQuad modelQuad)
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return;
        }

        float[] v = new float[9];
        float[] n = new float[9];
        float[] u = new float[6];

        for (int i = 0; i < 3; i++)
        {
            ModelVertex vertex = modelQuad.vertices.get(i);

            v[i * 3] = vertex.vertex.x;
            v[i * 3 + 1] = vertex.vertex.y;
            v[i * 3 + 2] = vertex.vertex.z;
            n[i * 3] = vertex.normal.x;
            n[i * 3 + 1] = vertex.normal.y;
            n[i * 3 + 2] = vertex.normal.z;
            u[i * 2] = vertex.uv.x;
            u[i * 2 + 1] = vertex.uv.y;
        }

        float[] t = BBSRendering.calculateTangents(v, n, u);

        if (t.length >= 4)
        {
            System.arraycopy(t, 0, quad.tangent, 0, 4);
        }
    }

    private void fillTexCoords(Quad quad)
    {
        for (int row = 0; row <= quad.nT; row++)
        {
            for (int col = 0; col <= quad.nS; col++)
            {
                int point = quad.point(col, row);
                float s = (float) col / quad.nS;
                float t = (float) row / quad.nT;

                this.texCoords[point * 2] = bilerp(quad.cornerU, s, t);
                this.texCoords[point * 2 + 1] = bilerp(quad.cornerV, s, t);
            }
        }
    }

    /** Two triangles per cell, bone by bone and material by material, so each range draws like a VAO. */
    private int[] buildIndices()
    {
        Map<ModelGroup, Map<String, List<Quad>>> grouped = new LinkedHashMap<>();
        int count = 0;

        for (Quad quad : this.quads)
        {
            grouped.computeIfAbsent(quad.group, (k) -> new LinkedHashMap<>()).computeIfAbsent(quad.material, (k) -> new ArrayList<>()).add(quad);
            count += quad.nS * quad.nT * 6;
        }

        int[] indices = new int[count];
        int i = 0;

        for (Map.Entry<ModelGroup, Map<String, List<Quad>>> group : grouped.entrySet())
        {
            for (Map.Entry<String, List<Quad>> material : group.getValue().entrySet())
            {
                int start = i;

                for (Quad quad : material.getValue())
                {
                    for (int row = 0; row < quad.nT; row++)
                    {
                        for (int col = 0; col < quad.nS; col++)
                        {
                            int i00 = quad.point(col, row);
                            int i10 = i00 + 1;
                            int i01 = quad.point(col, row + 1);
                            int i11 = i01 + 1;

                            indices[i++] = i00;
                            indices[i++] = i10;
                            indices[i++] = i11;
                            indices[i++] = i00;
                            indices[i++] = i11;
                            indices[i++] = i01;
                        }
                    }
                }

                this.draws.add(new Draw(group.getKey(), material.getKey(), start, i - start));
            }
        }

        return indices;
    }

    /**
     * Find, for every smooth seam, the grid edges lying on it from both sides. An edge is on a seam when
     * exactly its two corners sit on that seam's plane; each corner is identified with a corner of the
     * welded face by its local position (a box's side edge is one of that face's edges), which names the seam
     * corner through the layer's mapping. Anything else — the welded face itself, inset geometry, a triangle —
     * keeps its own normals.
     */
    private void buildSeamPairs()
    {
        List<SeamEdge> edges = new ArrayList<>();

        for (Quad quad : this.quads)
        {
            for (Snap snap : quad.snaps)
            {
                int onPlane = 0;

                for (int i = 0; i < 4; i++)
                {
                    if (snap.onPlane[i])
                    {
                        onPlane |= 1 << i;
                    }
                }

                int edge;
                int start;
                int end;

                /* The edge's points run from start to end: rows along s from column 0, columns along t from row 0. */
                switch (onPlane)
                {
                    case 0b0011: edge = ROW_0; start = 0; end = 1; break;
                    case 0b0110: edge = COL_N; start = 1; end = 2; break;
                    case 0b1100: edge = ROW_N; start = 3; end = 2; break;
                    case 0b1001: edge = COL_0; start = 0; end = 3; break;
                    default: continue;
                }

                int seamA = seamCorner(snap.def, quad.corners[start]);
                int seamB = seamCorner(snap.def, quad.corners[end]);

                if (seamA >= 0 && seamB >= 0 && seamA != seamB)
                {
                    edges.add(new SeamEdge(quad, snap.def.layer(), snap.def.source(), edge, seamA, seamB));
                }
            }
        }

        for (SeamEdge target : edges)
        {
            if (target.source() || !target.layer().smooth)
            {
                continue;
            }

            for (SeamEdge source : edges)
            {
                if (source.source() && source.layer() == target.layer() && target.sameSeam(source))
                {
                    this.seamPairs.add(new SeamPair(target, source, target.seamA() != source.seamA()));
                }
            }
        }
    }

    /** The seam corner a local cube position stands on, through the layer's welded-face corners; -1 when it is none of them. */
    private static int seamCorner(SnapDef snap, Vector3f local)
    {
        WeldBinding.Layer layer = snap.layer();
        Vector3f[] corners = snap.source() ? layer.sourceCorners : layer.targetCorners;

        for (int c = 0; c < corners.length; c++)
        {
            if (corners[c].distanceSquared(local) < PLANE_EPS * PLANE_EPS)
            {
                return snap.source() ? layer.sourceToTarget[c] : c;
            }
        }

        return -1;
    }

    /** Whether the band draws this quad — the VAO and CPU bakers leave it out. */
    public boolean isBand(ModelQuad quad)
    {
        return this.bandQuads.contains(quad);
    }

    /** Whether all of the bone's geometry is band, so it has nothing to draw on its own. */
    public boolean isBandOnly(ModelGroup group)
    {
        return this.bandOnly.contains(group);
    }

    /** The GPU side of the band, created on first use. */
    public WeldBandVAO getVAO()
    {
        if (this.vao == null)
        {
            this.vao = new WeldBandVAO(this);
        }

        return this.vao;
    }

    public void delete()
    {
        if (this.vao != null)
        {
            this.vao.delete();
            this.vao = null;
        }
    }

    /** What the band's shape is a function of: the pose of every welded bone and of every bone above them, and which welded bones show. */
    public long key()
    {
        long hash = 1125899906842597L;

        for (ModelGroup group : this.keyGroups)
        {
            hash = hash * 31 + (group.isVisible() ? 1 : 0);
            hash = hash * 31 + group.current.contentHash();
            hash = hash * 31 + (group.orient == null ? 0 : group.orient.hashCode());
            hash = hash * 31 + (group.offset == null ? 0 : group.offset.hashCode());
        }

        return hash;
    }

    /**
     * Pose the band: walk the model for the welded cubes' root-frame matrices, capture both faces of every
     * seam and build it, then move every grid point. A seam whose bone is hidden isn't built, and its bands
     * stay rigid — the same as an unwelded cube.
     */
    public void deform(Model model)
    {
        for (Cube cube : this.cubes.values())
        {
            cube.visible = false;
        }

        CubicRenderer.processRenderModel(this.walker, null, this.stack, model);

        for (WeldBinding binding : this.bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                Cube source = this.cubes.get(layer.sourceCube);
                Cube target = this.cubes.get(layer.targetCube);

                layer.resetCapture();

                if (target.visible)
                {
                    layer.captureTarget(target.bone, target.position);
                }

                if (source.visible)
                {
                    layer.captureSource(source.bone, source.position);
                }

                layer.computeSeam();
            }
        }

        for (Quad quad : this.quads)
        {
            this.deformQuad(quad);
        }

        for (SeamPair pair : this.seamPairs)
        {
            if (pair.target().layer().seamReady)
            {
                this.shareNormals(pair);
            }
        }
    }

    /**
     * Each corner is resolved rigidly, and every seam records its OWN displacement at the corners on its
     * plane (zero elsewhere). Every grid point then adds each seam's interpolated displacement scaled by that
     * seam's falloff weight at the point — so each seam bends only the strip near itself while the rest of
     * the cube stays straight. Per seam, not a shared "snapped surface": a shared one bleeds one seam's motion
     * into the other's band on a cube welded at both ends (bending only the foot wiggled the knee's band).
     */
    private void deformQuad(Quad quad)
    {
        Cube cube = quad.cube;
        Vector3f[] r = this.rigid;
        Vector3f[] n = this.cornerNormals;

        for (int i = 0; i < 4; i++)
        {
            cube.position.transformPosition(quad.corners[i], r[i]);
            cube.normal.transform(n[i].set(quad.normals[i]));
        }

        for (Snap snap : quad.snaps)
        {
            snap.active = snap.def.layer().seamReady;
            snap.curve = snap.active && snap.def.layer().curveReady;

            for (Vector3f displacement : snap.displacement)
            {
                displacement.set(0F, 0F, 0F);
            }
        }

        /* The first seam lying on a corner claims it; a curve claims it too, and moves its points itself. */
        for (int i = 0; i < 4; i++)
        {
            for (Snap snap : quad.snaps)
            {
                if (snap.active && snap.onPlane[i])
                {
                    if (!snap.curve)
                    {
                        WeldBinding.Layer layer = snap.def.layer();
                        Vector3f seam = snap.def.source() ? layer.seamAtSource(quad.corners[i], this.seamPosition) : layer.seamAtTarget(quad.corners[i], this.seamPosition);

                        snap.displacement[i].set(seam).sub(r[i]);
                    }

                    break;
                }
            }
        }

        int nS = quad.nS;
        int nT = quad.nT;

        if (quad.rails == RAILS_NONE)
        {
            for (int row = 0; row <= nT; row++)
            {
                for (int col = 0; col <= nS; col++)
                {
                    this.resolvePosition(quad, col, row);
                    this.baseNormal(quad, col, row);
                }
            }

            for (int row = 0; row <= nT; row++)
            {
                for (int col = 0; col <= nS; col++)
                {
                    this.gridNormal(quad, col, row);
                }
            }
        }
        else
        {
            this.deformRails(quad, quad.rails == RAILS_ROWS);
        }

        if (this.tangents != null)
        {
            this.normal.set(quad.tangent[0], quad.tangent[1], quad.tangent[2]);
            cube.normal.transform(this.normal);

            for (int point = quad.offset, end = quad.offset + quad.pointCount(); point < end; point++)
            {
                this.tangents[point * 4] = this.normal.x;
                this.tangents[point * 4 + 1] = this.normal.y;
                this.tangents[point * 4 + 2] = this.normal.z;
                this.tangents[point * 4 + 3] = quad.tangent[3];
            }
        }
    }

    /**
     * A bent side face: its two rails (the edge lines along the bone) in full, every line across the bone a
     * lerp between them — exact, those lines are straight. The shading normal follows from the same fact:
     * on a line across the bone the grid's tangent along the bone is the lerp of the rails' tangents and the
     * one across is the line itself, so the cross product of the two — what {@link #gridNormal} takes off the
     * grid — is the lerp of the cross products on the rails. Two per line instead of one per point.
     */
    private void deformRails(Quad quad, boolean rows)
    {
        int along = rows ? quad.nS : quad.nT;
        int across = rows ? quad.nT : quad.nS;
        float[] positions = this.positions;
        float[] normals = this.normals;

        for (int a = 0; a <= along; a++)
        {
            this.resolvePosition(quad, rows ? a : 0, rows ? 0 : a);
            this.resolvePosition(quad, rows ? a : across, rows ? across : a);
        }

        /* The rigid face normal settles which way the cross product faces — a mirrored matrix (the UI preview
         * flips Y) reverses it — and stands in where the grid degenerates. */
        Vector3f face = this.normal.set(this.cornerNormals[0]).normalize();
        float fx = face.x;
        float fy = face.y;
        float fz = face.z;

        for (int a = 0; a <= along; a++)
        {
            int r0 = railPoint(quad, rows, a, 0) * 3;
            int r1 = railPoint(quad, rows, a, across) * 3;
            int p00 = railPoint(quad, rows, Math.max(a - 1, 0), 0) * 3;
            int p01 = railPoint(quad, rows, Math.min(a + 1, along), 0) * 3;
            int p10 = railPoint(quad, rows, Math.max(a - 1, 0), across) * 3;
            int p11 = railPoint(quad, rows, Math.min(a + 1, along), across) * 3;

            /* The line across the bone, and the tangent along it on either rail (central, one-sided at the ends). */
            float dx = positions[r1] - positions[r0];
            float dy = positions[r1 + 1] - positions[r0 + 1];
            float dz = positions[r1 + 2] - positions[r0 + 2];
            float ax0 = positions[p01] - positions[p00];
            float ay0 = positions[p01 + 1] - positions[p00 + 1];
            float az0 = positions[p01 + 2] - positions[p00 + 2];
            float ax1 = positions[p11] - positions[p10];
            float ay1 = positions[p11 + 1] - positions[p10 + 1];
            float az1 = positions[p11 + 2] - positions[p10 + 2];

            float cx0 = ay0 * dz - az0 * dy;
            float cy0 = az0 * dx - ax0 * dz;
            float cz0 = ax0 * dy - ay0 * dx;
            float cx1 = ay1 * dz - az1 * dy;
            float cy1 = az1 * dx - ax1 * dz;
            float cz1 = ax1 * dy - ay1 * dx;

            for (int c = 0; c <= across; c++)
            {
                float f = (float) c / across;
                int p = railPoint(quad, rows, a, c) * 3;

                if (c > 0 && c < across)
                {
                    positions[p] = positions[r0] + dx * f;
                    positions[p + 1] = positions[r0 + 1] + dy * f;
                    positions[p + 2] = positions[r0 + 2] + dz * f;
                }

                float nx = cx0 + (cx1 - cx0) * f;
                float ny = cy0 + (cy1 - cy0) * f;
                float nz = cz0 + (cz1 - cz0) * f;
                float length = nx * nx + ny * ny + nz * nz;

                if (length < NORMAL_EPS_SQ)
                {
                    normals[p] = fx;
                    normals[p + 1] = fy;
                    normals[p + 2] = fz;

                    continue;
                }

                float inverse = (float) (1D / Math.sqrt(length));

                if (nx * fx + ny * fy + nz * fz < 0F)
                {
                    inverse = -inverse;
                }

                normals[p] = nx * inverse;
                normals[p + 1] = ny * inverse;
                normals[p + 2] = nz * inverse;
            }
        }
    }

    /** Grid point {@code a} along the bone on line {@code c} across it. */
    private static int railPoint(Quad quad, boolean rows, int a, int c)
    {
        return rows ? quad.point(a, c) : quad.point(c, a);
    }

    /**
     * One grid point's position: bilinearly interpolate the rigid position across the four corners (s along
     * 0->1, t along 0->3), then add EACH seam's interpolated displacement scaled by that seam's OWN falloff
     * weight at the point. A curved seam's displacement is where its curve puts the point, less the rigid
     * position, anywhere within its band.
     */
    private void resolvePosition(Quad quad, int col, int row)
    {
        Vector3f[] r = this.rigid;
        float s = (float) col / quad.nS;
        float t = (float) row / quad.nT;
        int local = row * (quad.nS + 1) + col;
        int p = (quad.offset + local) * 3;

        float rx = bilerp(r[0].x, r[1].x, r[2].x, r[3].x, s, t);
        float ry = bilerp(r[0].y, r[1].y, r[2].y, r[3].y, s, t);
        float rz = bilerp(r[0].z, r[1].z, r[2].z, r[3].z, s, t);
        float x = rx;
        float y = ry;
        float z = rz;

        for (Snap snap : quad.snaps)
        {
            if (snap.curve)
            {
                float distance = bilerp(snap.cornerDistance, s, t);

                if (!snap.shadowed && distance < snap.def.band())
                {
                    Vector3f[] c = quad.corners;
                    Vector3f[] g = snap.restGap;
                    float w = snap.weights[local];
                    Vector3f point = this.sample.set(
                        bilerp(c[0].x, c[1].x, c[2].x, c[3].x, s, t),
                        bilerp(c[0].y, c[1].y, c[2].y, c[3].y, s, t),
                        bilerp(c[0].z, c[1].z, c[2].z, c[3].z, s, t)
                    );
                    Vector3f shift = this.restShift.set(
                        w * bilerp(g[0].x, g[1].x, g[2].x, g[3].x, s, t),
                        w * bilerp(g[0].y, g[1].y, g[2].y, g[3].y, s, t),
                        w * bilerp(g[0].z, g[1].z, g[2].z, g[3].z, s, t)
                    );

                    snap.def.layer().curvePoint(snap.def.source(), point, distance, shift, this.seamPosition);
                    x += this.seamPosition.x - rx;
                    y += this.seamPosition.y - ry;
                    z += this.seamPosition.z - rz;
                }

                continue;
            }

            float w = snap.active ? snap.weights[local] : 0F;

            if (w > 0F)
            {
                Vector3f[] d = snap.displacement;

                x += w * bilerp(d[0].x, d[1].x, d[2].x, d[3].x, s, t);
                y += w * bilerp(d[0].y, d[1].y, d[2].y, d[3].y, s, t);
                z += w * bilerp(d[0].z, d[1].z, d[2].z, d[3].z, s, t);
            }
        }

        this.positions[p] = x;
        this.positions[p + 1] = y;
        this.positions[p + 2] = z;
    }

    /** The rigid face normal at a grid point: which way {@link #gridNormal} faces, and what stays where the grid degenerates. */
    private void baseNormal(Quad quad, int col, int row)
    {
        Vector3f[] n = this.cornerNormals;
        float s = (float) col / quad.nS;
        float t = (float) row / quad.nT;
        int p = quad.point(col, row) * 3;

        this.normal.set(
            bilerp(n[0].x, n[1].x, n[2].x, n[3].x, s, t),
            bilerp(n[0].y, n[1].y, n[2].y, n[3].y, s, t),
            bilerp(n[0].z, n[1].z, n[2].z, n[3].z, s, t)
        ).normalize();

        this.normals[p] = this.normal.x;
        this.normals[p + 1] = this.normal.y;
        this.normals[p + 2] = this.normal.z;
    }

    /**
     * Shading normal off the DEFORMED grid rather than the rigid face. The band next to a seam is sheared
     * toward it, and lit with the flat face's normal it reads as a sticker over a bend — the shape curves,
     * the light stays flat. Central differences between grid neighbours (one-sided on the border) give the
     * tangent plane of what is actually drawn. The rigid normal already in the grid only settles which way
     * the cross product faces — a mirrored matrix (the UI preview flips Y) reverses it — and stands in where
     * the grid degenerates (a triangle's doubled corner). Off the band the surface is the rigid bilerp, so
     * the result there is the face normal again and meets the rigid quads without a step.
     */
    private void gridNormal(Quad quad, int col, int row)
    {
        float[] positions = this.positions;
        float[] normals = this.normals;
        int sNext = quad.point(Math.min(col + 1, quad.nS), row) * 3;
        int sPrev = quad.point(Math.max(col - 1, 0), row) * 3;
        int tNext = quad.point(col, Math.min(row + 1, quad.nT)) * 3;
        int tPrev = quad.point(col, Math.max(row - 1, 0)) * 3;

        this.tangentS.set(positions[sNext] - positions[sPrev], positions[sNext + 1] - positions[sPrev + 1], positions[sNext + 2] - positions[sPrev + 2]);
        this.tangentT.set(positions[tNext] - positions[tPrev], positions[tNext + 1] - positions[tPrev + 1], positions[tNext + 2] - positions[tPrev + 2]);

        Vector3f cross = this.tangentS.cross(this.tangentT);

        if (cross.lengthSquared() < NORMAL_EPS_SQ)
        {
            return;
        }

        int p = quad.point(col, row) * 3;

        if (cross.x * normals[p] + cross.y * normals[p + 1] + cross.z * normals[p + 2] < 0F)
        {
            cross.negate();
        }

        cross.normalize();
        normals[p] = cross.x;
        normals[p + 1] = cross.y;
        normals[p + 2] = cross.z;
    }

    /**
     * One rounded edge under light: every point of either side takes the average of its own normal and the
     * other side's at the same spot along the seam (interpolated — the two sides need not split the edge
     * alike). Both sides read the other's ORIGINAL normals, so the result is symmetric. The geometry itself
     * does not move.
     */
    private void shareNormals(SeamPair pair)
    {
        SeamEdge a = pair.target();
        SeamEdge b = pair.source();
        int segmentsA = a.segments();
        int segmentsB = b.segments();

        for (int i = 0; i <= segmentsA; i++)
        {
            float p = (float) i / segmentsA;

            this.average(a.point(i), b, pair.reversed() ? 1F - p : p, this.sharedA, i);
        }

        for (int j = 0; j <= segmentsB; j++)
        {
            float p = (float) j / segmentsB;

            this.average(b.point(j), a, pair.reversed() ? 1F - p : p, this.sharedB, j);
        }

        for (int i = 0; i <= segmentsA; i++)
        {
            System.arraycopy(this.sharedA, i * 3, this.normals, a.point(i) * 3, 3);
        }

        for (int j = 0; j <= segmentsB; j++)
        {
            System.arraycopy(this.sharedB, j * 3, this.normals, b.point(j) * 3, 3);
        }
    }

    /** The unit mean of a point's normal and the other edge's normal at fraction {@code p} of its length, into {@code dest[slot]}. */
    private void average(int point, SeamEdge other, float p, float[] dest, int slot)
    {
        int segments = other.segments();
        float x = p * segments;
        int j0 = Math.max(0, Math.min(segments, (int) Math.floor(x)));
        int j1 = Math.min(segments, j0 + 1);
        int o0 = other.point(j0) * 3;
        int o1 = other.point(j1) * 3;
        float f = x - j0;

        if (f == 0F)
        {
            /* Both sides split the seam alike: the other side's own (unit) normal. */
            this.sample.set(this.normals[o0], this.normals[o0 + 1], this.normals[o0 + 2]);
        }
        else
        {
            this.sample.set(
                this.normals[o0] + (this.normals[o1] - this.normals[o0]) * f,
                this.normals[o0 + 1] + (this.normals[o1 + 1] - this.normals[o0 + 1]) * f,
                this.normals[o0 + 2] + (this.normals[o1 + 2] - this.normals[o0 + 2]) * f
            ).normalize();
        }

        int own = point * 3;
        float sx = this.normals[own] + this.sample.x;
        float sy = this.normals[own + 1] + this.sample.y;
        float sz = this.normals[own + 2] + this.sample.z;
        float length = sx * sx + sy * sy + sz * sz;

        if (length < SHARE_EPS_SQ)
        {
            sx = this.normals[own];
            sy = this.normals[own + 1];
            sz = this.normals[own + 2];
        }
        else
        {
            float inverse = (float) (1D / Math.sqrt(length));

            sx *= inverse;
            sy *= inverse;
            sz *= inverse;
        }

        dest[slot * 3] = sx;
        dest[slot * 3 + 1] = sy;
        dest[slot * 3 + 2] = sz;
    }

    /** Bilinear blend of four corner scalars laid out as (0,1) along the bottom edge and (3,2) along the top. */
    private static float bilerp(float c0, float c1, float c2, float c3, float s, float t)
    {
        float bottom = c0 + (c1 - c0) * s;
        float top = c3 + (c2 - c3) * s;

        return bottom + (top - bottom) * t;
    }

    private static float bilerp(float[] c, float s, float t)
    {
        return bilerp(c[0], c[1], c[2], c[3], s, t);
    }

    /** Smoothstep falloff: 1 at the seam, 0 at or beyond {@code band}, with a smooth (no-kink) ramp between. */
    private static float falloffWeight(float distance, float band)
    {
        if (band <= 1.0e-5F)
        {
            return distance <= 1.0e-5F ? 1F : 0F;
        }

        float x = distance / band;

        if (x <= 0F) return 1F;
        if (x >= 1F) return 0F;

        return 1F - x * x * (3F - 2F * x);
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

    /** Records the root-frame matrices of the welded cubes of every visible bone — the same stack the renderers build. */
    private class Walker implements ICubicRenderer
    {
        @Override
        public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
        {
            List<Cube> cubes = WeldMesh.this.cubesByGroup.get(group);

            if (cubes == null)
            {
                return false;
            }

            for (Cube cube : cubes)
            {
                cube.bone.set(stack.peek().getPositionMatrix());

                stack.push();
                CubicCubeRenderer.moveToPivot(stack, cube.cube.pivot);
                CubicCubeRenderer.rotate(stack, cube.cube.rotate);
                CubicCubeRenderer.moveBackFromPivot(stack, cube.cube.pivot);

                cube.position.set(stack.peek().getPositionMatrix());
                cube.normal.set(stack.peek().getNormalMatrix());
                cube.visible = true;

                stack.pop();
            }

            return false;
        }
    }
}
