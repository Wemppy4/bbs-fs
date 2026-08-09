package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

/**
 * Snapshot of camera, viewport and gizmo placement captured at the start
 * of a drag. Used by the gizmo to translate raw cursor motion into a
 * proper world-space delta via ray/plane intersections.
 *
 * The whole math lives in a single frame: the one in which the supplied
 * camera observes the scene. The gizmo origin must be expressed in the
 * same frame (i.e. for film/world rendering it is a world position, for
 * the form editor it is a model-space position).
 */
public class GizmoDrag
{
    private static final float PARALLEL_EPSILON = 1.0E-4F;

    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f view = new Matrix4f();
    public final Vector3d cameraOrigin = new Vector3d();

    public int viewportX;
    public int viewportY;
    public int viewportW;
    public int viewportH;

    public final Vector3d gizmoOrigin = new Vector3d();

    /**
     * Linear map from a unit change of {@code transform.translate} (the value
     * the user is editing) to the resulting world-space displacement of the
     * gizmo origin. Defaults to identity, which is correct for editors where
     * one local unit equals one world unit (e.g. BOBJ bones, root transforms).
     *
     * For cubic groups the model space is in pixels (1/16 block), so the
     * Jacobian's columns end up scaled by 1/16 and the drag math automatically
     * compensates for that without callers having to know the model type.
     */
    public final Matrix3f translateJacobian = new Matrix3f();

    /**
     * Unit-length world-space directions of the gizmo's X/Y/Z handles, as
     * actually rendered. Populated from {@link Gizmo#computeWorldAxes} when
     * the drag is created via {@link #fromRenderedGizmo}; defaults to identity
     * otherwise so callers without a rendered gizmo still get sensible
     * world-aligned axes.
     */
    public final Matrix3f gizmoWorldAxes = new Matrix3f();

    /**
     * World-space rotation axes used by the renderer when {@code transform.rotate}
     * components are mutated. For BOBJ models these match {@link #gizmoWorldAxes};
     * for cubic models they can differ by a sign because the renderer applies a
     * post-multiplied {@code Ry(180°)} that flips bone-local X and Z while leaving
     * Y unchanged. Editors fill this via {@link #computeRotateAxes} so the gizmo
     * doesn't have to know which model type it's editing.
     */
    public final Matrix3f rotateAxes = new Matrix3f();

    /**
     * World-space orthonormal basis {@link TransformSpace#GLOBAL} aligns to.
     * Identity &mdash; the plain world axes &mdash; unless a host sets it, which
     * is what the editors without a scene of their own (form editor, model
     * blocks, animation states) leave it at. The film viewport instead fills it
     * with the edited replay's own facing
     * ({@code BaseFilmController.getReplayWorldAxes}), so "global" there means
     * the actor's world rather than the map's: it stays flat and axis-aligned,
     * it just turns with the replay. Read through {@link #frameBasis}, and drawn
     * by the twin {@link #stackBasisForSpace}.
     */
    public final Matrix3f globalWorldAxes = new Matrix3f();

    /**
     * Euler rotation (ZYX radians) the renderer SUMS UNDER the edited transform's
     * rotate channels — non-zero when the edited value is an additive layer, like
     * a pose overlay stacked per-channel onto the base pose. The renderer then
     * shows {@code ZYX(base + rotate)}, so the drag's euler frame recovery and
     * write composition must run on the effective angles and subtract the base
     * back out of the written channels ({@code RotationDragMath}); with a zero
     * base (a plain transform) both collapse to the classic math. Quaternion
     * transforms never need it — their layers compose multiplicatively, which
     * the parent-frame recovery already absorbs.
     */
    public final Vector3f additiveRotationBase = new Vector3f();

    public GizmoDrag setup(Camera camera, Area viewport, Vector3f gizmoOrigin)
    {
        return this.setup(camera, viewport, gizmoOrigin.x, gizmoOrigin.y, gizmoOrigin.z);
    }

    public GizmoDrag setup(Camera camera, Area viewport, Vector3d gizmoOrigin)
    {
        return this.setup(camera, viewport, gizmoOrigin.x, gizmoOrigin.y, gizmoOrigin.z);
    }

