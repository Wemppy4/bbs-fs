package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.graphics.window.Window;

/**
 * The virtual cursor a gesture is driven by while Shift asks for precision.
 *
 * <p>Held, it advances at {@link DragStrategy#FINE_DRAG_FACTOR} of the real cursor and the
 * rest of the motion piles into a lag offset; released, it tracks the cursor 1:1 again with
 * no jump, because the offset it accumulated is simply kept. Every ray gesture reads its
 * position instead of the raw one, so they all slow uniformly without a line of per-mode code.
 */
public class FineCursor
{
    private float offsetX;
    private float offsetY;

    private int lastX;
    private int lastY;

    private boolean hasLast;

    /** Advances the virtual cursor for this frame from where the real one is now. */
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasLast)
        {
            this.reset(mouseX, mouseY);

            return;
        }

        if (Window.isShiftPressed())
        {
            float keep = 1F - DragStrategy.FINE_DRAG_FACTOR;

            this.offsetX += (mouseX - this.lastX) * keep;
            this.offsetY += (mouseY - this.lastY) * keep;
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    /**
     * Drops the accumulated lag and re-anchors on the real cursor. Used when a gesture starts
     * and when the cursor is teleported across a window edge — both are moves the gesture must
     * not read as drag.
     */
    public void reset(int mouseX, int mouseY)
    {
        this.offsetX = 0F;
        this.offsetY = 0F;
        this.lastX = mouseX;
        this.lastY = mouseY;
        this.hasLast = true;
    }

    /** Forgets the anchor, so the next {@link #update} re-takes it instead of reading a jump. */
    public void forget()
    {
        this.hasLast = false;
    }

    public int x(int mouseX)
    {
        return Math.round(mouseX - this.offsetX);
    }

    public int y(int mouseY)
    {
        return Math.round(mouseY - this.offsetY);
    }
}
