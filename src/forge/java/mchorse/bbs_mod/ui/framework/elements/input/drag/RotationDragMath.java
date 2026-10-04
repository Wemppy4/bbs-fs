package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * The euler glue shared by every rotation drag: compose the turn as a
 * matrix, read ZYX euler angles back out, and write them on the branch
 * continuous with the live pose so a value never jumps mid-gesture. Used to be
 * copy-pasted across the view/trackball/arcball drags and the typed-angle
 * input.
 */
public final class RotationDragMath
{
    private RotationDragMath()
    {}

    /**
     * Cursor angle (radians) around a projected centre, in the viewport pixel
     * convention (Y down) so it lines up with {@link GizmoDrag#projectToScreen}.
     */
    public static float screenAngle(Vector2f center, int mouseX, int mouseY)
    {
        return (float) Math.atan2(mouseY - center.y, mouseX - center.x);
    }

    /**
     * Unwrap a frame-to-frame angle step across the ±180° seam so a small
     * motion there registers as a small step instead of a near-full turn the
     * other way.
     */
    public static float wrapSeamRad(float delta)
    {
        if (delta > MathUtils.PI) return delta - MathUtils.PI * 2F;
        if (delta < -MathUtils.PI) return delta + MathUtils.PI * 2F;

        return delta;
    }

    /** {@code Rz·Ry·Rx} rotation from euler radians — the renderer's composition order. */
    public static Matrix3f eulerZYX(Vector3f radians)
    {
        return new Matrix3f().rotationZ(radians.z).rotateY(radians.y).rotateX(radians.x);
    }

    /**
     * The grab pose's ZYX euler, mode-aware: in QUATERNION mode the euler
     * channels are stale, so this is the cache quaternion's ZYX equivalent —
     * the exact angles {@link GizmoDrag#computeRotateAxes} perturbs, so the
     * source and the measured axes stay consistent.
     */
    public static Vector3f cacheSourceEuler(DragContext ctx)
    {
        return sourceEuler(ctx.cache());
    }

    /**
     * A transform's effective ZYX euler as the axis-measurement source, mode-aware:
     * in QUATERNION mode the euler channels are stale, so this decomposes the live
     * quaternion — the exact angles {@link GizmoDrag#computeRotateAxes} perturbs.
     * Feeding the stale channels to {@link #computeParentInverse} instead would
     * evaluate {@link #eulerAxes} at a different orientation than the measured
     * {@code rotateAxes} and recover a wrong parent frame.
     */
    public static Vector3f sourceEuler(Transform transform)
    {
        return transform.rotationMode == Transform.RotationMode.QUATERNION
            ? Matrices.toEulerZYXRadians(transform.quat, new Vector3f())
            : new Vector3f(transform.rotate);
    }

    /**
     * The bone's parent-frame inverse for a world/view-axis turn, built from the
     * MEASURED rotate axes ({@link GizmoDrag#computeRotateAxes}). Those fold in
     * the parent chain AND any model flip (the cubic {@code Ry(180)} the renderer
     * post-multiplies AFTER the bone's own rotation), so a world axis maps into
     * the correct local-delta frame. The raw bone orientation does NOT: since the
     * flip is post-multiplied, dividing it out leaves an extra conjugation that
     * turns X/Z backwards on cubic bones (see [[cubic-ry180-flip-gotcha]] — this
     * regressed trackball/view/arcball there). Returns {@code null} near a gimbal
     * pole, where the euler axes degenerate; the caller then skips the gesture.
     */
    public static Matrix3f parentInverse(DragContext ctx, GizmoDrag drag)
    {
        return computeParentInverse(drag.rotateAxes, effectiveSourceEuler(ctx, drag));
    }

    /**
     * The angles {@link GizmoDrag#computeRotateAxes} actually measured the axes
     * at: the cache source PLUS the renderer's additive base
     * ({@link GizmoDrag#additiveRotationBase}) — a pose overlay's channels add
     * onto the underlying pose, so the render sits at {@code ZYX(base + rotate)}
     * and {@code eulerAxes} evaluated at the channels alone would recover a
     * wrong parent frame. Quaternion mode is untouched: its layers compose
     * multiplicatively and the parent recovery absorbs the base by itself.
     */
    public static Vector3f effectiveSourceEuler(DragContext ctx, GizmoDrag drag)
    {
        Vector3f source = cacheSourceEuler(ctx);

        if (ctx.cache().rotationMode != Transform.RotationMode.QUATERNION && drag != null)
        {
            source.add(drag.additiveRotationBase);
        }

        return source;
    }

