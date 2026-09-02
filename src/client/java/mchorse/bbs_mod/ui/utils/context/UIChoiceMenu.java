package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Picking one option out of a fixed set, as a context menu: every option on a row of its own
 * with its icon and its name, the active one highlighted, and the whole list auto-keyed so a
 * pick is a two-stroke gesture — open, then press the option's number.
 *
 * <p>This shape was written out by hand everywhere it appeared — the transform space, the
 * preview's camera mode, the list of clip types — and the copies had drifted apart: some
 * highlighted the active option, some did not, some coloured the rows.
 *
 * <p>Three knobs cover all of them, and each is optional: {@link #current} highlights the
 * active option, {@link #color} tags rows with a colour of their own, {@link #unavailable}
 * greys out what cannot be picked yet. A list that picks passes a current; a list that
 * creates does not.
 *
 * <p>{@link #open} shows it as the menu; {@link #build} puts it INTO one the caller is
 * already assembling, so its own entries and its close hook survive.
 */
public class UIChoiceMenu <T>
{
    private final Iterable<T> options;

    private T current;
    private Function<T, Icon> icon = (option) -> Icons.NONE;
    private Function<T, IKey> label;

    private Function<T, Integer> color;

    private Predicate<T> available;
    private Function<T, IKey> unavailableLabel;

    public static <T> UIChoiceMenu<T> of(Iterable<T> options)
    {
        return new UIChoiceMenu<>(options);
    }

    public static <T> UIChoiceMenu<T> of(T[] options)
    {
        return new UIChoiceMenu<>(Arrays.asList(options));
    }

    private UIChoiceMenu(Iterable<T> options)
    {
        this.options = options;
    }

    /** The option to highlight as active. Leave unset when the list creates rather than picks. */
    public UIChoiceMenu<T> current(T current)
    {
        this.current = current;

        return this;
    }

    public UIChoiceMenu<T> icon(Function<T, Icon> icon)
    {
        this.icon = icon;

        return this;
    }

    public UIChoiceMenu<T> label(Function<T, IKey> label)
    {
        this.label = label;

        return this;
    }

    /**
     * A colour of the option's own, for sets where the colour carries meaning the icon does
     * not — the clip types, whose colour is how they read on the timeline. The active option
     * is highlighted instead: one row cannot say both "this is the one" and "this is my
     * colour", and which one it is matters more.
     */
    public UIChoiceMenu<T> color(Function<T, Integer> color)
    {
        this.color = color;

        return this;
    }

    /**
     * Options that cannot be picked yet: they stay on the list — so the set reads whole and
     * the keys of the ones below them do not shift once they arrive — but render grey, under
     * their own label, and do nothing when pressed. Grey wins over {@link #color} here: the
     * row is saying "not yet", which outranks whatever colour it will have.
     */
    public UIChoiceMenu<T> unavailable(Predicate<T> available, Function<T, IKey> label)
    {
        this.available = available;
        this.unavailableLabel = label;

        return this;
    }

    /** Shows the list as the context menu, for callers with nothing else to add to it. */
    public void open(UIContext context, Consumer<T> pick)
    {
        if (context != null)
        {
            context.replaceContextMenu((menu) -> this.build(menu, pick));
        }
    }

    public void build(ContextMenuManager menu, Consumer<T> pick)
    {
        this.build(menu, null, pick);
    }

    /** @param keysCategory the category the auto-keys are registered under, or null for none. */
    public void build(ContextMenuManager menu, IKey keysCategory, Consumer<T> pick)
    {
        if (keysCategory == null)
        {
            menu.autoKeys();
        }
        else
        {
            menu.autoKeys(keysCategory);
        }

        for (T option : this.options)
        {
            if (this.available != null && !this.available.test(option))
            {
                menu.action(this.icon.apply(option), this.unavailableLabel.apply(option), Colors.GRAY & Colors.RGB, () -> {});

                continue;
            }

            Icon icon = this.icon.apply(option);
            IKey label = this.label.apply(option);
            Runnable run = () -> pick.accept(option);

            /* The highlight colour of an active row is the menu's own business, so this asks
             * for it by flag rather than naming it a second time. */
            if (option.equals(this.current))
            {
                menu.action(icon, label, true, run);
            }
            else
            {
                menu.action(icon, label, this.color == null ? 0 : this.color.apply(option), run);
            }
        }
    }
}
