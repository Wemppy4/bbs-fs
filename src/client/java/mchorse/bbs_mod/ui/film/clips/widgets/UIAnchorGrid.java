package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A 3x3 grid of screen anchor presets (corners, edge centers, middle).
 * Clicking a dot picks that point of the screen.
 */
public class UIAnchorGrid extends UIElement
{
    public IAnchorCallback callback;

    private float valueX = -1F;
    private float valueY = -1F;

    public UIAnchorGrid(IAnchorCallback callback)
    {
        super();

        this.callback = callback;

        this.h(40);
    }

    public void setValue(float x, float y)
    {
        this.valueX = x;
        this.valueY = y;
    }

    private float fraction(int index)
    {
        return index / 2F;
    }

    private int cellIndex(int mouse, int start, int size)
    {
        int index = (mouse - start) * 3 / size;

        return index < 0 ? 0 : Math.min(index, 2);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY) && context.mouseButton == 0)
        {
            float x = this.fraction(this.cellIndex(context.mouseX, this.area.x, this.area.w));
            float y = this.fraction(this.cellIndex(context.mouseY, this.area.y, this.area.h));

            this.setValue(x, y);

            if (this.callback != null)
            {
                this.callback.accept(x, y);
            }

            UIUtils.playClick();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.area.render(context.batcher, Colors.A50);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);

        boolean inside = this.area.isInside(context.mouseX, context.mouseY);
        int hoverX = inside ? this.cellIndex(context.mouseX, this.area.x, this.area.w) : -1;
        int hoverY = inside ? this.cellIndex(context.mouseY, this.area.y, this.area.h) : -1;
        int padding = 6;

        for (int ix = 0; ix < 3; ix++)
        {
            for (int iy = 0; iy < 3; iy++)
            {
                float fx = this.fraction(ix);
                float fy = this.fraction(iy);
                int x = this.area.x + padding + (int) ((this.area.w - padding * 2) * fx);
                int y = this.area.y + padding + (int) ((this.area.h - padding * 2) * fy);
                boolean active = this.valueX == fx && this.valueY == fy;
                boolean hover = ix == hoverX && iy == hoverY;
                int size = active || hover ? 2 : 1;
                int color = active ? Colors.WHITE : (hover ? 0xffcccccc : 0xff888888);

                context.batcher.box(x - size, y - size, x + size + 1, y + size + 1, color);
            }
        }
    }

    public interface IAnchorCallback
    {
        public void accept(float x, float y);
    }
}
