package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The quick actions a grid cell (a form, a texture) offers on hover — the few things done
 * often enough to earn a button on the cell itself. Everything rarer stays in the context menu.
 */
public enum CellAction
{
    EDIT(Icons.EDIT, UIKeys.GENERAL_EDIT),
    DUPLICATE(Icons.DUPE, UIKeys.FORMS_CATEGORIES_CONTEXT_DUPLICATE_FORM),
    REMOVE(Icons.REMOVE, UIKeys.GENERAL_REMOVE),
    /* The two faces of one button: what it says is what pressing it does to this cell */
    PIN(Icons.BOOKMARK, UIKeys.TEXTURES_BROWSER_PIN),
    UNPIN(Icons.BOOKMARK, UIKeys.TEXTURES_BROWSER_UNPIN);

    /** Editing is the one action every cell has, so it keeps the same place — last — everywhere. */
    private static final CellAction[] MODIFIABLE = {DUPLICATE, REMOVE, EDIT};
    private static final CellAction[] READ_ONLY = {EDIT};
    private static final CellAction[] NONE = {};

    public final Icon icon;
    public final IKey label;

    CellAction(Icon icon, IKey label)
    {
        this.icon = icon;
        this.label = label;
    }

    /**
     * Which actions a cell shows: everything for content the user may add to and shrink,
     * only editing for content fed from assets.
     */
    public static CellAction[] of(boolean modifiable)
    {
        return modifiable ? MODIFIABLE : READ_ONLY;
    }

    public static CellAction[] none()
    {
        return NONE;
    }

    /**
     * The same actions with one more in front. Editing keeps the last place — the bar is
     * right-aligned, and a cell too narrow for everything drops buttons from the left.
     */
    public static CellAction[] with(CellAction first, CellAction[] actions)
    {
        CellAction[] result = new CellAction[actions.length + 1];

        result[0] = first;
        System.arraycopy(actions, 0, result, 1, actions.length);

        return result;
    }
}
