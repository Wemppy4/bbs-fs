package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.List;

public class UIDashboardPanels extends UIElement
{
    public List<UIDashboardPanel> panels = new ArrayList<>();
    public UIDashboardPanel panel;

    /**
     * Whether {@link #panel} has been told it is on screen. "Current panel" and "panel on screen"
     * are not the same thing: the current panel survives the screen closing, so this is what keeps
     * appear/disappear paired instead of firing a stray disappear on the way back in.
     */
    private boolean shown;

    public UIElement taskBar;
    public UIElement pinned;
    public UIScrollView panelButtons;

    public UIDashboardPanels()
    {
        this.taskBar = new UIElement();
        this.taskBar.relative(this).y(1F, -20).w(1F).h(20);
        this.pinned = new UIElement();
        this.pinned.relative(this.taskBar).h(20).row(0).resize();
        this.panelButtons = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.panelButtons.relative(this.pinned).x(1F, 5).h(20).wTo(this.taskBar.area, 1F).column(0).scroll();
        this.panelButtons.scroll.cancelScrolling().noScrollbar();
        this.panelButtons.scroll.scrollSpeed = 5;

        this.taskBar.add(new UIRenderable(this::renderBackground), this.pinned, this.panelButtons);
        this.add(this.taskBar);
    }

    public <T> T getPanel(Class<T> clazz)
    {
        for (UIDashboardPanel panel : this.panels)
        {
            if (panel.getClass() == clazz)
            {
                return (T) panel;
            }
        }

        return null;
    }

    public boolean isFlightSupported()
    {
        return this.panel instanceof IFlightSupported;
    }

    public void open()
    {
        for (UIDashboardPanel panel : this.panels)
        {
            panel.open();
        }
    }

    public void close()
    {
        /* Leaving the screen means leaving the panel too, and in that order: the editor on screen
         * takes its world effects down in disappear(), so close() no longer has to repeat them. */
        this.hideCurrentPanel();

        for (UIDashboardPanel panel : this.panels)
        {
            panel.close();
        }
    }

    public void setPanel(UIDashboardPanel panel)
    {
        UIDashboardPanel lastPanel = this.panel;

        if (this.panel != null)
        {
            this.hideCurrentPanel();
            this.panel.removeFromParent();
        }

        this.panel = panel;

        this.getEvents().emit(new PanelEvent(this, lastPanel, panel));

        if (this.panel != null)
        {
            this.setPanelPlacement(panel);

            this.prepend(this.panel);
            this.shown = true;
            this.panel.appear();
            this.panel.resize();
        }
    }

    private void hideCurrentPanel()
    {
        if (this.panel != null && this.shown)
        {
            this.shown = false;
            this.panel.disappear();
        }
    }

    private void setPanelPlacement(UIDashboardPanel panel)
    {
        panel.resetFlex().relative(this).w(1F).h(1F, -20);
    }

    public UIIcon registerPanel(UIDashboardPanel panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.setPanel(panel));

        button.tooltip(tooltip, Direction.TOP);
        button.highlight(() -> this.panel == panel, Direction.BOTTOM);

        this.panels.add(panel);
        this.panelButtons.add(button);

        return button;
    }

    protected void renderBackground(UIContext context)
    {
        Area area = this.taskBar.area;
        Area a = this.pinned.area;

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.chromeSurface());
        context.batcher.box(a.ex() + 2, a.y + 3, a.ex() + 3, a.ey() - 3, 0x44ffffff);
    }

    public static class PanelEvent extends UIEvent<UIDashboardPanels>
    {
        public final UIDashboardPanel lastPanel;
        public final UIDashboardPanel panel;

        public PanelEvent(UIDashboardPanels element, UIDashboardPanel lastPanel, UIDashboardPanel panel)
        {
            super(element);

            this.lastPanel = lastPanel;
            this.panel = panel;
        }
    }
}
