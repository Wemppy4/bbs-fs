package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItemGrid;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * A palette of colors as a grid of tight square cells.
 *
 * <p>The cells are {@link UIItemGrid} items like the cells of any other browser in BBS, so
 * the palette inherits the picking, the band, the keyboard and the drag from there: the
 * favorites are put into an order by dragging them and dropped with Delete, instead of only
 * ever growing in whatever order they happened to be added in.</p>
 *
 * <p>The list it shows is the {@link ValueColors} it was handed — the palette edits that
 * directly, so the settings hear about every change and save it.</p>
 */
public class UIColorPalette extends UIItemGrid<Color>
{
    public static final int CELL_SIZE = 10;

    private final ValueColors values;

    /** A plain click hands the color over — picking one is what the palette is for. */
    public Consumer<Color> callback;

    /** Told when the palette gains or loses cells, so the popup around it can resize. */
    public Runnable onChanged;

    /** Whether the user may take colors out of this palette; a fixed one only hands them over. */
    private boolean editable;

    public UIColorPalette(ValueColors values, Consumer<Color> callback)
    {
        /* Tight square cells, no band or spacing: the whole width is cells */
        super(null, null, new GridLayout(0, 0, 0, 0, 0, 1F));

        this.values = values;
        this.callback = callback;

        this.setCellSize(CELL_SIZE);
        this.scroll.scrollbar = false;
    }

    /** Several cells may be picked at once, and Delete drops them. */
    public UIColorPalette editable()
    {
        this.editable = true;

        this.multi();

        return this;
    }

    /** Cells of this palette may be dragged into another order. */
    public UIColorPalette sortable()
    {
        this.sorting();

        return this;
    }

    public UIColorPalette onChanged(Runnable callback)
    {
        this.onChanged = callback;

        return this;
    }

    /* The colors */

    @Override
    protected List<Color> visible()
    {
        return this.values.getCurrentColors();
    }

    public boolean isEmpty()
    {
        return this.visible().isEmpty();
    }

    public boolean hasColor(int index)
    {
        return index >= 0 && index < this.visible().size();
    }

    public Color getColor(int index)
    {
        return this.hasColor(index) ? this.visible().get(index) : null;
    }

    /**
     * The palette is laid out to exactly the height of its cells, so there is nothing to
     * scroll — and it must not try: a stray wheel event over it used to shift the cells out
     * of their place, and the scrolling being animated made them drift back.
     */
    @Override
    protected boolean hasOwnScroll()
    {
        return false;
    }

    @Override
    protected int originY()
    {
        return this.area.y;
    }

    /** The cell under the cursor, or -1 — what a context menu opened over the palette acts on. */
    public int getIndex(UIContext context)
    {
        return this.indexAt(this.contentX(context), this.contentY(context));
    }

    /**
     * How tall the palette stands at a given width. The popup asks before it lays anything
     * out, so this sizes the layout rather than reading a size laid out earlier; an empty
     * palette still claims one row so the popup's own height doesn't collapse.
     */
    public int getHeight(int width)
    {
        return Math.max(CELL_SIZE, this.layout.set(width, this.getCellSize(), this.visible().size()).getContentHeight(true));
    }

    private void changed()
    {
        if (this.onChanged != null)
        {
            this.onChanged.run();
        }
    }

    /* Hooks */

    /** A plain click picks the color; Ctrl and Shift are gathering cells to act on instead. */
    @Override
    protected void applySelectionOnClick(Color item, int index)
    {
        super.applySelectionOnClick(item, index);

        if (!Window.isCtrlPressed() && !Window.isShiftPressed() && this.callback != null)
        {
            this.callback.accept(item);
        }
    }

    @Override
    protected boolean onDelete(List<Color> items)
    {
        if (!this.editable)
        {
            return false;
        }

        this.values.removeAll(items);
        this.selection.clear();
        this.changed();

        return true;
    }

    @Override
    protected void reorder(List<Color> items, int insertion)
    {
        this.values.reorder(items, insertion);
    }

    /* Rendering */

    @Override
    protected void renderCell(UIContext context, Color item, int x, int y, int w, int h, CellState state)
    {
        context.batcher.iconArea(Icons.CHECKBOARD, x, y, w, h);
        UIColorPicker.renderAlphaPreviewQuad(context.batcher, x, y, x + w, y + h, item);

        if (state.dragged)
        {
            context.batcher.box(x, y, x + w, y + h, Colors.A50);
        }

        if (state.picked)
        {
            context.batcher.outline(x, y, x + w, y + h, Colors.A100 | BBSSettings.primaryColor.get());
        }
        else if (state.hover)
        {
            context.batcher.outline(x, y, x + w, y + h, Colors.WHITE);
        }
    }
}