    /**
     * Anchor the drag at the world origin recovered from the gizmo's last
     * render matrix. Falls back to {@code null} if the gizmo hasn't been
     * rendered yet, in which case the caller should skip ray-based dragging.
     */
    public static GizmoDrag fromRenderedGizmo(Camera camera, Area viewport)
    {
        Vector3d origin = new Vector3d();

        if (!Gizmo.INSTANCE.computeWorldOrigin(camera, origin))
        {
            return null;
        }

        GizmoDrag drag = new GizmoDrag().setup(camera, viewport, origin);

        Gizmo.INSTANCE.computeWorldAxes(camera, drag.gizmoWorldAxes);
        /* Sensible default: the visible gizmo arrows. Editors that know the
         * renderer's actual rotation axes (e.g. cubic models) can override via
         * setRotateAxes() to fix sign mismatches caused by post-applied flips. */
        drag.rotateAxes.set(drag.gizmoWorldAxes);

        return drag;
    }

    public GizmoDrag setup(Camera camera, Area viewport, double gx, double gy, double gz)
    {
        this.projection.set(camera.projection);
        this.view.set(camera.view);
        this.cameraOrigin.set(camera.position);

        this.viewportX = viewport.x;
        this.viewportY = viewport.y;
        this.viewportW = viewport.w;
        this.viewportH = viewport.h;

        this.gizmoOrigin.set(gx, gy, gz);

        return this;
    }

    public Vector3f rayDirection(int mouseX, int mouseY, Vector3f out)
    {
        Vector3f dir = CameraUtils.getMouseDirection(this.projection, this.view, mouseX, mouseY, this.viewportX, this.viewportY, this.viewportW, this.viewportH);

        return out.set(dir).normalize();
    }

    public boolean projectToScreen(Vector3d world, Vector2f out)
    {
        return this.projectToScreen(world.x, world.y, world.z, out);
    }

