package mchorse.bbs_mod.ui.utils;

/**
 * Space a transform edit operates in. GLOBAL aligns the gizmo and the edit
 * directions with the bone's origin (parent) axes, LOCAL with the bone's own
 * rotated axes. The transform data itself is always stored the same way;
 * the space only changes how a gesture maps onto it.
 */
public enum TransformSpace
{
    GLOBAL, LOCAL;
}
