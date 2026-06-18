package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix4f;

/**
 * Supplies the absolute world matrix of the element a
 * {@link mchorse.bbs_mod.ui.framework.elements.input.UITransform} edits, so its transform can be
 * captured in world space and reproduced at a different tick regardless of what the parent or any
 * animation did in between (the transform context menu's "copy/paste world transform" actions).
 *
 * <p>The matrix must reflect the element's <em>current</em> transform values and be re-evaluated on
 * every call — the world paste solves for the transform numerically by nudging its channels and
 * re-sampling (the same finite-difference approach the gizmo drag uses), which only works if a
 * change to the transform shows up in the next sample. {@code false} is returned when the element
 * can't be resolved right now (nothing selected, not rendered yet), which hides the world actions.
 */
public interface IWorldTransformProvider
{
    /** Sample the element's absolute world matrix (translation, rotation and scale) for its current
     *  transform values, re-evaluating the scene so live edits to the transform are reflected. */
    boolean getWorldMatrix(Matrix4f out);
}
