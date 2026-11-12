package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.context.ContextAction;

import java.util.List;
import java.util.function.Consumer;

public class UIActionList extends UIList<ContextAction>
{
    public UIActionList(Consumer<List<ContextAction>> callback)
    {
        super(callback);
    }

    /** A menu row is cut to its icon and its label; there is nothing in it to make room for. */
    @Override
    protected boolean canScaleRows()
    {
        return false;
    }

    /** A right click on a row that has a menu of its own opens that menu, with a step back to this one. */
    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 1 && this.area.isInside(context))
        {
            int index = this.indexAt(this.contentX(context), this.contentY(context));

            if (index >= 0 && index < this.visible().size())
            {
                ContextAction action = this.visible().get(index);
                UIContextMenu menu = action.submenu == null ? null : action.submenu.get();

                if (menu != null && !menu.isEmpty())
                {
                    context.replaceContextMenu(menu);
                }
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void renderListElement(UIContext context, ContextAction element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;

        element.render(context, context.batcher.getFont(), x, y, this.area.w, h, hover, selected);

        if (element.separated && i > 0)
        {
            UIContextMenuBar.divider(context.batcher, x, y, this.area.w);
        }
    }

    /** The top row still showing, taking the filter into account, or null when none is. */
    public ContextAction first()
    {
        return this.getElementAt(0);
    }

    /** What the filter matches against: the row reads as its label, not as an object. */
    @Override
    protected String elementToString(UIContext context, int i, ContextAction element)
    {
        return element.label.get();
    }
}
