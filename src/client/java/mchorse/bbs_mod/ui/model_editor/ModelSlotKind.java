package mchorse.bbs_mod.ui.model_editor;

/**
 * Which attachment of a model's config a slot is. It decides the frame the slot's transform
 * is applied in (see {@link UIModelEditorRenderer}) and which preview shows it: the held
 * items and armor ride the orbit view, the first-person hands only the first-person view.
 */
public enum ModelSlotKind
{
    ITEM_MAIN(false, false),
    ITEM_OFF(false, true),
    ARMOR(false, false),
    FIRST_PERSON_MAIN(true, false),
    FIRST_PERSON_OFF(true, true);

    public final boolean firstPerson;
    public final boolean offHand;

    ModelSlotKind(boolean firstPerson, boolean offHand)
    {
        this.firstPerson = firstPerson;
        this.offHand = offHand;
    }
}
