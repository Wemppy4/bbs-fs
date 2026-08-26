package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * One button of a context menu's icon bar: what it looks like, which zone of the bar it belongs
 * to, and what pressing it does here.
 *
 * <p>A verb that has no meaning in this menu is simply never registered. One that has a
 * meaning but cannot run right now is registered {@link #enabled disabled} instead, so that the
 * bar keeps its shape and a verb keeps its place from one opening to the next — a button that
 * moves depending on state is worse than a button that greys out.</p>
 */
public class MenuIcon
{
    public final Icon icon;
    public final MenuVerb.Slot slot;
    public final Runnable runnable;

    /** What the tooltip says. Defaults to the verb's own name; sites that name the thing they
     * copy ("Copy clips") override it. */
    public IKey label;
    public boolean enabled = true;

    /**
     * Whether pressing this leaves the menu standing. Most verbs are the last thing you do in a
     * menu, so the default is to close; a menu you work inside of — trying presets one after
     * another — keeps itself open instead.
     */
    public boolean keepOpen;

    public MenuIcon(MenuVerb verb, Runnable runnable)
    {
        this(verb.icon, verb.label, verb.slot, runnable);
    }

    /**
     * A button that belongs in the bar without being one of the shared verbs — the pose menu's
     * flip. It still picks a zone, so it lands in the same place every time it appears.
     */
    public MenuIcon(Icon icon, IKey label, MenuVerb.Slot slot, Runnable runnable)
    {
        this.icon = icon;
        this.label = label;
        this.slot = slot;
        this.runnable = runnable;
    }

    public MenuIcon label(IKey label)
    {
        this.label = label;

        return this;
    }

    public MenuIcon enabled(boolean enabled)
    {
        this.enabled = enabled;

        return this;
    }

    public MenuIcon keepOpen()
    {
        this.keepOpen = true;

        return this;
    }
}
