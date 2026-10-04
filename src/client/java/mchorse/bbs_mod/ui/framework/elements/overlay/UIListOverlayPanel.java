package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIListOverlayPanel extends UIOverlayPanel
{
    public Consumer<List<String>> callback;

    public UISearchList<String> list;

    public UIListOverlayPanel(IKey title, Consumer<String> callback)
    {
        this(title, callback, UIStringList::new);
    }

    public UIListOverlayPanel(IKey title, Consumer<String> callback, Function<Consumer<List<String>>, UIStringList> listFactory)
    {
        super(title);

        this.callback((l) ->
        {
            if (callback != null) callback.accept(l.get(0));
        });

        this.list = new UISearchList<>(listFactory.apply((l) ->
        {
            if (this.callback != null)
            {
                this.apply(l);
            }
        }));
        this.list.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12);
        this.list.list.scroll.scrollItemSize = Math.max(20, this.list.list.scroll.scrollItemSize);
        this.list.search.h(22);
        this.list.list.y(28).h(1F, -28);
        this.list.label(UIKeys.GENERAL_SEARCH).keyboardSelection(this::confirm);

        this.content.add(this.list);
    }

    public UIListOverlayPanel callback(Consumer<List<String>> callback)
    {
        this.callback = callback;

        return this;
    }

    private void apply(List<String> values)
    {
        if (!values.isEmpty() && this.callback != null)
        {
            this.close();
            this.callback.accept(values);
        }
    }

    @Override
    public void confirm()
    {
        String selected = this.list.list.getVisibleSelection();

        if (selected != null) this.apply(java.util.Collections.singletonList(selected));
    }

    @Override
    public int getPreferredWidth()
    {
        return 400;
    }

    public UIListOverlayPanel setValue(String value)
    {
        this.list.list.setCurrentScroll(value);

        return this;
    }

    public UIListOverlayPanel addValues(Collection<String> values)
    {
        this.list.list.add(values);

        return this;
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        if (!this.wasResized())
        {
            this.flex.h.set(0.75F, 0);
            this.flex.h.max = 420;
        }

        this.list.list.moveSelection(0);
        this.getContext().focus(this.list.search);
    }
}
