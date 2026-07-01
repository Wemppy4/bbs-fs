package mchorse.bbs_mod.cubic.weld;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

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

    /** Index of the corner matching {@code local} (by position), or -1 if none — used to spot welded vertices. */
    public static int cornerIndex(Vector3f[] corners, Vector3f local)
    {
        for (int i = 0; i < corners.length; i++)
        {
            if (corners[i].distanceSquared(local) < EPS_SQ)
            {
                return i;
            }
        }

        return -1;
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
                for (int k = 0; k < this.seam.length; k++)
                {
                    this.seam[k].set(this.capturedTargetWorld[k]);
                }

                this.seamReady = true;

                return;
            }

            float tanHalf = (float) Math.tan(Math.min(this.bendAngle(), this.maxBend) * 0.5F);

            for (int k = 0; k < this.seam.length; k++)
            {
                Vector3f target = this.capturedTargetWorld[k];
                float position = (target.x - center.x) * across.x + (target.y - center.y) * across.y + (target.z - center.z) * across.z;

                this.seam[k].set(normal).mul(position * tanHalf).add(target);
            }

            this.seamReady = true;
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
    }
}
