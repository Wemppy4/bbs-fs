package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

/**
 * A small form at the cursor: a title over a column of fields, for a quick edit of whatever was
 * clicked &mdash; a folder's name and colour, the onion skin's frames. It goes away the way any
 * context menu does, on Escape or a click outside, so the fields apply as they are edited rather
 * than waiting for a button.
 *
 * <p>A subclass adds its fields to {@link #column}; the popup is as big as the column.</p>
 */
public class UIPopup extends UIContextMenu
{
    /** The width a popup has unless it holds something that wants more, like a list. */
    public static final int WIDTH = 140;

    public final UIElement column;

    public UIPopup(IKey title)
    {
        this(title, WIDTH);
    }

    /**
     * @param title the line over the fields, or null when the first field already says what the
     *              popup is about (a toggle with the feature's name)
     */
    public UIPopup(IKey title, int width)
    {
        this.column = UI.column(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, UIConstants.CONTROL_HEIGHT);
        this.column.relative(this).w(width);

        if (title != null)
        {
            this.column.add(UI.label(title));
        }

        this.add(this.column);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void removeFromParent()
    {
        /* A colour field's picker hangs next to the popup rather than in it, and would outlive it */
        for (UIColor color : this.getChildren(UIColor.class))
        {
            color.picker.removeFromParent();
        }

        super.removeFromParent();
    }

    @Override
    public void setMouse(UIContext context)
    {
        /* The column has to be laid out once before its height is known */
        this.column.resize();

        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}
