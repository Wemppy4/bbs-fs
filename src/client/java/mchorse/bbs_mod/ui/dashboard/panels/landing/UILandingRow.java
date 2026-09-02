package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * One line of the landing screen's menu: an icon and a label. Hovering tints the label the way
 * every list in the mod does; there is no box, the hand cursor and the tint are the answer.
 */
public class UILandingRow extends UIClickable<UILandingRow>
{
    public static final int HEIGHT = 20;

    private static final int ICON_X = 2;
    private static final int TEXT_X = 22;

    private final Icon icon;
    private final IKey label;

    /** Wears the bar a colored context action wears — for the one entry that makes something new. */
    private boolean accent;

    public UILandingRow(Icon icon, IKey label, Consumer<UILandingRow> callback)
    {
        super(callback);

        this.icon = icon;
        this.label = label;

        this.h(HEIGHT);
    }

    public UILandingRow accent()
    {
        this.accent = true;

        return this;
    }

    @Override
    protected UILandingRow get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        Area area = this.area;
        FontRenderer font = context.batcher.getFont();
        boolean lit = this.hover && this.isEnabled();

        if (this.accent)
        {
            int color = BBSSettings.primaryColor.get() & Colors.RGB;

            context.batcher.box(area.x, area.y, area.x + 2, area.ey(), Colors.A100 | color);
            context.batcher.gradientHBox(area.x + 2, area.y, area.x + 24, area.ey(), Colors.A25 | color, color);
        }

        String text = font.limitToWidth(this.label.get(), area.w - TEXT_X - 2);

        context.batcher.icon(this.icon, area.x + ICON_X, area.my(), 0F, 0.5F);
        context.batcher.text(text, area.x + TEXT_X, area.y + (area.h - font.getHeight()) / 2 + 1, lit ? Colors.HIGHLIGHT : Colors.WHITE, false);
    }
}
