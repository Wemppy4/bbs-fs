package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.utils.MatrixUtils;
import mchorse.bbs_mod.utils.Pair;
import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;

/**
 * The frame of reference of a tracked bone: everything {@link TrackerClientClip}
 * needs in order to place the camera, apart from the clip's own offset and angle.
 *
 * <p>Both directions live here on purpose. The forward direction is what the clip
 * applies; the inverse is what the camera editor needs to turn a camera the user
 * flew (or orbited) into the offset and angle that reproduce it. Kept apart, the
 * two would drift out of sync at the first change to the placement math.</p>
 *
 * <p>Everything is expressed relative to an {@link #origin} — the camera position
 * the bone matrices were collected against, matching how the renderer builds
 * camera-relative matrices. The origin only shifts {@link #bone}, so the choice of
 * origin doesn't affect the resulting world position; the editor simply passes the
 * camera it is solving for.</p>
 */
public class TrackerFrame
{
    private static final MatrixUtils.RotationOrder ORDER = MatrixUtils.RotationOrder.YXZ;

    /* Angle refinement, see refineAngles */
    private static final int REFINEMENTS = 8;
    private static final int BACKOFFS = 6;
    private static final double DERIVATIVE = 0.05D;
    private static final double TOLERANCE = 1E-5D;

    /** Camera position the matrices below are relative to. */
    private final Vector3d origin = new Vector3d();

    /** Tracked bone's position, relative to {@link #origin}. */
    private final Vector3f bone = new Vector3f();

    /** Tracked bone's orientation. Carries the pose's scale, like the bone matrix does. */
    private final Matrix3d rotation = new Matrix3d();

    /** {@link #rotation} with the scale stripped off, for angle math (see {@link #solveAngles}). */
    private final Matrix3d rotationOnly = new Matrix3d();

    /** Relative mode's camera travel, already rotated into tracker space. Zero otherwise. */
    private final Vector3d positionDelta = new Vector3d();

    /** Relative mode's camera rotation, in tracker space. Identity otherwise. */
    private final Matrix3d angleDelta = new Matrix3d();

    /**
     * Resolve the frame of the given bone (attachment group) of the given entity.
     *
     * @param cx camera position X the resulting matrices are relative to
     * @return {@code null} when the entity has no form, or the form has no such group
     */
    public static TrackerFrame resolve(Map<String, IEntity> entities, IEntity entity, String group, double cx, double cy, double cz, float transition)
    {
        Form form = entity == null ? null : entity.getForm();

        if (form == null)
        {
            return null;
        }

        MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);

        if (!map.has(group))
        {
            return null;
        }

        Matrix4f formTransform = FilmMatrices.getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        Pair<Matrix4f, Float> totalMatrix = FilmMatrices.getTotalMatrix(entities, form.anchor.get(), formTransform, cx, cy, cz, transition, 0);

        if (totalMatrix.a != null)
        {
            formTransform = totalMatrix.a;
        }

        formTransform.mul(map.get(group).matrix());

