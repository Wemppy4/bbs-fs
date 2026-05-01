package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

public class TimelineRulerRenderer
{
    public static final int TIMELINE_BLOCK_HEIGHT = 21;
    public static final int RULER_BLOCK_HEIGHT = 21;

    private static final int SUBDIVISIONS = 5;
    private static final float MAJOR_ALPHA = 0.55F;
    private static final float MINOR_ALPHA = 0.3F;
    private static final float LABEL_ALPHA = 0.72F;

    public static int getTimelineBottom(Area area)
    {
        return Math.min(area.ey(), area.y + TIMELINE_BLOCK_HEIGHT);
    }

    public static int getRulerBottom(Area area)
    {
        return Math.min(area.ey(), area.y + RULER_BLOCK_HEIGHT);
    }

    public static void render(
        UIContext context,
        Area area,
        int mult,
        int startTick,
        int endTick,
        int durationTick,
        IntUnaryOperator toGraphX,
        IntFunction<String> labelFormatter
    )
    {
        if (mult <= 0)
        {
            return;
        }

        int timelineBottom = getTimelineBottom(area);
        int rulerBottom = getRulerBottom(area);
        int majorTop = area.y + 2;
        int minorTop = Math.max(area.y + 12, timelineBottom - 7);
        int timelineEndX = durationTick > 0 ? toGraphX.applyAsInt(durationTick) : Integer.MAX_VALUE;
        int visibleTimelineEx = Math.min(area.ex(), timelineEndX);
        int max = Integer.MAX_VALUE;

        int start = startTick - (startTick % mult);
        int end = endTick - (endTick % mult);

        start = Math.max(0, Math.min(start, max));
        end = Math.max(mult, Math.min(end, max));

        context.batcher.clip(area, context);
        context.batcher.box(area.x, area.y, area.ex(), rulerBottom, BBSSettings.chromeSurface());
        context.batcher.box(area.x, area.y, area.ex(), rulerBottom, BBSSettings.backgroundTint(Colors.A6));

        if (visibleTimelineEx < area.ex())
        {
            int rightX = Math.max(area.x, visibleTimelineEx);

            context.batcher.box(rightX, area.y, area.ex(), rulerBottom, BBSSettings.chromeSurface());
            context.batcher.box(rightX, area.y, area.ex(), rulerBottom, BBSSettings.backgroundTint(Colors.A6));
        }

        for (int tick = start; tick <= end; tick += mult)
        {
            int x = toGraphX.applyAsInt(tick);

            if (x >= area.ex() || x >= visibleTimelineEx)
            {
                break;
            }

            if (x < area.x)
            {
                continue;
            }

            context.batcher.box(x, majorTop, x + 1, timelineBottom - 1, Colors.setA(BBSSettings.dividerColor(), MAJOR_ALPHA));
            context.batcher.textShadow(labelFormatter.apply(tick), x + 4, area.y + 2, Colors.setA(Colors.WHITE, LABEL_ALPHA));

            int nextX = Math.min(toGraphX.applyAsInt(tick + mult), timelineEndX);
            int interval = nextX - x;

            if (interval >= SUBDIVISIONS * 4)
            {
                for (int k = 1; k < SUBDIVISIONS; k++)
                {
                    int mx = x + Math.round(interval * (k / (float) SUBDIVISIONS));

                    if (mx > area.x && mx < area.ex())
                    {
                        context.batcher.box(mx, minorTop, mx + 1, timelineBottom - 1, Colors.setA(BBSSettings.dividerColor(), MINOR_ALPHA));
                    }
                }
            }
        }

        if (timelineBottom < rulerBottom)
        {
            context.batcher.box(area.x, timelineBottom - 1, area.ex(), timelineBottom, BBSSettings.color(BBSSettings.dividerColor(), Colors.A50));
        }

        context.batcher.box(area.x, rulerBottom - 1, area.ex(), rulerBottom, BBSSettings.color(BBSSettings.dividerColor(), Colors.A75));
        context.batcher.unclip(context);
    }
}