    /**
     * Project a world-space point onto viewport pixel coordinates, matching the
     * mouse-coordinate convention used by {@link #rayDirection} (origin at the
     * viewport's top-left corner, Y growing downward).
     *
     * <p>The {@link #view} matrix is rotation-only &mdash; the camera translation
     * lives in {@link #cameraOrigin} &mdash; so the point is expressed relative to
     * the camera before being run through {@code projection * view}, mirroring the
     * inverse mapping in {@link CameraUtils#getMouseDirection}.</p>
     *
     * @return {@code false} when the point is on or behind the camera plane, in
     *         which case {@code out} is left untouched.
     */
    public boolean projectToScreen(double wx, double wy, double wz, Vector2f out)
    {
        Vector4f clip = new Vector4f(
            (float) (wx - this.cameraOrigin.x),
            (float) (wy - this.cameraOrigin.y),
            (float) (wz - this.cameraOrigin.z),
            1F
        );

        new Matrix4f(this.projection).mul(this.view).transform(clip);

        if (clip.w <= PARALLEL_EPSILON)
        {
            return false;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        out.x = this.viewportX + (ndcX + 1F) * (this.viewportW / 2F);
        out.y = this.viewportY + (1F - ndcY) * (this.viewportH / 2F);

        return true;
    }

    /**
     * Intersect the ray cast through the given screen position with a plane
     * passing through {@link #gizmoOrigin} and oriented along {@code planeNormal}.
     */
    public boolean intersectPlane(int mouseX, int mouseY, Vector3f planeNormal, Vector3d out)
    {
        /* Projection-agnostic ray: under ortho the direction is constant and
         * the per-pixel shift lives in the origin offset instead. */
        Vector3f originOffset = new Vector3f();
        Vector3f dir = CameraUtils.getMouseRay(this.projection, this.view, mouseX, mouseY, this.viewportX, this.viewportY, this.viewportW, this.viewportH, originOffset);
        double denom = dir.x * planeNormal.x + dir.y * planeNormal.y + dir.z * planeNormal.z;

        if (Math.abs(denom) < PARALLEL_EPSILON)
        {
            return false;
        }

        double originX = this.cameraOrigin.x + originOffset.x;
        double originY = this.cameraOrigin.y + originOffset.y;
        double originZ = this.cameraOrigin.z + originOffset.z;

        double t = ((this.gizmoOrigin.x - originX) * planeNormal.x
            + (this.gizmoOrigin.y - originY) * planeNormal.y
            + (this.gizmoOrigin.z - originZ) * planeNormal.z) / denom;

        if (t <= 0D)
        {
            return false;
        }

        out.set(originX + dir.x * t, originY + dir.y * t, originZ + dir.z * t);

        return true;
    }

    /**
     * Pick the plane normal best suited for dragging along a single axis:
     * perpendicular to the axis itself and as parallel as possible to the
     * camera ray, with a fallback when the axis is nearly aligned with the view.
     */
    public Vector3f planeNormalForAxis(int mouseX, int mouseY, Matrix3f basis, Axis axis, Vector3f out)
    {
        Vector3f axisDir = basis.getColumn(axis.ordinal(), new Vector3f());
        Vector3f viewDir = this.rayDirection(mouseX, mouseY, new Vector3f());
        Vector3f temp = new Vector3f();

        axisDir.cross(viewDir, temp);
        temp.cross(axisDir, out);

        if (out.lengthSquared() < PARALLEL_EPSILON)
        {
            Vector3f fallback = Math.abs(axisDir.y) < 0.9F ? new Vector3f(0F, 1F, 0F) : new Vector3f(1F, 0F, 0F);

            axisDir.cross(fallback, temp);
            temp.cross(axisDir, out);
        }

        return out.normalize();
    }

    /**
     * Plane normal for a two-axis (planar) handle drag.
     */
    public Vector3f planeNormalForPlane(Matrix3f basis, Axis axisA, Axis axisB, Vector3f out)
    {
        Vector3f a = basis.getColumn(axisA.ordinal(), new Vector3f());
        Vector3f b = basis.getColumn(axisB.ordinal(), new Vector3f());

        return a.cross(b, out).normalize();
    }

    /**
     * The world-space orthonormal basis a {@link TransformSpace} aligns the gizmo
     * GEOMETRY to: the frame its handles are drawn and picked in, and the world
     * directions a constrained translate slides along or a scale levers along.
     * {@link TransformSpace#LOCAL} is the bone's rendered frame ({@link #gizmoWorldAxes},
     * the visible arrows); {@link TransformSpace#GLOBAL} is {@link #globalWorldAxes}
     * (the world axes, turned by the replay's facing in the film viewport);
     * {@link TransformSpace#WORLD} is the world identity, container and all
     * ignored;
     * {@link TransformSpace#VIEW} is the camera's right/up/forward
     * ({@link #cameraBasis}, world axes when the view is degenerate);
     * {@link TransformSpace#PARENT} is {@link #gizmoWorldAxes} as well &mdash;
     * in that space the gizmo is PLACED on the cache's origin-flavour matrix
     * (the bone's frame before its own rotation, i.e. the parent frame), so the
     * drawn arrows already are the parent axes.
     *
     * <p>Rotation rings in LOCAL/GLOBAL/VIEW both DRAW and TURN about these
     * axes: a ring gesture composes a delta rotation about the drawn axis
     * (mapped into the bone's parent frame via
     * {@link mchorse.bbs_mod.ui.framework.elements.input.drag.RotationDragMath#parentInverse}),
     * so the bone always follows the ring the user grabbed. PARENT rings
     * instead bump the driven channel directly — the deliberate pre-spaces
     * behaviour (see {@link TransformSpace#PARENT}). The MEASURED
     * {@link #rotateAxes} (the renderer's response to the euler channels, which
     * folds in the cubic {@code Ry(180°)} post-flip AND the euler stack's gimbal
     * skew) is deliberately NOT a gesture basis anymore &mdash; a LOCAL ring
     * driven by it turned about the channel's intermediate gimbal axis, not the
     * bone's own drawn axis, drifting off the visual as the inner channels tilt.
     * It remains the ground truth for recovering the parent frame.
     */
    public Matrix3f frameBasis(TransformSpace space)
    {
        switch (space)
        {
            case GLOBAL:
                return new Matrix3f(this.globalWorldAxes);
            case WORLD:
                /* Deliberately NOT globalWorldAxes: this frame's whole point is
                 * to ignore whatever container the edited thing sits in. */
                return new Matrix3f();
            case VIEW:
                Matrix3f camera = this.cameraBasis();

                /* Constrained drags need axes whatever happens, so a degenerate
                 * view falls back to the world frame. */
                return camera == null ? new Matrix3f() : camera;
            default:
                /* LOCAL and PARENT: the frame the gizmo was drawn in IS the
                 * space frame — the placement matrix carries the bone's own
                 * frame in LOCAL and the origin/parent frame in PARENT. */
                return new Matrix3f(this.gizmoWorldAxes);
        }
    }

    /**
     * The camera's world-space right/up/forward as the columns of an orthonormal
     * basis &mdash; the single source of the screen frame. {@link #view} is the
     * rotation-only world&rarr;camera map, so its inverse takes the camera's own
     * axes back into world space. This is {@link #frameBasis}'s VIEW frame, and
     * the inherently screen-relative gestures (the screen translate, the sphere's
     * trackball/arcball tumble) read their right/up axes from here instead of
     * re-inverting the view matrix themselves. Returns {@code null} when the view
     * is degenerate; those gestures then don't start.
     */
    public Matrix3f cameraBasis()
    {
        Matrix3f viewAxes = this.view.get3x3(new Matrix3f());

        if (Math.abs(viewAxes.determinant()) < PARALLEL_EPSILON)
        {
            return null;
        }

        return viewAxes.invert();
    }

    /**
     * The 3&times;3 the gizmo's view-space drawing frame gets for a
     * {@link TransformSpace} ({@link Gizmo#reorientForSpace}). The drawing stack
     * already carries world&rarr;view, so this is {@code view · frameBasis(space)}
     * spelled out: {@link TransformSpace#GLOBAL} is {@code view · globalAxes},
     * {@link TransformSpace#WORLD} the view rotation itself
     * ({@code view · identity}) and {@link TransformSpace#VIEW} the identity
     * ({@code view · view⁻¹}) &mdash; whose third column the draw passes then lay
     * on the eye ray so the handles face the screen instead of merely paralleling
     * it ({@link Gizmo#applyViewShear}); the frame returned here, and everything
     * the drags read from it, stays orthonormal.
     * {@code globalAxes} is the drawn twin of {@link #globalWorldAxes} and must
     * come from the same source the drag's does &mdash; {@code null} means the
     * plain world axes. {@link TransformSpace#LOCAL} and
     * {@link TransformSpace#PARENT} never reach this &mdash; the reorient keeps
     * the placement frame for them (bone frame / origin-flavour parent frame).
     * Keeping it here ties the drawn frame to the same space&rarr;basis mapping
     * the drags read.
     */
    public static Matrix3f stackBasisForSpace(TransformSpace space, Matrix4f view, Matrix3f globalAxes)
    {
        if (space == TransformSpace.WORLD)
        {
            return view.get3x3(new Matrix3f());
        }

        if (space != TransformSpace.GLOBAL)
        {
            return new Matrix3f();
        }

        Matrix3f basis = view.get3x3(new Matrix3f());

        return globalAxes == null ? basis : basis.mul(globalAxes);
    }

    /** See {@link #globalWorldAxes}; {@code null} restores the plain world axes. */
    public GizmoDrag setGlobalAxes(Matrix3f axes)
    {
        if (axes == null)
        {
            this.globalWorldAxes.identity();
        }
        else
        {
            this.globalWorldAxes.set(axes);
        }

        return this;
    }

    public GizmoDrag setJacobian(Matrix3f jacobian)
    {
        this.translateJacobian.set(jacobian);

        return this;
    }

    public GizmoDrag setRotateAxes(Matrix3f axes)
    {
        this.rotateAxes.set(axes);

        return this;
    }

    /** See {@link #additiveRotationBase}; {@code null} clears it to zero. */
    public GizmoDrag setAdditiveRotationBase(Vector3f base)
    {
        if (base == null)
        {
            this.additiveRotationBase.set(0F, 0F, 0F);
        }
        else
        {
            this.additiveRotationBase.set(base);
        }

        return this;
    }

    /**
     * Numerically estimate how the gizmo's world position responds to changes
     * of {@code transform.translate}. Calls the sampler four times: at the
     * origin and at each unit basis vector. The differences become the columns
     * of the Jacobian, which encodes both the orientation and the scale of the
     * local-to-world mapping (including effects like the cubic /16 conversion).
     *
     * Restores the original translate value before returning so the caller is
     * free to keep using the {@code Transform} as is.
     */
    public static Matrix3f computeTranslateJacobian(Transform transform, Supplier<Vector3f> worldPositionSampler)
    {
        Vector3f saved = new Vector3f(transform.translate);

        try
        {
            transform.translate.set(0F, 0F, 0F);
            Vector3f origin = new Vector3f(worldPositionSampler.get());

            transform.translate.set(1F, 0F, 0F);
            Vector3f cx = new Vector3f(worldPositionSampler.get()).sub(origin);

            transform.translate.set(0F, 1F, 0F);
            Vector3f cy = new Vector3f(worldPositionSampler.get()).sub(origin);

            transform.translate.set(0F, 0F, 1F);
            Vector3f cz = new Vector3f(worldPositionSampler.get()).sub(origin);

            return new Matrix3f(
                cx.x, cx.y, cx.z,
                cy.x, cy.y, cy.z,
                cz.x, cz.y, cz.z
            );
        }
        finally
        {
            transform.translate.set(saved);
        }
    }

    /**
     * Numerically estimate the world-space axis around which each component of
     * {@code transform.rotate} actually rotates the bone. Perturbs each axis
     * by a small angle on top of the current {@code rotate} (NOT from zero) and
     * extracts the relative rotation from the antisymmetric part of
     * {@code R_perturbed · R_current⁻¹}.
     *
     * <p>Sampling around the current pose &mdash; rather than at identity &mdash;
     * matters because the renderer composes Euler angles. Perturbing
     * {@code rotate.x} on top of a non-trivial {@code (ry, rz)} rotates around
     * {@code parent · Rz(rz) · Ry(ry) · (1,0,0)}, not just {@code parent · (1,0,0)}.
     * Bones in a rest pose often have non-zero rotation, so a fixed-at-zero
     * sample would feed the gizmo a wrong axis and the user's drag would map
     * to the wrong direction.</p>
     *
     * <p>It also handles the cubic-model case: those models post-multiply by
     * {@code Ry(180°)} after the bone's own rotation, which flips bone-local X
     * and Z in world space (Y is preserved). The visible gizmo arrows for X
     * and Z therefore point opposite to the actual rotation axes; this method
     * recovers the correct axes the renderer rotates around.</p>
     *
     * <p>The {@code matrixSampler} must return a matrix whose linear part
     * reflects the bone's current rotation &mdash; for editors that distinguish
     * between &quot;origin&quot; (rotation-stripped) and &quot;matrix&quot;
     * (full) variants, always pass the latter. Returned columns correspond to
     * the rotation axes for {@code rotate.x}, {@code rotate.y} and
     * {@code rotate.z}, unit-length. Original {@code rotate} values are
     * restored before returning.</p>
     */
    public static Matrix3f computeRotateAxes(Transform transform, Supplier<Matrix4f> matrixSampler)
    {
        boolean quat = transform.rotationMode == Transform.RotationMode.QUATERNION;
        Vector3f savedRotate = new Vector3f(transform.rotate);
        Quaternionf savedQuat = new Quaternionf(transform.quat);

        /* In quaternion mode the euler channels don't drive the render, so
         * perturbing transform.rotate leaves no trace and the axes collapse to
         * identity — losing the model's flips (e.g. the cubic Ry(180)). Instead
         * perturb the QUATERNION with the euler-bumped equivalent of its own ZYX
         * angles, which reproduces the euler perturbation exactly, so the axes
         * (and their signs) match the euler path. */
        Vector3f source = quat ? Matrices.toEulerZYXRadians(transform.quat, new Vector3f()) : savedRotate;
        float delta = 0.05F;

        try
        {
            Matrix3f base = new Matrix3f();

            matrixSampler.get().get3x3(base);

            Matrix3f baseInverse = new Matrix3f(base);

            if (Math.abs(baseInverse.determinant()) < 1.0E-8F)
            {
                return new Matrix3f();
            }

            baseInverse.invert();

            Matrix3f axes = new Matrix3f();
            Vector3f col = new Vector3f();
            Matrix3f perturbed = new Matrix3f();
            Matrix3f relative = new Matrix3f();

            for (int i = 0; i < 3; i++)
            {
                Vector3f bumped = new Vector3f(source);

                if (i == 0) bumped.x += delta;
                else if (i == 1) bumped.y += delta;
                else bumped.z += delta;

                if (quat) transform.quat.set(new Quaternionf().rotationZYX(bumped.z, bumped.y, bumped.x));
                else transform.rotate.set(bumped);

                matrixSampler.get().get3x3(perturbed);
                relative.set(perturbed).mul(baseInverse);

                /* Antisymmetric part of a rotation matrix is sin(θ)·[axis]_skew.
                 * In JOML's column-major layout that translates to the formula
                 * below; normalize to drop the sin(θ) magnitude and we get the
                 * unit world-space axis around which the renderer rotates. */
                col.set(
                    relative.m12 - relative.m21,
                    relative.m20 - relative.m02,
                    relative.m01 - relative.m10
                );

                float lenSq = col.lengthSquared();

                if (lenSq < 1.0E-12F)
                {
                    col.set(i == 0 ? 1F : 0F, i == 1 ? 1F : 0F, i == 2 ? 1F : 0F);
                }
                else
                {
                    col.div((float) Math.sqrt(lenSq));
                }

                axes.setColumn(i, col);
            }

            return axes;
        }
        finally
        {
            transform.rotate.set(savedRotate);
            transform.quat.set(savedQuat);
        }
    }
}
