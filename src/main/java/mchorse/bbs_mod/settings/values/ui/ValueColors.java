package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public class ValueColors extends BaseValue
{
    private List<Color> colors = new ArrayList<>();

    /** Maximum number of stored colors, 0 = unlimited. Recent colors are capped so the
     * palette can't grow unboundedly and shove the picker popup off the screen; favorites
     * stay unlimited. */
    private int limit;

    public ValueColors(String id)
    {
        super(id);
    }

    public ValueColors limit(int limit)
    {
        this.limit = limit;

        return this;
    }

    public List<Color> getCurrentColors()
    {
        return this.colors;
    }

    /** Replace the whole palette, keeping the given order. */
    public void setColors(List<Color> colors)
    {
        this.preNotify();

        this.colors.clear();

        for (Color color : colors)
        {
            this.colors.add(color.copy());
        }

        this.trim();
        this.postNotify();
    }

    /**
     * The newest color goes in front, so the list reads in the order the palette shows it —
     * which is also the order a drag rearranges.
     */
    public void addColor(Color color)
    {
        int i = this.colors.indexOf(color);

        if (i == -1)
        {
            this.preNotify();
            this.colors.add(0, color.copy());
            this.trim();
            this.postNotify();
        }
    }

    /** Drop the oldest entries — the tail, since the newest is put in front. */
    private void trim()
    {
        while (this.limit > 0 && this.colors.size() > this.limit)
        {
            this.colors.remove(this.colors.size() - 1);
        }
    }

    public void remove(int index)
    {
        if (index < 0 || index >= this.colors.size())
        {
            return;
        }

        this.preNotify();
        this.colors.remove(index);
        this.postNotify();
    }

    public void removeAll(Collection<Color> removed)
    {
        this.preNotify();
        this.colors.removeAll(removed);
        this.postNotify();
    }

    /** Take the entries out and put them back before whatever now sits at {@code insertion}. */
    public void reorder(List<Color> moved, int insertion)
    {
        List<Color> tail = new ArrayList<>(this.colors.subList(insertion, this.colors.size()));

        this.preNotify();

        this.colors.removeAll(moved);
        tail.removeAll(moved);

        int at = this.colors.size() - tail.size();

        this.colors.addAll(at, moved);
        this.postNotify();
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (Color color : this.colors)
        {
            list.addInt(color.getARGBColor());
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!BaseType.isList(data))
        {
            return;
        }

        ListType list = (ListType) data;

        for (BaseType color : list)
        {
            if (color.isNumeric())
            {
                this.colors.add(new Color().set(color.asNumeric().intValue()));
            }
        }

        this.trim();
    }

    @Override
    public String toString()
    {
        StringJoiner joiner = new StringJoiner(", ");

        for (Color color : this.colors)
        {
            joiner.add("#" + Integer.toHexString(color.getARGBColor()));
        }

        return joiner.toString();
    }
}