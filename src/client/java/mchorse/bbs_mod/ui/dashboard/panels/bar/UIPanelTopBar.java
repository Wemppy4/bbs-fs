package mchorse.bbs_mod.ui.dashboard.panels.bar;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.IUITabs;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;

/**
 * The strip along the top of an editor panel: open tabs on the left, the panel's actions on the
 * right.
 *
 * <p>Every editor panel has one, whether or not it has tabs — that is what makes the panels look
 * alike. The bar is the only place that knows how the two halves share the width: the actions
 * measure themselves and the tab strip stops where they begin.</p>
 */
public class UIPanelTopBar extends UIElement
{
    public static final int HEIGHT = UIDataTabs.TABS_HEIGHT_PX;

    public final UIPanelActionBar actions;

    private UIDataTabs tabs;

    public UIPanelTopBar()
    {
        this.actions = new UIPanelActionBar();
        this.actions.relative(this).x(1F).h(HEIGHT).anchorX(1F);

        this.add(new UIRenderable(this::renderBackground), this.actions);
    }

    /**
     * Give this bar a tab strip. Panels that edit one thing at a time (the audio editor) simply
     * never call this and keep the bar for their actions alone.
     */
    public UIDataTabs enableTabs(IUITabs host)
    {
        if (this.tabs == null)
        {
            this.tabs = new UIDataTabs(host);
            this.tabs.relative(this).h(HEIGHT);

            this.addBefore(this.actions, this.tabs);
        }

        return this.tabs;
    }

    public UIDataTabs getTabs()
    {
        return this.tabs;
    }

    public boolean hasTabs()
    {
        return this.tabs != null;
    }

    @Override
    protected void afterResizeApplied()
    {
        int width = this.actions.getContentWidth();

        this.actions.w(width);

        if (this.tabs != null)
        {
            this.tabs.w(1F, -width);
        }

        super.afterResizeApplied();
    }

    private void renderBackground(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());
    }
}
