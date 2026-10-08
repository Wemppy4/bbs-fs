package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.context.ColorfulContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A context menu laid out in columns, each under a title of its own, with one search field over
 * all of them. It is for long sets that fall into groups — the palette of clip types — where a
 * single list runs off the screen and loses the grouping on the way.
 *
 * <p>Every column has the same fixed width, so the columns line up and do not jump about while
 * the search narrows them down. A name too long for it is cut short; the search still finds it
 * by the whole name.</p>
 *
 * <p>With icons only, a column is a grid of bare icons instead of a list of rows, the name of
 * each in its tooltip — for those who know the icons by now and want the menu small. The groups
 * then go one under another instead of side by side.</p>
 *
 * <p>The rows are numbered the way {@link ContextMenuManager#autoKey} numbers a plain menu,
 * reading the columns left to right, so a pick stays a two-stroke gesture. That is why the
 * search field is not focused when the menu opens — a focused field would swallow the numbers.
 * Once it is clicked into, Enter runs the first row still showing.</p>
 */
public class UIColumnsContextMenu extends UIContextMenu
{
    private static final int TITLE_HEIGHT = 16;
    private static final int TITLE_COLOR = Colors.LIGHTER_GRAY;

    /** Every column is this wide; a name that does not fit is cut short with an ellipsis */
    private static final int COLUMN_WIDTH = 130;

    /** The space between the block of columns and the edges of the menu; the search sits right on top */
    private static final int PADDING = 5;

    /** The side of an icon's cell, and how many of them a row of a column holds */
    private static final int TILE = 20;
    private static final int TILES_PER_ROW = COLUMN_WIDTH / TILE;

    public UIContextMenuBar bar;
    public UITextbox search;

    private final boolean iconsOnly;
    private final List<Column> columns = new ArrayList<>();

    private ContextAction action;
    private int keyed;

    public UIColumnsContextMenu(boolean iconsOnly)
    {
        super();

        this.iconsOnly = iconsOnly;

        this.bar = new UIContextMenuBar(this::dismiss);
        this.bar.relative(this).w(1F).h(UIContextMenuBar.HEIGHT);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this).w(1F).h(UISimpleContextMenu.FILTER_HEIGHT);

        this.add(this.bar, this.search);
    }

    public UIColumnsContextMenu column(IKey title, List<ContextAction> actions)
    {
        Column column = new Column(title, actions);

        for (ContextAction action : actions)
        {
            if (this.keyed < ContextMenuManager.AUTO_KEYS)
            {
                this.keys().register(ContextMenuManager.autoKey(action.label, this.keyed++), () ->
                {
                    action.runnable.run();
                    this.dismiss();
                }).category(UIKeys.CONTEXT_MENU_KEY_CATEGORY);
            }
        }

        this.columns.add(column);
        this.add(column.body);

        return this;
    }

    private void filter(String text)
    {
        for (Column column : this.columns)
        {
            column.filter(text);
        }

        /* Hidden icons give their cells up only when the grid is laid out again */
        if (this.iconsOnly)
        {
            this.resize();
        }
    }

    @Override
    public boolean isEmpty()
    {
        return this.columns.isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        if (context.canGoBack())
        {
            this.bar.register(new MenuIcon(MenuVerb.BACK, context::backContextMenu));
        }

        this.bar.sync(true);

        int top = this.bar.isVisible() ? this.bar.getTotalHeight() : 0;

        this.search.y(top);

        top += UISimpleContextMenu.FILTER_HEIGHT;

        int width;
        int height;

        if (this.iconsOnly)
        {
            /* The grids are small enough to stack: the groups go one under another, so the
             * menu is a single narrow strip */
            int y = top;

            for (Column column : this.columns)
            {
                column.titleX = PADDING;
                column.titleY = y;
                y += TITLE_HEIGHT;

                column.body.relative(this).xy(PADDING, y).w(COLUMN_WIDTH).h(column.getHeight());
                y += column.getHeight();
            }

            width = PADDING * 2 + COLUMN_WIDTH;
            height = y + PADDING;
        }
        else
        {
            int h = 0;

            for (int i = 0; i < this.columns.size(); i++)
            {
                Column column = this.columns.get(i);

                column.titleX = PADDING + i * COLUMN_WIDTH;
                column.titleY = top;

                h = Math.max(h, column.getHeight());
                column.body.relative(this).xy(column.titleX, top + TITLE_HEIGHT).w(COLUMN_WIDTH).h(1F, -top - TITLE_HEIGHT - PADDING);
            }

            width = PADDING * 2 + COLUMN_WIDTH * this.columns.size();
            height = top + TITLE_HEIGHT + h + PADDING;
        }

        width = Math.max(width, this.bar.getContentWidth());

        this.set(context.mouseX(), context.mouseY(), width, 0).h(height).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.action != null)
        {
            /* Let go of it before running, see UISimpleContextMenu */
            ContextAction action = this.action;

            this.action = null;

            action.runnable.run();
            this.dismiss();

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ENTER))
        {
            for (Column column : this.columns)
            {
                ContextAction action = column.first();

                if (action != null && action.runnable != null)
                {
                    action.runnable.run();
                    this.dismiss();

                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        FontRenderer font = context.batcher.getFont();

        for (Column column : this.columns)
        {
            int x = this.area.x + column.titleX;
            int y = this.area.y + column.titleY;
            String title = font.limitToWidth(column.title.get(), COLUMN_WIDTH);

            context.batcher.text(title, x, y + (TITLE_HEIGHT - font.getHeight()) / 2 + 1, TITLE_COLOR, false);
        }
    }

    /**
     * One column: its rows as a list, or as a grid of icons when the menu shows icons only.
     */
    private class Column
    {
        public final IKey title;
        public final List<ContextAction> actions;
        public final UIElement body;

        /** Where the title is drawn, relative to the menu */
        public int titleX;
        public int titleY;

        private UIActionList list;
        private final List<UIIcon> tiles = new ArrayList<>();

        public Column(IKey title, List<ContextAction> actions)
        {
            this.title = title;
            this.actions = actions;

            if (iconsOnly)
            {
                this.body = new UIElement();
                this.body.grid(0).items(TILES_PER_ROW).resizes(false);

                for (ContextAction action : actions)
                {
                    UIIcon tile = new UIActionTile(action, (b) -> UIColumnsContextMenu.this.action = action);

                    tile.wh(TILE, TILE);
                    this.tiles.add(tile);
                    this.body.add(tile);
                }
            }
            else
            {
                this.list = new UIActionList((picked) ->
                {
                    if (picked.get(0).runnable != null)
                    {
                        UIColumnsContextMenu.this.action = picked.get(0);
                    }
                });

                this.list.setList(actions);
                this.list.cancelScrollEdge();
                this.body = this.list;
            }
        }

        public int getHeight()
        {
            if (this.list != null)
            {
                return this.actions.size() * this.list.scroll.scrollItemSize;
            }

            return (this.actions.size() + TILES_PER_ROW - 1) / TILES_PER_ROW * TILE;
        }

        public void filter(String text)
        {
            if (this.list != null)
            {
                this.list.filter(text);

                return;
            }

            /* The same match as a list's filter, keyboard layout slips included */
            String filter = text.toLowerCase();
            String qwerty = KeyCodes.cyrillicToQwerty(filter);

            for (int i = 0; i < this.tiles.size(); i++)
            {
                String label = this.actions.get(i).label.get().toLowerCase();

                this.tiles.get(i).setVisible(label.contains(filter) || label.contains(qwerty));
            }
        }

        public ContextAction first()
        {
            if (this.list != null)
            {
                return this.list.first();
            }

            for (int i = 0; i < this.tiles.size(); i++)
            {
                if (this.tiles.get(i).isVisible())
                {
                    return this.actions.get(i);
                }
            }

            return null;
        }
    }

    /**
     * A row shown as its icon alone: the name moves into the tooltip, and the row's own colour,
     * when it has one, is marked the way a coloured row marks it ({@link ColorfulContextAction}),
     * only rising from the bottom edge rather than the left one.
     */
    private static class UIActionTile extends UIIcon
    {
        private final int color;

        public UIActionTile(ContextAction action, Consumer<UIIcon> callback)
        {
            super(action.icon, callback);

            this.color = action instanceof ColorfulContextAction ? ((ColorfulContextAction) action).color : 0;
            /* The tooltip is the only name the icon has, so it comes up at once */
            this.tooltip(action.label).tooltipImmediate();
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            /* A cell's hover, rising from the bottom edge like the colour below */
            RowStyle.cellWash(context.batcher, this.area.x, this.area.y, this.area.w, this.area.h, this.hover, false);

            if (this.color != 0)
            {
                int x = this.area.x;
                int ey = this.area.ey();

                context.batcher.box(x, ey - 2, this.area.ex(), ey, Colors.A100 | this.color);
                context.batcher.gradientVBox(x, this.area.y, this.area.ex(), ey - 2, this.color, Colors.A25 | this.color);
            }

            super.renderSkin(context);
        }
    }
}
