package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.MotionPathValue;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIEntryList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * The film's motion paths, every replay's together: a row reads {@code replay: bone} after a square
 * of the path's colour, so a row and its line on screen are matched at a glance.
 */
public class UIMotionPathList extends UIEntryList<UIMotionPathList.Entry>
{
    private static final int SWATCH = 6;
    private static final int SWATCH_GAP = 4;

    public UIMotionPathList(Consumer<List<Entry>> callback)
    {
        super(callback, UIMotionPathList::name);

        this.broken((entry) -> MotionPath.isBroken(entry.replay(), entry.path()));
    }

    private static String name(Entry entry)
    {
        String key = entry.path().target.get();
        String part = key.equals(MotionPath.ANCHOR) ? UIKeys.FILM_CONTROLLER_MOTION_PATH_ANCHOR.get() : MotionPath.shortName(key);

        return part.isEmpty() ? entry.replay().getName() : entry.replay().getName() + ": " + part;
    }

    /**
     * The list sits inside the motion path button's own menu, where a right click opens nothing (one
     * menu at a time). Here it opens over that menu instead, which a step back returns to.
     */
    @Override
    protected boolean mouseClickedContextMenu(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 1 && context.hasContextMenu())
        {
            UIContextMenu menu = this.createContextMenu(context);

            if (menu != null && !menu.isEmpty())
            {
                context.replaceContextMenu(menu);

                return true;
            }
        }

        return super.mouseClickedContextMenu(context);
    }

    @Override
    protected void renderElementPart(UIContext context, Entry element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int swatchX = x + this.rowContentX(element);
        int swatchY = y + (this.scroll.scrollItemSize - SWATCH) / 2;
        int textX = swatchX + SWATCH + SWATCH_GAP;
        String label = font.limitToWidth(this.elementToString(context, i, element), this.rowContentEnd(x) - textX);

        context.batcher.box(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, Colors.A100 | element.path().color.get());
        context.batcher.textShadow(label, textX, y + (this.scroll.scrollItemSize - font.getHeight()) / 2, this.rowColor(element, hover || selected));
    }

    /** One path in the list, with the replay it belongs to. */
    public record Entry(Replay replay, MotionPathValue path)
    {}
}
