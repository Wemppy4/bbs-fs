package mchorse.bbs_mod.cubic.weld;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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

    /** Why a weld declaration fails to resolve against a model — surfaced by the editor instead of a silent no-op. */
    public enum Issue
    {
        SOURCE_BONE,
        TARGET_BONE,
        SOURCE_FACE,
        TARGET_FACE,

        /** The bone exists, but none of its cubes has a quad on the welded face (mesh-only bone, or a trimmed cube). */
        SOURCE_CUBES,
        TARGET_CUBES
    }

    /**
     * The first reason this weld can't resolve, or null when it can. THE precondition of {@link #resolve}:
     * a weld passing this always yields a binding (both sides have a faced cube, so pairing yields at least
     * one layer), so the two can't drift apart.
     */
    public static Issue diagnose(Model model, ModelWeld weld)
    {
        if (model.getGroup(weld.sourceBone) == null) return Issue.SOURCE_BONE;
        if (model.getGroup(weld.targetBone) == null) return Issue.TARGET_BONE;
        if (CubeFace.fromName(weld.sourceFace) == null) return Issue.SOURCE_FACE;
        if (CubeFace.fromName(weld.targetFace) == null) return Issue.TARGET_FACE;

        if (facedCubes(model.getGroup(weld.sourceBone), CubeFace.fromName(weld.sourceFace)).isEmpty()) return Issue.SOURCE_CUBES;
        if (facedCubes(model.getGroup(weld.targetBone), CubeFace.fromName(weld.targetFace)).isEmpty()) return Issue.TARGET_CUBES;

        return null;
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

        List<ModelCube> sourceCubes = facedCubes(sourceGroup, sourceFace);
        List<ModelCube> targetCubes = facedCubes(targetGroup, targetFace);
        List<Layer> layers = new ArrayList<>();

        /* Rest-pose world matrices of both bones, composed the exact way the renderer stacks them: the
         * corner correspondence between the two faces is decided HERE, in the one pose where the modeler
         * actually aligned them — matching by proximity in whatever animated pose the first frame happens
         * to catch fixes a bent/twisted mapping forever. (The rest twist bias is read off the same pose.) */
        Matrix4f sourceGroupRest = restWorldMatrix(sourceGroup);
        Matrix4f targetGroupRest = restWorldMatrix(targetGroup);

        /* The share knob is authored as the PARENT bone's share, but the seam math is anchored to the
         * target's face — so resolve which side the parent actually is from the model hierarchy and flip
         * the share when the parent turned out to be the source. Source/target roles are whatever order
         * the weld was authored in; without this, the same knob value would mean opposite things on two
         * welds of the same rig. Unrelated bones (no ancestry either way) treat the target as the parent. */
        float targetShare = isAncestor(sourceGroup, targetGroup) ? 1F - weld.parentShare : weld.parentShare;

        for (int[] pair : pairByCrossSection(sourceCubes, sourceFace, targetCubes, targetFace))
        {
            ModelCube sourceCube = sourceCubes.get(pair[0]);
            ModelCube targetCube = targetCubes.get(pair[1]);

            layers.add(new Layer(
                sourceCube, sourceFace, sourceGroupRest, restCubeMatrix(sourceGroupRest, sourceCube),
                targetCube, targetFace, targetGroupRest, restCubeMatrix(targetGroupRest, targetCube),
                weld, targetShare
            ));
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
            float score = 0F;

            for (int r = 0; r < 4; r++)
            {
                score += sourceRest[r].distanceSquared(targetRest[permutation[r]]);
            }

            if (score < bestScore)
            {
                bestScore = score;
                best = permutation;
            }
        }

        return best.clone();
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

        /* The target bone's EFFECTIVE share (0..1) of the joint's deformation — the authored parent share,
         * already flipped by resolve() if the parent turned out to be the source. The source takes the rest. */
        public final float targetShare;

        /* Whether the seam also distributes twist (rotation about the bone axis) across the band. */
        public final boolean twist;

        /* Twist already present between the two faces in the rest pose (rotated cubes), subtracted from the
         * live measurement so only ANIMATED twist deforms the band. */
        private final float restTwist;

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

        /* The seam leaves both welded faces exactly where the rigid pose already puts them (no bend AND no
         * rest gap between the faces), so snapping to it is a no-op and the group may ride its baked VAO. */
        public boolean identity;

        private Layer(ModelCube sourceCube, CubeFace sourceFace, Matrix4f sourceGroupRest, Matrix4f sourceCubeRest, ModelCube targetCube, CubeFace targetFace, Matrix4f targetGroupRest, Matrix4f targetCubeRest, ModelWeld weld, float targetShare)
        {
            this.sourceCube = sourceCube;
            this.targetCube = targetCube;
            this.sourceCorners = faceCorners(sourceCube, sourceFace);
            this.targetCorners = faceCorners(targetCube, targetFace);

            Vector3f[] sourceRestWorld = transformCorners(sourceCubeRest, this.sourceCorners);
            Vector3f[] targetRestWorld = transformCorners(targetCubeRest, this.targetCorners);

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

            Vector3f restSourceAxis = sourceGroupRest.transformDirection(new Vector3f(this.sourceFaceNormal)).normalize();
            Vector3f restTargetAxis = targetGroupRest.transformDirection(new Vector3f(this.targetFaceNormal)).normalize();

            this.restTwist = this.twistBetween(sourceRestWorld, targetRestWorld, restSourceAxis, restTargetAxis);
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

            /* Ride the VAO only when the seam moves nothing — no bend AND the faces already meet at rest. A
             * rest-pose gap makes this false, so the weld seals it now instead of snapping it shut on first bend. */
            this.identity = this.seamMatchesRigid();
            this.seamReady = true;
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

        /* The seam is indexed by target corner, so the target face reads it in place. */
        private static final int[] TARGET_ORDER = {0, 1, 2, 3};

        /**
         * Seam position for a point on the target cube's welded plane — bilinear over the face rect, so
         * inset geometry at the joint rides the seam too, not only the four exact corners.
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

            /* A face inset to a line has a degenerate edge — park that param mid-seam. */
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
