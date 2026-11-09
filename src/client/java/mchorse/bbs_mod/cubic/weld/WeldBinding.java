package mchorse.bbs_mod.cubic.weld;

import mchorse.bbs_mod.cubic.data.model.CubeFace;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
 * different size, so they get their OWN seam: the cubes of the two bones are paired by how their welded
 * faces fit in the rest pose (base to base, jacket to jacket — whether the layers differ by inflate or by
 * raw size) and every pair seals independently. A single shared seam would drag the base layer out to the
 * jacket's size and puff the joint. A bone of many cubes gets its pairs picked by hand instead
 * ({@link ModelWeld#pairs}): then only those weld, and the rest of the bone stays rigid.
 *
 * <p>Both faces' rigid world poses are needed before the seam can be built, so the renderer first walks the
 * pose and captures them ({@link Layer#captureSource}, {@link Layer#captureTarget}), then
 * {@link Layer#computeSeam}, and only then deforms the bands toward {@link Layer#seam}.
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

    /** Why a weld declaration fails to resolve against a model — surfaced by the editor instead of a silent no-op. */
    public enum Issue
    {
        SOURCE_BONE,
        TARGET_BONE,

        /** Both faces sit on one bone — rigid, so there is no joint between them to seal; the source face would just collapse onto the target's. */
        SAME_BONE,

        SOURCE_FACE,
        TARGET_FACE,

        /** The bone exists, but none of its cubes has a quad on the welded face (mesh-only bone, or a trimmed cube). */
        SOURCE_CUBES,
        TARGET_CUBES,

        /** A pair names a cube the bone doesn't have (cubes changed outside BBS), or one without the welded face. */
        SOURCE_PAIR,
        TARGET_PAIR
    }

    /**
     * The first reason this weld can't resolve, or null when it can. THE precondition of {@link #resolve}:
     * a weld passing this always yields a binding (both sides have a faced cube, so pairing yields at least
     * one layer; a weld going by its pairs has a picked pair, every cube of which carries the face), so the
     * two can't drift apart.
     */
    public static Issue diagnose(Model model, ModelWeld weld)
    {
        if (model.getGroup(weld.sourceBone) == null) return Issue.SOURCE_BONE;
        if (model.getGroup(weld.targetBone) == null) return Issue.TARGET_BONE;
        if (model.getGroup(weld.sourceBone) == model.getGroup(weld.targetBone)) return Issue.SAME_BONE;
        if (CubeFace.fromName(weld.sourceFace) == null) return Issue.SOURCE_FACE;
        if (CubeFace.fromName(weld.targetFace) == null) return Issue.TARGET_FACE;

        if (facedCubes(model.getGroup(weld.sourceBone), CubeFace.fromName(weld.sourceFace)).isEmpty()) return Issue.SOURCE_CUBES;
        if (facedCubes(model.getGroup(weld.targetBone), CubeFace.fromName(weld.targetFace)).isEmpty()) return Issue.TARGET_CUBES;

        for (ModelWeld.Pair pair : weld.pairs)
        {
            if (!pairable(model.getGroup(weld.sourceBone), CubeFace.fromName(weld.sourceFace), pair.source())) return Issue.SOURCE_PAIR;
            if (!pairable(model.getGroup(weld.targetBone), CubeFace.fromName(weld.targetFace), pair.target())) return Issue.TARGET_PAIR;
        }

        return null;
    }

    /** Whether a pair's side can weld: not picked yet, or a cube of the bone that has the welded face. */
    public static boolean pairable(ModelGroup group, CubeFace face, int cube)
    {
        return cube < 0 || (cube < group.cubes.size() && faceQuad(group.cubes.get(cube), face) != null);
    }

    /** The middle of a cube's welded face in the rest pose, where {@link #resolve} pairs the faces; null for a cube without that face. */
    public static Vector3f restFaceCenter(ModelGroup group, ModelCube cube, CubeFace face)
    {
        if (faceQuad(cube, face) == null)
        {
            return null;
        }

        return average(transformCorners(restCubeMatrix(restWorldMatrix(group), cube), faceCorners(cube, face)));
    }

    /** A point in the frame {@code group}'s cubes are drawn in, carried into the rest pose — where {@link #restFaceCenter} stands. */
    public static Vector3f restPoint(ModelGroup group, Vector3f point)
    {
        return restWorldMatrix(group).transformPosition(point, new Vector3f());
    }

    /**
     * How far {@code point}, at rest, is from where the weld meets one of its sides: the nearest middle
     * of that side's welded faces — of its picked cubes, when the weld goes by its pairs. MAX_VALUE for
     * a side without its bone or face.
     */
    public static float restDistance(Model model, ModelWeld weld, boolean source, Vector3f point)
    {
        ModelGroup group = model.getGroup(source ? weld.sourceBone : weld.targetBone);
        CubeFace face = CubeFace.fromName(source ? weld.sourceFace : weld.targetFace);
        float distance = Float.MAX_VALUE;

        if (group == null || face == null)
        {
            return distance;
        }

        boolean picked = weld.picksCubes();

        for (int i = 0; i < group.cubes.size(); i++)
        {
            if (picked && !picks(weld, source, i))
            {
                continue;
            }

            Vector3f center = restFaceCenter(group, group.cubes.get(i), face);

            if (center != null)
            {
                distance = Math.min(distance, center.distance(point));
            }
        }

        return distance;
    }

    /** Whether a picked pair of the weld has {@code cube} on one side. */
    private static boolean picks(ModelWeld weld, boolean source, int cube)
    {
        for (ModelWeld.Pair pair : weld.pairs)
        {
            if (pair.isPicked() && pair.side(source) == cube)
            {
                return true;
            }
        }

        return false;
    }

    public static WeldBinding resolve(Model model, ModelWeld weld)
    {
        if (diagnose(model, weld) != null)
        {
            return null;
        }

        CubeFace sourceFace = CubeFace.fromName(weld.sourceFace);
        CubeFace targetFace = CubeFace.fromName(weld.targetFace);
        ModelGroup sourceGroup = model.getGroup(weld.sourceBone);
        ModelGroup targetGroup = model.getGroup(weld.targetBone);

        /* Picked by hand, the two lists line up pair by pair; else every faced cube takes part, and the fit pairs them below. */
        boolean picked = weld.picksCubes();
        List<ModelCube> sourceCubes = picked ? new ArrayList<>() : facedCubes(sourceGroup, sourceFace);
        List<ModelCube> targetCubes = picked ? new ArrayList<>() : facedCubes(targetGroup, targetFace);
        List<Layer> layers = new ArrayList<>();

        if (picked)
        {
            pickedCubes(weld, sourceGroup, targetGroup, sourceCubes, targetCubes);
        }

        /* Rest-pose world matrices of both bones, composed the exact way the renderer stacks them: which
         * cube pairs with which, and the corner correspondence within each pair, are decided HERE, in the
         * one pose where the modeler actually aligned them — matching by proximity in whatever animated
         * pose the first frame happens to catch fixes a bent/twisted mapping forever. (The rest twist bias
         * is read off the same pose.) */
        Matrix4f sourceGroupRest = restWorldMatrix(sourceGroup);
        Matrix4f targetGroupRest = restWorldMatrix(targetGroup);
        Vector3f[][] sourceRestWorld = restFaceCorners(sourceGroupRest, sourceCubes, sourceFace);
        Vector3f[][] targetRestWorld = restFaceCorners(targetGroupRest, targetCubes, targetFace);

        /* The share knob is authored as the PARENT bone's share, but the seam math is anchored to the
         * target's face — so resolve which side the parent actually is from the model hierarchy and flip
         * the share when the parent turned out to be the source. Source/target roles are whatever order
         * the weld was authored in; without this, the same knob value would mean opposite things on two
         * welds of the same rig. Unrelated bones (no ancestry either way) treat the target as the parent. */
        float targetShare = isAncestor(sourceGroup, targetGroup) ? 1F - weld.parentShare : weld.parentShare;

        for (int[] pair : picked ? samePlaces(sourceCubes.size()) : pairByRestFit(sourceRestWorld, targetRestWorld))
        {
            layers.add(new Layer(
                sourceCubes.get(pair[0]), sourceFace, sourceGroupRest, sourceRestWorld[pair[0]],
                targetCubes.get(pair[1]), targetFace, targetGroupRest, targetRestWorld[pair[1]],
                weld, targetShare
            ));
        }

        return layers.isEmpty() ? null : new WeldBinding(sourceGroup, targetGroup, layers);
    }

    /** The group's cubes that carry the welded face, in model order; {@link #pairByRestFit} pairs them up. */
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
     * The cubes of the weld's picked pairs, side by side: the i-th source cube welds to the i-th target
     * cube. A pair with a side not picked yet sits out; so does one naming a cube an earlier pair took on
     * the same side, since a cube carries one seam per face — the panel never makes such a pair, a file
     * edited by hand can.
     */
    private static void pickedCubes(ModelWeld weld, ModelGroup sourceGroup, ModelGroup targetGroup, List<ModelCube> sourceCubes, List<ModelCube> targetCubes)
    {
        for (ModelWeld.Pair pair : weld.pairs)
        {
            if (!pair.isPicked())
            {
                continue;
            }

            ModelCube source = sourceGroup.cubes.get(pair.source());
            ModelCube target = targetGroup.cubes.get(pair.target());

            if (!sourceCubes.contains(source) && !targetCubes.contains(target))
            {
                sourceCubes.add(source);
                targetCubes.add(target);
            }
        }
    }

    /** The pairs of two lists matched by place, as {@link #pairByRestFit} gives them. */
    private static List<int[]> samePlaces(int count)
    {
        List<int[]> pairs = new ArrayList<>();

        for (int i = 0; i < count; i++)
        {
            pairs.add(new int[] {i, i});
        }

        return pairs;
    }

    /** Each cube's welded-face corners carried to the rest-pose world by its bone and its own modeling transform. */
    private static Vector3f[][] restFaceCorners(Matrix4f groupRest, List<ModelCube> cubes, CubeFace face)
    {
        Vector3f[][] corners = new Vector3f[cubes.size()][];

        for (int i = 0; i < cubes.size(); i++)
        {
            corners[i] = transformCorners(restCubeMatrix(groupRest, cubes.get(i)), faceCorners(cubes.get(i), face));
        }

        return corners;
    }

    /**
     * Pair source cubes to target cubes by how well their welded faces fit in the rest pose — the same
     * corner-to-corner distance {@link #matchCorners} minimizes, so size and position count together: each
     * skin layer welds to its own counterpart (base to base, inflated jacket to jacket, whether the layers
     * differ by inflate or by raw size), and two same-sized cubes side by side each take the one actually in
     * front of them rather than whichever the model lists first. Greedy: the best-fitting free pair is taken
     * first, so a spare cube on the longer side is left unwelded instead of dragging a mismatched partner
     * onto its seam.
     */
    private static List<int[]> pairByRestFit(Vector3f[][] sources, Vector3f[][] targets)
    {
        float[][] fit = new float[sources.length][targets.length];
        List<int[]> candidates = new ArrayList<>();

        for (int s = 0; s < sources.length; s++)
        {
            for (int t = 0; t < targets.length; t++)
            {
                fit[s][t] = matchDistance(sources[s], targets[t], matchCorners(sources[s], targets[t]));
                candidates.add(new int[] {s, t});
            }
        }

        candidates.sort((a, b) ->
        {
            int byFit = Float.compare(fit[a[0]][a[1]], fit[b[0]][b[1]]);

            if (byFit != 0) return byFit;
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);

            return Integer.compare(a[1], b[1]);
        });

        boolean[] sourceUsed = new boolean[sources.length];
        boolean[] targetUsed = new boolean[targets.length];
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

    /** Whether {@code ancestor} sits above {@code group} in the bone hierarchy. */
    private static boolean isAncestor(ModelGroup ancestor, ModelGroup group)
    {
        for (ModelGroup parent = group.parent; parent != null; parent = parent.parent)
        {
            if (parent == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The bone's rest-pose world matrix: every ancestor's initial transform composed exactly like
     * {@code ICubicRenderer.applyGroupTransformations} does at rest (the translate term is zero there,
     * since {@code current == initial}), so rest corners land where the renderer would put them.
     */
    private static Matrix4f restWorldMatrix(ModelGroup group)
    {
        Matrix4f matrix = group.parent != null ? restWorldMatrix(group.parent) : new Matrix4f();
        Vector3f pivot = group.initial.translate;
        Vector3f rotate = group.initial.rotate;
        Vector3f scale = group.initial.scale;

        matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        /* NOT Transform.createRotation(): that reads euler as radians, while cubic group channels are
         * degrees — the same reason rotateGroup goes through toLocalRotationZYXDegrees. */
        if (group.initial.rotationMode == Transform.RotationMode.QUATERNION)
        {
            matrix.rotate(group.initial.quat);
        }
        else if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
        {
            matrix.rotate(Matrices.toLocalRotationZYXDegrees(rotate));
        }

        matrix.scale(scale.x, scale.y, scale.z);
        matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);

        return matrix;
    }

    /** The cube's own modeling transform on top of its bone, mirroring {@code CubicCubeRenderer.rotate} (ZYX). */
    private static Matrix4f restCubeMatrix(Matrix4f groupMatrix, ModelCube cube)
    {
        Matrix4f matrix = new Matrix4f(groupMatrix);
        Vector3f pivot = cube.pivot;
        Vector3f rotate = cube.rotate;

        matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
        {
            matrix.rotateZ(MathUtils.toRad(rotate.z)).rotateY(MathUtils.toRad(rotate.y)).rotateX(MathUtils.toRad(rotate.x));
        }

        matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);

        return matrix;
    }

    /* All 24 orderings of {0,1,2,3}, for the optimal corner assignment below. */
    private static final int[][] PERMUTATIONS = permutations();

    private static int[][] permutations()
    {
        int[][] result = new int[24][];
        int n = 0;

        for (int a = 0; a < 4; a++)
        {
            for (int b = 0; b < 4; b++)
            {
                for (int c = 0; c < 4; c++)
                {
                    for (int d = 0; d < 4; d++)
                    {
                        if (a != b && a != c && a != d && b != c && b != d && c != d)
                        {
                            result[n++] = new int[] {a, b, c, d};
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Match each source corner to a distinct target corner, minimizing total rest-pose distance — a true
     * bijection (4! is 24 candidates, brute force is exact). Independent per-corner nearest matching can
     * send two source corners to the SAME target corner on a twisted or offset rest fit, which degenerates
     * the seam quad.
     */
    private static int[] matchCorners(Vector3f[] sourceRest, Vector3f[] targetRest)
    {
        int[] best = PERMUTATIONS[0];
        float bestScore = Float.MAX_VALUE;

        for (int[] permutation : PERMUTATIONS)
        {
            float score = matchDistance(sourceRest, targetRest, permutation);

            if (score < bestScore)
            {
                bestScore = score;
                best = permutation;
            }
        }

        return best.clone();
    }

    /** Total squared rest-pose distance between the corners under a source -> target assignment: how well the faces fit. */
    private static float matchDistance(Vector3f[] sourceRest, Vector3f[] targetRest, int[] sourceToTarget)
    {
        float score = 0F;

        for (int r = 0; r < sourceToTarget.length; r++)
        {
            score += sourceRest[r].distanceSquared(targetRest[sourceToTarget[r]]);
        }

        return score;
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

    private static Vector3f[] transformCorners(Matrix4f matrix, Vector3f[] corners)
    {
        Vector3f[] world = new Vector3f[corners.length];

        for (int i = 0; i < corners.length; i++)
        {
            world[i] = matrix.transformPosition(corners[i], new Vector3f());
        }

        return world;
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

        /* The target bone's EFFECTIVE share (0..1) of the joint's deformation — the authored parent share,
         * already flipped by resolve() if the parent turned out to be the source. The source takes the rest. */
        public final float targetShare;

        /* Whether the seam also distributes twist (rotation about the bone axis) across the band. */
        public final boolean twist;

        /* Whether the two sides share shading normals along the seam (the patch buffer resolves it after the walk). */
        public final boolean smooth;

        /* Whether the bands bend along a curve (see ModelWeld#curve); false too when the bands are too thin to carry one. */
        public final boolean curve;

        /* Twist already present between the two faces in the rest pose (rotated cubes), subtracted from the
         * live measurement so only ANIMATED twist deforms the band. */
        private final float restTwist;

        /* The curve mode's rest: each cube's rest matrix (bone x cube rotate) and its inverse, which turn a
         * captured matrix into the pose's change since rest; the band lengths (local units, the same the
         * mesh measures its points in); where the seam sits along the curve; the curve at rest, a cubic
         * Bezier from the far end of the target's band to the far end of the source's, its handles along
         * each bone; and, per source corner, how far the target's matched corner sat from it at rest. */
        private final Matrix4f targetRest = new Matrix4f();
        private final Matrix4f sourceRest = new Matrix4f();
        private final Matrix4f targetRestInverse = new Matrix4f();
        private final Matrix4f sourceRestInverse = new Matrix4f();
        private final float targetBand;
        private final float sourceBand;
        private final float seamParameter;
        private final Vector3f[] restCurve = vectors(4);
        private final Vector3f[] restGap = vectors(4);

        /* Rigid world matrices of the two cubes, captured with the faces; the curve is built from them. */
        public final Matrix4f capturedTargetMatrix = new Matrix4f();
        public final Matrix4f capturedSourceMatrix = new Matrix4f();

        /* The curve of the frame: its control points, each end's rotation since rest and what's left of its
         * linear change once that's taken out (a scale), and the turn that cancels the tangent mismatch at
         * either end so the band meets its rigid cube exactly. Ready only when the seam is (both bones
         * shown) and neither cube's pose mirrors. */
        public boolean curveReady;
        private final Vector3f[] poseCurve = vectors(4);
        private final Quaternionf targetRotation = new Quaternionf();
        private final Quaternionf sourceRotation = new Quaternionf();
        private final Matrix3f targetStretch = new Matrix3f();
        private final Matrix3f sourceStretch = new Matrix3f();
        private final Quaternionf startFix = new Quaternionf();
        private final Quaternionf endFix = new Quaternionf();

        /* Scratch of the curve, reused every frame and every point. */
        private final Matrix4f poseMatrix = new Matrix4f();
        private final Matrix3f linear = new Matrix3f();
        private final Matrix3f stretch = new Matrix3f();
        private final Quaternionf turn = new Quaternionf();
        private final Vector3f restPoint = new Vector3f();
        private final Vector3f restTangent = new Vector3f();
        private final Vector3f tangent = new Vector3f();
        private final float[] faceParams = new float[2];

        /* The last ring asked for, kept for the next point on it; NaN (no ring) once the curve changes. */
        private float ringParameter = Float.NaN;
        private final Quaternionf ring = new Quaternionf();
        private final Vector3f ringCentre = new Vector3f();
        private final Vector3f ringRestCentre = new Vector3f();

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

        /* Source corner -> target corner: a bijection fixed at resolve time from the rest pose (the one
         * pose where the modeler aligned the faces), so no animated first frame can bake in a bad match. */
        public final int[] sourceToTarget;

        /* The shared seam, indexed by target corner. */
        public final Vector3f[] seam = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

        public boolean sourceCaptured;
        public boolean targetCaptured;
        public boolean seamReady;

        private Layer(ModelCube sourceCube, CubeFace sourceFace, Matrix4f sourceGroupRest, Vector3f[] sourceRestWorld, ModelCube targetCube, CubeFace targetFace, Matrix4f targetGroupRest, Vector3f[] targetRestWorld, ModelWeld weld, float targetShare)
        {
            this.sourceCube = sourceCube;
            this.targetCube = targetCube;
            this.sourceCorners = faceCorners(sourceCube, sourceFace);
            this.targetCorners = faceCorners(targetCube, targetFace);
            this.sourceToTarget = matchCorners(sourceRestWorld, targetRestWorld);
            this.targetFaceNormal = new Vector3f(targetFace.normal);
            this.sourceFaceNormal = new Vector3f(sourceFace.normal);
            this.targetWeldPlane = this.targetCorners[0].dot(this.targetFaceNormal);
            this.sourceWeldPlane = this.sourceCorners[0].dot(this.sourceFaceNormal);
            this.targetAxisExtent = axisExtent(targetCube, this.targetFaceNormal);
            this.sourceAxisExtent = axisExtent(sourceCube, this.sourceFaceNormal);
            this.maxBend = (float) Math.toRadians(weld.maxAngle);
            this.falloff = weld.seamFalloff;
            this.targetShare = targetShare;
            this.twist = weld.twist;
            this.smooth = weld.smooth;

            Vector3f restSourceAxis = sourceGroupRest.transformDirection(new Vector3f(this.sourceFaceNormal)).normalize();
            Vector3f restTargetAxis = targetGroupRest.transformDirection(new Vector3f(this.targetFaceNormal)).normalize();

            this.restTwist = this.twistBetween(sourceRestWorld, targetRestWorld, restSourceAxis, restTargetAxis);

            this.targetRest.set(restCubeMatrix(targetGroupRest, targetCube));
            this.sourceRest.set(restCubeMatrix(sourceGroupRest, sourceCube));
            this.targetRest.invert(this.targetRestInverse);
            this.sourceRest.invert(this.sourceRestInverse);
            this.targetBand = this.falloff * this.targetAxisExtent;
            this.sourceBand = this.falloff * this.sourceAxisExtent;

            for (int k = 0; k < 4; k++)
            {
                this.restGap[k].set(targetRestWorld[this.sourceToTarget[k]]).sub(sourceRestWorld[k]);
            }

            this.curve = weld.curve && this.layOutRestCurve(this.targetRest, this.sourceRest);

            float targetLength = this.targetRest.transformDirection(new Vector3f(this.targetFaceNormal)).length() * this.targetBand;
            float sourceLength = this.sourceRest.transformDirection(new Vector3f(this.sourceFaceNormal)).length() * this.sourceBand;

            this.seamParameter = targetLength + sourceLength > EPS ? targetLength / (targetLength + sourceLength) : 0.5F;
        }

        /**
         * Lay the curve out at rest: from the target band's far end, the middle of its welded face pushed a
         * band into the cube, to the source band's far end, with each handle a third of the way along its
         * own bone — a straight line run at an even pace when the bones line up. Whether there is a curve to
         * speak of: two bands too thin to span anything have none.
         */
        private boolean layOutRestCurve(Matrix4f targetRest, Matrix4f sourceRest)
        {
            if (this.targetBand < EPS || this.sourceBand < EPS)
            {
                return false;
            }

            Vector3f targetEnd = average(this.targetCorners).sub(new Vector3f(this.targetFaceNormal).mul(this.targetBand));
            Vector3f sourceEnd = average(this.sourceCorners).sub(new Vector3f(this.sourceFaceNormal).mul(this.sourceBand));

            targetRest.transformPosition(targetEnd, this.restCurve[0]);
            sourceRest.transformPosition(sourceEnd, this.restCurve[3]);

            float span = this.restCurve[0].distance(this.restCurve[3]);

            if (span < EPS)
            {
                return false;
            }

            Vector3f targetAxis = targetRest.transformDirection(new Vector3f(this.targetFaceNormal)).normalize();
            Vector3f sourceAxis = sourceRest.transformDirection(new Vector3f(this.sourceFaceNormal)).normalize();

            this.restCurve[1].set(targetAxis).mul(span / 3F).add(this.restCurve[0]);
            this.restCurve[2].set(sourceAxis).mul(span / 3F).add(this.restCurve[3]);

            return true;
        }

        public void resetCapture()
        {
            this.sourceCaptured = false;
            this.targetCaptured = false;
            this.seamReady = false;
            this.curveReady = false;
        }

        /**
         * Record the target face's rigid world corners plus the shear axis (the face normal by the FULL cube
         * matrix — the cube's modeling rotation is a legit part of the face direction) and the bend axis (the
         * same normal by the BONE matrix only, so a cube's static rotate can't masquerade as a fold).
         */
        public void captureTarget(Matrix4f bone, Matrix4f cube)
        {
            for (int i = 0; i < this.targetCorners.length; i++)
            {
                cube.transformPosition(this.targetCorners[i], this.capturedTargetWorld[i]);
            }

            cube.transformDirection(this.capturedTargetNormalWorld.set(this.targetFaceNormal)).normalize();
            bone.transformDirection(this.capturedTargetBoneAxis.set(this.targetFaceNormal)).normalize();
            this.capturedTargetMatrix.set(cube);
            this.targetCaptured = true;
        }

        /** Record the source face's rigid world corners and its bone axis — see {@link #captureTarget}. */
        public void captureSource(Matrix4f bone, Matrix4f cube)
        {
            for (int i = 0; i < this.sourceCorners.length; i++)
            {
                cube.transformPosition(this.sourceCorners[i], this.capturedSourceWorld[i]);
            }

            bone.transformDirection(this.capturedSourceBoneAxis.set(this.sourceFaceNormal)).normalize();
            this.capturedSourceMatrix.set(cube);
            this.sourceCaptured = true;
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
                /* The target's tilt is its SHARE of the bend (0.5 = the bisector); the source implicitly
                 * takes the rest by snapping to the same seam. Capped so tan can't blow up at share -> 1. */
                float tilt = Math.min(this.effectiveBend() * this.targetShare, TILT_CAP);
                float tanShear = (float) Math.tan(tilt);

                for (int k = 0; k < this.seam.length; k++)
                {
                    Vector3f target = this.capturedTargetWorld[k];
                    float position = (target.x - center.x) * across.x + (target.y - center.y) * across.y + (target.z - center.z) * across.z;

                    this.seam[k].set(normal).mul(position * tanShear).add(target);
                }
            }

            this.applyTwist();
            this.seamReady = true;
            this.curveReady = this.curve && this.computeCurve();
        }

        /**
         * The curve of the frame. Each end of the rest curve rides its own cube's change since rest (a
         * rigid band end stays put on its cube), and each handle turns and stretches with that cube, so the
         * curve leaves either band end along its bone. Each end's rotation is read off its linear change,
         * whatever is left of it (a scale) is kept apart, so a ring can turn between the two and stretch
         * between the two. False, and the shear stands in, when a cube's pose mirrors it: no rotation
         * carries a mirror.
         */
        private boolean computeCurve()
        {
            this.ringParameter = Float.NaN;

            if (!this.endPose(this.capturedTargetMatrix, this.targetRestInverse, this.targetRotation, this.targetStretch, this.restCurve[0], this.restCurve[1], this.poseCurve[0], this.poseCurve[1]))
            {
                return false;
            }

            if (!this.endPose(this.capturedSourceMatrix, this.sourceRestInverse, this.sourceRotation, this.sourceStretch, this.restCurve[3], this.restCurve[2], this.poseCurve[3], this.poseCurve[2]))
            {
                return false;
            }

            /* Read off a linear change, a rotation need not carry the rest tangent exactly onto the posed one
             * (a scale across the bone tilts it): the turn that does at either end, undone there, so the
             * band ends meet their cubes exactly. Identity whenever the bones only turn and move. */
            this.endFix(0F, this.targetRotation, this.startFix);
            this.endFix(1F, this.sourceRotation, this.endFix);

            return true;
        }

        /** One end of the curve: its rotation and stretch since rest, its point and its handle, posed. */
        private boolean endPose(Matrix4f captured, Matrix4f restInverse, Quaternionf rotation, Matrix3f stretch, Vector3f restEnd, Vector3f restHandle, Vector3f end, Vector3f handle)
        {
            captured.mul(restInverse, this.poseMatrix);
            this.poseMatrix.get3x3(this.linear);

            if (this.linear.determinant() <= EPS_SQ * EPS_SQ)
            {
                return false;
            }

            this.linear.getNormalizedRotation(rotation);
            stretch.set(rotation).transpose().mul(this.linear);

            this.poseMatrix.transformPosition(restEnd, end);
            this.linear.transform(handle.set(restHandle).sub(restEnd)).add(end);

            return true;
        }

        /** The turn undoing, at parameter {@code u}, what the bare rotation leaves between the rest tangent carried and the posed one. */
        private void endFix(float u, Quaternionf rotation, Quaternionf dest)
        {
            bezierTangent(this.restCurve, u, this.restTangent);
            bezierTangent(this.poseCurve, u, this.tangent);
            rotation.transform(this.restTangent);

            if (this.restTangent.lengthSquared() < EPS_SQ || this.tangent.lengthSquared() < EPS_SQ)
            {
                dest.identity();

                return;
            }

            dest.rotationTo(this.restTangent.normalize(), this.tangent.normalize()).conjugate();
        }

        /**
         * Where a band point lands when the band bends along the curve. The point, given in its cube's local
         * space with its distance from the welded plane, stands on a ring across the bone: the target's
         * band runs from the curve's start to the seam, the source's from the seam to the end, each in
         * proportion to its length. The ring sits on the curve at that spot, turned between the two bones
         * (twist and all) and then onto the curve's tangent, stretched between the two, and the point keeps
         * its offset from the rest curve on it. {@code restShift} moves the point at rest first: a source
         * point closes the gap its face had to the target's (see {@link #restGapAt}), faded the way the
         * shear fades it, so at rest the band is what the shear draws there.
         */
        public Vector3f curvePoint(boolean source, Vector3f local, float distance, Vector3f restShift, Vector3f dest)
        {
            float u = source
                ? 1F - (1F - distance / this.sourceBand) * (1F - this.seamParameter)
                : (1F - distance / this.targetBand) * this.seamParameter;

            u = MathUtils.clamp(u, 0F, 1F);

            /* A ring is a function of u alone, and points come in rings (a rail's pair, a face's row). */
            if (u != this.ringParameter)
            {
                this.ring(u);
            }

            Vector3f offset = (source ? this.sourceRest : this.targetRest).transformPosition(local, this.restPoint).add(restShift).sub(this.ringRestCentre);

            this.ring.transform(this.stretch.transform(offset));

            return dest.set(this.ringCentre).add(offset);
        }

        /**
         * The ring at {@code u}: its centre on the rest curve and on the posed one, and its turn — between
         * the bones' rotations, then the end fix, then the swing that takes the rest tangent so turned onto
         * the curve's (at either end the swing is what the fix undoes) — and stretch.
         */
        private void ring(float u)
        {
            this.ringParameter = u;

            bezier(this.restCurve, u, this.ringRestCentre);
            bezier(this.poseCurve, u, this.ringCentre);

            this.targetRotation.slerp(this.sourceRotation, u, this.ring);

            bezierTangent(this.restCurve, u, this.restTangent);
            bezierTangent(this.poseCurve, u, this.tangent);
            this.ring.transform(this.restTangent);
            this.ring.premul(this.startFix.slerp(this.endFix, u, this.turn));

            if (this.restTangent.lengthSquared() > EPS_SQ && this.tangent.lengthSquared() > EPS_SQ)
            {
                this.ring.premul(this.turn.rotationTo(this.restTangent.normalize(), this.tangent.normalize()));
            }

            this.targetStretch.lerp(this.sourceStretch, u, this.stretch);
        }

        /**
         * How far a point of the source's welded face had to go at rest to reach the target's face: the
         * corners' gaps, bilinear over the face. Zero for faces that fit; a skin's jacket layers often sit a
         * hair apart.
         */
        public Vector3f restGapAt(Vector3f local, Vector3f dest)
        {
            float[] st = faceParams(this.sourceCorners, local, this.faceParams);

            return bilerp(this.restGap, IDENTITY_ORDER, st[0], st[1], dest);
        }

        /**
         * Rotate the seam about the target's bone axis by the target's SHARE of the joint's twist. The
         * snapping machinery distributes the rest: the target band follows the seam, and the source face —
         * twisted the full angle in its rigid pose — unwinds the remainder as it snaps to the same seam,
         * so a turning wrist wraps gradually across both bands instead of shearing at one plane.
         */
        private void applyTwist()
        {
            if (!this.twist)
            {
                return;
            }

            float bend = this.bendAngle();

            /* Near a full fold the two bone axes go antiparallel, the swing-removal arc degenerates and the
             * twist reading with it — fade it out before the math gets there. A fully folded joint has no
             * visually readable twist anyway. */
            float fade = MathUtils.clamp((TWIST_FADE_END - bend) / (TWIST_FADE_END - TWIST_FADE_START), 0F, 1F);

            if (fade <= 0F)
            {
                return;
            }

            float angle = wrapAngle(this.twistBetween(this.capturedSourceWorld, this.capturedTargetWorld, this.capturedSourceBoneAxis, this.capturedTargetBoneAxis) - this.restTwist);
            float seamTwist = angle * this.targetShare * fade;

            if (Math.abs(seamTwist) < 1.0e-4F)
            {
                return;
            }

            Vector3f center = average(this.seam);
            Quaternionf rotation = new Quaternionf().rotationAxis(seamTwist, this.capturedTargetBoneAxis);
            Vector3f offset = new Vector3f();

            for (Vector3f corner : this.seam)
            {
                rotation.transform(offset.set(corner).sub(center));
                corner.set(offset).add(center);
            }
        }

        /**
         * Signed twist (radians) of the source face about the joint axis relative to the target face, swing
         * removed: the shortest-arc rotation taking the source bone axis onto the target's rest relation
         * (opposite of the target axis) carries a source corner direction into the target's frame — a
         * shortest arc adds no twist of its own — and the residual in-plane angle to the MATCHED target
         * corner direction is pure twist. Positive about the target bone axis (right-handed).
         */
        private float twistBetween(Vector3f[] sourceWorld, Vector3f[] targetWorld, Vector3f sourceAxis, Vector3f targetAxis)
        {
            Vector3f uT = new Vector3f(targetWorld[this.sourceToTarget[0]]).sub(average(targetWorld));
            Vector3f uS = new Vector3f(sourceWorld[0]).sub(average(sourceWorld));

            new Quaternionf().rotationTo(sourceAxis, new Vector3f(targetAxis).negate()).transform(uS);

            uT.sub(new Vector3f(targetAxis).mul(uT.dot(targetAxis)));
            uS.sub(new Vector3f(targetAxis).mul(uS.dot(targetAxis)));

            if (uT.lengthSquared() < EPS_SQ || uS.lengthSquared() < EPS_SQ)
            {
                return 0F;
            }

            float sin = new Vector3f(uT).cross(uS).dot(targetAxis);
            float cos = uT.dot(uS);

            return (float) Math.atan2(sin, cos);
        }

        private static float wrapAngle(float angle)
        {
            float twoPi = (float) (Math.PI * 2);

            angle %= twoPi;

            if (angle > Math.PI) angle -= twoPi;
            if (angle < -Math.PI) angle += twoPi;

            return angle;
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

        /* Absolute ceiling for the seam's effective bend: past ~172 degrees tan(bend/2) blows the shear out
         * to infinity, so no configured maxAngle may push the asymptote beyond this. */
        private static final float HARD_CAP = (float) Math.toRadians(172);

        /* Ceiling for the TARGET's tilt alone (share can push it past bend/2; tan explodes at 90). At the
         * default share 0.5 this is exactly HARD_CAP/2, so it never clips the classic bisector split. */
        private static final float TILT_CAP = (float) Math.toRadians(86);

        /* The twist reading degenerates as the joint approaches a full fold (bone axes go antiparallel), so
         * twist fades from full effect at 135 degrees of bend to nothing at 165. */
        private static final float TWIST_FADE_START = (float) Math.toRadians(135);
        private static final float TWIST_FADE_END = (float) Math.toRadians(165);

        /**
         * The bend the seam actually follows: exact up to {@link #maxBend}, then easing asymptotically toward
         * a soft cap a bit past it instead of freezing dead — a hard stop reopens the joint the moment the
         * bones bend further, and the discontinuity in the seam's velocity reads as a snap in motion.
         */
        private float effectiveBend()
        {
            float bend = this.bendAngle();
            float max = Math.min(this.maxBend, HARD_CAP);

            if (bend <= max)
            {
                return bend;
            }

            float range = Math.min(max * 0.25F, HARD_CAP - max);

            return range <= 1.0e-3F ? max : max + range * (float) Math.tanh((bend - max) / range);
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

        /**
         * Seam position for a point on the target cube's welded plane — bilinear over the face rect, so
         * inset geometry at the joint rides the seam too, not only the four exact corners.
         */
        public Vector3f seamAtTarget(Vector3f local, Vector3f dest)
        {
            /* The seam is indexed by target corner, so the target face reads it in place. */
            return this.seamAt(this.targetCorners, IDENTITY_ORDER, local, dest);
        }

        public Vector3f seamAtSource(Vector3f local, Vector3f dest)
        {
            return this.seamAt(this.sourceCorners, this.sourceToTarget, local, dest);
        }

        private Vector3f seamAt(Vector3f[] corners, int[] order, Vector3f local, Vector3f dest)
        {
            float[] st = faceParams(corners, local, this.faceParams);

            return bilerp(this.seam, order, st[0], st[1], dest);
        }
    }

    private static final float EPS = 1.0e-4F;

    /* Corners in their own order, for a bilerp over values indexed like the face's corners. */
    private static final int[] IDENTITY_ORDER = {0, 1, 2, 3};

    /**
     * Where a point stands over a face's rect: s along corners 0 to 1, t along 0 to 3, both clamped to the
     * face, so inset geometry at the joint reads the nearest spot of it.
     */
    private static float[] faceParams(Vector3f[] corners, Vector3f local, float[] dest)
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

        /* A face inset to a line has a degenerate edge — park that param mid-seam. */
        float s = l1 > EPS_SQ ? (dx * e1x + dy * e1y + dz * e1z) / l1 : 0.5F;
        float t = l2 > EPS_SQ ? (dx * e2x + dy * e2y + dz * e2z) / l2 : 0.5F;

        dest[0] = Math.max(0F, Math.min(1F, s));
        dest[1] = Math.max(0F, Math.min(1F, t));

        return dest;
    }

    /** Bilinear blend of four points over a face, {@code order} naming which point stands at each face corner. */
    private static Vector3f bilerp(Vector3f[] points, int[] order, float s, float t, Vector3f dest)
    {
        Vector3f s0 = points[order[0]];
        Vector3f s1 = points[order[1]];
        Vector3f s2 = points[order[2]];
        Vector3f s3 = points[order[3]];
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

    /** A point of the cubic Bezier through four control points. */
    private static Vector3f bezier(Vector3f[] p, float u, Vector3f dest)
    {
        float v = 1F - u;
        float a = v * v * v;
        float b = 3F * v * v * u;
        float c = 3F * v * u * u;
        float d = u * u * u;

        return dest.set(
            p[0].x * a + p[1].x * b + p[2].x * c + p[3].x * d,
            p[0].y * a + p[1].y * b + p[2].y * c + p[3].y * d,
            p[0].z * a + p[1].z * b + p[2].z * c + p[3].z * d
        );
    }

    /** The cubic Bezier's derivative at {@code u}: which way, and how fast, it runs there. */
    private static Vector3f bezierTangent(Vector3f[] p, float u, Vector3f dest)
    {
        float v = 1F - u;
        float a = 3F * v * v;
        float b = 6F * v * u;
        float c = 3F * u * u;

        return dest.set(
            (p[1].x - p[0].x) * a + (p[2].x - p[1].x) * b + (p[3].x - p[2].x) * c,
            (p[1].y - p[0].y) * a + (p[2].y - p[1].y) * b + (p[3].y - p[2].y) * c,
            (p[1].z - p[0].z) * a + (p[2].z - p[1].z) * b + (p[3].z - p[2].z) * c
        );
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
