package mchorse.bbs_mod.ui.forms;

/**
 * Where the cells of one form category sit: a header band across the top, then rows of cells
 * below it. Every coordinate here is relative to the category's top-left corner.
 *
 * <p>This is the one place that knows the arithmetic. Painting, hit-testing a click, placing a
 * drop caret and scrolling to a form all ask it instead of each keeping their own copy of the
 * row/column math.</p>
 */
public class FormGridLayout
{
    public static final int HEADER = 22;

    /** Range of the cell width the user can zoom through. */
    public static final int MIN_CELL = 40;
    public static final int MAX_CELL = 140;

    /** Space between the grid and the category's left/right edges. */
    public static final int MARGIN = 6;

    /** Space between neighbouring cells. */
    public static final int GAP = 3;

    /** Space between the header band and the first row, and below the last row. */
    public static final int GRID_TOP = 3;
    public static final int GRID_BOTTOM = 7;

    private int width;
    private int cell;
    private int cellHeight;
    private int perRow = 1;
    private int count;

    /** Height of a cell for a given width — cells keep the 3:4 of the original 60×80. */
    public static int heightFor(int cellWidth)
    {
        return cellWidth * 4 / 3;
    }

    public FormGridLayout set(int width, int cellWidth, int count)
    {
        this.width = width;
        this.cell = cellWidth;
        this.cellHeight = heightFor(cellWidth);
        this.count = count;
        this.perRow = Math.max(1, (width - MARGIN * 2 + GAP) / (cellWidth + GAP));

        return this;
    }

    public int getCellWidth()
    {
        return this.cell;
    }

    public int getCellHeight()
    {
        return this.cellHeight;
    }

    public int getPerRow()
    {
        return this.perRow;
    }

    public int getCount()
    {
        return this.count;
    }

    public int getRows()
    {
        return this.count == 0 ? 0 : (this.count + this.perRow - 1) / this.perRow;
    }

    public int getRow(int index)
    {
        return index / this.perRow;
    }

    public int getContentHeight(boolean expanded)
    {
        int rows = expanded ? this.getRows() : 0;

        if (rows == 0)
        {
            return HEADER;
        }

        return HEADER + GRID_TOP + rows * this.cellHeight + (rows - 1) * GAP + GRID_BOTTOM;
    }

    public int getX(int index)
    {
        return MARGIN + (index % this.perRow) * (this.cell + GAP);
    }

    public int getY(int index)
    {
        return this.getRowY(this.getRow(index));
    }

    public int getRowY(int row)
    {
        return HEADER + GRID_TOP + row * (this.cellHeight + GAP);
    }

    public boolean isHeader(int y)
    {
        return y >= 0 && y < HEADER;
    }

    /**
     * Index of the cell under a point, or -1 when the point is on the header, in a gap or
     * past the last cell.
     */
    public int getIndex(int x, int y)
    {
        int column = this.getColumn(x);
        int row = this.getRowAt(y);

        if (column < 0 || row < 0)
        {
            return -1;
        }

        int cx = this.getX(column);
        int cy = this.getRowY(row);

        if (x >= cx + this.cell || y >= cy + this.cellHeight)
        {
            return -1;
        }

        int index = column + row * this.perRow;

        return index < this.count ? index : -1;
    }

    /**
     * The slot a dragged form would land in if dropped at a point: 0 puts it first,
     * {@link #getCount()} puts it last. Reads like a text caret — the nearest boundary
     * between cells of the row under the cursor.
     */
    public int getInsertion(int x, int y)
    {
        if (this.count == 0 || y < HEADER + GRID_TOP)
        {
            return 0;
        }

        int row = Math.min(this.getRows() - 1, Math.max(0, (y - HEADER - GRID_TOP) / (this.cellHeight + GAP)));
        int pitch = this.cell + GAP;
        int column = Math.round((x - MARGIN) / (float) pitch);

        column = Math.max(0, Math.min(this.perRow, column));

        return Math.min(this.count, column + row * this.perRow);
    }

    private int getColumn(int x)
    {
        x -= MARGIN;

        if (x < 0)
        {
            return -1;
        }

        int column = x / (this.cell + GAP);

        return column < this.perRow ? column : -1;
    }

    private int getRowAt(int y)
    {
        y -= HEADER + GRID_TOP;

        if (y < 0)
        {
            return -1;
        }

        int row = y / (this.cellHeight + GAP);

        return row < this.getRows() ? row : -1;
    }
}
