package mchorse.bbs_mod.ui.framework.tooltips;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.styles.TooltipStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;

import java.util.List;

public class LabelTooltip implements ITooltip
{
    public IKey label;
    public int width = 200;
    public Direction direction;

    public LabelTooltip(IKey label, Direction direction)
    {
        this.label = label;
        this.direction = direction;
    }

    public LabelTooltip(IKey label, int width, Direction direction)
    {
        this(label, direction);
        this.width = width;
    }

    @Override
    public IKey getLabel()
    {
        return this.label;
    }

    @Override
    public void renderTooltip(UIContext context)
    {
        String label = this.label.get();

        if (label.isEmpty())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        List<String> strings = font.wrap(label, this.width);

        if (strings.isEmpty())
        {
            return;
        }

        TooltipStyle style = TooltipStyle.get();
        int w = strings.size() == 1 ? font.getWidth(strings.get(0)) : this.width;
        int h = (font.getHeight() + 4) * strings.size() - 4;

        TooltipPlacement.place(context, context.tooltip.area, w, h, this.direction, 6, 3, Area.SHARED);

        Area.SHARED.offset(3);
        style.renderBackground(context, Area.SHARED);
        Area.SHARED.offset(-3);

        for (String line : strings)
        {
            context.batcher.text(line, Area.SHARED.x, Area.SHARED.y, style.getTextColor());

            Area.SHARED.y += font.getHeight() + 4;
        }
    }
}
