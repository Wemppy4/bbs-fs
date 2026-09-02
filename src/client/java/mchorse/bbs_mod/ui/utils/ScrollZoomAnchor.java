package mchorse.bbs_mod.ui.utils;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Keeps whatever is under the cursor where it is on screen while a scrolled content changes
 * its size — zooming a grid of cells, a list of thumbnails, a texture sheet.
 *
 * <p>The content is described by two lookups the caller supplies: which item lies at a
 * content Y, and where that item sits (top and height) in the current layout. The anchor
 * remembers how far into the item the cursor was, lets the caller relayout, then scrolls so
 * the same point of the same item lands back under the cursor.</p>
 *
 * <pre>
 * ScrollZoomAnchor.keep(scroll, mouseY - area.y, this::rowAt, this::rowPlacement, () -> relayout());
 * </pre>
 */
public class ScrollZoomAnchor
{
    /** Where an item sits in the content: its top edge and its height. */
    public record Placement(int top, int height)
    {}

    /**
     * @param scroll    the scroll being zoomed
     * @param mouseY    cursor Y relative to the top of the scroll area
     * @param itemAt    the item under a content Y, or null when nothing is there
     * @param placement where an item sits in the current layout
     * @param relayout  applies the size change and lays the content out again
     */
    public static <T> void keep(Scroll scroll, int mouseY, IntFunction<T> itemAt, Function<T, Placement> placement, Runnable relayout)
    {
        int contentY = mouseY + (int) scroll.getScroll();
        T item = itemAt.apply(contentY);
        float within = 0F;

        if (item != null)
        {
            Placement before = placement.apply(item);

            within = before.height() > 0 ? (contentY - before.top()) / (float) before.height() : 0F;
        }

        relayout.run();

        if (item != null)
        {
            Placement after = placement.apply(item);

            scroll.setScroll(after.top() + within * after.height() - mouseY);
        }
    }
}
