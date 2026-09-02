package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

public class UIText extends UIElement implements ITextColoring
{
    private IKey temp = IKey.EMPTY;
    private List<String> text;
    private int lineHeight = 12;
    private int color = Colors.WHITE;
    private int hoverColor = Colors.WHITE;
    private boolean shadow = true;
    private int paddingH;
    private int paddingV;
    private float anchorX;

    private int lines;
    private boolean updates;
    private String lastString;

    /**
     * Inner width the current {@link #text} was wrapped against; a different width means the
     * wrap is stale
     */
    private int wrappedWidth = -1;

    public UIText(String text)
    {
        this();

        this.text(text);
    }

    public UIText(IKey text)
    {
        this();

        this.text(text);
    }

    public UIText()
    {
        super();
    }

    private int height()
    {
        FontRenderer font = Batcher2D.getDefaultTextRenderer();
        int height = Math.max(this.lines, 1) * this.lineHeight - (this.lineHeight - font.getHeight());

        return height + this.paddingV * 2;
    }

    public IKey getText()
    {
        return this.temp;
    }

    public UIText text(String text)
    {
        return this.text(IKey.constant(text));
    }

    public UIText text(IKey text)
    {
        this.temp = text;
        this.text = null;
        this.lines = 0;

        return this;
    }

    public UIText lineHeight(int lineHeight)
    {
        this.lineHeight = lineHeight;

        return this;
    }

    public UIText color(int color, boolean shadow)
    {
        this.color = this.hoverColor = color;
        this.shadow = shadow;

        return this;
    }

    public UIText hoverColor(int color)
    {
        this.hoverColor = color;

        return this;
    }

    public UIText padding(int padding)
    {
        return this.padding(padding, padding);
    }

    public UIText padding(int horizontal, int vertical)
    {
        this.paddingH = horizontal;
        this.paddingV = vertical;

        return this;
    }

    public UIText textAnchorX(float anchor)
    {
        this.anchorX = anchor;

        return this;
    }

    public UIText updates()
    {
        this.updates = true;

        return this;
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.color(color, shadow);
    }

    /**
     * Wrap the text against the current width if the cached wrap is stale, and take the
     * resulting height. Returns whether the height changed, i.e. whether whoever lays this
     * element out has to run again.
     */
    private boolean wrap()
    {
        int width = this.area.w - this.paddingH * 2;

        if (this.text != null && this.wrappedWidth == width)
        {
            return false;
        }

        /* New text (or first wrap) means the height flex was never applied yet, so a pass is
         * owed even if the line count happens to match */
        boolean fresh = this.text == null;
        int before = this.height();

        this.text = Batcher2D.getDefaultTextRenderer().wrap(this.temp.get(), width);
        this.lines = this.text.size();
        this.wrappedWidth = width;
        this.h(this.height());

        return fresh || this.height() != before;
    }

    @Override
    public void resize()
    {
        /* Width is known from the previous pass in the common case, so the height is right
         * before the flex is applied and no second pass is needed */
        if (this.area.w > 0)
        {
            this.wrap();
        }

        super.resize();

        if (this.area.w > 0 && this.wrap())
        {
            this.requestLayout();
        }
    }

    /**
     * The height changed after layout already ran: ask for another pass next frame rather
     * than resizing the container from inside a render or a resize
     */
    private void requestLayout()
    {
        UIElement container = this.getParentContainer();

        if (container != null)
        {
            container.invalidateLayout();
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.updates)
        {
            if (this.lastString == null)
            {
                this.lastString = this.temp.get();
            }
            else if (!this.lastString.equals(this.temp.get()))
            {
                this.text = null;
                this.lastString = this.temp.get();
            }
        }

        FontRenderer font = context.batcher.getFont();

        if (this.area.w > 0)
        {
            if (this.wrap())
            {
                this.requestLayout();
            }

            int y = this.paddingV;
            int color = this.area.isInside(context) ? this.hoverColor : this.color;

            for (String line : this.text)
            {
                int x = this.area.x + this.paddingH;

                if (this.anchorX != 0)
                {
                    x = x + (int) (((this.area.w - this.paddingH * 2) - (font.getWidth(line))) * this.anchorX);
                }

                context.batcher.text(line, x, this.area.y + y, color, this.shadow);

                y += this.lineHeight;
            }
        }

        super.render(context);
    }
}