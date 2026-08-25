package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.function.Consumer;

/**
 * A button whose sign is two icons stacked, one above the other.
 *
 * <p>It exists for the pair of half-height arrows: pointing at each other reads as "bring these
 * together" (fold), pointing apart as "push these open" (unfold). Neither is an icon of its own in
 * the atlas, and neither should be — they are the same two arrows, arranged.</p>
 */
public class UIStackedIcon extends UIIcon
{
    /** Half the gap between the two icons, in pixels. */
    private static final int SPLIT = 5;

    private final Icon top;
    private final Icon bottom;

    public UIStackedIcon(Icon top, Icon bottom, Consumer<UIIcon> callback)
    {
        super(top, callback);

        this.top = top;
        this.bottom = bottom;
    }

    @Override
    protected void renderIcon(UIContext context, int color)
    {
        int x = this.area.mx();
        int y = this.area.my();

        context.batcher.icon(this.top, color, x, y - SPLIT, 0.5F, 0.5F);
        context.batcher.icon(this.bottom, color, x, y + SPLIT, 0.5F, 0.5F);
    }
}
