package mchorse.bbs_mod.ui.framework.elements.input.drag;

/**
 * The operation a transform edit performs. Replaces the magic int
 * (0 translate, 1 scale, 2 rotate) that used to travel through
 * {@code UIPropTransform.enableMode} and every per-frame dispatch.
 */
public enum TransformOp
{
    TRANSLATE, SCALE, ROTATE;
}
