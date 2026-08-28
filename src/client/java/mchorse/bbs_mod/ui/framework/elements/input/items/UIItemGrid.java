package mchorse.bbs_mod.ui.framework.elements.input.items;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.TooltipPlacement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Items as cells of a grid laid out by a {@link GridLayout}: hover with quick actions along
 * the cell's top, a caret between cells for a drop, a stack of cards for the ghost.
 *
 * <p>Two ways of being hosted. On its own, the grid scrolls and clips itself. {@link #embedded()}
 * in a parent scroll view (one grid per category down a column, say), it has no scrolling of
 * its own: it reports its height through {@link #contentSize()}, keeps its {@link #area} in
 * step with it, and the parent lays it out and scrolls.</p>
 *
 * <p>And two ways of running: down the usual rows, or {@link #horizontal()} — one row of cells
 * that scrolls sideways, for a strip of frames under a canvas. The geometry stays the layout's;
 * only which axis is the long one changes.</p>
 *
 * @param <T> what's in the cells
 */
public abstract class UIItemGrid<T> extends UIItems<T>
{
    public static final int GHOST_SIZE = 48;

    /** How far from the cursor the name card of a cell that can't carry its own name sits. */
    private static final int CAPTION_OFFSET = 8;

    private static final Area CAPTION_CARD = new Area();

    protected final GridLayout layout;
    protected final CellState state = new CellState();

    private int cellSize = 60;
    private boolean embedded;
    private boolean horizontal;
    private int lastHeight = -1;

    /* What's under the cursor, refreshed every frame */
    protected int hoverIndex = -1;
    protected int hoverAction = -1;

    /* The label of the hovered quick action, drawn after everything else so nothing clips or covers it */
    private CellAction labelAction;
    private int labelX;
    private int labelY;

    /* The name of the hovered cell when the cell itself doesn't say it whole */
    private String hoverCaption;

    public UIItemGrid(Consumer<List<T>> callback, GridLayout layout)
    {
        this(callback, null, layout);
    }

    public UIItemGrid(Consumer<List<T>> callback, BiPredicate<T, T> same, GridLayout layout)
    {
        this(callback, same, null, null, layout);
    }

    /** See {@link UIItems#UIItems(Consumer, BiPredicate, Selection, ItemDrag)} for the shared selection and drag. */
    public UIItemGrid(Consumer<List<T>> callback, BiPredicate<T, T> same, Selection<T> selection, ItemDrag<T> drag, GridLayout layout)
    {
        super(callback, same, selection, drag);

        this.layout = layout;
        this.scroll.scrollSpeed = 40;

        /* The name card is drawn as a tooltip, and only an element with one gets asked to draw it */
        this.tooltip(IKey.EMPTY);
    }

    /* Settings */

    /** Hosted inside a parent scroll view: no scrolling or clipping of its own. */
    public UIItemGrid<T> embedded()
    {
        this.embedded = true;
        this.scroll.scrollbar = false;

        return this;
    }

    public boolean isEmbedded()
    {
        return this.embedded;
    }

    /** One row of cells scrolling sideways, instead of rows scrolling down. Not for an embedded grid. */
    public UIItemGrid<T> horizontal()
    {
        this.horizontal = true;
        this.layout.strip();
        this.scroll.direction = ScrollDirection.HORIZONTAL;

        return this;
    }

    public boolean isHorizontal()
    {
        return this.horizontal;
    }

    public GridLayout getLayout()
    {
        return this.layout;
    }

    public int getCellSize()
    {
        return this.cellSize;
    }

    public void setCellSize(int size)
    {
        this.cellSize = Math.max(1, size);
        this.relayout();
    }

    /* Hooks */

    /** Paint one cell; {@code state} says what overlays it gets. */
    protected abstract void renderCell(UIContext context, T item, int x, int y, int w, int h, CellState state);

    /** A caption for a cell, or null for none. */
    protected String caption(T item)
    {
        return null;
    }

    /** The quick actions a cell offers on hover. */
    protected CellAction[] actions(T item)
    {
        return CellAction.none();
    }

    /** A quick action was pressed on a cell. */
    protected void onAction(T item, CellAction action)
    {}

    /**
     * Something under a point that takes a drop of its own (a folder cell), instead of a
     * slot between cells; null when the drop would only reorder.
     */
    protected Object dropTargetAt(int x, int y)
    {
        return null;
    }

    /** Whether the cells are shown at all — a collapsed category keeps only its band. */
    protected boolean isExpanded()
    {
        return true;
    }

    /**
     * Whether a cell this wide says the item's whole {@link #caption(Object) name} itself. When
     * it doesn't — no name strip at a small zoom, or a name longer than the strip — the name is
     * shown by the cursor instead, so a grid of tiny cells can still be read.
     */
    protected boolean showsCaption(UIContext context, T item, int cellWidth)
    {
        return true;
    }

    /** Where the hovered action's label goes; the default draws it itself after painting. */
    protected void hoveredAction(CellAction action, int x, int y)
    {
        this.labelAction = action;
        this.labelX = x;
        this.labelY = y;
    }

    /* Layout */

    /** Lay the cells out for the current width and count, and tell whoever scrolls how tall that is. */
    public void relayout()
    {
        int width = Math.max(this.cellSize, this.area.w);

        this.layout.set(width, this.cellSize, this.visible().size());

        int h = this.contentSize();

        if (this.embedded)
        {
            this.syncHeight(h);
        }
        else
        {
            this.scroll.scrollSize = h;
            this.scroll.clamp();
        }
    }

    /** Embedded: the element's height is the content's, and the parent is told when it changes. */
    private void syncHeight(int h)
    {
        if (this.lastHeight == h)
        {
            return;
        }

        this.lastHeight = h;
        this.h(h);

        UIElement container = this.getParentContainer();

        if (container != null)
        {
            container.resize();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        this.relayout();
    }

    @Override
    protected boolean hasOwnScroll()
    {
        return !this.embedded;
    }

    /** Sideways, the scrolling is taken out of X rather than Y. */
    @Override
    protected int originX()
    {
        return this.horizontal ? this.area.x - (int) this.scroll.getScroll() : super.originX();
    }

    @Override
    protected int originY()
    {
        return this.embedded || this.horizontal ? this.area.y : super.originY();
    }

    @Override
    protected boolean clearsOnEmpty()
    {
        return true;
    }

    /* Geometry */

    @Override
    protected int indexAt(int x, int y)
    {
        if (!this.isExpanded())
        {
            return -1;
        }

        int index = this.layout.getIndex(x, y);

        return index < this.visible().size() ? index : -1;
    }

    @Override
    protected void areaOf(int index, Area out)
    {
        out.set(this.layout.getX(index), this.layout.getY(index), this.layout.getCellWidth(), this.layout.getCellHeight());
    }

    @Override
    protected int insertionAt(int x, int y)
    {
        return this.isExpanded() ? this.layout.getInsertion(x, y) : this.visible().size();
    }

    @Override
    protected int contentSize()
    {
        return this.horizontal ? this.layout.getContentWidth() : this.layout.getContentHeight(this.isExpanded());
    }

    @Override
    protected int step(int index, int dx, int dy)
    {
        int size = this.visible().size();

        if (dx != 0)
        {
            return MathUtils.clamp(index + dx, 0, size - 1);
        }

        int target = index + dy * this.layout.getPerRow();

        /* Stepping past the first or last row stays put rather than wrapping */
        return target < 0 || target >= size ? index : target;
    }

    @Override
    protected void scrollIntoView(int index)
    {
        if (!this.embedded && index >= 0 && index < this.layout.getCount())
        {
            if (this.horizontal)
            {
                this.scroll.scrollIntoView(this.layout.getX(index), this.layout.getCellWidth() + this.layout.getGap(), this.layout.getGap());
            }
            else
            {
                this.scroll.scrollIntoView(this.layout.getY(index), this.layout.getCellHeight() + this.layout.getGap(), this.layout.getGap());
            }
        }
    }

    /* Input */

    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        T item = this.visible().get(index);
        CellAction[] actions = this.actions(item);

        if (index == this.hoverIndex && actions.length > 0 && CellActionBar.fits(this.layout.getCellWidth()))
        {
            int action = CellActionBar.getAction(this.layout.getX(index), this.layout.getY(index), this.layout.getCellWidth(), actions.length, this.contentX(context), this.contentY(context));

            if (action != -1)
            {
                this.onAction(item, actions[action]);

                return true;
            }
        }

        return super.pressItem(index, context);
    }

    @Override
    protected void reportDropTarget(int x, int y)
    {
        Object target = this.dropTargetAt(x, y);

        if (target != null)
        {
            this.drag.setTarget(target);
        }
        else
        {
            super.reportDropTarget(x, y);
        }
    }

    protected void updateHover(UIContext context)
    {
        boolean inside = this.area.isInside(context) && !this.drag.isActive() && !context.hasContextMenu();
        int x = this.contentX(context);
        int y = this.contentY(context);

        this.hoverIndex = inside ? this.indexAt(x, y) : -1;
        this.hoverAction = -1;
        this.labelAction = null;
        this.hoverCaption = null;

        if (this.hoverIndex == -1)
        {
            return;
        }

        T item = this.visible().get(this.hoverIndex);
        CellAction[] actions = this.actions(item);
        int width = this.layout.getCellWidth();

        if (actions.length > 0 && CellActionBar.fits(width))
        {
            int cx = this.layout.getX(this.hoverIndex);
            int cy = this.layout.getY(this.hoverIndex);

            this.hoverAction = CellActionBar.getAction(cx, cy, width, actions.length, x, y);

            if (this.hoverAction != -1)
            {
                int ax = this.originX() + CellActionBar.getActionX(cx, width, actions.length, this.hoverAction);
                int ay = this.originY() + cy + CellActionBar.HEIGHT;

                this.hoveredAction(actions[this.hoverAction], context.globalX(ax), context.globalY(ay));
            }
        }

        /* An action has its own label, and a band being stretched is drawing rather than pointing */
        if (this.hoverAction == -1 && !this.marquee.isActive() && !this.showsCaption(context, item, width))
        {
            this.hoverCaption = this.caption(item);
        }
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        /* The count or the width may have changed under us since the last frame */
        this.relayout();

        super.render(context);

        if (this.labelAction != null)
        {
            CellActionBar.renderLabel(context, this.labelAction, this.labelX, this.labelY);
        }

        if (this.hoverAction != -1)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }
    }

    /**
     * The name of the hovered cell beside the cursor, for a cell too small to carry it. It goes
     * through the tooltip — drawn after the whole screen — so no neighbouring cell, column or
     * panel painted after the grid covers it.
     */
    @Override
    public void renderTooltip(UIContext context, Area area)
    {
        if (this.hoverCaption == null)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        /* The card is the text plus its 3px padding on each side */
        Area card = TooltipPlacement.nearMouse(context, font.getWidth(this.hoverCaption) + 6, font.getHeight() + 6, CAPTION_OFFSET, true, 2, CAPTION_CARD);

        context.batcher.textCard(this.hoverCaption, card.x + 3, card.y + 3, Colors.WHITE, Colors.A75, 3);
    }

    @Override
    protected void renderContent(UIContext context)
    {
        this.updateHover(context);

        if (this.isExpanded())
        {
            this.renderCells(context);
        }
    }

    /**
     * The window of Y (in the space the cells are painted in) the cells must fall into to be
     * worth painting; null to paint them all. Embedded, it's the container's area — a grid
     * inside a scroll view overrides this with the view's slice, shifted by its scrolling.
     */
    protected Area visibleWindow()
    {
        if (this.embedded)
        {
            UIElement container = this.getParentContainer();

            return container == null ? null : container.area;
        }

        return this.area;
    }

    protected void renderCells(UIContext context)
    {
        List<T> visible = this.visible();
        Area window = this.visibleWindow();
        int cellW = this.layout.getCellWidth();
        int cellH = this.layout.getCellHeight();
        int ox = this.originX();
        int oy = this.originY();

        for (int i = 0; i < visible.size(); i++)
        {
            int cx = ox + this.layout.getX(i);
            int cy = oy + this.layout.getY(i);

            /* Only what's in view is worth painting: along whichever axis the grid runs */
            if (window != null && (this.horizontal
                ? cx + cellW < window.x || cx > window.ex()
                : cy + cellH < window.y || cy > window.ey()))
            {
                continue;
            }

            T item = visible.get(i);

            this.state.reset();
            this.state.hover = i == this.hoverIndex;
            this.state.picked = this.selection.contains(item);
            this.state.selected = this.state.picked && !this.selection.isGroup();
            this.state.dragged = this.drag.isDragging(item);
            this.state.dropTarget = this.drag.isTarget(item);
            this.state.hoveredAction = this.state.hover ? this.hoverAction : -1;

            this.renderCell(context, item, cx, cy, cellW, cellH, this.state);
        }
    }

    /** The caret between cells where the drop lands. */
    @Override
    protected void renderInsertion(UIContext context, int insertion)
    {
        int count = this.visible().size();
        int primary = BBSSettings.primaryColor.get();

        if (insertion < 0)
        {
            return;
        }

        if (!this.isExpanded() || count == 0)
        {
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.y + Math.max(2, this.layout.getHeader()), Colors.A100 | primary, 1);

            return;
        }

        int gap = this.layout.getGap();
        int x;
        int y;

        if (insertion < count)
        {
            x = this.layout.getX(insertion) - gap / 2 - 1;
            y = this.layout.getY(insertion);
        }
        else
        {
            x = this.layout.getX(count - 1) + this.layout.getCellWidth() + gap / 2 - 1;
            y = this.layout.getY(count - 1);
        }

        x += this.originX();
        y += this.originY();

        context.batcher.box(x, y, x + 2, y + this.layout.getCellHeight(), Colors.A100 | primary);
    }

    /** A small stack of the carried cells; the front one is painted like a cell with no overlays. */
    @Override
    protected void renderDragGhost(UIContext context)
    {
        /* Inside a parent's scroll view the ghost would be clipped to it; the host draws it instead */
        if (this.embedded || this.drag.getItems().isEmpty())
        {
            return;
        }

        int size = Math.min(this.cellSize, GHOST_SIZE);
        int h = this.layout.heightFor(size);
        T front = this.drag.getItems().get(0);

        this.drag.renderGhost(context, size, h, this.drag.hasTarget(), (ctx, x, y, w, gh) ->
        {
            this.renderCell(ctx, front, x, y, w, gh, this.state.reset());
        });
    }
}
