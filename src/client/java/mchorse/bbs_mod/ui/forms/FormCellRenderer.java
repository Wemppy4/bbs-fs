package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.cells.CellPainter;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How a single form cell looks: the picture of the form on the shared {@link CellPainter
 * cell ground}, its name once the cell is wide enough, its hotkey, and the quick action bar
 * while hovered.
 */
public class FormCellRenderer
{
    /** Cell width from which a name strip is drawn along the bottom edge. */
    public static final int NAME_THRESHOLD = 80;

    public static boolean hasName(int cellWidth)
    {
        return cellWidth >= NAME_THRESHOLD;
    }

    /**
     * Whether the cell says the form's whole name — it has a strip and the name fits in it.
     * What the cell can't say, the grid says by the cursor instead.
     */
    public static boolean showsWholeName(UIContext context, Form form, int w)
    {
        return hasName(w) && CellPainter.captionFits(context, form.getDisplayName(), w);
    }

    public static void render(UIContext context, Form form, int x, int y, int w, int h, CellState state, CellAction[] actions)
    {
        context.batcher.clip(x, y, w, h, context);

        CellPainter.ground(context, x, y, w, h, state);
        FormUtilsClient.renderPreview(form, context, x, y, x + w, y + h);
        CellPainter.dim(context, x, y, w, h, state);

        if (hasName(w))
        {
            CellPainter.caption(context, form.getDisplayName(), x, y, w, h, state.hover || state.selected);
        }

        if (state.hover && !state.dragged && CellActionBar.fits(w) && actions.length > 0)
        {
            CellActionBar.render(context, x, y, w, actions, state.hoveredAction);
        }
        else
        {
            renderHotkey(context, form, x, y, w);
        }

        CellPainter.frames(context, x, y, w, h, state);

        context.batcher.unclip(context);
    }

    private static void renderHotkey(UIContext context, Form form, int x, int y, int w)
    {
        int keybind = form.hotkey.get();

        if (keybind <= 0)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String key = font.limitToWidth(KeyCodes.getName(keybind), w - 8);

        context.batcher.textCard(key, x + w - font.getWidth(key) - 4, y + 4, Colors.WHITE, Colors.A50, 2);
    }
}
