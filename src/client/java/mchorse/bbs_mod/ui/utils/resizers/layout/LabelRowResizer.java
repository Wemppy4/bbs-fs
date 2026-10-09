package mchorse.bbs_mod.ui.utils.resizers.layout;

import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.resizers.AutomaticResizer;
import mchorse.bbs_mod.ui.utils.resizers.ChildResizer;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;

/**
 * Lays out a "name : control" row (see {@link mchorse.bbs_mod.ui.utils.UI#labelRow}): the name
 * fills the left, the control keeps a fixed width on the right. Once the row is too narrow for
 * the name to read, a stacking row moves the name on its own line and gives the control the
 * whole width.
 *
 * <p>The threshold is the row's width rather than the name's length, so every row of a column
 * switches at once instead of some stacking and some not. The row stacks only when its parent
 * gave it the height for two lines, i.e. asked {@link #getH(int)} with the width &mdash; inside a
 * layout that doesn't (a row, a grid) it stays on one line.</p>
 */
public class LabelRowResizer extends AutomaticResizer
{
    /** Least room the name keeps beside the control before the row stacks */
    public static final int MIN_LABEL_WIDTH = 60;

    private final int controlWidth;
    private final boolean stack;

    public static LabelRowResizer apply(UIElement element, int controlWidth, boolean stack)
    {
        LabelRowResizer resizer = new LabelRowResizer(element, controlWidth, stack);

        element.post(resizer);

        return resizer;
    }

    protected LabelRowResizer(UIElement parent, int controlWidth, boolean stack)
    {
        super(parent, UIConstants.MARGIN);

        this.controlWidth = controlWidth;
        this.stack = stack;
    }

    private boolean isNarrow(int w)
    {
        return this.stack && w > 0 && w - this.controlWidth - this.margin < MIN_LABEL_WIDTH;
    }

    private int labelH()
    {
        return Batcher2D.getDefaultTextRenderer().getHeight();
    }

    private int controlH()
    {
        UIElement control = this.child(1);
        int h = control == null ? 0 : control.getFlex().getH();

        return h > 0 ? h : UIConstants.CONTROL_HEIGHT;
    }

    private UIElement child(int index)
    {
        int i = 0;

        for (IUIElement element : this.parent.getChildren())
        {
            if (element instanceof UIElement)
            {
                if (i == index)
                {
                    return (UIElement) element;
                }

                i ++;
            }
        }

        return null;
    }

    private int stackedH()
    {
        return this.labelH() + this.margin + this.controlH();
    }

    private int inlineH()
    {
        int h = 0;

        for (ChildResizer child : this.getResizers())
        {
            if (child.element.isVisible())
            {
                h = Math.max(h, child.resizer == null ? 0 : child.resizer.getH());
            }
        }

        return h > 0 ? h : UIConstants.CONTROL_HEIGHT;
    }

    @Override
    public void apply(Area area, IResizer resizer, ChildResizer child)
    {
        Area row = this.parent.area;

        if (!child.element.isVisible())
        {
            area.set(row.x, row.y, 0, 0);

            return;
        }

        boolean label = child.element == this.child(0);

        if (this.isNarrow(row.w) && row.h >= this.stackedH())
        {
            if (label)
            {
                area.set(row.x, row.y, row.w, this.labelH());
            }
            else
            {
                int h = this.controlH();

                area.set(row.x, row.ey() - h, row.w, h);
            }

            return;
        }

        int h = resizer == null ? 0 : resizer.getH();

        h = h > 0 ? h : row.h;

        if (label)
        {
            area.set(row.x, row.y, row.w - this.controlWidth - this.margin, h);
        }
        else
        {
            area.set(row.ex() - this.controlWidth, row.y, this.controlWidth, h);
        }
    }

    @Override
    public int getH()
    {
        return this.inlineH();
    }

    @Override
    public int getH(int w)
    {
        return this.isNarrow(w) ? this.stackedH() : this.inlineH();
    }
}
