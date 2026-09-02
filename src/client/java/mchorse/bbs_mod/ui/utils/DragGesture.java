package mchorse.bbs_mod.ui.utils;

/**
 * A press that may become a drag: armed by the button going down, {@link #isActive() active}
 * once the cursor has travelled a few pixels. A press that goes nowhere stays an ordinary
 * click for the host to handle on release, so nothing is ever moved by accident.
 *
 * <p>The base for every drag-like gesture here — carrying forms or textures, stretching a
 * selection band — so the threshold and the arming live in one place.</p>
 */
public abstract class DragGesture
{
    public static final int THRESHOLD = 4;

    private boolean pressed;
    private boolean active;
    protected int startX;
    protected int startY;

    protected void press(int x, int y)
    {
        this.pressed = true;
        this.active = false;
        this.startX = x;
        this.startY = y;
    }

    public void reset()
    {
        this.pressed = false;
        this.active = false;
    }

    /** Whether a button is held on something draggable, active or not yet. */
    public boolean isPressed()
    {
        return this.pressed;
    }

    public boolean isActive()
    {
        return this.active;
    }

    /** Feed the cursor; returns whether the gesture is active after this move. */
    public boolean update(int x, int y)
    {
        if (this.pressed && !this.active)
        {
            this.active = Math.abs(x - this.startX) >= THRESHOLD || Math.abs(y - this.startY) >= THRESHOLD;
        }

        return this.active;
    }
}
