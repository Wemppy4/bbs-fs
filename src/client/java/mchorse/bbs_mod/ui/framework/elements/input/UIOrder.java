package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * Reorderable chip strip bound to a {@link ValueOrder}: every token renders
 * as a small card, grabbing one drags it sideways through the row while the
 * rest reflow around the empty slot, and releasing commits the new order.
 * Right click resets to the default order.
 */
public class UIOrder extends UIElement
{
    /** Gap between neighbouring chip cards. */
    private static final int GAP = 4;
    /** The card padding {@link mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D#textCard}
     *  draws with, so layout and hitboxes line up with the rendered cards. */
    private static final int CARD = 3;

    private final ValueOrder value;
    /** Working copy of the order; diverges from the value only mid-drag. */
    private final List<String> order = new ArrayList<>();

    private int dragging = -1;
    /** Cursor offset within the grabbed chip, so it doesn't jump under the cursor. */
    private int grabOffset;

    public UIOrder(ValueOrder value)
    {
        this.value = value;

        this.context((menu) -> menu.action(Icons.REFRESH, UIKeys.ORDER_RESET, this.value::reset));

        /* The chip set is fixed (only the order changes), so the natural strip
         * width is known up front; sizing to it leaves the rest of the row to
         * the setting's label instead of clipping it against a guessed width. */
        this.syncFromValue();
        this.w(this.totalWidth(Batcher2D.getDefaultTextRenderer()));
    }

    private String label(String token)
    {
        return this.value.getLabel(token).get();
    }

    private int color(String token)
    {
        int color = this.value.getColor(token);

        return color == 0 ? Colors.WHITE : color;
    }

    /** Card box width of a chip (text plus {@link #CARD} padding on both sides). */
    private int width(FontRenderer font, String token)
    {
        return font.getWidth(this.label(token)) + CARD * 2 - 1;
    }

    private int totalWidth(FontRenderer font)
    {
        int total = (this.order.size() - 1) * GAP;

        for (String token : this.order)
        {
            total += this.width(font, token);
        }

        return total;
    }

    /** Text x of every chip, the strip right-aligned within the element. */
    private int[] chipXs(FontRenderer font)
    {
        int[] xs = new int[this.order.size()];
        int x = Math.max(this.area.x, this.area.ex() - this.totalWidth(font));

        for (int i = 0; i < xs.length; i++)
        {
            xs[i] = x + CARD;
            x += this.width(font, this.order.get(i)) + GAP;
        }

        return xs;
    }

    /** Text x the lifted chip follows the cursor at, clamped to the strip. */
    private int liftedX(UIContext context, FontRenderer font)
    {
        int total = this.totalWidth(font);
        int left = Math.max(this.area.x, this.area.ex() - total);
        int width = this.width(font, this.order.get(this.dragging));

        return MathUtils.clamp(context.mouseX - this.grabOffset, left + CARD, left + total - width + CARD);
    }

    private int chipAt(UIContext context, FontRenderer font)
    {
        int[] xs = this.chipXs(font);
        int y = this.area.my(font.getHeight());

        if (context.mouseY < y - CARD || context.mouseY >= y + font.getHeight() + CARD)
        {
            return -1;
        }

        for (int i = 0; i < xs.length; i++)
        {
            int left = xs[i] - CARD;

            if (context.mouseX >= left && context.mouseX < left + this.width(font, this.order.get(i)))
            {
                return i;
            }
        }

        return -1;
    }

    private void syncFromValue()
    {
        List<String> current = this.value.get();

        if (!this.order.equals(current))
        {
            this.order.clear();
            this.order.addAll(current);
        }
    }

    /**
     * Re-slot the lifted chip from the raw cursor position: the insertion
     * index is how many of the other chips' centres (laid out compactly,
     * without the lifted chip) sit left of the cursor. Working off the
     * cursor rather than the clamped chip position lets the chip reach both
     * ends regardless of chip width differences, and the thresholds don't
     * move with the ghost slot, so the reorder can't oscillate.
     */
    private void updateDrag(UIContext context, FontRenderer font)
    {
        int insert = 0;
        int x = Math.max(this.area.x, this.area.ex() - this.totalWidth(font));

        for (int i = 0; i < this.order.size(); i++)
        {
            if (i == this.dragging)
            {
                continue;
            }

            int width = this.width(font, this.order.get(i));

            if (context.mouseX > x + width / 2)
            {
                insert += 1;
            }

            x += width + GAP;
        }

        if (insert != this.dragging)
        {
            this.order.add(insert, this.order.remove(this.dragging));
            this.dragging = insert;
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            FontRenderer font = context.batcher.getFont();
            int chip = this.chipAt(context, font);

            if (chip != -1)
            {
                this.dragging = chip;
                this.grabOffset = context.mouseX - this.chipXs(font)[chip];

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.dragging != -1 && context.mouseButton == 0)
        {
            this.dragging = -1;

            if (!this.order.equals(this.value.get()))
            {
                this.value.set(new ArrayList<>(this.order));
                UIUtils.playClick();
            }

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();

        if (this.dragging == -1)
        {
            this.syncFromValue();
        }
        else
        {
            this.updateDrag(context, font);
        }

        int[] xs = this.chipXs(font);
        int y = this.area.my(font.getHeight());
        int hovered = this.dragging == -1 && this.area.isInside(context) ? this.chipAt(context, font) : -1;

        for (int i = 0; i < this.order.size(); i++)
        {
            /* The lifted chip's slot stays empty; the chip itself rides the cursor on top. */
            if (i == this.dragging)
            {
                continue;
            }

            String token = this.order.get(i);
            int background = i == hovered ? BBSSettings.primaryColor(Colors.A50) : Colors.A50;

            context.batcher.textCard(this.label(token), xs[i], y, this.color(token), background, CARD);
        }

        if (this.dragging != -1)
        {
            String token = this.order.get(this.dragging);

            context.batcher.textCard(this.label(token), this.liftedX(context, font), y, this.color(token), BBSSettings.primaryColor(Colors.A75), CARD);
        }

        super.render(context);
    }
}
