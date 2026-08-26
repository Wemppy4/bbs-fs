package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.context.ContextAction;

/**
 * A context menu: an optional {@link UIContextMenuBar bar of verbs} along the top, and the list
 * of labelled actions below it.
 *
 * <p>The bar belongs to every menu and shows up only once something is put into it, so a menu
 * that wants a plus does not have to be a special kind of menu to get one.</p>
 */
public class UISimpleContextMenu extends UIContextMenu
{
    /** No menu is narrower than this, however short its labels are. */
    public static final int MIN_WIDTH = 100;

    public UIList<ContextAction> actions;
    public UIContextMenuBar bar;

    private ContextAction action;

    public UISimpleContextMenu()
    {
        super();

        this.bar = new UIContextMenuBar(this::removeFromParent);
        this.bar.relative(this).w(1F).h(UIContextMenuBar.HEIGHT);

        this.actions = new UIActionList((action) ->
        {
            if (action.get(0).runnable != null)
            {
                this.action = action.get(0);
            }
        });

        this.actions.cancelScrollEdge().full(this);
        this.add(this.bar, this.actions);
    }

    @Override
    public boolean isEmpty()
    {
        return this.actions.getList().isEmpty() && this.bar.isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.bar.sync(!this.actions.getList().isEmpty());

        int top = this.bar.isVisible() ? this.bar.getTotalHeight() : 0;
        int w = Math.max(MIN_WIDTH, this.bar.getContentWidth());

        for (ContextAction action : this.actions.getList())
        {
            w = Math.max(action.getWidth(context.batcher.getFont()), w);
        }

        this.actions.y(top).h(1F, -top);

        this.set(context.mouseX(), context.mouseY(), w, 0).h(this.actions.scroll.scrollSize + top).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.action != null)
        {
            this.action.runnable.run();
            this.removeFromParent();

            return true;
        }

        return super.subMouseReleased(context);
    }

    public void pick(int index)
    {
        this.actions.setIndex(index);

        ContextAction action = this.actions.getCurrentFirst();

        if (action != null && action.runnable != null)
        {
            action.runnable.run();
            this.removeFromParent();
        }
    }
}
