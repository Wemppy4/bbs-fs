package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * A strip of tabs: a scrollable row (or column) of tab elements with the active one marked,
 * an optional hover box, an optional label card for icon-only tabs, and hooks for what the
 * owner does when a tab is selected, closed or reordered.
 *
 * <p>The strip does not own the model. Owners rebuild the tabs from their own list
 * ({@link #removeAll()}, then {@link #addTab} per tab) and answer {@link #active} on demand,
 * so the tab bars of the UI (panel switchers, editor documents, dock stacks) share the looks
 * and the input plumbing without sharing a data structure.</p>
 */
public class UITabStrip extends UIScrollView
{
    private final List<UIElement> tabs = new ArrayList<>();

    private IntSupplier active = () -> -1;
    private IntSupplier activeColor = () -> Colors.A75 | BBSSettings.primaryColor.get();
    private Direction activeEdge = Direction.BOTTOM;
    private IntSupplier hoverColor;
    private IntSupplier background;
    private IntFunction<IKey> hoverLabels;
    private boolean fixed;

    private IntConsumer onSelect;
    private IntConsumer onClose;
    private BiConsumer<Integer, Integer> onReorder;

    public UITabStrip(ScrollDirection direction)
    {
        super(direction);

        this.scroll.noScrollbar();
        this.direction(direction);
    }

    /** Lay the tabs along {@code direction}; the scroll axis follows. */
    public UITabStrip direction(ScrollDirection direction)
    {
        this.scroll.direction = direction;

        if (direction == ScrollDirection.VERTICAL)
        {
            this.column(0).scroll().vertical();
        }
        else
        {
            this.row(0).scroll();
        }

        return this;
    }

    /** Index of the active tab, asked at render time so the owner's state stays the truth. */
    public UITabStrip active(IntSupplier active)
    {
        this.active = active;

        return this;
    }

    /**
     * Fill behind the active tab instead of marking it with the highlight bar — asking for a
     * fill is what turns the bar off, so a tab that wants to melt into the content below it
     * (a document tab) says so once, and every other strip keeps the mark the rest of the UI uses.
     */
    public UITabStrip activeColor(IntSupplier color)
    {
        this.activeColor = color;
        this.activeEdge = null;

        return this;
    }

    /** Move the highlight bar to {@code edge} — the side of the tab that faces the content. */
    public UITabStrip activeEdge(Direction edge)
    {
        this.activeEdge = edge;

        return this;
    }

    /** Fill behind the hovered tab; none by default, since icon tabs hover by tinting themselves. */
    public UITabStrip hoverColor(IntSupplier color)
    {
        this.hoverColor = color;

        return this;
    }

    public UITabStrip background(IntSupplier color)
    {
        this.background = color;

        return this;
    }

    /** Label per tab for the card shown next to the cursor while hovering icon-only tabs. */
    public UITabStrip hoverLabels(IntFunction<IKey> labels)
    {
        this.hoverLabels = labels;

        return this;
    }

    /** Never scroll: tabs that don't fit are simply cut off at the edge. */
    public UITabStrip fixed()
    {
        this.fixed = true;

        return this;
    }

    public UITabStrip onSelect(IntConsumer callback)
    {
        this.onSelect = callback;

        return this;
    }

    public UITabStrip onClose(IntConsumer callback)
    {
        this.onClose = callback;

        return this;
    }

    /** Called with (from, to); the owner reorders its model and rebuilds the strip. */
    public UITabStrip onReorder(BiConsumer<Integer, Integer> callback)
    {
        this.onReorder = callback;

        return this;
    }

    /* Tabs */

    public <T extends UIElement> T addTab(T tab)
    {
        this.tabs.add(tab);
        this.add(tab);

        return tab;
    }

    public UIElement getTab(int index)
    {
        return index >= 0 && index < this.tabs.size() ? this.tabs.get(index) : null;
    }

    public int getTabCount()
    {
        return this.tabs.size();
    }

    public int indexOf(UIElement tab)
    {
        return this.tabs.indexOf(tab);
    }

    @Override
    public void removeAll()
    {
        super.removeAll();

        this.tabs.clear();
    }

    /**
     * Index of the tab under the cursor in screen coordinates (the scroll offset is taken
     * into account), or -1. Usable from outside the strip, e.g. by a drag that targets a tab.
     */
    public int getTabIndex(int mouseX, int mouseY)
    {
        if (!this.area.isInside(mouseX, mouseY))
        {
            return -1;
        }

        boolean vertical = this.scroll.direction == ScrollDirection.VERTICAL;
        int shift = (int) this.scroll.getScroll();
        int x = mouseX + (vertical ? 0 : shift);
        int y = mouseY + (vertical ? shift : 0);

        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (this.tabs.get(i).area.isInside(x, y))
            {
                return i;
            }
        }

        return -1;
    }

    public void select(int index)
    {
        if (this.onSelect != null && index >= 0 && index < this.tabs.size())
        {
            this.onSelect.accept(index);
        }
    }

    public void select(UIElement tab)
    {
        this.select(this.tabs.indexOf(tab));
    }

    public void close(int index)
    {
        if (this.onClose != null && index >= 0 && index < this.tabs.size())
        {
            this.onClose.accept(index);
        }
    }

    public void reorder(int from, int to)
    {
        if (this.onReorder != null && from != to && this.getTab(from) != null && this.getTab(to) != null)
        {
            this.onReorder.accept(from, to);
        }
    }

    /* Input */

    /**
     * Runs before the pressed tab itself hears the click; return true to claim the press.
     * The dock uses it to arm a drag that may or may not turn into a tab activation.
     */
    protected boolean pressTab(int index, UIContext context)
    {
        return false;
    }

    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            int index = this.getTabIndex(context.mouseX, context.mouseY);

            if (index >= 0 && this.pressTab(index, context))
            {
                return this;
            }
        }

        return super.childrenMouseClicked(context);
    }

    @Override
    public void resize()
    {
        super.resize();

        /* No scroll size means the wheel has nothing to move and no edge shadows get drawn */
        if (this.fixed)
        {
            this.scroll.scrollSize = 0;
            this.scroll.clamp();
        }
    }

    /* Rendering */

    /**
     * Mark where an external drag would insert: right after tab {@code index}, clamped to
     * the strip. Call it from outside the strip's clip so the caret stays visible at the edge.
     */
    public void renderInsertionCaret(UIContext context, int index)
    {
        UIElement tab = this.getTab(index);

        if (tab == null)
        {
            return;
        }

        int shift = (int) this.scroll.getScroll();
        int color = BBSSettings.primaryColor(Colors.A100);

        if (this.scroll.direction == ScrollDirection.VERTICAL)
        {
            int y = Math.min(tab.area.ey() - shift, this.area.ey() - 1);

            context.batcher.box(this.area.x, y - 1, this.area.ex(), y + 1, color);
        }
        else
        {
            int x = Math.min(tab.area.ex() - shift, this.area.ex() - 1);

            context.batcher.box(x - 1, this.area.y, x + 1, this.area.ey(), color);
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.background != null)
        {
            this.area.render(context.batcher, this.background.getAsInt());
        }

        super.render(context);

        this.renderHoverCard(context);
    }

    @Override
    protected void preRender(UIContext context)
    {
        int active = this.active.getAsInt();

        for (int i = 0; i < this.tabs.size(); i++)
        {
            UIElement tab = this.tabs.get(i);

            if (i == active)
            {
                if (this.activeEdge != null)
                {
                    context.batcher.highlight(tab.area, this.activeEdge);
                }
                else
                {
                    tab.area.render(context.batcher, this.activeColor.getAsInt());
                }
            }
            else if (this.hoverColor != null && tab.area.isInside(context))
            {
                tab.area.render(context.batcher, this.hoverColor.getAsInt());
            }
        }

        super.preRender(context);
    }

    /** Whether the label card may show right now; a drag in flight, say, hides it. */
    protected boolean canShowHoverCard()
    {
        return true;
    }

    private void renderHoverCard(UIContext context)
    {
        if (this.hoverLabels == null || !this.canShowHoverCard())
        {
            return;
        }

        int hovered = this.getTabIndex(context.mouseX, context.mouseY);

        if (hovered < 0)
        {
            return;
        }

        String label = this.hoverLabels.apply(hovered).get();

        if (!label.isEmpty())
        {
            /* Above the strip, or below it when the strip sits at the top of the screen */
            int ty = this.area.y - 14;

            context.batcher.textCard(label, context.mouseX + 6, ty < 2 ? this.area.ey() + 4 : ty);
        }
    }
}
