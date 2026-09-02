package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.TooltipPlacement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The strip of quick actions along the top edge of a hovered grid cell, and the label of the
 * one under the cursor.
 *
 * <p>Icons follow the rest of BBS: their own size, white, lighter under the cursor, over a
 * gradient rather than a box — the strip along the top of the model block editor.</p>
 */
public class CellActionBar
{
    /** Cell width from which a hovered cell shows its bar at all. */
    public static final int THRESHOLD = 60;

    public static final int HEIGHT = 20;
    public static final int BUTTON = 20;

    private static final Area LABEL = new Area();

    /**
     * A translucent overlay that darkens on the light theme and lightens on the dark one —
     * the way hover reads on any ground without being tied to a surface tone.
     */
    public static int ink(int alpha)
    {
        return alpha | (BBSSettings.lightSurfaces() ? 0 : 0xffffff);
    }

    public static boolean fits(int cellWidth)
    {
        return cellWidth >= THRESHOLD;
    }

    /**
     * How many of the actions a cell this wide can show. The bar is right-aligned, so what
     * doesn't fit is dropped from the left — editing, the one action every cell has, is the
     * last to go. Everything below takes the FULL count and works this out itself, so an
     * index handed out here is an index into the whole array.
     */
    public static int visible(int cellWidth, int actions)
    {
        return fits(cellWidth) ? Math.min(actions, cellWidth / BUTTON) : 0;
    }

    /** Left edge of the first button shown — the bar is right-aligned along the cell's top. */
    public static int getX(int cellX, int cellWidth, int actions)
    {
        return cellX + cellWidth - visible(cellWidth, actions) * BUTTON;
    }

    /** The centre of one action's button, for putting its label under it. */
    public static int getActionX(int cellX, int cellWidth, int actions, int index)
    {
        return getX(cellX, cellWidth, actions) + (index - actions + visible(cellWidth, actions)) * BUTTON + BUTTON / 2;
    }

    /** Which action is under a point of a cell, or -1. */
    public static int getAction(int cellX, int cellY, int cellWidth, int actions, int x, int y)
    {
        int count = visible(cellWidth, actions);

        if (count == 0 || y < cellY || y >= cellY + HEIGHT)
        {
            return -1;
        }

        int bx = getX(cellX, cellWidth, actions);
        int index = (x - bx) / BUTTON;

        return x >= bx && index >= 0 && index < count ? actions - count + index : -1;
    }

    public static void render(UIContext context, int x, int y, int w, CellAction[] actions, int hovered)
    {
        Batcher2D batcher = context.batcher;
        int count = visible(w, actions.length);
        int first = actions.length - count;
        int bx = getX(x, w, actions.length);

        batcher.gradientVBox(x, y, x + w, y + HEIGHT, Colors.A75, 0);

        for (int i = 0; i < count; i++)
        {
            int ax = bx + i * BUTTON;
            int color = hovered == first + i ? Colors.LIGHTEST_GRAY : Colors.WHITE;

            batcher.icon(actions[first + i].icon, color, ax + BUTTON / 2, y + HEIGHT / 2, 0.5F, 0.5F);
        }
    }

    /**
     * The label of the hovered action — drawn by the host after everything else so it isn't
     * clipped by the cell or covered by neighbours. {@code x} is the button's centre,
     * {@code y} the bar's bottom edge; the card goes under the button, or above it at the
     * bottom of the screen.
     */
    public static void renderLabel(UIContext context, CellAction action, int x, int y)
    {
        String label = action.label.get();
        FontRenderer font = context.batcher.getFont();
        int w = font.getWidth(label) + 6;
        int h = font.getHeight() + 6;

        Area.SHARED.set(x - BUTTON / 2, y - HEIGHT, BUTTON, HEIGHT);

        Area area = TooltipPlacement.place(context, Area.SHARED, w, h, Direction.BOTTOM, 0, 2, LABEL);

        context.batcher.textCard(label, area.x + 3, area.y + 3, Colors.WHITE, Colors.A75, 3);
    }
}
