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
 */
@com.github.bsideup.jabel.Desugar
public record MatrixCacheEntry(Matrix4f matrix, Matrix4f origin, Vector3f evaluatedRotation)
{
    public MatrixCacheEntry(Matrix4f matrix, Matrix4f origin)
    {
        this(matrix, origin, null);
    }
}