    /**
     * Compose a parent-frame delta rotation onto the grab base and write it per
     * the edited transform's mode. In QUATERNION mode the composed rotation is
     * stored as a quaternion straight from the delta — no euler decomposition,
     * so the drag never hits gimbal lock, and winding is meaningless there. In
     * EULER mode it decomposes ZYX with the two readback anchors held apart:
     *
     * <ul>
     * <li>The WINDING always follows the LIVE channels. Continuity is not a
     * preference — a value can only know how far it has already wound from
     * itself, so anchoring it to the grab capped every gesture at half a turn:
     * a trackball spin past 180° kept turning the bone but wrote the angle
     * folded back (185° stored as −175°), which is the same orientation but the
     * wrong number to keyframe.</li>
     * <li>The BRANCH follows {@code baseEuler}, the very pose the delta composes
     * onto: the result belongs in the euler family the gesture STARTED in, so a
     * passage beside the pole (a branch point of the euler map) self-recovers
     * instead of flowing onto the flipped family and stranding the outer angles
     * at ±180 for the rest of the gesture. Anchoring it to the live channels
     * instead lets that strand latch — the walking reference keeps choosing the
     * family it just landed on.</li>
     * </ul>
     *
     * <p>These two used to share one anchor, which looked like a forced choice
     * between winding and pole-recovery. They are independent questions, so
     * nothing is traded by answering them separately — and once separated, the
     * branch has exactly one right answer for every drag, which is why it isn't
     * a parameter.
     *
     * @param deltaLocal the delta rotation in the bone's parent frame (a pure
     *        rotation matrix); left untouched.
     * @param baseEuler  the grab euler stack the euler path composes onto, and
     *        thereby the branch anchor.
     */
    public static void applyLocalDelta(DragContext ctx, Matrix3f deltaLocal, Vector3f baseEuler)
    {
        if (ctx.transform().rotationMode == Transform.RotationMode.QUATERNION)
        {
            Quaternionf delta = new Quaternionf().setFromNormalized(deltaLocal);

            ctx.writeRotationQuat(delta.mul(new Quaternionf(ctx.cache().quat)));

            return;
        }

        /* An additive layer (pose overlay) renders as ZYX(add + channels), so the
         * delta must compose onto — and both anchors sit at — the EFFECTIVE
         * angles; the written channels then shed the base again
         * (channels' = total − add). Zero base collapses to the classic math. */
        GizmoDrag drag = ctx.drag();
        Vector3f add = drag == null ? new Vector3f() : drag.additiveRotationBase;
        Vector3f effectiveBase = new Vector3f(baseEuler).add(add);
        Vector3f effectiveWinding = new Vector3f(ctx.transform().rotate).add(add);

        Vector3f euler = Matrices.toCompatibleEulerZYXRadians(
            new Matrix3f(deltaLocal).mul(eulerZYX(effectiveBase)),
            effectiveBase,
            effectiveWinding,
            new Vector3f()
        ).sub(add);

        ctx.writeRotateDeg(MathUtils.toDeg(euler.x), MathUtils.toDeg(euler.y), MathUtils.toDeg(euler.z));
    }

    /**
     * World-direction &rarr; bone-parent-frame map from measured euler axes:
     * {@code parent^-1 = eulerAxes(source) * rotateAxes^-1}. {@code rotateAxes}
     * already folds in the parent and any model flips, so this recovers the
     * pure parent rotation; it is constant for the whole drag since the parent
     * doesn't move. Returns {@code null} when {@code rotateAxes} is degenerate.
     */
    private static Matrix3f computeParentInverse(Matrix3f rotateAxes, Vector3f sourceRadians)
    {
        Matrix3f rotateAxesInverse = new Matrix3f(rotateAxes);

        if (Math.abs(rotateAxesInverse.determinant()) < 1.0E-4F)
        {
            return null;
        }

        return eulerAxes(sourceRadians).mul(rotateAxesInverse.invert());
    }

    /**
     * Columns of the returned matrix are the (parent-frame) axes that
     * {@code rotate.x}, {@code rotate.y} and {@code rotate.z} rotate around for
     * the renderer's {@code Rz * Ry * Rx} order. They are orthonormal at rest
     * but skew as the bone turns, which is exactly why the decomposition has to
     * be re-evaluated against the live pose rather than a frozen snapshot.
     */
    private static Matrix3f eulerAxes(Vector3f rotateRadians)
    {
        Matrix3f axes = new Matrix3f();

        axes.setColumn(0, new Matrix3f().rotationZ(rotateRadians.z).rotateY(rotateRadians.y).transform(new Vector3f(1F, 0F, 0F)));
        axes.setColumn(1, new Matrix3f().rotationZ(rotateRadians.z).transform(new Vector3f(0F, 1F, 0F)));
        axes.setColumn(2, new Vector3f(0F, 0F, 1F));

        return axes;
    }
}
