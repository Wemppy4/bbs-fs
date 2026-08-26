package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;

/**
 * General purpose overlay list editor of generic data
 */
public abstract class UIEditorOverlayPanel <T> extends UIOverlayPanel
{
    public UIList<T> list;
    public UIScrollView editor;

    protected T item;

    public UIEditorOverlayPanel(IKey title)
    {
        super(title);

        this.list = this.createList();
        this.list.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, this::addItem).label(this.getAddLabel());
            menu.icon(MenuVerb.REMOVE, this::removeItem).label(this.getRemoveLabel()).enabled(!this.list.getList().isEmpty());
        });

        this.editor = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

        this.list.relative(this.content).w(120).h(1F);
        this.editor.relative(this.content).x(120).w(1F, -120).h(1F);

        this.content.add(this.editor, this.list);
        this.content.x(6).y(26).w(1F, -32);
    }

    protected abstract UIList<T> createList();

    protected IKey getAddLabel()
    {
        return IKey.EMPTY;
    }

    protected IKey getRemoveLabel()
    {
        return IKey.EMPTY;
    }

    protected void addItem()
    {
        this.addNewItem();
        this.list.update();
    }

    protected void addNewItem()
    {}

    protected void removeItem()
    {
        int index = this.list.getIndex();

        this.list.getList().remove(index);

        index = Math.max(index - 1, 0);
        T item = this.list.getList().isEmpty() ? null : this.list.getList().get(index);

        this.pickItem(item, true);
        this.list.update();
    }

    protected void pickItem(T item, boolean select)
    {
        this.item = item;

        this.editor.setVisible(item != null);

        if (item != null)
        {
            this.fillData(item);

            if (select)
            {
                this.list.setCurrentScroll(item);
            }

            this.resize();
        }
    }

    protected abstract void fillData(T item);
}