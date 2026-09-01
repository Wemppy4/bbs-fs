package mchorse.bbs_mod.ui.utils.resizers.layout;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.resizers.AutomaticResizer;
import mchorse.bbs_mod.ui.utils.resizers.ChildResizer;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import mchorse.bbs_mod.ui.utils.resizers.Margin;

public class ColumnResizer extends AutomaticResizer
{
    private int x;
    private int y;
    private int w;

    /**
     * Visible children marked with {@link UIElement#expand()} in the current pass
     */
    private int expanders;

    /**
     * Height each of them gets on top of the one it asked for, and the remainder of that
     * division, handed out a pixel at a time so the column fills exactly. Negative until the
     * first expanding child asks for it, see {@link #expand()}
     */
    private int share = -1;
    private int remainder;

    /**
     * Default width
     */
    private int width;

    /**
     * Keeps on adding elements vertically without shifting them into
     * the next row and resize the height of the element
     */
    private boolean vertical;

    /**
     * Stretch column to the full width of the parent element
     */
    private boolean stretch;

    /**
     * Scroll mode, this will automatically calculate the scroll area
     */
    private boolean scroll;

    /**
     * Place elements after it reached the bottom on the left, instead of the right
     */
    private boolean flip;

    public static ColumnResizer apply(UIElement element, int margin)
    {
        ColumnResizer resizer = new ColumnResizer(element, margin);

        element.post(resizer);

        return resizer;
    }

    protected ColumnResizer(UIElement element, int margin)
    {
        super(element, margin);
    }

    public ColumnResizer width(int width)
    {
        this.width = width;

        return this;
    }

    public ColumnResizer vertical()
    {
        return this.vertical(true);
    }

    public ColumnResizer vertical(boolean vertical)
    {
        this.vertical = vertical;

        return this;
    }

    public ColumnResizer stretch()
    {
        this.stretch = true;

        return this;
    }

    public ColumnResizer scroll()
    {
        this.scroll = true;

        return this;
    }

    public ColumnResizer flip()
    {
        this.flip = true;

        return this;
    }

    @Override
    public void apply(Area area)
    {
        this.x = 0;
        this.y = 0;
        this.w = 0;

        this.share = -1;
        this.remainder = 0;
        this.expanders = 0;

        for (ChildResizer child : this.getResizers())
        {
            if (child.element.isVisible() && child.element.isExpanding())
            {
                this.expanders ++;
            }
        }
    }

    @Override
    public void apply(Area area, IResizer resizer, ChildResizer child)
    {
        /* A child hidden with setVisible(false) must not keep its slot — it used to leave a
         * blank row (the "Tracks" section of a non-model form showed a 20px hole where the
         * bone-tracks toggle would be). */
        if (!child.element.isVisible())
        {
            area.set(this.parent.area.x, this.parent.area.y, 0, 0);

            return;
        }

        Margin margin = child.element.margin;
        int w = resizer == null ? this.width : resizer.getW();
        int h = resizer == null ? this.height : resizer.getH();

        if (w == 0)
        {
            w = this.width;
        }

        if (h == 0)
        {
            h = this.height;
        }

        /* The height the child asked for is its minimum; the leftover of the column is added on
         * top of it. Only in vertical mode: a wrapping column has no single leftover to share. */
        if (this.vertical && child.element.isExpanding())
        {
            h += this.expand();
        }

        if (this.stretch)
        {
            w = this.parent.area.w - this.padding * 2;
        }

        int marginTop = margin.top;

        if (!this.vertical && this.y + h + marginTop > this.parent.area.h - this.padding * 2)
        {
            this.x += (this.w + this.padding) * (this.flip ? -1 : 1);
            this.y = this.w = 0;

            marginTop = 0;
        }

        int x = this.parent.area.x + this.x + this.padding + margin.left;
        int y = this.parent.area.y + this.y + this.padding + marginTop;

        area.set(x, y, w, h);

        this.w = Math.max(this.w, w + margin.horizontal());
        this.y += h + this.margin + marginTop + margin.bottom;
    }

    @Override
    public void postApply(Area area)
    {
        if (this.scroll && this.parent.area.scroll != null)
        {
            Scroll scroll = this.parent.area.scroll;

            if (this.vertical && scroll.direction == ScrollDirection.VERTICAL)
            {
                scroll.scrollSize = this.y - this.margin + this.padding * 2;
            }
            else if (!this.vertical && scroll.direction == ScrollDirection.HORIZONTAL)
            {
                scroll.scrollSize = this.x + this.w + this.padding * 2;
            }

            scroll.clamp();
        }
    }

    /**
     * Height an expanding child gets on top of the one it asked for: an equal share of what this
     * column has left over after the others.
     *
     * <p>Measured on the first such child rather than in {@link #apply(Area)}, because a column
     * that is itself laid out by another one only learns its own height when that parent places
     * it &mdash; which happens after its own {@code apply(Area)} has already run. By the time a
     * child is being placed, {@code this.parent.area} is this pass' area.</p>
     */
    private int expand()
    {
        if (this.share < 0)
        {
            int extra = this.parent.area.h - this.contentH();

            this.share = extra > 0 ? extra / this.expanders : 0;
            this.remainder = extra > 0 ? extra % this.expanders : 0;
        }

        int share = this.share;

        if (this.remainder > 0)
        {
            share ++;
            this.remainder --;
        }

        return share;
    }

    /**
     * How tall this column is before anything expands, i.e. the sum of the heights its visible
     * children asked for
     */
    private int contentH()
    {
        int y = this.padding * 2;

        for (ChildResizer child : this.getResizers())
        {
            if (!child.element.isVisible())
            {
                continue;
            }

            int h = child.resizer == null ? 0 : child.resizer.getH();

            y += (h == 0 ? this.height : h) + this.margin + child.element.margin.vertical();
        }

        return y - this.margin;
    }

    @Override
    public int getH()
    {
        if (this.vertical && !this.scroll)
        {
            return this.contentH();
        }

        return super.getH();
    }
}