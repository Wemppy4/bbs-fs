package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * One button of a context menu's icon bar: a {@link MenuVerb} plus what pressing it does here.
 *
 * <p>A verb that has no meaning in this menu is simply never registered. One that has a
 * meaning but cannot run right now is registered {@link #enabled disabled} instead, so that the
 * bar keeps its shape and a verb keeps its place from one opening to the next — a button that
 * moves depending on state is worse than a button that greys out.</p>
 */
public class MenuIcon
{
    public final MenuVerb verb;
    public final Runnable runnable;

    /** What the tooltip says. Defaults to the verb's own name; sites that name the thing they
     * copy ("Copy clips") override it. */
    public IKey label;
    public boolean enabled = true;

    public MenuIcon(MenuVerb verb, Runnable runnable)
    {
        this.verb = verb;
        this.runnable = runnable;
        this.label = verb.label;
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
}
