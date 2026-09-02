package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * What a place with nothing in it says instead of showing nothing: a line telling what to do
 * to fill it. Where the way to fill it is a right click on the spot itself, a mouse pointer
 * presses its right button under the line, in a loop, so the gesture is shown and not only named.
 *
 * <p>The line says what to DO, never "pick something": a state nobody can act their way out of
 * is worse than an empty box.</p>
 */
public class EmptyStateRenderer
{
    /** How long one loop of the mouse animation takes, in ticks. */
    private static final double PERIOD = 80D;

    /** How far the pointer travels down before the fade takes it. */
    private static final int TRAVEL = 70;

    /**
     * Just the line, centered, for a place that fills up from somewhere else — a properties
     * panel waiting for a pick. No pointer: the gesture belongs to the other element.
     */
    public static void renderHint(UIContext context, Area area, IKey label)
    {
        FontRenderer font = context.batcher.getFont();
        int w = (int) (area.w / 1.5F);

        context.batcher.wallText(label.get(), area.mx() - w / 2, area.my() - font.getHeight(), Colors.setA(Colors.WHITE, 0.5F), w, 12, 0.5F, 0F, true);
    }

    /**
     * The label with the pointer pressing right under it, both fading as the pointer leaves.
     * Everything is drawn within the area, so a short one loses the tail rather than painting
     * over whatever sits below it.
     *
     * <p>The background is what the pointer sinks into and what the label fades towards, so it
     * has to be the rung the list itself sits on — a lighter one leaves a visible patch.</p>
     */
    public static void renderRightClickHere(UIContext context, Area area, IKey label, int background)
    {
        int primary = BBSSettings.primaryColor.get();
        double ticks = context.getTickTransition() % PERIOD;
        double factor = Math.abs(ticks / PERIOD * 2 - 1F);

        factor = Interpolations.EXP_INOUT.interpolate(0, 1, factor);

        /* The click itself: a short press right before the pointer starts leaving */
        double factor2 = Lerps.envelope(ticks, 37, 40, 40, 43);

        factor2 = Interpolations.CUBIC_OUT.interpolate(0, 1, factor2);

        int offset = (int) (factor * TRAVEL + factor2 * 2);

        context.batcher.dropCircleShadow(area.mx(), area.my() + (int) (factor * TRAVEL), 16, 0, 16, Colors.A50 | primary, primary);
        InputRenderer.renderMouseButtons(context.batcher, area.mx() - 6, area.my() - 8 + offset, 0, false, factor2 > 0, false, false);

        int w = (int) (area.w / 1.1F);
        int color = Colors.lerp(background, 0x444444, 1 - (float) factor);

        color = Colors.setA(color, MathUtils.clamp(1 - (float) factor, 10F / 255F, 1F));

        context.batcher.wallText(label.get(), area.mx() - w / 2, area.my() - 20, color, w, 12, 0.5F, 1, true);

        /* Where the pointer goes to disappear: a fade into the surface, then the surface itself */
        int fadeTop = area.my() + 20;
        int fadeBottom = Math.min(area.ey(), fadeTop + 20);
        int maskBottom = Math.min(area.ey(), area.my() + 90);

        if (fadeBottom > fadeTop)
        {
            context.batcher.gradientVBox(area.x, fadeTop, area.ex(), fadeBottom, background & Colors.RGB, background);
        }

        if (maskBottom > fadeBottom)
        {
            context.batcher.box(area.x, fadeBottom, area.ex(), maskBottom, background);
        }
    }
}
