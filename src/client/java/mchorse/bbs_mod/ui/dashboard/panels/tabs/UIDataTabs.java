package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;

public class UIDataTabs extends UITabStrip
{
    public static final int TABS_HEIGHT_PX = 18;

    private static final int TAB_MIN_WIDTH = 110;
    private static final int TAB_MAX_WIDTH = 230;

    public final IUITabs host;
    public final UIIcon add;
    private final ArrayList<UIDataTabElement> elements = new ArrayList<>();

    public UIDataTabs(IUITabs host)
    {
        super(ScrollDirection.HORIZONTAL);

        this.host = host;
        this.scroll.scrollSpeed = 20;
        this.background(BBSSettings::chromeSurface);
        this.activeColor(BBSSettings::baseSurface);
        this.hoverColor(() -> BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        this.active(host::getCurrentTab);
        this.onSelect(host::switchTab);
        this.onClose(host::closeTab);

        this.add = new UIIcon(Icons.ADD, (b) -> host.addTab());
        this.add.wh(TABS_HEIGHT_PX, TABS_HEIGHT_PX);
    }

    public void sync()
    {
        if (!this.host.areTabsEnabled())
        {
            this.setVisible(false);
            return;
        }

        this.setVisible(true);

        double scrollPos = this.scroll.getScroll();
        int count = this.host.getTabCount();

        while (this.elements.size() < count)
        {
            this.elements.add(new UIDataTabElement(this, this.host, TABS_HEIGHT_PX));
        }

        while (this.elements.size() > count)
        {
            UIDataTabElement removed = this.elements.remove(this.elements.size() - 1);
            removed.removeFromParent();
        }

        this.removeAll();

        FontRenderer font = Batcher2D.getDefaultTextRenderer();
        int baseMin = UIDataTabElement.measureWidth(font, this.host.getNewTabLabel());

        baseMin = Math.max(TAB_MIN_WIDTH, Math.min(TAB_MAX_WIDTH, baseMin));

        boolean hasNewTab = false;

        for (int i = 0; i < count; i++)
        {
            IKey label = this.host.getTabLabel(i);
            int w = UIDataTabElement.measureWidth(font, label);

            w = Math.max(baseMin, Math.min(TAB_MAX_WIDTH, w));
            hasNewTab |= this.host.isNewTab(i);

            UIDataTabElement tabElement = this.elements.get(i);

            tabElement.setTab(i, label, this.host.getTabTooltip(i), this.host.getTabIcon(i));
            tabElement.wh(w, TABS_HEIGHT_PX);
            this.addTab(tabElement);
        }

        if (!hasNewTab)
        {
            this.add(this.add);
        }

        this.resize();
        this.scroll.setScroll(scrollPos);
    }
}
