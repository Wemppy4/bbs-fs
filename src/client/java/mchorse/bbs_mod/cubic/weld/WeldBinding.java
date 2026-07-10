package mchorse.bbs_mod.cubic.weld;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ModelWeld} resolved against a concrete model. The weld seals a bending joint by pulling both
 * bones' welded faces onto a shared seam, so the bend distributes across both cubes (the parent's face
 * shears to meet the child's, the child's shears back) instead of only the child deforming against a rigid
 * parent.
 *
 * <p>A bone is usually two coincident cubes — the base skin and the inflated jacket layer. Each layer is a
 * different size, so they get their OWN seam: the cubes of the two bones are paired by matching welded-face
 * cross-section (base to base, jacket to jacket — whether the layers differ by inflate or by raw size) and
 * every pair seals independently. A single shared seam would drag the base layer out to the jacket's size
 * and puff the joint.
 *
 * <p>Because the parent draws before the child, the rigid world poses of both faces can't be known in one
 * traversal — the renderer runs a capture pass first (the renderer fills {@link Layer#resetCapture} state),
 * then {@link Layer#computeSeam}, then the draw pass snaps to {@link Layer#seam}.
 */
public class WeldBinding
{
    private static final float EPS_SQ = 1.0e-6F;

    public final ModelGroup sourceGroup;
    public final ModelGroup targetGroup;
    public final List<Layer> layers;

    private WeldBinding(ModelGroup sourceGroup, ModelGroup targetGroup, List<Layer> layers)
    {
        this.sourceGroup = sourceGroup;
        this.targetGroup = targetGroup;
        this.layers = layers;
    }

    public static WeldBinding resolve(Model model, ModelWeld weld)
    {
        CubeFace sourceFace = CubeFace.fromName(weld.sourceFace);
        CubeFace targetFace = CubeFace.fromName(weld.targetFace);
        ModelGroup sourceGroup = model.getGroup(weld.sourceBone);
        ModelGroup targetGroup = model.getGroup(weld.targetBone);

        if (sourceFace == null || targetFace == null || sourceGroup == null || targetGroup == null)
        {
            return null;
        }

        List<ModelCube> sourceCubes = facedCubes(sourceGroup, sourceFace);
        List<ModelCube> targetCubes = facedCubes(targetGroup, targetFace);
        float maxBend = (float) Math.toRadians(weld.maxAngle);
        float falloff = weld.seamFalloff;
        List<Layer> layers = new ArrayList<>();

        for (int[] pair : pairByCrossSection(sourceCubes, sourceFace, targetCubes, targetFace))
        {
            layers.add(new Layer(sourceCubes.get(pair[0]), sourceFace, targetCubes.get(pair[1]), targetFace, maxBend, falloff));
        }

        return layers.isEmpty() ? null : new WeldBinding(sourceGroup, targetGroup, layers);
    }

    /**
     * Whether any of the group's welds actually moves its geometry this frame — a seam identical to the
     * rigid pose renders the same as the baked VAO, so the group can stay on it instead of tessellating on
     * the CPU. A seam differs from rigid whenever the joint bends OR the two welded faces don't already
     * coincide at rest (mismatched or overlapping cubes), so a rest-pose gap seals here too, not only bends.
     */
    public static boolean hasActiveSeam(List<WeldBinding> welds, ModelGroup group)
    {
        for (WeldBinding weld : welds)
        {
            if (weld.sourceGroup != group && weld.targetGroup != group)
            {
                continue;
            }

            for (Layer layer : weld.layers)
            {
                if (layer.seamReady && !layer.identity)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Welded face quads per cube, so the bevel can leave those faces (and their edges) sharp — the seam
     * lives on them — while the rest of the cube still rounds.
     */
    public static Map<ModelCube, Set<ModelQuad>> weldedFaces(Model model, List<ModelWeld> welds)
    {
        Map<ModelCube, Set<ModelQuad>> faces = new HashMap<>();

        for (ModelWeld weld : welds)
        {
            collectFaces(model, weld.sourceBone, weld.sourceFace, faces);
            collectFaces(model, weld.targetBone, weld.targetFace, faces);
        }

        return faces;
    }

    private static void collectFaces(Model model, String bone, String faceName, Map<ModelCube, Set<ModelQuad>> faces)
    {
        CubeFace face = CubeFace.fromName(faceName);
        ModelGroup group = model.getGroup(bone);

        if (face == null || group == null)
        {
            return;
        }

        for (ModelCube cube : facedCubes(group, face))
        {
            faces.computeIfAbsent(cube, (k) -> new HashSet<>()).add(faceQuad(cube, face));
        }
    }

    /** The group's cubes that carry the welded face, in model order; {@link #pairByCrossSection} pairs them up. */
    private static List<ModelCube> facedCubes(ModelGroup group, CubeFace face)
    {
        List<ModelCube> cubes = new ArrayList<>();

        for (ModelCube cube : group.cubes)
        {
            if (faceQuad(cube, face) != null)
            {
                cubes.add(cube);
            }
        }

        return cubes;
    }

    /**
     * Pair source cubes to target cubes by how closely their welded faces match in cross-section, so each
     * skin layer welds to its own counterpart (base to base, inflated jacket to jacket) whether the layers
     * differ by inflate or by raw size. Greedy: the closest-matching free pair is taken first, so a spare
     * cube on the longer side is left unwelded instead of dragging a mismatched partner onto its seam.
     */
    private static List<int[]> pairByCrossSection(List<ModelCube> sources, CubeFace sourceFace, List<ModelCube> targets, CubeFace targetFace)
    {
        Vector2f[] sourceSizes = crossSections(sources, sourceFace);
        Vector2f[] targetSizes = crossSections(targets, targetFace);
        List<int[]> candidates = new ArrayList<>();

        for (int s = 0; s < sources.size(); s++)
        {
            for (int t = 0; t < targets.size(); t++)
            {
                candidates.add(new int[] {s, t});
            }
        }

        candidates.sort((a, b) ->
        {
            int byScore = Float.compare(crossSectionDistance(sourceSizes[a[0]], targetSizes[a[1]]), crossSectionDistance(sourceSizes[b[0]], targetSizes[b[1]]));

            if (byScore != 0) return byScore;
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);

            return Integer.compare(a[1], b[1]);
        });

        boolean[] sourceUsed = new boolean[sources.size()];
        boolean[] targetUsed = new boolean[targets.size()];
        List<int[]> pairs = new ArrayList<>();

        for (int[] pair : candidates)
        {
            if (!sourceUsed[pair[0]] && !targetUsed[pair[1]])
            {
                sourceUsed[pair[0]] = true;
                targetUsed[pair[1]] = true;
                pairs.add(pair);
            }
        }

        return pairs;
    }

    /** The two in-plane extents of each cube's welded face (sorted small-to-large) — its cross-section size. */
    private static Vector2f[] crossSections(List<ModelCube> cubes, CubeFace face)
    {
        Vector3f[] axes = inPlaneAxes(face.normal);
        Vector2f[] sizes = new Vector2f[cubes.size()];

        for (int i = 0; i < cubes.size(); i++)
        {
            float a = axisExtent(cubes.get(i), axes[0]);
            float b = axisExtent(cubes.get(i), axes[1]);

            sizes[i] = a <= b ? new Vector2f(a, b) : new Vector2f(b, a);
        }

        return sizes;
    }

    private static float crossSectionDistance(Vector2f a, Vector2f b)
    {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    /** The two unit axes spanning an axis-aligned face's plane (the pair that isn't its normal). */
    private static Vector3f[] inPlaneAxes(Vector3f normal)
    {
        if (Math.abs(normal.x) > 0.5F) return new Vector3f[] {new Vector3f(0F, 1F, 0F), new Vector3f(0F, 0F, 1F)};
        if (Math.abs(normal.y) > 0.5F) return new Vector3f[] {new Vector3f(1F, 0F, 0F), new Vector3f(0F, 0F, 1F)};

        return new Vector3f[] {new Vector3f(1F, 0F, 0F), new Vector3f(0F, 1F, 0F)};
    }

    private static int nearest(Vector3f world, Vector3f[] corners)
    {
        int best = 0;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < corners.length; i++)
        {
            float dist = corners[i].distanceSquared(world);

            if (dist < bestDist)
            {
                bestDist = dist;
                best = i;
            }
        }

        return best;
    }

    private static Vector3f average(Vector3f[] points)
    {
        Vector3f sum = new Vector3f();

        for (Vector3f point : points)
        {
            sum.add(point);
        }

        return sum.mul(1F / points.length);
    }

    private static Vector3f[] faceCorners(ModelCube cube, CubeFace face)
    {
        ModelQuad quad = faceQuad(cube, face);
        Vector3f[] corners = new Vector3f[4];

        for (int i = 0; i < 4; i++)
        {
            corners[i] = new Vector3f(quad.vertices.get(i).vertex);
        }

        return corners;
    }

    /** How far the cube spans along {@code normal} in local space — the range of its vertices projected on it. */
    private static float axisExtent(ModelCube cube, Vector3f normal)
    {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (ModelQuad quad : cube.quads)
        {
            for (ModelVertex vertex : quad.vertices)
            {
                float projection = vertex.vertex.dot(normal);

                min = Math.min(min, projection);
                max = Math.max(max, projection);
            }
        }

        return max - min;
    }

    private static ModelQuad faceQuad(ModelCube cube, CubeFace face)
    {
        for (ModelQuad quad : cube.quads)
        {
            if (quad.vertices.size() == 4 && quad.normal.distanceSquared(face.normal) < EPS_SQ)
            {
                return quad;
            }
        }

        return null;
    }

    /**
     * One welded layer: a source cube glued to a target cube. Carries its own seam so each skin layer keeps
     * its own size through the bend. The renderer fills the captured world poses during the capture pass.
     */
    public static class Layer
    {
        /* Max world-space slack (squared) between a welded corner and its seam still counted as "already there":
         * ~1e-3 blocks (0.016 texel), far above matrix-chain float noise yet far below any visible joint gap. */
        private static final float IDENTITY_EPS_SQ = 1.0e-6F;

        public final ModelCube sourceCube;
        public final ModelCube targetCube;

        /* Local welded-face corners of each cube. */
        public final Vector3f[] sourceCorners;
        public final Vector3f[] targetCorners;

        /* Local outward normals of each welded face — the axis the bend spreads along (each bone's length). */
        public final Vector3f targetFaceNormal;
        public final Vector3f sourceFaceNormal;

        /* Local coordinate of each welded face along its own normal — the plane the bend is measured from. */
        public final float targetWeldPlane;
        public final float sourceWeldPlane;

        /* Length of each cube along its welded-face normal — the bend band is a fraction of this. */
        public final float targetAxisExtent;
        public final float sourceAxisExtent;

        /* Largest bend (radians) the seam follows; beyond it the shear holds steady. */
        public final float maxBend;

        /* Fraction (0..1) of a cube's axis length the bend spreads from the seam; smaller = tighter band. */
        public final float falloff;

        /* Rigid world poses of the two faces, captured each frame before any snapping. */
        public final Vector3f[] capturedSourceWorld = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        public final Vector3f[] capturedTargetWorld = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

        /* World shear axis: the target face's outward normal carried to world by the FULL cube matrix (bone x cube
         * rotate) — the direction the seam slides along. A vector (not a quaternion) so a scaled or MIRRORED matrix
         * (the UI preview flips Y) carries exactly; quaternion extraction from such a matrix is garbage. */
        public final Vector3f capturedTargetNormalWorld = new Vector3f();

        /* World bend axes of each BONE (group) WITHOUT the cubes' own rotate — the same face normal by the bone basis
         * only. The bend is the angle between these, so a cube rotated in Blockbench can't masquerade as a fold. */
        public final Vector3f capturedTargetBoneAxis = new Vector3f();
        public final Vector3f capturedSourceBoneAxis = new Vector3f();

        /* Source corner -> target corner, matched by proximity once both faces are captured. */
        public final int[] sourceToTarget = {-1, -1, -1, -1};

        /* The shared seam, indexed by target corner. */
        public final Vector3f[] seam = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

        public boolean sourceCaptured;
        public boolean targetCaptured;
        public boolean seamReady;

        /* The seam leaves both welded faces exactly where the rigid pose already puts them (no bend AND no
         * rest gap between the faces), so snapping to it is a no-op and the group may ride its baked VAO. */
        public boolean identity;

        private Layer(ModelCube sourceCube, CubeFace sourceFace, ModelCube targetCube, CubeFace targetFace, float maxBend, float falloff)
        {
            this.sourceCube = sourceCube;
            this.targetCube = targetCube;
            this.sourceCorners = faceCorners(sourceCube, sourceFace);
            this.targetCorners = faceCorners(targetCube, targetFace);
            this.targetFaceNormal = new Vector3f(targetFace.normal);
            this.sourceFaceNormal = new Vector3f(sourceFace.normal);
            this.targetWeldPlane = this.targetCorners[0].dot(this.targetFaceNormal);
            this.sourceWeldPlane = this.sourceCorners[0].dot(this.sourceFaceNormal);
            this.targetAxisExtent = axisExtent(targetCube, this.targetFaceNormal);
            this.sourceAxisExtent = axisExtent(sourceCube, this.sourceFaceNormal);
            this.maxBend = maxBend;
            this.falloff = falloff;
        }

        public void resetCapture()
        {
            this.sourceCaptured = false;
            this.targetCaptured = false;
            this.seamReady = false;
        }

        /**
         * Build the seam by SHEARING the target face along the parent bone's axis — the BOBJ simple-rig trick
         * (its joint slides vertices along the bone instead of rotating them). Each corner slides along the
         * face normal proportionally to its position across the bend; only that along-axis component moves, so
         * the cross-section keeps its full width (a rotation would shrink the projection by cos — a pinch),
         * while the slope tilts the seam to the bend's bisector so both bones flex toward it.
         *
         * <p>The fold direction is read from the captured corner displacements (no sign-fragile axis math); the
         * magnitude is tan(bend/2) from the two bone orientations, which grows with the angle instead of
         * saturating the way a displacement projection does (that left everything past ~45° bending one-sided).
         */
        public void computeSeam()
        {
            if (!this.sourceCaptured || !this.targetCaptured)
            {
                return;
            }

            for (int r = 0; r < this.sourceToTarget.length; r++)
            {
                if (this.sourceToTarget[r] == -1)
                {
                    this.sourceToTarget[r] = nearest(this.capturedSourceWorld[r], this.capturedTargetWorld);
                }
            }

            Vector3f normal = new Vector3f(this.capturedTargetNormalWorld);
            Vector3f center = average(this.capturedTargetWorld);
            Vector3f across = this.foldAxis(normal, center);

            if (across == null)
            {
                /* No fold direction (the joint isn't bent), so the seam sits on the target's rigid face. */
                for (int k = 0; k < this.seam.length; k++)
                {
                    this.seam[k].set(this.capturedTargetWorld[k]);
                }
            }
            else
            {
                float tanHalf = (float) Math.tan(Math.min(this.bendAngle(), this.maxBend) * 0.5F);

                for (int k = 0; k < this.seam.length; k++)
                {
                    Vector3f target = this.capturedTargetWorld[k];
                    float position = (target.x - center.x) * across.x + (target.y - center.y) * across.y + (target.z - center.z) * across.z;

                    this.seam[k].set(normal).mul(position * tanHalf).add(target);
                }
            }

            /* Ride the VAO only when the seam moves nothing — no bend AND the faces already meet at rest. A
             * rest-pose gap makes this false, so the weld seals it now instead of snapping it shut on first bend. */
            this.identity = this.seamMatchesRigid();
            this.seamReady = true;
        }

        /**
         * Whether the finished seam leaves BOTH welded faces exactly where their rigid pose already puts them:
         * the target corners unmoved and every source corner already sitting on the seam it maps to. Only then
         * is snapping a true no-op — a bent joint moves the target, and a rest gap (mismatched or overlapping
         * cubes) leaves the source off-seam — so anything else must render through the seam, not the VAO.
         */
        private boolean seamMatchesRigid()
        {
            for (int k = 0; k < this.seam.length; k++)
            {
                if (this.seam[k].distanceSquared(this.capturedTargetWorld[k]) > IDENTITY_EPS_SQ)
                {
                    return false;
                }
            }

            for (int r = 0; r < this.sourceToTarget.length; r++)
            {
                if (this.capturedSourceWorld[r].distanceSquared(this.seam[this.sourceToTarget[r]]) > IDENTITY_EPS_SQ)
                {
                    return false;
                }
            }

            return true;
        }

        /**
         * The in-plane axis the joint folds across, read from the data: the direction in which the child
         * corners' displacement along the face normal increases. Returns a unit vector in the face plane, or
         * null when the face is barely bent (no meaningful fold direction yet).
         */
        private Vector3f foldAxis(Vector3f normal, Vector3f center)
        {
            Vector3f axis = new Vector3f();

            for (int k = 0; k < this.capturedTargetWorld.length; k++)
            {
                Vector3f target = this.capturedTargetWorld[k];
                int r = this.sourceForTarget(k);
                Vector3f source = r == -1 ? target : this.capturedSourceWorld[r];
                float slide = (source.x - target.x) * normal.x + (source.y - target.y) * normal.y + (source.z - target.z) * normal.z;

                axis.add((target.x - center.x) * slide, (target.y - center.y) * slide, (target.z - center.z) * slide);
            }

            axis.sub(new Vector3f(normal).mul(axis.dot(normal)));

            return axis.lengthSquared() < EPS_SQ ? null : axis.normalize();
        }

        /**
         * The fold angle, from the two bones' world axes alone (no quaternions, so scale/reflection are irrelevant).
         * At rest a straight limb has the two welded faces pointing apart, so their bone-carried normals are opposite
         * (angle PI); folding closes that gap, so the bend is PI minus the angle between the axes. Only the swing is
         * measured — twist around the bone axis (which the seam ignores anyway) never leaks in.
         */
        private float bendAngle()
        {
            float dot = this.capturedTargetBoneAxis.dot(this.capturedSourceBoneAxis);

            return (float) Math.PI - (float) Math.acos(Math.max(-1F, Math.min(1F, dot)));
        }

        private int sourceForTarget(int k)
        {
            for (int r = 0; r < this.sourceToTarget.length; r++)
            {
                if (this.sourceToTarget[r] == k)
                {
                    return r;
                }
            }

            return -1;
        }

        /* The seam is indexed by target corner, so the target face reads it in place. */
        private static final int[] TARGET_ORDER = {0, 1, 2, 3};

        /**
         * Seam position for a point on the target cube's welded plane — bilinear over the face rect, so
         * inset (beveled) geometry at the joint rides the seam too, not only the four exact corners.
         */
        public Vector3f seamAtTarget(Vector3f local, Vector3f dest)
        {
            return this.seamAt(this.targetCorners, TARGET_ORDER, local, dest);
        }

        public Vector3f seamAtSource(Vector3f local, Vector3f dest)
        {
            return this.seamAt(this.sourceCorners, this.sourceToTarget, local, dest);
        }

        private Vector3f seamAt(Vector3f[] corners, int[] order, Vector3f local, Vector3f dest)
        {
            Vector3f c0 = corners[0];
            Vector3f c1 = corners[1];
            Vector3f c3 = corners[3];
            float e1x = c1.x - c0.x;
            float e1y = c1.y - c0.y;
            float e1z = c1.z - c0.z;
            float e2x = c3.x - c0.x;
            float e2y = c3.y - c0.y;
            float e2z = c3.z - c0.z;
            float dx = local.x - c0.x;
            float dy = local.y - c0.y;
            float dz = local.z - c0.z;
            float l1 = e1x * e1x + e1y * e1y + e1z * e1z;
            float l2 = e2x * e2x + e2y * e2y + e2z * e2z;

            /* A face inset to a line by a large bevel has a degenerate edge — park that param mid-seam. */
            float s = l1 > EPS_SQ ? (dx * e1x + dy * e1y + dz * e1z) / l1 : 0.5F;
            float t = l2 > EPS_SQ ? (dx * e2x + dy * e2y + dz * e2z) / l2 : 0.5F;

            s = Math.max(0F, Math.min(1F, s));
            t = Math.max(0F, Math.min(1F, t));

            Vector3f s0 = this.seam[order[0]];
            Vector3f s1 = this.seam[order[1]];
            Vector3f s2 = this.seam[order[2]];
            Vector3f s3 = this.seam[order[3]];
            float w0 = (1F - s) * (1F - t);
            float w1 = s * (1F - t);
            float w2 = s * t;
            float w3 = (1F - s) * t;

            return dest.set(
                s0.x * w0 + s1.x * w1 + s2.x * w2 + s3.x * w3,
                s0.y * w0 + s1.y * w1 + s2.y * w2 + s3.y * w3,
                s0.z * w0 + s1.z * w1 + s2.z * w2 + s3.z * w3
            );
        }
    }
}
