package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;

/**
 * What the model editor's viewport gizmo is on: the bone it sits at, what kind of thing is
 * edited there (an attachment slot on the bone, or the pose of the bone itself), and the
 * transform editor the gizmo drives.
 */
public record ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor)
{
}
