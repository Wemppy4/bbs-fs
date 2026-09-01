package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.model.config.ArmorSlotValue;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;

/**
 * The attachment slot the model editor's viewport gizmo is on: the slot, what kind of
 * attachment it is, and the transform editor the gizmo drives (the one in the slot's row).
 */
public record ModelSlotTarget(ArmorSlotValue slot, ModelSlotKind kind, UIPropTransform editor)
{
}
