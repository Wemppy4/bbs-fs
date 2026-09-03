package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;

/**
 * What the model editor's viewport gizmo is on: the bone it sits at, what kind of thing is
 * edited there (an attachment slot on the bone, the pose of the bone itself, a group's rest), the
 * transform editor the gizmo drives, and — for an editor that works on a stand-in transform
 * rather than the model's own numbers — what pushes the stand-in into the model before the bone
 * is evaluated afresh ({@code apply}; null when the transform is the model's).
 */
public record ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor, Runnable apply)
{
    public ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor)
    {
        this(bone, kind, editor, null);
    }
}
