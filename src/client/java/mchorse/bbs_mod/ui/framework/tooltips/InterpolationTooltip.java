package mchorse.bbs_mod.ui.framework.tooltips;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.renderers.InterpolationRenderer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.interps.IInterp;

import java.util.function.Supplier;

public class InterpolationTooltip implements ITooltip
{
    public float ax;
    public float ay;
    public Supplier<IInterp> interpolation;
    public Supplier<Integer> duration;
    public int margin = 10;

    public InterpolationTooltip(float ax, float ay, Supplier<IInterp> interpolation)
    {
        this(ax, ay, interpolation, null);
    }

    public InterpolationTooltip(float ax, float ay, Supplier<IInterp> interpolation, Supplier<Integer> duration)
    {
        this.ax = ax;
        this.ay = ay;
        this.interpolation = interpolation;
        this.duration = duration;
    }

    public InterpolationTooltip margin(int margin)
    {
        this.margin = margin;

        return this;
    }

    @Override
    public IKey getLabel()
    {
        return IKey.EMPTY;
    }

    @Override
    public void renderTooltip(UIContext context)
    {
        IInterp interpolation = this.interpolation == null ? null : this.interpolation.get();

        if (interpolation == null)
        {
            return;
        }

        int duration = this.duration == null ? 40 : this.duration.get();

        /* The preview sits beside the element, vertically centred on it: ax picks the side */
        Direction direction = this.ax < 0.5F ? Direction.LEFT : Direction.RIGHT;
        int w = InterpolationRenderer.PREVIEW_WIDTH;
        int h = InterpolationRenderer.PREVIEW_HEIGHT;

        TooltipPlacement.place(context, context.tooltip.area, w, h, direction, this.margin, 0, Area.SHARED);
        InterpolationRenderer.renderInterpolationPreview(interpolation, context, Area.SHARED, duration);
    }
}