package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;

/**
 * What the model editor's viewport gizmo is on: the bone it sits at, what kind of thing is
 * edited there (an attachment slot on the bone, the pose of the bone itself, a group's rest), the
 * transform editor the gizmo drives, and — for an editor that works on a stand-in transform
 * rather than the model's own numbers — what pushes the stand-in into the model before the bone
 * is evaluated afresh ({@code apply}; null when the transform is the model's), and which handles the
 * gizmo offers ({@code mask}; null for all of them) — a rest has no scale, and a rest shared by
 * several picked bones has no rotation either.
 */
public record ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor, Runnable apply, Gizmo.HandleMask mask)
{
    public ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor)
    {
        this(bone, kind, editor, null, null);
    }

    public ModelSlotTarget(String bone, ModelSlotKind kind, UIPropTransform editor, Runnable apply)
    {
        this(bone, kind, editor, apply, null);
    }
}
