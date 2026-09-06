package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
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

    /** Space kept between the words of a caption and either edge of the cell. */
    public static final int CAPTION_PADDING = 3;

    /**
     * What state the cell is in, said the way a row says it — only turned a quarter, so the wash
     * climbs from the bottom edge instead of running in from the side.
     *
     * <p>Goes down before the thing the cell is about, so a form keeps its own colours and a
     * texture is shown as it is; the mark reads in the margins around the picture and along the
     * caption strip, which is where a cell has room to say anything at all.</p>
     */
    public static void marks(UIContext context, int x, int y, int w, int h, CellState state)
    {
        RowStyle.cellWash(context.batcher, x, y, w, h, state.hover, state.isLit() || state.picked);
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
     * The bar along the bottom edge, last so nothing paints over it. Only the cell that is
     * <em>the</em> chosen one wears it; one of a multi-selection has the wash and no bar, which is
     * the same difference the bar draws between a picked row and a hovered one.
     */
    public static void bar(UIContext context, int x, int y, int w, int h, CellState state)
    {
        if (state.isLit())
        {
            RowStyle.cellBar(context.batcher, x, y, w, h);
        }
    }

    /**
     * Whether a cell this wide says a caption whole, or cuts it short to fit the strip. What
     * the cell can't say the grid says by the cursor instead, so the two agree on where the
     * words stop fitting — down to the strict comparison, which is the one
     * {@link FontRenderer#limitToWidth(String, int) the cut} makes: a caption exactly as wide
     * as the room it has is already shortened.
     */
    public static boolean captionFits(UIContext context, String label, int w)
    {
        return context.batcher.getFont().getWidth(label) < w - CAPTION_PADDING * 2;
    }

    /** A caption along the bottom of a cell, in the strip kept clear for it. */
    public static void caption(UIContext context, String label, int x, int y, int w, int h, boolean bright)
    {
        caption(context, label, x, y, w, h, bright, 1F);
    }

    /**
     * The same caption, faded along with the picture above it.
     *
     * <p>Nothing is drawn behind it: cells keep {@link #CAPTION_HEIGHT} clear of their picture, so
     * the words have the strip to themselves and the darkening that used to buy them contrast was
     * only shading the cell's own ground.</p>
     */
    public static void caption(UIContext context, String label, int x, int y, int w, int h, boolean bright, float alpha)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        label = font.limitToWidth(label, w - CAPTION_PADDING * 2);

        batcher.textShadow(label, x + (w - font.getWidth(label)) / 2, y + h - CAPTION_HEIGHT + (CAPTION_HEIGHT - font.getHeight()) / 2 + 1, Colors.mulA(bright ? Colors.WHITE : Colors.LIGHTEST_GRAY, alpha));
    }
}
