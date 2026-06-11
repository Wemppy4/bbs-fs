package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

public class TimelineRulerRenderer
{
    public static final int TIMELINE_BLOCK_HEIGHT = 21;
    public static final int RULER_BLOCK_HEIGHT = 21;

    /** Ticks per second in BBS, mirrors {@link mchorse.bbs_mod.camera.utils.TimeUtils}. */
    private static final int FPS = 20;

    /** "Nice" 1-2-5 steps in raw ticks, used when the ruler labels ticks. */
    private static final int[] STEPS_TICKS =
    {
        1, 2, 5, 10, 20, 50, 100, 200, 500,
        1000, 2000, 5000, 10000, 20000, 50000, 100000
    };

    /** "Nice" steps that land on round (sub)seconds, used when the ruler labels seconds. */
    private static final int[] STEPS_SECONDS =
    {
        1, 2, 5, 10,
        FPS, FPS * 2, FPS * 5, FPS * 10, FPS * 20,
        FPS * 50, FPS * 100, FPS * 200,
        FPS * 500, FPS * 1000, FPS * 2000, FPS * 5000
    };

    private static final int MIN_LABEL_PADDING = 18;
    private static final int MIN_MAJOR_GAP = 44;
    private static final int MIN_MINOR_GAP = 5;
    private static final int ITERATION_CAP = 4096;

