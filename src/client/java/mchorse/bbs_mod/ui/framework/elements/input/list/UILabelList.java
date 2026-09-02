package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.utils.NaturalOrderComparator;

import java.util.List;
import java.util.function.Consumer;

public class UILabelList <T> extends UIList<Label<T>>
{
    public UILabelList(Consumer<List<Label<T>>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = 16;
    }

    public void add(IKey title, T value)
    {
        this.add(new Label<>(title, value));
    }

    public void setCurrentValue(T value)
    {
        for (int i = 0; i < this.list.size(); i ++)
        {
            if (this.list.get(i).value.equals(value))
            {
                this.setIndex(i);

                return;
            }
        }
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) -> NaturalOrderComparator.compare(true, a.title.get(), b.title.get()));

        return true;
    }

    @Override
    protected String elementToString(UIContext context, int i, Label<T> element)
    {
        return element.title.get();
    }
}