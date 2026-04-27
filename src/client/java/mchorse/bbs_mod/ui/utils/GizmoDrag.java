package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

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

    /**
     * Project the cursor onto a virtual arcball centered on the gizmo.
     * Inside the visible disc we use the front hemisphere; outside it we
     * continue on the classic hyperbolic sheet so dragging stays smooth and
     * keeps rotating even after the cursor leaves the sphere's outline.
     *
     * Returns the unit vector from {@link #gizmoOrigin} to that virtual
     * trackball point in world space.
     */
    public Vector3f projectTrackball(int mouseX, int mouseY, float radius, Vector3f out)
    {
        Vector3f dir = this.rayDirection(mouseX, mouseY, new Vector3f());
        Vector3f toCamera = new Vector3f(
            (float) (this.cameraOrigin.x - this.gizmoOrigin.x),
            (float) (this.cameraOrigin.y - this.gizmoOrigin.y),
            (float) (this.cameraOrigin.z - this.gizmoOrigin.z)
        );
        float cameraDistSq = toCamera.lengthSquared();

        if (cameraDistSq < PARALLEL_EPSILON)
        {
            return out.set(0F, 0F, 1F);
        }

        float cameraDist = (float) Math.sqrt(cameraDistSq);
        Vector3f viewNormal = new Vector3f(toCamera).div(cameraDist);
        Matrix3f invView = new Matrix4f(this.view).invert().get3x3(new Matrix3f());
        Vector3f right = invView.getColumn(0, new Vector3f()).normalize();
        Vector3f up = invView.getColumn(1, new Vector3f()).normalize();

        double denom = dir.x * viewNormal.x + dir.y * viewNormal.y + dir.z * viewNormal.z;
        double numer = -cameraDistSq / cameraDist;
        double t = Math.abs(denom) < PARALLEL_EPSILON ? -numer / PARALLEL_EPSILON : numer / denom;

        if (t <= 0D)
        {
            t = cameraDist;
        }

        Vector3f planePoint = new Vector3f(
            toCamera.x + dir.x * (float) t,
            toCamera.y + dir.y * (float) t,
            toCamera.z + dir.z * (float) t
        );
        float x = planePoint.dot(right);
        float y = planePoint.dot(up);
        float dSq = x * x + y * y;
        float rSq = radius * radius;
        float z;

        if (dSq <= rSq * 0.5F)
        {
            z = (float) Math.sqrt(Math.max(rSq - dSq, 0F));
        }
        else
        {
            float d = (float) Math.sqrt(Math.max(dSq, PARALLEL_EPSILON));

            z = (rSq * 0.5F) / d;
        }

        out.set(right).mul(x)
            .add(new Vector3f(up).mul(y))
            .add(new Vector3f(viewNormal).mul(z));

        return out.normalize();
    }

    /**
     * Intersect the ray cast through the given screen position with a plane
     * passing through {@link #gizmoOrigin} and oriented along {@code planeNormal}.
     */
    public boolean intersectPlane(int mouseX, int mouseY, Vector3f planeNormal, Vector3d out)
    {
        Vector3f dir = this.rayDirection(mouseX, mouseY, new Vector3f());
        double denom = dir.x * planeNormal.x + dir.y * planeNormal.y + dir.z * planeNormal.z;

        if (Math.abs(denom) < PARALLEL_EPSILON)
        {
            return false;
        }

        double t = ((this.gizmoOrigin.x - this.cameraOrigin.x) * planeNormal.x
            + (this.gizmoOrigin.y - this.cameraOrigin.y) * planeNormal.y
            + (this.gizmoOrigin.z - this.cameraOrigin.z) * planeNormal.z) / denom;

        if (t <= 0D)
        {
            return false;
        }

        out.set(this.cameraOrigin.x + dir.x * t, this.cameraOrigin.y + dir.y * t, this.cameraOrigin.z + dir.z * t);

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
        Vector3f saved = new Vector3f(transform.rotate);
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
                transform.rotate.set(saved);

                if (i == 0) transform.rotate.x += delta;
                else if (i == 1) transform.rotate.y += delta;
                else transform.rotate.z += delta;

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
            transform.rotate.set(saved);
        }
    }
}
