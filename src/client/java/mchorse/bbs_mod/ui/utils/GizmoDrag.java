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
 * Snapshot of camera, viewport and gizmo placement captured at the start of a drag;
 * turns cursor motion into a world-space delta via ray/plane intersections.
 * All the math lives in ONE frame — the one the supplied camera observes — so the
 * gizmo origin must be expressed in it too (world for the film, model space for the
 * form editor).
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

    /** Linear map from a unit change of {@code transform.translate} to the resulting
     *  world displacement of the gizmo origin. Identity where one local unit is one
     *  world unit; for cubic groups (pixels, 1/16 block) its columns come out scaled by
     *  1/16, so the drag math compensates without knowing the model type. */
    public final Matrix3f translateJacobian = new Matrix3f();

    /** Unit world directions of the gizmo's X/Y/Z handles AS RENDERED, from
     *  {@link Gizmo#computeWorldAxes}; identity without a rendered gizmo. */
    public final Matrix3f gizmoWorldAxes = new Matrix3f();

    /** World axes the renderer actually turns about when {@code transform.rotate} is
     *  mutated. Matches {@link #gizmoWorldAxes} for BOBJ; for cubic it can differ in
     *  SIGN — the renderer post-multiplies {@code Ry(180°)}, flipping bone-local X/Z.
     *  Filled via {@link #computeRotateAxes}. */
    public final Matrix3f rotateAxes = new Matrix3f();

    /** The basis {@link TransformSpace#GLOBAL} aligns to: plain world axes unless a
     *  host sets it. The film fills it with the replay's own facing, so "global" there
     *  is the actor's world, not the map's. Drawn by the twin {@link #stackBasisForSpace}. */
    public final Matrix3f globalWorldAxes = new Matrix3f();

    /**
     * World-space basis of the bone's OWN frame ({@link TransformSpace#LOCAL}), carried
     * explicitly: LOCAL and {@link TransformSpace#PARENT} both used to read
     * {@link #gizmoWorldAxes} &mdash; the axes of the DRAWN handles &mdash; so a
     * snapshot could only answer for the frame the gesture started in, and an edit
     * walked into the other frame kept running on the old one. Filled through
     * {@link #setFrameAxes}; not every host does, hence {@link #hasFrameAxes}.
     */
    public final Matrix3f localWorldAxes = new Matrix3f();

    /** World-space basis of the frame the bone's channels compose in — its parent's.
     *  The twin of {@link #localWorldAxes}. */
    public final Matrix3f parentWorldAxes = new Matrix3f();

    /** Whether {@link #localWorldAxes}/{@link #parentWorldAxes} were filled in. */
    private boolean hasFrameAxes;

    /** Euler (ZYX radians) the renderer SUMS UNDER the edited rotate channels — non-zero
     *  for an additive layer like a pose overlay, where it shows {@code ZYX(base + rotate)}.
     *  The drag must then work at the effective angles and subtract the base back out
     *  ({@code RotationDragMath}); zero base collapses to the classic math. Quaternions
     *  never need it. */
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
        /* The SCENE camera, deliberately — never GizmoLens, though the handles are drawn
         * through it: the lens's narrow frustum is also a zoom, so solving a drag through
         * it reads a cursor move as a much smaller world step and the model crawls. Only
         * the pick stencil goes through the lens ("which handle is under the cursor" is a
         * question about the picture; "where does it end up" is about the world). */
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
     * Project a world point to viewport pixels, in {@link #rayDirection}'s convention
     * (top-left origin, Y down). {@link #view} is rotation-only, so the point is taken
     * relative to {@link #cameraOrigin} first.
     *
     * @return {@code false} when the point is on or behind the camera plane.
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
     * The world basis a {@link TransformSpace} aligns the gizmo GEOMETRY to: the frame
     * its handles are drawn and picked in, and the directions a constrained translate
     * slides or a scale levers along.
     *
     * <p>Rotation rings in EVERY frame both DRAW and TURN about these axes (the delta is
     * mapped into the bone's parent frame by {@code RotationDragMath#parentInverse}), so
     * the bone follows the ring the user grabbed. The MEASURED {@link #rotateAxes} is
     * deliberately NOT a gesture basis: it carries the euler stack's gimbal skew, so a
     * LOCAL ring driven by it drifted off the visual as inner channels tilted. It stays
     * the ground truth for recovering the parent frame.
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
            case LOCAL:
                /* Each frame carried in its own right (see localWorldAxes), so a gesture
                 * moved between them mid-edit gets the axes it asked for. */
                return new Matrix3f(this.hasFrameAxes ? this.localWorldAxes : this.gizmoWorldAxes);
            default:
                /* PARENT; hosts that fill no pair fall back to the drawn axes. */
                return new Matrix3f(this.hasFrameAxes ? this.parentWorldAxes : this.gizmoWorldAxes);
        }
    }

    /**
     * The camera's right/up/forward as an orthonormal basis — the single source of the
     * screen frame, used by VIEW and by the screen-relative gestures (screen translate,
     * trackball/arcball) instead of each re-inverting the view matrix. The SCENE
     * camera's, not the lens's (see {@link #setup}). {@code null} on a degenerate view;
     * those gestures then don't start.
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
     * The drawn twin of {@link #frameBasis}: the 3&times;3 the gizmo's view-space
     * drawing frame gets ({@link Gizmo#reorientForSpace}). The stack already carries
     * world&rarr;view, so this is {@code view · frameBasis(space)} — GLOBAL becomes
     * {@code view · globalAxes}, WORLD the view rotation, VIEW the identity. LOCAL and
     * PARENT never reach here: the reorient keeps their placement frame.
     *
     * <p>🔴 {@code globalAxes} must come from the same source the drag's does, or the
     * handles are drawn off the frame they slide in ({@code null} = plain world axes).
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

    /**
     * Both bone frames at once, each as the matrix the gizmo WOULD be placed on in that
     * frame (the host's origin matrix with and without the bone's own rotation).
     * 🔴 Pass them in the space the gizmo's origin is read back in — lift host-frame
     * matrices through the renderer first, exactly as the rotate axes are.
     *
     * <p>A {@code null} in either leaves BOTH unset: half a pair would answer for one
     * frame and quietly lie about the other.
     */
    public GizmoDrag setFrameAxes(Matrix4f local, Matrix4f parent)
    {
        if (local == null || parent == null)
        {
            return this;
        }

        this.localWorldAxes.set(basisOf(local));
        this.parentWorldAxes.set(basisOf(parent));
        this.hasFrameAxes = true;

        return this;
    }

    /** Whether {@link #setFrameAxes} was given a pair of frames. */
    public boolean hasFrameAxes()
    {
        return this.hasFrameAxes;
    }

    /** A matrix's rotation as an orthonormal basis, so residual scale cannot leak into
     *  an axis direction. A degenerate column falls back to the identity's. */
    private static Matrix3f basisOf(Matrix4f matrix)
    {
        Matrix3f basis = matrix.get3x3(new Matrix3f());

        for (int i = 0; i < 3; i++)
        {
            Vector3f column = basis.getColumn(i, new Vector3f());

            if (column.lengthSquared() < 1.0E-8F)
            {
                column.set(i == 0 ? 1F : 0F, i == 1 ? 1F : 0F, i == 2 ? 1F : 0F);
            }
            else
            {
                column.normalize();
            }

            basis.setColumn(i, column);
        }

        return basis;
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
     * Numerically estimate how the gizmo's world position responds to
     * {@code transform.translate}: four samples (origin plus each unit axis), the
     * differences become the Jacobian's columns, carrying both the orientation and the
     * scale of the local-to-world mapping. Restores the original value before returning.
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
     * Numerically estimate the world axis each component of {@code transform.rotate}
     * actually turns the bone about: perturb it slightly ON TOP OF the current rotation
     * (never from zero — the renderer composes eulers, so a bumped X turns about
     * {@code Rz·Ry·(1,0,0)}) and read the axis off the antisymmetric part of
     * {@code R_perturbed · R_current⁻¹}. This is also what recovers the cubic
     * {@code Ry(180°)} flip, which points the drawn X/Z arrows opposite the real axes.
     *
     * <p>🔴 {@code matrixSampler} must return the ROTATION-BEARING matrix: hand it an
     * origin (rotation-stripped) one and the perturbation leaves no trace, so the axes
     * silently collapse to identity. Columns are the unit axes of x/y/z; the original
     * values are restored before returning.
     */
    public static Matrix3f computeRotateAxes(Transform transform, Supplier<Matrix4f> matrixSampler)
    {
        boolean quat = transform.rotationMode == Transform.RotationMode.QUATERNION;
        Vector3f savedRotate = new Vector3f(transform.rotate);
        Quaternionf savedQuat = new Quaternionf(transform.quat);

        /* In quaternion mode the euler channels don't drive the render, so perturbing
         * them collapses the axes to identity. Perturb the QUATERNION with the bumped
         * equivalent of its own ZYX angles instead, matching the euler path sign for sign. */
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

                /* The antisymmetric part is sin(θ)·[axis]_skew (JOML column-major);
                 * normalising drops sin(θ) and leaves the unit world axis. */
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
