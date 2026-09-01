package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingRow;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * "Where to go next": the places to look when something is unclear, the key that lists every
 * key, and the one big button. There is no "no thanks" — the tour is what the button starts,
 * and it is short.
 */
public class UINextStepsPage extends UIWelcomePage
{
    private static final int COLUMN_W = 220;

    public UINextStepsPage(UIWelcomeOverlayPanel panel)
    {
        super(UIKeys.ONBOARDING_NEXT_TITLE, UIKeys.ONBOARDING_NEXT_SLOGAN);

        UILandingRow tutorials = new UILandingRow(Icons.PLAY, UIKeys.SUPPORTERS_TUTORIALS, (b) -> UIUtils.openWebLink(UILandingScreen.TUTORIALS_LINK));
        UILandingRow wiki = new UILandingRow(Icons.HELP, UIKeys.SUPPORTERS_WIKI, (b) -> UIUtils.openWebLink(UILandingScreen.WIKI_LINK));
        UILandingRow discord = new UILandingRow(Icons.DISCORD, IKey.constant("Discord"), (b) -> UIUtils.openWebLink(UILandingScreen.DISCORD_LINK));

        UILabel keys = UI.label(UIKeys.ONBOARDING_NEXT_KEYS, UILandingRow.HEIGHT).color(DIMMED);

        keys.labelAnchor(0F, 0.5F);

        UIButton start = new UIButton(UIKeys.ONBOARDING_NEXT_START, (b) -> panel.close());

        /* A list reads as a column, not a spread: narrower than the tab, centered under it */
        this.narrow(COLUMN_W);
        this.body.column(2).vertical().stretch();
        this.body.add(tutorials, wiki, discord, keys, new UIElement().h(8), start);
    }
}
