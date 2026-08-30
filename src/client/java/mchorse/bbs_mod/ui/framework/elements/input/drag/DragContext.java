package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Everything a {@link DragStrategy} may read from or write to its host
 * transform editor. The editor implements this as a thin bridge, so the
 * strategies never touch the UI element directly and every write still
 * flows through the editor's virtual {@code setT/setS/setR/setR2} — which
 * is what routes an edit onto a whole selection in the delta editors.
 */
public interface DragContext
{
    /** The transform being edited (live values). */
    Transform transform();

    /** Snapshot of the transform taken when the edit began. */
    Transform cache();

    GizmoDrag drag();

    /** Replace the drag snapshot mid-gesture (the depth wheel re-acquires it). */
    void setDrag(GizmoDrag drag);

    /** A freshly built drag from the host's hotkey supplier, or {@code null}. */
    GizmoDrag freshHotkeyDrag();

    /** The reference frame this edit operates in — the ONE thing that decides it. */
    TransformSpace space();

    boolean isModel();

    /** Whether the edited bone's ROTATION is owned by an enabled IK chain: the render
     *  follows the solve, so the measured axes degenerate and a ring would sweep while
     *  the bone ignores it. Rotation gestures refuse to start instead. */
    default boolean rotationConstrained()
    {
        return false;
    }

    /** Whether values of the given operation should snap to the configured step. */
    boolean shouldSnap(TransformOp op);

    /** Per-pixel step of the additive (non-ray) drag for the given operation. */
    float additiveFactor(TransformOp op);

    /** {@code factor} along {@code axis} rotated into the transform's local frame. */
    Vector3f localTranslateVector(double factor, Axis axis);

    /** World-space radius the rotate sphere was last drawn at ({@code 0} until rendered). */
    float sphereWorldRadius();

    /** Re-read the transform into the editor's value fields (after an off-frame write). */
    void refreshFields();

    void writeTranslate(float x, float y, float z);

    void writeScale(float x, float y, float z);

    void writeRotateDeg(float xDeg, float yDeg, float zDeg);

    /** Store the full local rotation as a quaternion (for a bone in
     *  {@link mchorse.bbs_mod.utils.pose.Transform.RotationMode#QUATERNION} mode),
     *  flipping the edited transform(s) into quaternion storage. No euler
     *  decomposition, so the gizmo drag stays gimbal-free. */
    void writeRotationQuat(Quaternionf quat);
}
