package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.function.Consumer;

/**
 * A horizontal row of selectable icons — an icon-based alternative to {@link UICirculate}. Each item
 * is an icon with its own tooltip; clicking an icon selects it (the active one is highlighted, the
 * rest are dimmed), and the icon currently under the cursor shows its tooltip.
 *
 * <p>Layout, hit-testing and painting live in {@link UIIconStrip}; all this adds is that exactly
 * one item is active at a time.
 */
public class UIIcons extends UIIconStrip<UIIcons>
{
    protected int value;

    public UIIcons(Consumer<UIIcons> callback)
    {
        super(callback);
    }

    public UIIcons add(Icon icon, IKey tooltip)
    {
        this.addItem(icon, tooltip);

        return this;
    }

    public int getValue()
    {
        return this.value;
    }

    public void setValue(int value)
    {
        if (!this.items.isEmpty())
        {
            this.value = Math.max(0, Math.min(value, this.items.size() - 1));
        }
    }

    @Override
    protected UIIcons get()
    {
        return this;
    }

    @Override
    protected boolean isActive(int index)
    {
        return index == this.value;
    }

    @Override
    protected boolean pick(int index)
    {
        if (index == this.value)
        {
            return false;
        }

        this.value = index;

        return true;
    }
}
