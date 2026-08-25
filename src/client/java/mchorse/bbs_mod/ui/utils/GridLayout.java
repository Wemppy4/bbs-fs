package mchorse.bbs_mod.ui.utils;

/**
 * Where the cells of a grid sit: an optional band across the top, then rows of equal cells
 * below it. Every coordinate is relative to the grid's top-left corner.
 *
 * <p>This is the one place that knows the arithmetic. Painting, hit-testing a click, placing a
 * drop caret and scrolling to a cell all ask it instead of each keeping their own copy of the
 * row/column math. Hosts fix the spacing and the cell proportions once, in the constructor,
 * and feed the width, the cell size and the count as those change.</p>
 */
public class GridLayout
{
    protected final int header;
    protected final int margin;
    protected final int gap;
    protected final int gridTop;
    protected final int gridBottom;
    protected final float aspect;

    private int cell;
    private int cellHeight;
    private int perRow = 1;
    private int count;

    /**
     * @param header     height of the band above the rows (0 for none)
     * @param margin     space between the rows and the left/right edges
     * @param gap        space between neighbouring cells
     * @param gridTop    space between the band and the first row
     * @param gridBottom space below the last row
     * @param aspect     cell height as a multiple of its width
     */
    public GridLayout(int header, int margin, int gap, int gridTop, int gridBottom, float aspect)
    {
        this.header = header;
        this.margin = margin;
        this.gap = gap;
        this.gridTop = gridTop;
        this.gridBottom = gridBottom;
        this.aspect = aspect;
    }

    public int heightFor(int cellWidth)
    {
        return Math.round(cellWidth * this.aspect);
    }

    public GridLayout set(int width, int cellWidth, int count)
    {
        this.cell = cellWidth;
        this.cellHeight = this.heightFor(cellWidth);
        this.count = count;
        this.perRow = Math.max(1, (width - this.margin * 2 + this.gap) / (cellWidth + this.gap));

        return this;
    }

    public int getHeader()
    {
        return this.header;
    }

    public int getGap()
    {
        return this.gap;
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
            return this.header;
        }

        return this.header + this.gridTop + rows * this.cellHeight + (rows - 1) * this.gap + this.gridBottom;
    }

    public int getX(int index)
    {
        return this.margin + (index % this.perRow) * (this.cell + this.gap);
    }

    public int getY(int index)
    {
        return this.getRowY(this.getRow(index));
    }

    public int getRowY(int row)
    {
        return this.header + this.gridTop + row * (this.cellHeight + this.gap);
    }

    public boolean isHeader(int y)
    {
        return y >= 0 && y < this.header;
    }

    /** The row under a content Y, clamped to the rows there are; -1 above the first row. */
    public int getRowAt(int y)
    {
        y -= this.header + this.gridTop;

        if (y < 0 || this.getRows() == 0)
        {
            return -1;
        }

        return Math.min(this.getRows() - 1, y / (this.cellHeight + this.gap));
    }

    /**
     * Index of the cell under a point, or -1 when the point is on the band, in a gap or
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

        if (x >= cx + this.cell || y >= cy + this.cellHeight || y < cy)
        {
            return -1;
        }

        int index = column + row * this.perRow;

        return index < this.count ? index : -1;
    }

    /**
     * The slot a dragged cell would land in if dropped at a point: 0 puts it first,
     * {@link #getCount()} puts it last. Reads like a text caret — the nearest boundary
     * between cells of the row under the cursor.
     */
    public int getInsertion(int x, int y)
    {
        if (this.count == 0 || y < this.header + this.gridTop)
        {
            return 0;
        }

        int row = Math.max(0, this.getRowAt(y));
        int pitch = this.cell + this.gap;
        int column = Math.round((x - this.margin) / (float) pitch);

        column = Math.max(0, Math.min(this.perRow, column));

        return Math.min(this.count, column + row * this.perRow);
    }

    private int getColumn(int x)
    {
        x -= this.margin;

        if (x < 0)
        {
            return -1;
        }

        int column = x / (this.cell + this.gap);

        return column < this.perRow ? column : -1;
    }
}
