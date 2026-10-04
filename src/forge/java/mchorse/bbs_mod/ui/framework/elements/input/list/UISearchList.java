package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.graphics.window.InputCodes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;

public class UISearchList <T> extends UIElement
{
    public UITextbox search;
    public UIList<T> list;
    private Runnable confirm;
    private UIListGrid<T> grid;

    public UISearchList(UIList<T> list)
    {
        this.search = new UITextbox(100, (str) -> this.filter(str, false))
        {
            @Override
            public boolean subKeyPressed(UIContext context)
            {
                if (this.isFocused() && confirm != null && context.getKeyAction() != KeyAction.RELEASED)
                {
                    int key = context.getKeyCode();

                    if (key == InputCodes.KEY_UP || key == InputCodes.KEY_DOWN)
                    {
                        UISearchList.this.list.moveSelection(key == InputCodes.KEY_UP ? -1 : 1);
                        if (grid != null && grid.isVisible()) grid.revealSelection();
                        return true;
                    }

                    if (key == InputCodes.KEY_ENTER || key == InputCodes.KEY_KP_ENTER)
                    {
                        if (context.getKeyAction() == KeyAction.PRESSED) confirm.run();
                        return true;
                    }
                }

                return super.subKeyPressed(context);
            }
        };
        this.search.relative(this).set(0, 0, 0, 20).w(1, 0);

        this.list = list;
        this.list.relative(this).set(0, 20, 0, 0).w(1, 0).h(1, -20);

        this.add(this.search, this.list);
    }

    public UISearchList<T> label(IKey label)
    {
        this.search.textbox.setPlaceholder(label);

        return this;
    }

    public UISearchList<T> keyboardSelection(Runnable confirm)
    {
        this.confirm = confirm;
        return this;
    }

    public UISearchList<T> views()
    {
        this.grid = new UIListGrid<>(this.list);
        this.grid.relative(this.list).wh(1F, 1F);
        this.grid.setVisible(false);
        this.grid.setEnabled(false);
        UIIcon rows = new UIIcon(Icons.LIST, b -> this.setTiles(false));
        UIIcon tiles = new UIIcon(Icons.GALLERY, b -> this.setTiles(true));
        rows.relative(this).x(1F, -40).wh(20, 20);
        tiles.relative(this).x(1F, -20).wh(20, 20);
        rows.highlight(() -> !this.grid.isVisible(), mchorse.bbs_mod.utils.Direction.BOTTOM);
        tiles.highlight(() -> this.grid.isVisible(), mchorse.bbs_mod.utils.Direction.BOTTOM);
        this.search.w(1F, -44);
        this.add(this.grid, rows, tiles);
        this.setTiles(true);
        return this;
    }

    private void setTiles(boolean tiles)
    {
        this.list.setVisible(!tiles);
        this.list.setEnabled(!tiles);
        this.grid.setVisible(tiles);
        this.grid.setEnabled(tiles);
        if (tiles) this.grid.revealSelection();
        else this.list.moveSelection(0);
        if (this.confirm != null && this.getContext() != null) this.getContext().focus(this.search);
    }

    public void filter(String str, boolean fill)
    {
        if (fill)
        {
            this.search.setText(str);
        }

        this.list.filter(str);

        if (this.confirm != null)
        {
            this.list.deselect();
            this.list.moveSelection(0);
        }
        if (this.grid != null) this.grid.relayout();
    }
}
