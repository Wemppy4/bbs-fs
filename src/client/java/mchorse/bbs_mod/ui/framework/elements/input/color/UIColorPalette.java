package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.GridLayout;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.List;
import java.util.function.Consumer;

/**
 * Color palette GUI element
 *
 * This element allows to provide a way to select a color from a grid like
 * list
 */
public class UIColorPalette extends UIElement
{
    public List<Color> colors;
    public Consumer<Color> callback;
    public int cellSize = 10;

    /* Tight square cells, no band or spacing: the whole width is cells */
    private final GridLayout layout = new GridLayout(0, 0, 0, 0, 0, 1F);

    public UIColorPalette(Consumer<Color> callback)
    {
        super();

        this.callback = callback;
    }

    public UIColorPalette colors(List<Color> colors)
    {
        this.colors = colors;

        return this;
    }

    public UIColorPalette cellSize(int cellSize)
    {
        this.cellSize = cellSize;

        return this;
    }

    /** Lay the colors out for the given width; the palette's cells depend only on that and the count. */
    private GridLayout layout(int width)
    {
        return this.layout.set(width, this.cellSize, this.colors.size());
    }

    public int getHeight(int width)
    {
        /* An empty palette still takes one row so the picker's layout doesn't collapse */
        return Math.max(this.cellSize, this.layout(width).getContentHeight(true));
    }

    public boolean hasColor(int index)
    {
        return index >= 0 && index < this.colors.size();
    }

    public int getIndex(UIContext context)
    {
        int cell = this.layout(this.area.w).getIndex(context.mouseX - this.area.x, context.mouseY - this.area.y);

        /* Cells run from the newest color (first) to the oldest (last) */
        return cell < 0 ? -1 : this.colors.size() - 1 - cell;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = this.getIndex(context);

            if (this.hasColor(index) && this.callback != null)
            {
                this.callback.accept(this.colors.get(index));
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* Draw recent colors panel */
        int count = this.colors.size();

        if (count > 0)
        {
            GridLayout layout = this.layout(this.area.w);

            if (this.area.h > this.cellSize)
            {
                context.batcher.iconArea(Icons.CHECKBOARD, this.area.x, this.area.y, this.area.w, this.area.h - this.cellSize);
            }

            context.batcher.iconArea(Icons.CHECKBOARD, this.area.x, this.area.ey() - this.cellSize, count % layout.getPerRow() * this.cellSize, this.cellSize);

            for (int i = count - 1, j = 0; i >= 0; i--, j++)
            {
                Color c = this.colors.get(i);
                int x = this.area.x + layout.getX(j);
                int y = this.area.y + layout.getY(j);

                UIColorPicker.renderAlphaPreviewQuad(context.batcher, x, y, x + this.cellSize, y + this.cellSize, c);
            }
        }

        super.render(context);
    }
}
