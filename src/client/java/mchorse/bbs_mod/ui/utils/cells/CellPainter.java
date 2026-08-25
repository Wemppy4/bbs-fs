package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The parts of a grid cell that don't depend on what's in it: the ground under the picture
 * (hover, chosen), the dimming of a cell being dragged, the frames on top, and a caption
 * strip along the bottom. Cells of forms and of textures are painted from these, so the
 * two grids read as one family.
 */
public class CellPainter
{
    /** Height of the caption strip along the bottom of a cell. */
    public static final int CAPTION_HEIGHT = 14;

    /**
     * Under the picture: the accent for a chosen cell, and nothing at all under the cursor.
     * Hovering is said with the frame instead — a wash over the ground shifted every picture's
     * colours with it, and in a grid of pictures that reads as the picture changing.
     */
    public static void ground(UIContext context, int x, int y, int w, int h, CellState state)
    {
        if (state.isLit())
        {
            context.batcher.box(x, y, x + w, y + h, Colors.A25 | BBSSettings.primaryColor.get());
        }
    }

    /** Over the picture of a cell being dragged, so the grid shows where it came from without shouting. */
    public static void dim(UIContext context, int x, int y, int w, int h, CellState state)
    {
        if (state.dragged)
        {
            context.batcher.box(x, y, x + w, y + h, BBSSettings.color(BBSSettings.baseSurface(), Colors.A75));
        }
    }

    /**
     * Frames go last so nothing paints over them. Solid for the cell that's chosen or under
     * the cursor, lighter for one of a pick — the cursor reads as strongly as the choice
     * does, since that's the whole of what says where it is.
     */
    public static void frames(UIContext context, int x, int y, int w, int h, CellState state)
    {
        int primary = BBSSettings.primaryColor.get();

        if (state.isLit() || state.hover)
        {
            context.batcher.outline(x, y, x + w, y + h, Colors.A100 | primary, 1);
        }
        else if (state.picked)
        {
            context.batcher.outline(x, y, x + w, y + h, Colors.A75 | primary, 1);
        }
    }

    /** A caption along the bottom of a cell, on a gradient so it reads over any picture. */
    public static void caption(UIContext context, String label, int x, int y, int w, int h, boolean bright)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        label = font.limitToWidth(label, w - 6);

        batcher.gradientVBox(x, y + h - CAPTION_HEIGHT - 8, x + w, y + h, 0, Colors.A75);
        batcher.textShadow(label, x + (w - font.getWidth(label)) / 2, y + h - CAPTION_HEIGHT + (CAPTION_HEIGHT - font.getHeight()) / 2 + 1, bright ? Colors.WHITE : Colors.LIGHTEST_GRAY);
    }
}