        return new TrackerFrame(cx, cy, cz, formTransform);
    }

    private TrackerFrame(double cx, double cy, double cz, Matrix4f boneTransform)
    {
        this.origin.set(cx, cy, cz);
        boneTransform.getTranslation(this.bone);

        this.rotation.set(new Matrix3d(boneTransform));
        this.rotationOnly.set(this.rotation);

        normalize(this.rotationOnly);
        this.angleDelta.identity();
    }

    /**
     * Fold in relative mode: the camera's own travel and rotation (measured from
     * where the clips underneath were at the clip's first tick) become movement
     * within tracker space.
     *
     * @param current position produced by the clips underneath at the current tick
     * @param first the same, at the clip's first tick
     */
    public TrackerFrame relative(Position current, Position first)
    {
        this.positionDelta.set(
            current.point.x - first.point.x,
            current.point.y - first.point.y,
            current.point.z - first.point.z
        );
        this.rotation.transform(this.positionDelta);

        this.angleDelta.set(ORDER.getRotationMatrix(
            Math.toRadians(current.angle.yaw - first.angle.yaw),
            Math.toRadians(current.angle.pitch - first.angle.pitch),
            Math.toRadians(current.angle.roll - first.angle.roll)
        ));

        return this;
    }

    /**
     * Vector from {@link #origin} to the tracked point, i.e. the bone displaced by
     * the offset in the bone's own axes. This is the point look-at mode aims at.
     */
    public Vector3d track(Point offset)
    {
        Vector3d point = new Vector3d(offset.x, offset.y, offset.z);

        this.rotation.transform(point);

        return point.add(this.bone.x, this.bone.y, this.bone.z);
    }

    /* Forward: offset and angle to camera */

    public Vector3d position(Point offset)
    {
        return this.track(offset).add(this.origin).add(this.positionDelta);
    }

    public Angle angles(Point angle)
    {
        Matrix3d matrix = new Matrix3d(this.rotation)
            .mul(ORDER.getRotationMatrix(Math.toRadians(angle.y), Math.toRadians(angle.x), Math.toRadians(angle.z)))
            .mul(this.angleDelta);
        Vector3d euler = ORDER.getEulerAngles(matrix);

        return angle((float) (Math.toDegrees(-euler.y) - 180D), (float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.z));
    }

    public Angle lookAtAngles(Point offset, Point angle)
    {
        Vector3d track = this.track(offset);
        Angle lookAt = Angle.angle(track.x, track.y, track.z);

        return angle(lookAt.yaw + (float) angle.y, lookAt.pitch + (float) angle.x, lookAt.roll + (float) angle.z);
    }

    /* Inverse: camera to offset and angle */

    /**
     * Offset that places the camera at the given world position.
     *
     * @return {@code null} when the bone's matrix is degenerate (zero scale), in
     *         which case there is no offset that would reach the position
     */
    public Point solveOffset(double x, double y, double z)
    {
        Matrix3d inverse = new Matrix3d(this.rotation);

        if (Math.abs(inverse.determinant()) < 1E-8D)
        {
            return null;
        }

        Vector3d point = new Vector3d(x, y, z)
            .sub(this.origin)
            .sub(this.positionDelta)
            .sub(this.bone.x, this.bone.y, this.bone.z);

        inverse.invert().transform(point);

        return new Point(point.x, point.y, point.z);
    }

    /**
     * Angle offset that points the camera at the given angles.
     *
     * <p>Closed form, then refined. The scale is stripped off the bone's rotation
     * here because the euler extraction {@link #angles} ends with measures
     * directions, and scaling a matrix's columns doesn't turn them: for a bone the
     * pose scales uniformly the closed form is already the exact answer, and the
     * refinement exits on its first check.</p>
     */
    public Point solveAngles(float yaw, float pitch, float roll)
    {
        Matrix3d target = orientation(yaw, pitch, roll);
        Matrix3d matrix = new Matrix3d(this.rotationOnly).transpose()
            .mul(target)
            .mul(new Matrix3d(this.angleDelta).transpose());
        Vector3d euler = ORDER.getEulerAngles(matrix);
        Vector3d angle = new Vector3d(Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z));

        this.refineAngles(angle, target);

        return new Point(angle.x, angle.y, angle.z);
    }

    /**
     * Newton refinement of the closed form above, needed when the pose scales the
     * bone unevenly: the scale sits between the bone's rotation and the angle
     * offset ({@code rotation * scale * offset}), so it skews how the offset turns
     * the camera and the closed form lands beside the target.
     *
     * <p>A step is only taken when it actually shrinks the residual, so an
     * orientation a stretched bone cannot reach at all settles on the closest one
     * instead of running away. The derivative is deliberately a coarse
     * {@link #DERIVATIVE} of a degree: it steps over the euler parameterization's
     * own singularity (a pitch offset at ±90°, where yaw and roll turn the camera
     * the same way), which a finer one walks straight into.</p>
     */
    private void refineAngles(Vector3d angle, Matrix3d target)
    {
        Vector3d residual = this.residual(angle, target);
        Vector3d shifted = new Vector3d();

        for (int i = 0; i < REFINEMENTS && residual.length() > TOLERANCE; i++)
        {
            Matrix3d jacobian = new Matrix3d();

            for (int c = 0; c < 3; c++)
            {
                shifted.set(angle).setComponent(c, angle.get(c) + DERIVATIVE);
                jacobian.setColumn(c, this.residual(shifted, target).sub(residual).div(DERIVATIVE));
            }

            if (Math.abs(jacobian.determinant()) < 1E-6D)
            {
                return;
            }

            Vector3d step = jacobian.invert().transform(new Vector3d(residual).negate());
            boolean improved = false;

            for (int k = 0; k < BACKOFFS; k++)
            {
                Vector3d candidate = new Vector3d(angle).add(step);
                Vector3d error = this.residual(candidate, target);

                if (error.length() < residual.length())
                {
                    angle.set(candidate);
                    residual = error;
                    improved = true;

                    break;
                }

                step.mul(0.5D);
            }

            if (!improved)
            {
                return;
            }
        }
    }

    /**
     * How far the given angle offset lands from the orientation being solved for,
     * as the turn (in degrees, around each axis) that closes the gap. Measured on
     * the orientation rather than on the angles themselves: the same camera can be
     * written as two different yaw/pitch/roll triples, and chasing the triple sends
     * the refinement after a difference the viewer can't see.
     */
    private Vector3d residual(Vector3d angle, Matrix3d target)
    {
        Angle produced = this.angles(new Point(angle.x, angle.y, angle.z));
        Matrix3d delta = orientation(produced.yaw, produced.pitch, produced.roll).transpose().mul(target);
        double cos = Math.max(-1D, Math.min(1D, (delta.m00 + delta.m11 + delta.m22 - 1D) / 2D));
        double turn = Math.acos(cos);
        double sin = Math.sin(turn);
        Vector3d axis = new Vector3d(delta.m12 - delta.m21, delta.m20 - delta.m02, delta.m01 - delta.m10);

        /* The axis comes out scaled by 2 sin(turn), which vanishes as the gap closes */
        return axis.mul(Math.toDegrees(sin < 1E-9D ? 0.5D : turn / (2D * sin)));
    }

    /**
     * Angle offset that points the camera at the given angles in look-at mode,
     * i.e. the framing offset on top of the direction towards the tracked point.
     */
    public Point solveLookAtAngles(Point offset, float yaw, float pitch, float roll)
    {
        Vector3d track = this.track(offset);
        Angle lookAt = Angle.angle(track.x, track.y, track.z);

        return new Point(wrap(pitch - lookAt.pitch), wrap(yaw - lookAt.yaw), wrap(roll - lookAt.roll));
    }

    /** Camera orientation as a matrix, i.e. the inverse of what {@link #angles} ends with. */
    private static Matrix3d orientation(float yaw, float pitch, float roll)
    {
        return ORDER.getRotationMatrix(-Math.toRadians(yaw + 180D), Math.toRadians(pitch), Math.toRadians(roll));
    }

    private static Angle angle(float yaw, float pitch, float roll)
    {
        Angle angle = new Angle(yaw, pitch);

        angle.roll = roll;

        return angle;
    }

    /**
     * Strip the pose's scale off, so what's left is the bone's rotation. A bone
     * matrix is a rotation with the scale applied to its columns, so normalizing
     * them recovers the rotation.
     */
    private static void normalize(Matrix3d matrix)
    {
        Vector3d column = new Vector3d();

        for (int i = 0; i < 3; i++)
        {
            matrix.getColumn(i, column);

            double length = column.length();

            if (length < 1E-8D)
            {
                matrix.identity();

                return;
            }

            matrix.setColumn(i, column.div(length));
        }
    }

    private static double wrap(double degrees)
    {
        return degrees - 360D * Math.round(degrees / 360D);
    }
}
