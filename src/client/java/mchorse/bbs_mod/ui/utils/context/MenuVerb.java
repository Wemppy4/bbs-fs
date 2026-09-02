package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The verbs a context menu shows as an icon in its bar rather than as a labelled row of its
 * list — the few operations that mean the same thing everywhere in BBS and have an
 * unambiguous glyph. Anything context specific ("split the clip", "shift to the cursor")
 * stays a row, where it can spell out what it does.
 *
 * <p>The {@link Slot}, not the order of the calls that registered the buttons, decides where a
 * verb sits — otherwise a verb added by a base class and one added by its subclass would land
 * in whichever order the constructors happened to run.</p>
 *
 * <p>Adding comes first because a context menu opens with its top left corner exactly under
 * the cursor: the plus lands under the mouse, one click away with no travel. Removal sits right
 * beside it, the way add and remove pair up everywhere else in BBS; what tells it apart is its
 * colour, not its distance. The step back, when there is one, comes before both — it is the way
 * out of where you are, not something you do here.</p>
 *
 * <p>Resetting sits with removal: it throws the current work away just as surely, so it is
 * marked the same and stands ahead of the verbs that only shuffle data about.</p>
 */
public enum MenuVerb
{
    BACK(Icons.ARROW_LEFT, UIKeys.GENERAL_BACK, Slot.NAVIGATE),
    ADD(Icons.ADD, UIKeys.GENERAL_ADD, Slot.CREATE),
    REMOVE(Icons.REMOVE, UIKeys.GENERAL_REMOVE, Slot.DESTROY),
    COPY(Icons.COPY, UIKeys.GENERAL_COPY, Slot.CLIPBOARD),
    PASTE(Icons.PASTE, UIKeys.GENERAL_PASTE, Slot.CLIPBOARD),
    RESET(Icons.REFRESH, UIKeys.GENERAL_RESET, Slot.DESTROY),
    SAVE(Icons.SAVED, UIKeys.GENERAL_SAVE, Slot.COMMON),
    PRESETS(Icons.MORE, UIKeys.GENERAL_PRESETS, Slot.COMMON);

    /** The zones of the bar, laid out left to right in this order. */
    public enum Slot
    {
        NAVIGATE, CREATE, DESTROY, CLIPBOARD, COMMON;

        /** Whether what sits here undoes work, and so is marked in the destructive colour. */
        public boolean isDestructive()
        {
            return this == DESTROY;
        }
    }

    public final Icon icon;
    public final IKey label;
    public final Slot slot;

    MenuVerb(Icon icon, IKey label, Slot slot)
    {
        this.icon = icon;
        this.label = label;
        this.slot = slot;
    }
}
