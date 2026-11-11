package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItems;
import mchorse.bbs_mod.ui.utils.Scroll;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Where every scroll view and list under an element stands, to put back after the element's
 * contents are rebuilt for the same thing &mdash; undo refills editors this way, and without it each
 * Ctrl+Z threw their lists and options to the top.
 *
 * <p>Scrolls are matched by their place in the tree, so they are only put back when the rebuilt
 * tree has as many as before. Apply it after a resize, or the scrolls clamp against empty areas.</p>
 */
public class ScrollSnapshot
{
    private final List<Double> scrolls = new ArrayList<>();

    public static ScrollSnapshot of(UIElement element)
    {
        ScrollSnapshot snapshot = new ScrollSnapshot();

        visit(element, (scroll) -> snapshot.scrolls.add(scroll.getScroll()));

        return snapshot;
    }

    private static void visit(UIElement element, Consumer<Scroll> consumer)
    {
        element.visitChildren(UIElement.class, true, (child) ->
        {
            if (child instanceof UIScrollView view) consumer.accept(view.scroll);
            else if (child instanceof UIItems<?> items) consumer.accept(items.scroll);
        });
    }

    public void apply(UIElement element)
    {
        List<Scroll> fresh = new ArrayList<>();

        visit(element, fresh::add);

        if (fresh.size() != this.scrolls.size())
        {
            return;
        }

        for (int i = 0; i < fresh.size(); i++)
        {
            fresh.get(i).setScroll(this.scrolls.get(i));
        }
    }
}
