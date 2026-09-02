package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * "Let's make it look the way you like": the two colours everything else is derived from,
 * side by side, and four ready-made pairs under them for those who would rather choose than
 * mix. The screen itself is the preview — it repaints as the colours change.
 */
public class UIAppearancePage extends UIWelcomePage
{
    private static final int SWATCH_H = 40;

    private final UIColor primary;
    private final UIColor secondary;

    public UIAppearancePage()
    {
        super(UIKeys.ONBOARDING_APPEARANCE_TITLE, UIKeys.ONBOARDING_APPEARANCE_SLOGAN);

        this.primary = UIValueFactory.colorUI(BBSSettings.primaryColor, null);
        this.secondary = UIValueFactory.colorUI(BBSSettings.secondaryColor, null);

        UIElement colors = new UIElement();
        UIElement swatches = new UIElement();

        colors.row(8).height(UIConstants.CONTROL_HEIGHT);
        colors.add(this.primary, this.secondary);
        swatches.row(6).height(SWATCH_H);
        swatches.add(
            this.swatch(UIKeys.ONBOARDING_APPEARANCE_SWATCH_DARK, 0x1d1d1d, 0xff3242),
            this.swatch(UIKeys.ONBOARDING_APPEARANCE_SWATCH_LIGHT, 0xf3f3f3, 0xe0273a),
            this.swatch(UIKeys.ONBOARDING_APPEARANCE_SWATCH_WARM, 0x231d18, 0xf0a030),
            this.swatch(UIKeys.ONBOARDING_APPEARANCE_SWATCH_COOL, 0x171b22, 0x4c9cff)
        );

        this.body.column(12).vertical().stretch();
        this.body.add(colors, swatches);
    }

    private UISwatch swatch(IKey label, int secondary, int primary)
    {
        return new UISwatch(label, secondary, primary, (s) -> this.apply(s.secondary, s.primary));
    }

    private void apply(int secondary, int primary)
    {
        BBSSettings.secondaryColor.set(secondary);
        BBSSettings.primaryColor.set(primary);
        this.secondary.setColor(secondary);
        this.primary.setColor(primary);
    }

    /** One ready-made pair: the surface with the accent sitting on it, and a name under. */
    private static class UISwatch extends UIClickable<UISwatch>
    {
        public final IKey label;
        public final int secondary;
        public final int primary;

        public UISwatch(IKey label, int secondary, int primary, Consumer<UISwatch> callback)
        {
            super(callback);

            this.label = label;
            this.secondary = secondary;
            this.primary = primary;
        }

        @Override
        protected UISwatch get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            Area area = this.area;
            int tileH = area.h - font.getHeight() - 4;
            boolean current = (BBSSettings.secondaryColor.get() & Colors.RGB) == this.secondary
                && (BBSSettings.primaryColor.get() & Colors.RGB) == this.primary;

            context.batcher.box(area.x, area.y, area.ex(), area.y + tileH, Colors.A100 | this.secondary);
            context.batcher.box(area.mx() - 4, area.y + tileH / 2 - 4, area.mx() + 4, area.y + tileH / 2 + 4, Colors.A100 | this.primary);

            if (current)
            {
                context.batcher.outline(area.x, area.y, area.ex(), area.y + tileH, Colors.A100 | this.primary, 2);
            }
            else if (this.hover)
            {
                context.batcher.outline(area.x, area.y, area.ex(), area.y + tileH, Colors.setA(Colors.WHITE, 0.5F), 1);
            }

            String label = font.limitToWidth(this.label.get(), area.w);

            context.batcher.text(label, area.mx() - font.getWidth(label) / 2, area.y + tileH + 4, this.hover ? Colors.WHITE : DIMMED, false);
        }
    }
}