    private static final float MAJOR_ALPHA = 0.55F;
    private static final float MINOR_ALPHA = 0.28F;
    private static final float LABEL_ALPHA = 0.72F;
    private static final float GRID_MAJOR_ALPHA = 0.35F;
    private static final float GRID_MINOR_ALPHA = 0.16F;

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
        int startTick,
        int durationTick,
        IntUnaryOperator toGraphX,
        IntFunction<String> labelFormatter
    )
    {
        render(context, area, startTick, durationTick, toGraphX, labelFormatter, null);
    }

    public static void render(
        UIContext context,
        Area area,
        int startTick,
        int durationTick,
        IntUnaryOperator toGraphX,
        IntFunction<String> labelFormatter,
        Consumer<UIContext> rulerDecorator
    )
    {
        int timelineBottom = getTimelineBottom(area);
        int rulerBottom = getRulerBottom(area);
        int timelineEndX = durationTick > 0 ? toGraphX.applyAsInt(durationTick) : Integer.MAX_VALUE;
        int visibleEx = Math.min(area.ex(), timelineEndX);

        context.batcher.clip(area, context);
        context.batcher.box(area.x, area.y, area.ex(), rulerBottom, BBSSettings.chromeSurface());

        if (visibleEx < area.ex())
        {
            context.batcher.box(Math.max(area.x, visibleEx), area.y, area.ex(), rulerBottom, BBSSettings.chromeSurface());
        }

        if (rulerDecorator != null)
        {
            rulerDecorator.accept(context);
        }

        renderTicks(context, area, startTick, timelineBottom, visibleEx, toGraphX, labelFormatter);

        if (timelineBottom < rulerBottom)
        {
            context.batcher.box(area.x, timelineBottom - 1, area.ex(), timelineBottom, BBSSettings.color(BBSSettings.dividerColor(), Colors.A50));
        }

        context.batcher.box(area.x, rulerBottom - 1, area.ex(), rulerBottom, BBSSettings.color(BBSSettings.dividerColor(), Colors.A75));
        context.batcher.unclip(context);
    }

    private static void renderTicks(
        UIContext context,
        Area area,
        int startTick,
        int timelineBottom,
        int visibleEx,
        IntUnaryOperator toGraphX,
        IntFunction<String> labelFormatter
    )
    {
        double pxPerTick = pixelsPerTick(toGraphX);

        if (pxPerTick <= 0)
        {
            return;
        }

        int step = chooseStep(area, startTick, pxPerTick, context.batcher.getFont(), labelFormatter);
        int minor = minorStep(step, pxPerTick);
        int labelMargin = (int) Math.ceil(step * pxPerTick);

        int majorColor = Colors.setA(BBSSettings.dividerColor(), MAJOR_ALPHA);
        int minorColor = Colors.setA(BBSSettings.dividerColor(), MINOR_ALPHA);
        int labelColor = Colors.setA(Colors.WHITE, LABEL_ALPHA);

        int lineBottom = timelineBottom - 1;
        int majorTop = area.y + 2;
        int minorTop = lineBottom - Math.max(4, Math.round((lineBottom - majorTop) * 0.4F));

        long first = Math.max(0, (long) Math.floor(startTick / (double) minor) * minor - step);

        for (long tick = first, i = 0; i < ITERATION_CAP; tick += minor, i++)
        {
            int x = toGraphX.applyAsInt((int) tick);

            if (x >= visibleEx)
            {
                break;
            }

            boolean major = tick % step == 0;

            if (x >= area.x)
            {
                context.batcher.box(x, major ? majorTop : minorTop, x + 1, lineBottom, major ? majorColor : minorColor);
            }

            if (major && x > area.x - labelMargin)
            {
                context.batcher.textShadow(labelFormatter.apply((int) tick), x + 4, area.y + 2, labelColor);
            }
        }
    }

    /**
     * Draw the ruler's vertical lines extended over the whole track area, aligned exactly
     * with the labeled ticks of {@link #render}. Meant to be called as an overlay after the
     * tracks are painted.
     */
    public static void renderGrid(
        UIContext context,
        Area area,
        int top,
        int startTick,
        int durationTick,
        IntUnaryOperator toGraphX,
        IntFunction<String> labelFormatter
    )
    {
        double pxPerTick = pixelsPerTick(toGraphX);

        if (pxPerTick <= 0 || top >= area.ey())
        {
            return;
        }

        int step = chooseStep(area, startTick, pxPerTick, context.batcher.getFont(), labelFormatter);
        int minor = minorStep(step, pxPerTick);
        int timelineEndX = durationTick > 0 ? toGraphX.applyAsInt(durationTick) : Integer.MAX_VALUE;
        int visibleEx = Math.min(area.ex(), timelineEndX);

        int majorColor = Colors.setA(BBSSettings.dividerColor(), GRID_MAJOR_ALPHA);
        int minorColor = Colors.setA(BBSSettings.dividerColor(), GRID_MINOR_ALPHA);

        context.batcher.clip(area.x, top, area.ex(), area.ey(), context);

        long first = Math.max(0, (long) Math.floor(startTick / (double) minor) * minor);

        for (long tick = first, i = 0; i < ITERATION_CAP; tick += minor, i++)
        {
            int x = toGraphX.applyAsInt((int) tick);

            if (x >= visibleEx)
            {
                break;
            }

            if (x >= area.x)
            {
                context.batcher.box(x, top, x + 1, area.ey(), tick % step == 0 ? majorColor : minorColor);
            }
        }

        context.batcher.unclip(context);
    }

    /**
     * Derive on-screen pixels per tick from the projection by probing a wide span,
     * so the result is immune to integer rounding at low zoom.
     */
    private static double pixelsPerTick(IntUnaryOperator toGraphX)
    {
        int probe = 100000;

        return (toGraphX.applyAsInt(probe) - toGraphX.applyAsInt(0)) / (double) probe;
    }

    private static int chooseStep(Area area, int startTick, double pxPerTick, FontRenderer font, IntFunction<String> labelFormatter)
    {
        int rightTick = startTick + (int) (area.w / pxPerTick);
        int labelWidth = Math.max(font.getWidth(labelFormatter.apply(Math.max(startTick, 0))), font.getWidth(labelFormatter.apply(rightTick)));
        int minMajorPx = Math.max(MIN_MAJOR_GAP, labelWidth + MIN_LABEL_PADDING);

        return niceStep(minMajorPx / pxPerTick);
    }

    private static int minorStep(int step, double pxPerTick)
    {
        int minor = Math.max(1, step / subdivisions(step));

        return minor * pxPerTick < MIN_MINOR_GAP ? step : minor;
    }

    private static int niceStep(double desired)
    {
        int[] steps = BBSSettings.editorSeconds.get() ? STEPS_SECONDS : STEPS_TICKS;

        for (int step : steps)
        {
            if (step >= desired)
            {
                return step;
            }
        }

        return steps[steps.length - 1];
    }

    /**
     * Pick how many minor ticks divide a major step so that minors land on round values.
     */
    private static int subdivisions(int step)
    {
        int decade = 1;

        while (decade * 10 <= step)
        {
            decade *= 10;
        }

        switch (Math.round(step / (float) decade))
        {
            case 2:
            case 4:
                return 4;
            default:
                return 5;
        }
    }
}
