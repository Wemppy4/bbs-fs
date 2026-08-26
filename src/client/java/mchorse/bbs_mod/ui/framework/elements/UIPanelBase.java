package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel base GUI
 *
 * With this base class, you can add multi panel elements which could be
 * switched between using buttons.
 */
public class UIPanelBase <T extends UIElement> extends UIElement
{
    public T view;
    public UITabStrip buttons;
    public List<T> panels = new ArrayList<>();
    public Direction direction;

    public UIPanelBase()
    {
        this(Direction.BOTTOM);
    }

    public UIPanelBase(Direction direction)
    {
        super();

        this.direction = direction == null ? Direction.BOTTOM : direction;
        this.buttons = new UITabStrip(ScrollDirection.HORIZONTAL);
        this.buttons.scroll.cancelScrolling();
        this.buttons.scroll.scrollSpeed = 5;
        this.buttons.active(() -> this.panels.indexOf(this.view));
        this.buttons.onSelect((index) -> this.setPanel(this.panels.get(index)));

        this.setButtonsPlacement();

        this.add(new UIRenderable(this::renderOverlay), this.buttons);
    }

    public void changeDirection(Direction direction)
    {
        this.direction = direction == null ? Direction.BOTTOM : direction;

        this.setButtonsPlacement();

        if (this.view != null)
        {
            this.setPanelPlacement(this.view);
        }

        for (UIElement element : this.buttons.getChildren(UIElement.class))
        {
            if (element.tooltip != null)
            {
                element.tooltip(element.tooltip.getLabel(), this.direction.opposite());
            }
        }

        this.resize();
    }

    /**
     * The bar is a 20px strip on the {@link #direction} side; LEFT/RIGHT run it down the side,
     * BOTTOM/RIGHT put it at the far edge. Everything below derives from these two facts.
     */
    private boolean isSideBar()
    {
        return this.direction.isHorizontal();
    }

    private boolean isFarBar()
    {
        return this.direction == Direction.BOTTOM || this.direction == Direction.RIGHT;
    }

    private void setButtonsPlacement()
    {
        boolean side = this.isSideBar();
        boolean far = this.isFarBar();

        this.buttons.resetFlex().relative(this);
        this.buttons.direction(side ? ScrollDirection.VERTICAL : ScrollDirection.HORIZONTAL);
        /* The mark sits on the side facing the panel, the same way the tooltips point away from it. */
        this.buttons.activeEdge(this.direction.opposite());

        if (side)
        {
            this.buttons.w(20).h(1F);

            if (far)
            {
                this.buttons.x(1F, -20);
            }
        }
        else
        {
            this.buttons.w(1F).h(20);

            if (far)
            {
                this.buttons.y(1F, -20);
            }
        }
    }

    private void setPanelPlacement(UIElement panel)
    {
        Flex flex = panel.getFlex();

        /* Reset the panel's flex without resetting the post resizer */
        flex.relative = null;

        flex.x.reset();
        flex.y.reset();
        flex.w.reset();
        flex.h.reset();

        boolean side = this.isSideBar();
        boolean far = this.isFarBar();

        /* The panel takes what the bar leaves; a near bar also pushes it off the origin */
        panel.relative(this).w(1F, side ? -20 : 0).h(1F, side ? 0 : -20);

        if (!far)
        {
            if (side)
            {
                panel.x(20);
            }
            else
            {
                panel.y(20);
            }
        }
    }

    public UIIcon getButton(T panel)
    {
        return (UIIcon) this.buttons.getTab(this.panels.indexOf(panel));
    }

    /**
     * Register a panel with given texture and tooltip
     */
    public UIIcon registerPanel(T panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.buttons.select(b));

        if (tooltip != null && !tooltip.get().isEmpty())
        {
            button.tooltip(tooltip, this.direction.opposite());
        }

        panel.markContainer();
        this.panels.add(panel);
        this.buttons.addTab(button);

        return button;
    }

    /**
     * Switch current panel to given one
     */
    public void setPanel(T panel)
    {
        if (this.view != null)
        {
            this.view.removeFromParent();
        }

        this.view = panel;

        if (this.view != null)
        {
            this.setPanelPlacement(panel);

            this.view.resize();
            this.prepend(this.view);
        }
    }

    protected void renderOverlay(UIContext context)
    {
        boolean side = this.isSideBar();
        boolean far = this.isFarBar();
        int x = side && far ? this.area.ex() - 20 : this.area.x;
        int y = !side && far ? this.area.ey() - 20 : this.area.y;

        this.renderBackground(context, x, y, side ? 20 : this.area.w, side ? this.area.h : 20);
    }

    protected void renderBackground(UIContext context, int x, int y, int w, int h)
    {}
}