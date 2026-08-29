package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link UIIcons} with every cell switching on its own: the same icon strip, but the items are
 * independent toggles rather than one choice out of N. Use it where a set of things is turned on
 * and off together - a strip of five checkboxes read as one question instead of five rows.
 *
 * <p>A cell either keeps its own flag - {@link #add(Icon, IKey, boolean)}, and the callback fires
 * once per flip with {@link #getLastToggled()} naming the cell that moved - or is bound to where
 * the flag actually lives, with {@link #add(Icon, IKey, IKey, Supplier)} for a
 * {@link ValueBoolean} and {@link #add(Icon, IKey, IKey, Supplier, Consumer)} for anything else.
 * A bound strip needs no callback and no filling in: it reads the flags as it draws, so it is
 * never the stale copy of a value the panel has already moved on from, and writing back is the
 * cell's own business.
 *
 * <p>The value arrives as a supplier rather than a reference for the reason a resettable widget's
 * does: panels outlive the object they edit, and a cell that grabbed a value once would keep
 * flipping the form the user has already left.
 */
public class UIIconToggles extends UIIconStrip<UIIconToggles>
{
    protected final List<Toggle> toggles = new ArrayList<>();

    /** The cell the last click flipped, {@code -1} until one is clicked. */
    protected int last = -1;

    public UIIconToggles(Consumer<UIIconToggles> callback)
    {
        super(callback);
    }

    /** A cell holding its own flag; read it back with {@link #getValue(int)}. */
    public UIIconToggles add(Icon icon, IKey tooltip, boolean value)
    {
        Toggle toggle = new Toggle(null);

        toggle.own = value;
        toggle.getter = () -> toggle.own;
        toggle.setter = (v) -> toggle.own = v;

        return this.add(icon, tooltip, toggle);
    }

    /**
     * A cell that names itself: the tooltip is the label with the explanation under it, the shape
     * every strip of toggles ends up building by hand otherwise.
     */
    public UIIconToggles add(Icon icon, IKey label, IKey comment, boolean value)
    {
        return this.add(icon, tooltip(label, comment), value);
    }

    /** A cell bound to a value, named by its label alone. */
    public UIIconToggles add(Icon icon, IKey label, Supplier<ValueBoolean> value)
    {
        return this.add(icon, label, null, value);
    }

    /** A cell bound to a getter and setter, named by its label alone. */
    public UIIconToggles add(Icon icon, IKey label, Supplier<Boolean> getter, Consumer<Boolean> setter)
    {
        return this.add(icon, label, null, getter, setter);
    }

    /** A cell bound to a value, which it reads, writes and (see {@link #resettable()}) resets. */
    public UIIconToggles add(Icon icon, IKey label, IKey comment, Supplier<ValueBoolean> value)
    {
        Toggle toggle = new Toggle(value);

        toggle.getter = () ->
        {
            ValueBoolean current = value.get();

            return current != null && current.get();
        };
        toggle.setter = (v) ->
        {
            ValueBoolean current = value.get();

            if (current != null)
            {
                current.set(v);
            }
        };

        return this.add(icon, tooltip(label, comment), toggle);
    }

    /** A cell bound to a flag that isn't a value - a field, a keyframe's payload, a setting. */
    public UIIconToggles add(Icon icon, IKey label, IKey comment, Supplier<Boolean> getter, Consumer<Boolean> setter)
    {
        Toggle toggle = new Toggle(null);

        toggle.getter = getter;
        toggle.setter = setter;

        return this.add(icon, tooltip(label, comment), toggle);
    }

    private UIIconToggles add(Icon icon, IKey tooltip, Toggle toggle)
    {
        this.addItem(icon, tooltip);
        this.toggles.add(toggle);

        return this;
    }

    /**
     * Right clicking the strip puts its bound values back to what they declare - one verb for the
     * whole strip, because a strip is one question however many cells it has. Offered only while
     * something is off it, and only for cells bound to a value.
     */
    public UIIconToggles resettable()
    {
        this.context((menu) ->
        {
            List<BaseValue> values = this.changedValues();

            if (values.isEmpty())
            {
                return;
            }

            menu.action(Icons.UNDO, UIKeys.VALUE_RESET, () -> values.forEach(BaseValue::reset));
        });

        return this;
    }

    public boolean getValue(int index)
    {
        return this.has(index) && this.toggles.get(index).getter.get();
    }

    public void setValue(int index, boolean value)
    {
        if (this.has(index))
        {
            this.toggles.get(index).setter.accept(value);
        }
    }

    public int getLastToggled()
    {
        return this.last;
    }

    private List<BaseValue> changedValues()
    {
        List<BaseValue> values = new ArrayList<>();

        for (Toggle toggle : this.toggles)
        {
            BaseValue value = toggle.value == null ? null : toggle.value.get();

            if (value != null && !value.isDefault())
            {
                values.add(value);
            }
        }

        return values;
    }

    private boolean has(int index)
    {
        return index >= 0 && index < this.toggles.size();
    }

    private static IKey tooltip(IKey label, IKey comment)
    {
        if (label == null)
        {
            return null;
        }

        return comment == null ? label : IKey.comp(Arrays.asList(label, IKey.constant("\n"), comment));
    }

    @Override
    protected UIIconToggles get()
    {
        return this;
    }

    @Override
    protected boolean isActive(int index)
    {
        return this.getValue(index);
    }

    @Override
    protected boolean pick(int index)
    {
        this.setValue(index, !this.getValue(index));
        this.last = index;

        return true;
    }

    /** Where one cell's flag is read from and written to, and the value behind it when there is one. */
    private static class Toggle
    {
        public final Supplier<? extends BaseValue> value;

        public Supplier<Boolean> getter;
        public Consumer<Boolean> setter;

        /** The flag itself for a cell that keeps its own; unused by the bound ones. */
        public boolean own;

        public Toggle(Supplier<? extends BaseValue> value)
        {
            this.value = value;
        }
    }
}
