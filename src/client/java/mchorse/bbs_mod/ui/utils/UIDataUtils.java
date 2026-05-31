package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.renderers.InputRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.Collection;
import java.util.function.Consumer;

public class UIDataUtils
{
    public static void requestNames(ContentType type, Consumer<Collection<String>> consumer)
    {
        type.getRepository().requestKeys(consumer);
    }

    public static void renderRightClickHere(UIContext context, Area area)
    {
        int primary = BBSSettings.primaryColor.get();
        double ticks = context.getTickTransition() % 80D;
        double factor = Math.abs(ticks / 80D * 2 - 1F);

        factor = Interpolations.EXP_INOUT.interpolate(0, 1, factor);

        double factor2 = Lerps.envelope(ticks, 37, 40, 40, 43);

        factor2 = Interpolations.CUBIC_OUT.interpolate(0, 1, factor2);

        int offset = (int) (factor * 70 + factor2 * 2);

        context.batcher.dropCircleShadow(area.mx(), area.my() + (int) (factor * 70), 16, 0, 16, Colors.A50 | primary, primary);
        InputRenderer.renderMouseButtons(context.batcher, area.mx() - 6, area.my() - 8 + offset, 0, false, factor2 > 0, false, false);

        String label = UIKeys.GENERAL_RIGHT_CLICK.get();
        int w = (int) (area.w / 1.1F);
        int background = BBSSettings.baseSurface();
        int color = Colors.lerp(background, 0x444444, 1 - (float) factor);

        color = Colors.setA(color, MathUtils.clamp(1 - (float) factor, 10F / 255F, 1F));

        context.batcher.wallText(label, area.mx() - w / 2, area.my() - 20, color, w, 12, 0.5F, 1, true);

        context.batcher.gradientVBox(area.x, area.my() + 20, area.ex(), area.my() + 40, background & Colors.RGB, background);
        context.batcher.box(area.x, area.my() + 40, area.ex(), area.my() + 90, background);
    }
}