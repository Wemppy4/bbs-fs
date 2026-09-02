package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class UIDashboardPanels extends UIElement
{
    /** How thick the task bar is, whichever edge it is docked to — a bar icon is 20×20. */
    public static final int BAR = 20;

    /**
     * The edges the bar can be docked to, in the order they are offered — and the order
     * {@code BBSSettings.taskbarSide} stores, so the bottom (the default) is 0.
     */
    public static final Direction[] SIDES = {Direction.BOTTOM, Direction.TOP, Direction.LEFT, Direction.RIGHT};

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

    /** Which edge the bar is docked to. Never null — see {@link #applySide(Direction)}. */
    private Direction side;

    /** Every icon in the bar, in the order it was put there. */
    private final List<BarButton> buttons = new ArrayList<>();

    public UIDashboardPanels()
    {
        this.taskBar = new UIElement();
        this.pinned = new UIElement();
        this.panelButtons = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.panelButtons.scroll.cancelScrolling().noScrollbar();
        this.panelButtons.scroll.scrollSpeed = 5;

        this.taskBar.context(this::fillSideMenu);
        this.taskBar.add(new UIRenderable(this::renderBackground), this.pinned, this.panelButtons);
        this.add(this.taskBar);

        this.applySide(getSettingsSide());
    }

    /* Sides */

    /** The edge the bar is docked to, as the settings currently have it. */
    public static Direction getSettingsSide()
    {
        return SIDES[MathUtils.clamp(BBSSettings.taskbarSide.get(), 0, SIDES.length - 1)];
    }

    /** The labels for the four sides, in the order {@link #SIDES} lists them. */
    public static IKey[] getSideLabels()
    {
        IKey[] labels = new IKey[SIDES.length];

        for (int i = 0; i < SIDES.length; i++)
        {
            labels[i] = getSideLabel(SIDES[i]);
        }

        return labels;
    }

    private static IKey getSideLabel(Direction side)
    {
        return switch (side)
        {
            case TOP -> UIKeys.DASHBOARD_TASKBAR_SIDE_TOP;
            case LEFT -> UIKeys.DASHBOARD_TASKBAR_SIDE_LEFT;
            case RIGHT -> UIKeys.DASHBOARD_TASKBAR_SIDE_RIGHT;
            default -> UIKeys.DASHBOARD_TASKBAR_SIDE_BOTTOM;
        };
    }

    private static Icon getSideIcon(Direction side)
    {
        return switch (side)
        {
            case TOP -> Icons.ARROW_UP;
            case LEFT -> Icons.ARROW_LEFT;
            case RIGHT -> Icons.ARROW_RIGHT;
            default -> Icons.ARROW_DOWN;
        };
    }

    /**
     * Whether the bar runs left to right, i.e. it is docked to the top or the bottom edge.
     *
     * <p>{@link Direction#isVertical()} answers about the edge, not about the strip lying on
     * it, and those two are opposites — hence this.</p>
     */
    private static boolean runsHorizontally(Direction side)
    {
        return side.isVertical();
    }

    public Direction getSide()
    {
        return this.side;
    }

    /**
     * Dock the task bar to an edge of the screen.
     *
     * <p>Everything that has a side to it — where the bar sits, which way its buttons stack and
     * scroll, where their tooltips and selection marks point, and which edge the panel gives up
     * — is decided here, so a new edge is one call and not a hunt through the dashboard.</p>
     */
    public void setSide(Direction side)
    {
        if (side == null || side == this.side)
        {
            return;
        }

        this.applySide(side);

        if (this.panel != null)
        {
            this.setPanelPlacement(this.panel);
        }

        this.invalidateLayout();
    }

    private void applySide(Direction side)
    {
        this.side = side;

        this.taskBar.resetFlex().relative(this);
        this.pinned.resetFlex().relative(this.taskBar);
        this.panelButtons.resetFlex().relative(this.pinned);

        if (runsHorizontally(side))
        {
            this.taskBar.w(1F).h(BAR);
            this.pinned.h(BAR).row(0).resize();
            this.panelButtons.x(1F, 5).h(BAR).wTo(this.taskBar.area, 1F).row(0).scroll();
            this.panelButtons.scroll.direction = ScrollDirection.HORIZONTAL;

            if (side == Direction.BOTTOM)
            {
                this.taskBar.y(1F, -BAR);
            }
        }
        else
        {
            this.taskBar.w(BAR).h(1F);
            this.pinned.w(BAR).column(0).vertical();
            this.panelButtons.y(1F, 5).w(BAR).hTo(this.taskBar.area, 1F).column(0).vertical().scroll();
            this.panelButtons.scroll.direction = ScrollDirection.VERTICAL;

            if (side == Direction.RIGHT)
            {
                this.taskBar.x(1F, -BAR);
            }
        }

        /* The scroll carried over from the other axis means nothing here */
        this.panelButtons.scroll.setScroll(0);

        for (BarButton button : this.buttons)
        {
            button.apply(side);
        }
    }

    private void fillSideMenu(ContextMenuManager menu)
    {
        for (int i = 0; i < SIDES.length; i++)
        {
            Direction side = SIDES[i];
            int index = i;

            menu.action(getSideIcon(side), getSideLabel(side), this.side == side, () -> BBSSettings.taskbarSide.set(index));
        }
    }

    /* Panels */

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
        this.setSide(getSettingsSide());

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
        panel.resetFlex().relative(this);

        switch (this.side)
        {
            case TOP -> panel.y(BAR).w(1F).h(1F, -BAR);
            case LEFT -> panel.x(BAR).w(1F, -BAR).h(1F);
            case RIGHT -> panel.w(1F, -BAR).h(1F);
            default -> panel.w(1F).h(1F, -BAR);
        }
    }

    /** Register a panel and the button that opens it. */
    public UIIcon registerPanel(UIDashboardPanel panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.setPanel(panel));

        this.panels.add(panel);
        this.panelButtons.add(button);

        return this.addBarButton(new BarButton(button, tooltip, () -> this.panel == panel));
    }

    /**
     * Put an icon into the pinned end of the bar — the part that stays put while the panel
     * buttons scroll.
     */
    public UIIcon pin(UIIcon icon, IKey tooltip)
    {
        this.pinned.add(icon);

        return this.addBarButton(new BarButton(icon, tooltip, null));
    }

    private UIIcon addBarButton(BarButton button)
    {
        this.buttons.add(button);
        button.apply(this.side);

        return button.icon;
    }

    protected void renderBackground(UIContext context)
    {
        Area area = this.taskBar.area;
        Area a = this.pinned.area;

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.chromeSurface());

        if (runsHorizontally(this.side))
        {
            context.batcher.box(a.ex() + 2, a.y + 3, a.ex() + 3, a.ey() - 3, 0x44ffffff);
        }
        else
        {
            context.batcher.box(a.x + 3, a.ey() + 2, a.ex() - 3, a.ey() + 3, 0x44ffffff);
        }
    }

    /**
     * An icon living in the task bar, together with what the bar has to redecide about it when
     * it gets docked to another edge.
     */
    private static class BarButton
    {
        public final UIIcon icon;
        public final IKey tooltip;

        /** When this button is the chosen one, or null for the icons that are never chosen. */
        public final BooleanSupplier highlight;

        public BarButton(UIIcon icon, IKey tooltip, BooleanSupplier highlight)
        {
            this.icon = icon;
            this.tooltip = tooltip;
            this.highlight = highlight;
        }

        public void apply(Direction side)
        {
            /* The tooltip leans into the screen, the selection mark sits on the docked edge */
            this.icon.tooltip(this.tooltip, side.opposite());

            if (this.highlight != null)
            {
                this.icon.highlight(this.highlight, side);
            }
        }
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
