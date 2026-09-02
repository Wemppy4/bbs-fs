package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.ui.utils.GridLayout;

/**
 * The grid of one form category: a header band across the top, then rows of 3:4 cells (the
 * proportions of the original 60×80). See {@link GridLayout} for the arithmetic.
 */
public class FormGridLayout extends GridLayout
{
    public static final int HEADER = 20;

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

    public FormGridLayout()
    {
        super(HEADER, MARGIN, GAP, GRID_TOP, GRID_BOTTOM, 4 / 3F);
    }

    /** Height of a cell for a given width — cells keep the 3:4 of the original 60×80. */
    public static int cellHeightFor(int cellWidth)
    {
        return cellWidth * 4 / 3;
    }
}
