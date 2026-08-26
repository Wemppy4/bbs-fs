package mchorse.bbs_mod.ui.framework.tooltips.styles;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;

public abstract class TooltipStyle
{
    public static final TooltipStyle LIGHT = new LightTooltipStyle();
    public static final TooltipStyle DARK = new DarkTooltipStyle();

    /**
     * Tooltips follow the surfaces they sit on: a light interface gets the
     * light tooltip. Nothing picks this by hand any more.
     */
    public static TooltipStyle get()
    {
        return BBSSettings.lightSurfaces() ? LIGHT : DARK;
    }

    public abstract void renderBackground(UIContext context, Area area);

    public abstract int getTextColor();

    public abstract int getForegroundColor();
}
