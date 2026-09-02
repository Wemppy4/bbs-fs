package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;

/**
 * A context menu: an optional {@link UIContextMenuBar bar of verbs} along the top, an optional
 * filter under it, and the list of labelled actions below.
 *
 * <p>The bar belongs to every menu and shows up only once something is put into it, so a menu
 * that wants a plus does not have to be a special kind of menu to get one.</p>
 */
public class UISimpleContextMenu extends UIContextMenu
{
    /** No menu is narrower than this, however short its labels are. */
    public static final int MIN_WIDTH = 100;

    /** From this many rows on, the list gets a filter: reading it through stops being the quick way. */
    public static final int FILTER_THRESHOLD = 12;
    public static final int FILTER_HEIGHT = 20;

    public UIList<ContextAction> actions;
    public UIContextMenuBar bar;
    public UITextbox filter;

    private ContextAction action;

    /** Whether the filter may take the keyboard — see {@link #canFocusFilter}. */
    private boolean focusFilter = true;
    private boolean shown;

    public UISimpleContextMenu()
    {
        super();

        this.bar = new UIContextMenuBar(this::dismiss);
        this.bar.relative(this).w(1F).h(UIContextMenuBar.HEIGHT);

        this.filter = new UITextbox(100, (text) -> this.actions.filter(text));
        this.filter.placeholder(UIKeys.GENERAL_SEARCH);
        this.filter.relative(this).w(1F).h(FILTER_HEIGHT);
        this.filter.setVisible(false);

        this.actions = new UIActionList((action) ->
        {
            if (action.get(0).runnable != null)
            {
                this.action = action.get(0);
            }
        });

        this.actions.cancelScrollEdge().full(this);
        this.add(this.bar, this.filter, this.actions);
    }

    /**
     * Whether the filter, when there is one, may be focused the moment the menu opens.
     *
     * <p>It may not when the rows carry shortcuts of their own — the auto-assigned numbers, or
     * the letters the interpolations are bound to. A focused field swallows every key, and
     * taking away a shortcut that already works is a worse trade than one more click.</p>
     */
    public void canFocusFilter(boolean focusFilter)
    {
        this.focusFilter = focusFilter;
    }

    @Override
    public boolean isEmpty()
    {
        return this.actions.getList().isEmpty() && this.bar.isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        if (context.canGoBack())
        {
            this.bar.register(new MenuIcon(MenuVerb.BACK, context::backContextMenu));
        }

        boolean hasActions = !this.actions.getList().isEmpty();
        boolean filtering = this.actions.getList().size() > FILTER_THRESHOLD;

        this.bar.sync(hasActions);

        int top = this.bar.isVisible() ? this.bar.getTotalHeight() : 0;

        /* A menu that is nothing but its bar is exactly as wide as the bar — the buttons fill
         * it edge to edge instead of huddling in a corner of a box sized for labels. */
        int w = hasActions ? MIN_WIDTH : this.bar.getContentWidth();

        for (ContextAction action : this.actions.getList())
        {
            w = Math.max(action.getWidth(context.batcher.getFont()), w);
        }

        w = Math.max(w, this.bar.getContentWidth());

        this.filter.setVisible(filtering);
        this.filter.y(top);

        if (filtering)
        {
            top += FILTER_HEIGHT;
        }

        this.actions.y(top).h(1F, -top);

        this.set(context.mouseX(), context.mouseY(), w, 0).h(this.actions.scroll.scrollSize + top).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
    }

    @Override
    public void render(UIContext context)
    {
        /* Focusing belongs to the first frame: until then the menu is not in the tree yet */
        if (!this.shown)
        {
            this.shown = true;

            if (this.focusFilter && this.filter.isVisible())
            {
                context.focus(this.filter);
            }
        }

        super.render(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.action != null)
        {
            /* Let go of it before running: the action may open the next menu and leave this one
             * parked, and a step back would then hand it the release of a press it never saw —
             * which is this same click, and it would run all over again. */
            ContextAction action = this.action;

            this.action = null;

            action.runnable.run();
            this.dismiss();

            return true;
        }

        return super.subMouseReleased(context);
    }

    public void pick(int index)
    {
        this.actions.setIndex(index);

        ContextAction action = this.actions.getCurrentFirst();

        this.action = null;

        if (action != null && action.runnable != null)
        {
            action.runnable.run();
            this.dismiss();
        }
    }
}
