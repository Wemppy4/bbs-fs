package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The quick actions a form cell offers on hover — the few things done often enough to earn a
 * button on the cell itself. Everything rarer stays in the context menu.
 */
public enum FormCellAction
{
    EDIT(Icons.EDIT, UIKeys.GENERAL_EDIT, false),
    DUPLICATE(Icons.DUPE, UIKeys.FORMS_CATEGORIES_CONTEXT_DUPLICATE_FORM, false),
    REMOVE(Icons.REMOVE, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FORM, true);

    /** Editing is the one action every category has, so it keeps the same place — last — everywhere. */
    private static final FormCellAction[] MODIFIABLE = {DUPLICATE, REMOVE, EDIT};
    private static final FormCellAction[] READ_ONLY = {EDIT};

    public final Icon icon;
    public final IKey label;

    /** Whether the action destroys something and so is tinted red under the cursor. */
    public final boolean danger;

    FormCellAction(Icon icon, IKey label, boolean danger)
    {
        this.icon = icon;
        this.label = label;
        this.danger = danger;
    }

    /**
     * Which actions cells of a category show: categories fed from assets (models, particles,
     * mobs) can't be added to or shrunk, so only editing is offered there.
     */
    public static FormCellAction[] of(FormCategory category)
    {
        return category.canModify(null) ? MODIFIABLE : READ_ONLY;
    }
}
