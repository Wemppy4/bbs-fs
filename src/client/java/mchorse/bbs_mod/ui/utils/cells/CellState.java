package mchorse.bbs_mod.ui.utils.cells;

/**
 * Everything about one grid cell that decides its overlays. The host reuses one instance
 * across the cells it paints, resetting it for each.
 */
public class CellState
{
    public boolean hover;

    /** The one item the grid has chosen — what an editor edits, what a picker hands back. */
    public boolean selected;

    /** Part of a multi-selection. */
    public boolean picked;

    /** Being dragged right now: the cell stays in place but goes translucent. */
    public boolean dragged;

    /** The cell a drag would drop into (a folder). */
    public boolean dropTarget;

    /** Index of the hovered quick action, or -1. Only read while {@link #hover}. */
    public int hoveredAction = -1;

    public CellState reset()
    {
        this.hover = this.selected = this.picked = this.dragged = this.dropTarget = false;
        this.hoveredAction = -1;

        return this;
    }

    /** Whether the cell reads as chosen — selected, or about to receive a drop. */
    public boolean isLit()
    {
        return this.selected || this.dropTarget;
    }
}
