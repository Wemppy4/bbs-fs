package mchorse.bbs_mod.forms.renderers.utils;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One captured bone/part frame: the full matrix, the origin flavour (the frame
 * BEFORE the bone's own rotation), and — for model bones — the bone's EVALUATED
 * channel rotation (ZYX euler radians) after the channels phase
 * (rest &rarr; actions &rarr; pose). The rotation is the effective additive
 * total the renderer composes, which the gizmo needs to edit one additive layer
 * (a pose overlay) of it correctly; {@code null} for non-bone entries and for
 * bones whose rotation left the euler channels (quaternion mode).
 *
 * <p>{@code pivot} is set for a form's frames only: the form's pivot in those frames' units,
 * see {@link #placement(boolean)}.</p>
 */
public record MatrixCacheEntry(Matrix4f matrix, Matrix4f origin, Vector3f evaluatedRotation, Vector3f pivot)
{
    public MatrixCacheEntry(Matrix4f matrix, Matrix4f origin)
    {
        this(matrix, origin, null, null);
    }

    public MatrixCacheEntry(Matrix4f matrix, Matrix4f origin, Vector3f evaluatedRotation)
    {
        this(matrix, origin, evaluatedRotation, null);
    }

    /**
     * Where a gizmo editing this frame's transform sits: the own frame or the origin flavour
     * ({@code TransformSpace#placesOnOwnFrame}), moved onto the pivot that transform turns about.
     *
     * <p>A bone's frames already stand on its pivot. A form's stand on the form's origin — the
     * frame its body parts and geometry hang in, which every other reader wants — and the form's
     * pivot is set off from it.</p>
     */
    public Matrix4f placement(boolean ownFrame)
    {
        Matrix4f frame = ownFrame ? this.matrix : this.origin;

        if (frame == null || this.pivot == null)
        {
            return frame;
        }

        return new Matrix4f(frame).translate(this.pivot);
    }
}
