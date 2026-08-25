package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
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

    /**
     * A translucent overlay that darkens on the light theme and lightens on the dark one —
     * the way hover reads on any ground without being tied to a surface tone.
     */
    public static int ink(int alpha)
    {
        return alpha | (BBSSettings.isLightTheme() ? 0 : 0xffffff);
    }

    public static boolean fits(int cellWidth)
    {
        return cellWidth >= THRESHOLD;
    }

    /** Left edge of the first button — the bar is right-aligned along the cell's top. */
    public static int getX(int cellX, int cellWidth, int actions)
    {
        return cellX + cellWidth - actions * BUTTON;
    }

    /** Which action is under a point of a cell, or -1. */
    public static int getAction(int cellX, int cellY, int cellWidth, int actions, int x, int y)
    {
        if (actions == 0 || y < cellY || y >= cellY + HEIGHT)
        {
            return -1;
        }

        int bx = getX(cellX, cellWidth, actions);
        int index = (x - bx) / BUTTON;

        return x >= bx && index >= 0 && index < actions ? index : -1;
    }

    public static void render(UIContext context, int x, int y, int w, CellAction[] actions, int hovered)
    {
        Batcher2D batcher = context.batcher;
        int bx = getX(x, w, actions.length);

        batcher.gradientVBox(x, y, x + w, y + HEIGHT, Colors.A75, 0);

        for (int i = 0; i < actions.length; i++)
        {
            int ax = bx + i * BUTTON;
            int color = hovered == i ? Colors.LIGHTEST_GRAY : Colors.WHITE;

            batcher.icon(actions[i].icon, color, ax + BUTTON / 2, y + HEIGHT / 2, 0.5F, 0.5F);
        }
    }

    /**
     * The label of the hovered action — drawn by the host after everything else so it isn't
     * clipped by the cell or covered by neighbours. {@code x} is the button's centre.
     */
    public static void renderLabel(UIContext context, CellAction action, int x, int y)
    {
        String label = action.label.get();
        FontRenderer font = context.batcher.getFont();
        int w = font.getWidth(label) + 6;

        x = Math.max(2, Math.min(x - w / 2, context.menu.width - w - 2));

        context.batcher.textCard(label, x + 3, y + 3, Colors.WHITE, Colors.A75, 3);
    }
}
