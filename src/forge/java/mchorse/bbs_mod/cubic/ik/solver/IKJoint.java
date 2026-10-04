package mchorse.bbs_mod.cubic.ik.solver;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One directed bone of an IK chain, as the solver sees it: its channel angles —
 * the solver's variables — plus per-axis degrees of freedom, and its captured
 * and working world frames.
 *
 * <p>The solver's variable is {@link #angles}: the bone's ZYX rotation channels
 * in radians, the SAME parametrization the renderer composes ({@code
 * Rz·Ry·Rx}), so limits clamp the very numbers the animator sees on the
 * rotation pads and the result folds back into the pose without any
 * reconstruction. A quaternion-mode bone enters through its compatible-euler
 * decomposition and leaves as a quaternion again — the parametrization is
 * internal to the solve.
 *
 * <p>Axis indices are {@code 0 = X, 1 = Y, 2 = Z} throughout.
 */
public final class IKJoint
{
    /* --- captured at build (the FK pose the solve starts from) --- */

    /** World pivot position at capture. */
    public final Vector3f startPosition = new Vector3f();

    /** World rotation at capture (pivot frame AFTER the bone's own rotation). */
    public final Quaternionf startWorldRotation = new Quaternionf();

    /** FK channel angles at capture, ZYX radians — the blend base and the value locked axes hold. */
    public final Vector3f startAngles = new Vector3f();

    /* --- the solver variable --- */

    /** Current channel angles, ZYX radians. Starts equal to {@link #startAngles}. */
    public final Vector3f angles = new Vector3f();

    /* --- per-axis degrees of freedom (Blender's bone IK panel) --- */

    /** A locked axis is absent from the Jacobian and FROZEN at its FK value. */
    public final boolean[] locked = new boolean[3];

    /** Whether {@link #limitMin}/{@link #limitMax} apply on this axis. */
    public final boolean[] limited = new boolean[3];

    /** Lower rotation limit per axis, radians. */
    public final float[] limitMin = new float[3];

    /** Upper rotation limit per axis, radians. */
    public final float[] limitMax = new float[3];

    /** 0 = moves freely, approaching 1 = increasingly reluctant to move. */
    public final float[] stiffness = new float[3];

    /* --- working state, refreshed by IKChain.forward() --- */

    /** World pivot position at the current angles. */
    public final Vector3f position = new Vector3f();

    /** World rotation of the parent frame (pivot frame BEFORE this bone's rotation). */
    public final Quaternionf parentRotation = new Quaternionf();

    /** World rotation at the current angles. */
    public final Quaternionf worldRotation = new Quaternionf();

    /** How willing this axis is to move: {@code 1 - stiffness}, clamped to [0, 1]. */
    public float weight(int axis)
    {
        float w = 1F - this.stiffness[axis];

        return w < 0F ? 0F : Math.min(w, 1F);
    }

    /** Clamps {@link #angles} into the enabled per-axis limits. */
    public void clampLimits()
    {
        for (int axis = 0; axis < 3; axis++)
        {
            if (!this.limited[axis])
            {
                continue;
            }

            float value = get(this.angles, axis);
            float clamped = Math.max(this.limitMin[axis], Math.min(this.limitMax[axis], value));

            if (clamped != value)
            {
                set(this.angles, axis, clamped);
            }
        }
    }

    static float get(Vector3f v, int axis)
    {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    static void set(Vector3f v, int axis, float value)
    {
        if (axis == 0) v.x = value;
        else if (axis == 1) v.y = value;
        else v.z = value;
    }
}
