package mchorse.bbs_mod.ui.utils.resizers.layout;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.resizers.AutomaticResizer;
import mchorse.bbs_mod.ui.utils.resizers.ChildResizer;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;

public class RowResizer extends AutomaticResizer
{
    private int i;
    private int x;
    private int w;

    /**
     * Visible children without an explicit width, i.e. the ones that share the leftover
     */
    private int count;

    /**
     * Visible children in this pass. Counted once in {@link #apply(Area)}: rebuilding the
     * child list per child made a row of N elements cost N² lookups
     */
    private int visible;

    /**
     * Preferred element to use in the row for the width adjustments caused by
     * integer arithmetics, -1 = to the size() / 2
     */
    private int preferred = -1;

    /**
     * Default width for row elements if not specified by resizer
     */
    private int width;

    /**
     * Whether the area should be resized according to the sum or row elements
     */
    private boolean resize;

    /**
     * Whether the elements would be placed from right to left
     */
    private boolean reverse;

    /**
     * Scroll mode, this will automatically calculate the scroll area
     */
    private boolean scroll;

    public static RowResizer apply(UIElement element, int margin)
    {
        RowResizer resizer = new RowResizer(element, margin);

        element.post(resizer);

        return resizer;
    }

    protected RowResizer(UIElement parent, int margin)
    {
        super(parent, margin);
    }

    public RowResizer preferred(int index)
    {
        this.preferred = index;

        return this;
    }

    public RowResizer width(int width)
    {
        this.width = width;

        return this;
    }

    public RowResizer resize()
    {
        this.resize = true;

        return this;
    }

    public RowResizer reverse()
    {
        this.reverse = true;

        return this;
    }

    public RowResizer scroll()
    {
        this.scroll = true;

        return this;
    }

    @Override
    public void apply(Area area)
    {
        this.i = this.x = this.w = 0;
        this.visible = 0;
        this.count = 0;

        for (ChildResizer resizer : this.getResizers())
        {
            if (!resizer.element.isVisible())
            {
                continue;
            }

            int w = Math.max(resizer.resizer == null ? 0 : resizer.resizer.getW(), 0);

            this.visible ++;

            if (w > 0)
            {
                this.w += w;
            }
            else
            {
                this.count ++;
            }
        }
    }

    @Override
    public void apply(Area area, IResizer resizer, ChildResizer child)
    {
        /* Same as in the column: a hidden child keeps no slot in the row */
        if (!child.element.isVisible())
        {
            area.set(this.parent.area.x, this.parent.area.y, 0, 0);

            return;
        }

        int c = this.visible;
        int original = this.parent.area.w - this.padding * 2 - this.margin * (c - 1);
        int w = this.count > 0 ? (original - this.w) / this.count : 0;
        int x = this.parent.area.x + this.padding + this.x + child.element.margin.left;

        /* If it's reverse, start adding from the right side */
        if (this.reverse)
        {
            x = this.parent.area.ex() - this.padding - this.x - child.element.margin.right;
        }

        /* If resizer specifies its custom width, use that one instead */
        int cw = resizer == null ? 0 : resizer.getW();
        int ch = resizer == null ? this.height : resizer.getH();

        if (this.width > 0)
        {
            cw = this.width;
        }

        cw = cw > 0 ? cw : w;

        /* Readjust the middle element width to balance out int imprecision */
        int preferred = this.preferred == -1 ? c / 2 : this.preferred;

        /* Only when something actually shared the leftover; a row of fixed-width icons in a
         * wider strip must not have its middle icon stretched to fill the gap */
        if (this.i == preferred && this.count > 0 && !this.resize && this.width <= 0)
        {
            int diff = original - this.w - w * this.count;

            if (diff > 0)
            {
                cw += diff;
            }
        }

        /* Subtract the width from the X position */
        if (this.reverse)
        {
            x -= cw;
        }

        area.set(x, this.parent.area.y + this.padding + child.element.margin.top, cw, ch > 0 ? ch : this.parent.area.h - this.padding * 2);

        this.x += cw + this.margin + child.element.margin.horizontal();
        this.i ++;
    }

    @Override
    public void postApply(Area area)
    {
        if (this.scroll && this.parent.area.scroll != null)
        {
            Scroll scroll = this.parent.area.scroll;

            if (scroll.direction == ScrollDirection.HORIZONTAL)
            {
                scroll.scrollSize = this.x - this.margin + this.padding * 2;
            }

            scroll.clamp();
        }
    }

    @Override
    public int getW()
    {
        if (this.resize)
        {
            int w = 0;
            boolean any = false;

            for (ChildResizer resizer : this.getResizers())
            {
                if (!resizer.element.isVisible())
                {
                    continue;
                }

                any = true;

                int cw = resizer.resizer == null ? 0 : resizer.resizer.getW();

                if (cw == 0 && this.width > 0)
                {
                    cw = this.width;
                }

                w += Math.max(cw, 0) + this.margin + resizer.element.margin.horizontal();
            }

            return (any ? w - this.margin : 0) + this.padding * 2;
        }

        return 0;
    }

    @Override
    public int getH()
    {
        int h = 0;

        for (ChildResizer child : this.getResizers())
        {
            if (!child.element.isVisible())
            {
                continue;
            }

            h = Math.max(h, child.resizer == null ? 0 : child.resizer.getH() + child.element.margin.vertical());
        }

        if (h == 0)
        {
            h = this.height;
        }

        return h + this.padding * 2;
    }
}