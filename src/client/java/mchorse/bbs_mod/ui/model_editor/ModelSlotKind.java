package mchorse.bbs_mod.ui.model_editor;

/**
 * What the model editor's viewport gizmo is on: an attachment of the model's config, or a bone
 * of its sneaking pose. It decides the frame the transform is applied in (see
 * {@link UIModelEditorRenderer}) and which preview shows it: the held items, the armor and the
 * pose bones ride the orbit view, the first-person hands only the first-person view.
 */
public enum ModelSlotKind
{
    ITEM_MAIN(false, false),
    ITEM_OFF(false, true),
    ARMOR(false, false),
    FIRST_PERSON_MAIN(true, false),
    FIRST_PERSON_OFF(true, true),

    /** A bone of the sneaking pose: the transform IS the bone's, not something hung on it. */
    POSE(false, false),

    /** A group's own rest in the model editor — the pivot it turns about and the rotation it rests at. */
    ANCHOR(false, false);

    public final boolean firstPerson;
    public final boolean offHand;

    ModelSlotKind(boolean firstPerson, boolean offHand)
    {
        this.firstPerson = firstPerson;
        this.offHand = offHand;
    }
}
