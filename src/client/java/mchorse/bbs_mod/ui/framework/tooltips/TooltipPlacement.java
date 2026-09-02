package mchorse.bbs_mod.ui.framework.tooltips;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * Where a floating box (tooltip, preview, label card) goes: next to its anchor on the
 * asked side, on the opposite side when the screen edge would push it back over the
 * anchor, and always fully on screen.
 *
 * <p>Every hover card in the UI needs the same three steps, so they live here instead of
 * each caller clamping on its own (and forgetting the flip).</p>
 */
public class TooltipPlacement
{
    /**
     * Place a {@code w×h} box on the {@code direction} side of {@code anchor}, {@code gap}
     * pixels away, flip it to the opposite side if it still overlaps the anchor after
     * being pushed inside the screen, and keep {@code padding} pixels from the edges.
     *
     * @return {@code target}, for chaining
     */
    public static Area place(UIContext context, Area anchor, int w, int h, Direction direction, int gap, int padding, Area target)
    {
        placeAt(context, anchor, w, h, direction, gap, padding, target);

        if (target.intersects(anchor))
        {
            placeAt(context, anchor, w, h, direction.opposite(), gap, padding, target);
        }

        return target;
    }

    private static void placeAt(UIContext context, Area anchor, int w, int h, Direction direction, int gap, int padding, Area target)
    {
        int x = anchor.x(direction.anchorX) - (int) (w * (1 - direction.anchorX)) + gap * direction.factorX;
        int y = anchor.y(direction.anchorY) - (int) (h * (1 - direction.anchorY)) + gap * direction.factorY;

        target.set(clampX(context, x, w, padding), clampY(context, y, h, padding), w, h);
    }

    /**
     * Place a {@code w×h} box next to the cursor: to its right and {@code below} or above
     * it, {@code offset} pixels away. Each axis flips to the other side of the cursor when
     * the box would leave the screen, and the result is clamped inside {@code padding}.
     *
     * @return {@code target}, for chaining
     */
    public static Area nearMouse(UIContext context, int w, int h, int offset, boolean below, int padding, Area target)
    {
        int x = context.mouseX + offset;
        int y = below ? context.mouseY + offset : context.mouseY - offset - h;

        if (x + w > context.menu.width - padding)
        {
            x = context.mouseX - offset - w;
        }

        if (below ? y + h > context.menu.height - padding : y < padding)
        {
            y = below ? context.mouseY - offset - h : context.mouseY + offset;
        }

        target.set(clampX(context, x, w, padding), clampY(context, y, h, padding), w, h);

        return target;
    }

    private static int clampX(UIContext context, int x, int w, int padding)
    {
        return MathUtils.clamp(x, padding, context.menu.width - w - padding);
    }

    private static int clampY(UIContext context, int y, int h, int padding)
    {
        return MathUtils.clamp(y, padding, context.menu.height - h - padding);
    }
}
