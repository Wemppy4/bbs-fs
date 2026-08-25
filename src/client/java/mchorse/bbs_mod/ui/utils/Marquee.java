package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A rubber-band selection: the rectangle stretched from where the button went down to where
 * the cursor is. Armed by a press, it becomes {@link #isActive() active} once the cursor has
 * moved a few pixels — a press that goes nowhere stays an ordinary click for the host to
 * handle on release.
 *
 * <p>The corners are kept in whatever space the host presses in (content coordinates of a
 * scrolled view, usually), so the host asks {@link #getArea()} in the same space.</p>
 */
public class Marquee
{
    public static final int THRESHOLD = 4;

    private final Area area = new Area();
    private boolean pressed;
    private boolean active;
    private int startX;
    private int startY;
    private int endX;
    private int endY;

    public void press(int x, int y)
    {
        this.pressed = true;
        this.active = false;
        this.startX = this.endX = x;
        this.startY = this.endY = y;
    }

    public void reset()
    {
        this.pressed = false;
        this.active = false;
    }

    public boolean isPressed()
    {
        return this.pressed;
    }

    public boolean isActive()
    {
        return this.active;
    }

    public boolean update(int x, int y)
    {
        if (!this.pressed)
        {
            return false;
        }

        this.endX = x;
        this.endY = y;

        if (!this.active)
        {
            this.active = Math.abs(x - this.startX) >= THRESHOLD || Math.abs(y - this.startY) >= THRESHOLD;
        }

        return this.active;
    }

    /** The rectangle as it stands, normalised so width and height are never negative. */
    public Area getArea()
    {
        int x1 = Math.min(this.startX, this.endX);
        int y1 = Math.min(this.startY, this.endY);
        int x2 = Math.max(this.startX, this.endX);
        int y2 = Math.max(this.startY, this.endY);

        this.area.set(x1, y1, x2 - x1, y2 - y1);

        return this.area;
    }

    /** Draw the band; {@code offsetX/Y} carry it from the host's space to the screen. */
    public void render(UIContext context, int offsetX, int offsetY)
    {
        if (!this.active)
        {
            return;
        }

        Area area = this.getArea();
        int x1 = area.x + offsetX;
        int y1 = area.y + offsetY;
        int x2 = area.ex() + offsetX;
        int y2 = area.ey() + offsetY;
        int primary = BBSSettings.primaryColor.get();

        context.batcher.box(x1, y1, x2, y2, Colors.A25 | primary);
        context.batcher.outline(x1, y1, x2, y2, Colors.A100 | primary, 1);
    }
}
