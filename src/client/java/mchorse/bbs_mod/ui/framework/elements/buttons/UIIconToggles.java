package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link UIIcons} with every cell switching on its own: the same icon strip, but the items are
 * independent toggles rather than one choice out of N. Use it where a set of things is turned on
 * and off together - a strip of five checkboxes read as one question instead of five rows.
 *
 * <p>The callback fires once per flip, with {@link #getLastToggled()} naming the cell that moved.
 */
public class UIIconToggles extends UIIconStrip<UIIconToggles>
{
    protected final List<Boolean> values = new ArrayList<>();

    /** The cell the last click flipped, {@code -1} until one is clicked. */
    protected int last = -1;

    public UIIconToggles(Consumer<UIIconToggles> callback)
    {
        super(callback);
    }

    public UIIconToggles add(Icon icon, IKey tooltip, boolean value)
    {
        this.addItem(icon, tooltip);
        this.values.add(value);

        return this;
    }

    public boolean getValue(int index)
    {
        return this.has(index) && this.values.get(index);
    }

    public void setValue(int index, boolean value)
    {
        if (this.has(index))
        {
            this.values.set(index, value);
        }
    }

    public int getLastToggled()
    {
        return this.last;
    }

    private boolean has(int index)
    {
        return index >= 0 && index < this.values.size();
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
        this.values.set(index, !this.values.get(index));
        this.last = index;

        return true;
    }
}
