package mchorse.bbs_mod.cubic.weld;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
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
 * different size, so they get their OWN seam: the cubes of the two bones are paired by inflate (outermost to
 * outermost) and every pair seals independently. A single shared seam would drag the base layer out to the
 * jacket's size and puff the joint.
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
        int count = Math.min(sourceCubes.size(), targetCubes.size());
        float maxBend = (float) Math.toRadians(weld.maxAngle);
        List<Layer> layers = new ArrayList<>();

        for (int i = 0; i < count; i++)
        {
            layers.add(new Layer(sourceCubes.get(i), sourceFace, targetCubes.get(i), targetFace, maxBend));
        }

        return layers.isEmpty() ? null : new WeldBinding(sourceGroup, targetGroup, layers);
    }

    /** The group's cubes that carry the welded face, outermost (most inflated) first, so layers pair up. */
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

        cubes.sort((a, b) -> Float.compare(b.inflate, a.inflate));

        return cubes;
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

        /* Local outward normal of the target face — the axis the seam slides along (the parent bone's length). */
        public final Vector3f targetFaceNormal;

        /* Largest bend (radians) the seam follows; beyond it the shear holds steady. */
        public final float maxBend;

        /* Rigid world poses of the two faces, captured each frame before any snapping. */
        public final Vector3f[] capturedSourceWorld = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        public final Vector3f[] capturedTargetWorld = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        public final Quaternionf capturedTargetRot = new Quaternionf();
        public final Quaternionf capturedSourceRot = new Quaternionf();

        /* Source corner -> target corner, matched by proximity once both faces are captured. */
        public final int[] sourceToTarget = {-1, -1, -1, -1};

        /* The shared seam, indexed by target corner. */
        public final Vector3f[] seam = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

        public boolean sourceCaptured;
        public boolean targetCaptured;
        public boolean seamReady;

        private Layer(ModelCube sourceCube, CubeFace sourceFace, ModelCube targetCube, CubeFace targetFace, float maxBend)
        {
            this.sourceCube = sourceCube;
            this.targetCube = targetCube;
            this.sourceCorners = faceCorners(sourceCube, sourceFace);
            this.targetCorners = faceCorners(targetCube, targetFace);
            this.targetFaceNormal = new Vector3f(targetFace.normal);
            this.maxBend = maxBend;
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

            Vector3f normal = new Vector3f(this.targetFaceNormal);
            this.capturedTargetRot.transform(normal).normalize();

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

        private float bendAngle()
        {
            Quaternionf relative = new Quaternionf(this.capturedTargetRot).conjugate().mul(this.capturedSourceRot);
            float angle = relative.angle();

            return angle > (float) Math.PI ? (float) (2.0 * Math.PI) - angle : angle;
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
