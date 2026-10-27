package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.onboarding.welcome.UIWelcomeOverlayPanel;

/**
 * What happens the first time: the welcome screen, once, when the dashboard first comes up.
 */
public class Onboarding
{
    /** The dashboard screen came up. */
    public static void dashboardOpened(UIDashboard dashboard)
    {
        if (!BBSSettings.onboardingWelcomeSeen.get())
        {
            showWelcome(dashboard.context);
        }
    }

    /** The welcome screen, whether for the first time or from the settings. */
    public static void showWelcome(UIContext context)
    {
        UIWelcomeOverlayPanel panel = new UIWelcomeOverlayPanel();

        panel.onClose((e) -> BBSSettings.onboardingWelcomeSeen.set(true));

        /* It paints the whole screen itself; dimming and blurring under it would be wasted work */
        UIOverlay.addOverlay(context, panel, 1F, 1F).noBackground();
    }
}
