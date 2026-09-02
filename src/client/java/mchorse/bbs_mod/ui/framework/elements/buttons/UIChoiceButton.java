package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A button that shows which option is active and opens the list to change it: the trigger
 * half of {@link UIChoiceMenu}, for choices that are visible all the time rather than hidden
 * behind a hotkey.
 *
 * <p>The label is the active option's name and its icon sits on the left, in plain white —
 * the colour cue, where a set has one, lives in the dropdown, not on the trigger.
 *
 * <p>Where a choice needs no permanent trigger, a plain {@link mchorse.bbs_mod.ui.framework.elements.UIIcon}
 * over {@code UIChoiceMenu.open} is the smaller half of the same pattern (the preview's
 * camera mode does that).
 *
 * @param <T> the option type — an enum, usually.
 */
public class UIChoiceButton <T> extends UIButton
{
    private final UIChoiceMenu<T> menu;
    private final Function<T, Icon> icon;
    private final Function<T, IKey> labels;

    private T value;
    private Consumer<T> callback;

    public UIChoiceButton(Iterable<T> options, Function<T, Icon> icon, Function<T, IKey> label)
    {
        super(IKey.EMPTY, (b) -> ((UIChoiceButton<?>) b).open());

        this.icon = icon;
        this.labels = label;
        this.menu = UIChoiceMenu.of(options).icon(icon).label(label);
    }

    /** @see UIChoiceMenu#unavailable */
    public UIChoiceButton<T> unavailable(Predicate<T> available, Function<T, IKey> label)
    {
        this.menu.unavailable(available, label);

        return this;
    }

    /** @see UIChoiceMenu#color */
    public UIChoiceButton<T> color(Function<T, Integer> color)
    {
        this.menu.color(color);

        return this;
    }

    /** What to do with a picked option, beyond becoming the button's value. */
    public UIChoiceButton<T> callback(Consumer<T> callback)
    {
        this.callback = callback;

        return this;
    }

    public T getValue()
    {
        return this.value;
    }

    /** Sets the active option without running the callback — for the initial value. */
    public UIChoiceButton<T> setValue(T value)
    {
        this.value = value;

        if (value != null)
        {
            this.label = this.labels.apply(value);
        }

        return this;
    }

    public void open()
    {
        this.menu.current(this.value).open(this.getContext(), this::pick);
    }

    private void pick(T value)
    {
        this.setValue(value);

        if (this.callback != null)
        {
            this.callback.accept(value);
        }
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        super.renderSkin(context);

        if (this.value != null)
        {
            context.batcher.icon(this.icon.apply(this.value), Colors.WHITE, this.area.x + 4, this.area.my(), 0F, 0.5F);
        }
    }
}
