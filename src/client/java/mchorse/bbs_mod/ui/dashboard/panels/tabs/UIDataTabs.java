package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;

public class UIDataTabs extends UIElement
{
    public static final int TABS_HEIGHT_PX = 18;

    private static final int TAB_MIN_WIDTH = 110;
    private static final int TAB_MAX_WIDTH = 230;
    private static final int TABS_GAP = 0;

    public final IUITabs host;
    public final UIScrollView scroll;
    public final UIIcon add;
    private final ArrayList<UIDataTabElement> tabs = new ArrayList<>();
    private int rightInsetPx;

    public UIDataTabs(IUITabs host)
    {
        this.host = host;
        this.scroll = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.scroll.scroll.scrollSpeed = 20;
        this.scroll.column(TABS_GAP).scroll();
        this.scroll.scroll.noScrollbar();
        this.updateScrollBounds();

        this.add = new UIIcon(Icons.ADD, (b) -> host.addTab());
        this.add.wh(TABS_HEIGHT_PX, TABS_HEIGHT_PX);

        this.add(new UIRenderable(this::renderBackground), this.scroll);
    }

    public void setRightInsetPx(int rightInsetPx)
    {
        this.rightInsetPx = Math.max(0, rightInsetPx);
        this.updateScrollBounds();
    }

    private void updateScrollBounds()
    {
        this.scroll.relative(this).x(TABS_GAP).w(1F, -TABS_GAP * 2 - this.rightInsetPx).h(TABS_HEIGHT_PX);
    }

    public void sync()
    {
        if (!this.host.areTabsEnabled())
        {
            this.setVisible(false);
            return;
        }

        this.setVisible(true);

        double scrollPos = this.scroll.scroll.getScroll();
        int count = this.host.getTabCount();

        while (this.tabs.size() < count)
        {
            this.tabs.add(new UIDataTabElement(this.host, TABS_HEIGHT_PX));
        }

        while (this.tabs.size() > count)
        {
            UIDataTabElement removed = this.tabs.remove(this.tabs.size() - 1);
            removed.removeFromParent();
        }

        this.scroll.removeAll();

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

            UIDataTabElement tabElement = this.tabs.get(i);

            tabElement.setTab(i, label, this.host.getTabTooltip(i), this.host.getTabIcon(i));
            tabElement.wh(w, TABS_HEIGHT_PX);
            this.scroll.add(tabElement);
        }

        if (!hasNewTab)
        {
            this.scroll.add(this.add);
        }

        this.scroll.resize();
        this.scroll.scroll.setScroll(scrollPos);
    }

    private void renderBackground(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());
    }
}
