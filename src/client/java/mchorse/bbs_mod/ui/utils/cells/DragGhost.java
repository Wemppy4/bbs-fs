package mchorse.bbs_mod.ui.utils.cells;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * What a drag carries, drawn beside the cursor: a small stack of cards (up to three, one
 * per item), the front one painted by the host, and a count when there are several. Lit
 * when the cursor is over somewhere the drop would land.
 */
public class DragGhost
{
    public static final int OFFSET = 10;
    public static final int STEP = 4;

    /** Paints the front card's picture into the given rectangle. */
    public interface Painter
    {
        public void paint(UIContext context, int x, int y, int w, int h);
    }

    public static void render(UIContext context, int mouseX, int mouseY, int w, int h, int count, boolean landing, Painter painter)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();
        int x = mouseX + OFFSET;
        int y = mouseY + OFFSET;
        int stack = Math.min(3, count);

        for (int i = stack - 1; i >= 0; i--)
        {
            int ox = x + i * STEP;
            int oy = y + i * STEP;

            batcher.box(ox, oy, ox + w, oy + h, BBSSettings.color(BBSSettings.raisedSurface(), landing ? Colors.A100 : Colors.A50));
            batcher.outline(ox, oy, ox + w, oy + h, landing ? Colors.A100 | primary : BBSSettings.dividerColor(), 1);

            if (i == 0)
            {
                painter.paint(context, ox, oy, w, h);
            }
        }

        if (count > 1)
        {
            batcher.textCard(String.valueOf(count), x + w - 4, y - 4, Colors.WHITE, Colors.A100 | primary, 3);
        }
    }

    /** A caption under the ghost — "Copy…" while Ctrl is held. */
    public static void label(UIContext context, String label, int mouseX, int mouseY, int h, boolean landing)
    {
        int x = mouseX + OFFSET;
        int y = mouseY + OFFSET + h + 8;

        context.batcher.textCard(label, x, y, Colors.WHITE, landing ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.A75, 3);
    }
}
