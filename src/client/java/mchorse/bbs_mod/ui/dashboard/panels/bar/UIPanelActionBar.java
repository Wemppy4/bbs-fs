package mchorse.bbs_mod.ui.dashboard.panels.bar;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A row of icon buttons that lives at the right end of a {@link UIPanelTopBar}.
 *
 * <p>The bar owns the entire look of a panel's actions: button size, hover, the highlight of a
 * button whose feature is currently active, and the direction its buttons' tooltips open. Panels
 * only say <em>which</em> buttons they have and, for toggles, <em>when</em> those read as active.</p>
 *
 * <p>Buttons sit in three slots, always laid out in the same order so that the same thing is in
 * the same place in every panel: {@link #action panel actions}, then a separator, then
 * {@link #common the buttons every panel shares} such as save, and finally
 * {@link #menu the panel's menu} at the very end.</p>
 */
public class UIPanelActionBar extends UIElement
{
    public static final int BUTTON_SIZE = UIPanelTopBar.HEIGHT;
    public static final int SEPARATOR_WIDTH = 8;

    /**
     * The bar hangs off the top edge of the panel, so an active button is underlined and its
     * tooltip opens downwards.
     */
    private static final Direction EDGE = Direction.BOTTOM;

    private final List<UIIcon> actions = new ArrayList<>();
    private final List<UIIcon> common = new ArrayList<>();
    private final UIElement separator = new UIElement();

    private UIIcon menu;
    private int contentWidth;

    public UIPanelActionBar()
    {
        this.separator.wh(SEPARATOR_WIDTH, BUTTON_SIZE);

        this.row(0);
        this.sync();
    }

    /** Add a panel specific button. */
    public UIPanelActionBar action(UIIcon icon)
    {
        return this.action(icon, null);
    }

    /**
     * Add a panel specific button that stays highlighted while {@code active} holds, the way the
     * film editor's mode buttons do.
     */
    public UIPanelActionBar action(UIIcon icon, BooleanSupplier active)
    {
        this.actions.add(this.adopt(icon, active));
        this.sync();

        return this;
    }

    /** Add a button every panel shares, such as save. Sits between the actions and the menu. */
    public UIPanelActionBar common(UIIcon icon)
    {
        this.common.add(this.adopt(icon, null));
        this.sync();

        return this;
    }

    /**
     * Set the button that opens this panel's menu — the last button of the bar. A panel that
     * wants its own menu (the film opens film options instead of the data manager) simply sets
     * it again, replacing the one its base class put there.
     */
    public UIPanelActionBar menu(UIIcon icon)
    {
        this.menu = this.adopt(icon, null);
        this.sync();

        return this;
    }

    /** The button that opens this panel's menu, or null. */
    public UIIcon getMenuButton()
    {
        return this.menu;
    }

    /** Drop a button this panel does not want (the film saves from its own menu, not from the bar). */
    public void dismiss(UIIcon icon)
    {
        boolean removed = this.actions.remove(icon);

        removed |= this.common.remove(icon);

        if (this.menu == icon)
        {
            this.menu = null;
            removed = true;
        }

        if (removed)
        {
            this.sync();
        }
    }

    private UIIcon adopt(UIIcon icon, BooleanSupplier active)
    {
        icon.wh(BUTTON_SIZE, BUTTON_SIZE);

        if (active != null)
        {
            icon.highlight(active, EDGE);
        }

        if (icon.tooltip instanceof LabelTooltip label)
        {
            label.direction = EDGE;
        }

        return icon;
    }

    /**
     * Rebuild the row out of the slots and re-measure the bar. Only visible buttons take part, so
     * a panel that hides a button has to call this afterwards.
     */
    public void sync()
    {
        this.removeAll();

        int width = this.addSlot(this.actions, 0);

        if (width > 0 && (this.hasVisible(this.common) || this.isVisible(this.menu)))
        {
            this.add(this.separator);

            width += SEPARATOR_WIDTH;
        }

        width = this.addSlot(this.common, width);

        if (this.isVisible(this.menu))
        {
            this.add(this.menu);

            width += BUTTON_SIZE;
        }

        this.contentWidth = width;
        this.setVisible(width > 0);
    }

    private int addSlot(List<UIIcon> slot, int width)
    {
        for (UIIcon icon : slot)
        {
            if (icon.isVisible())
            {
                this.add(icon);

                width += BUTTON_SIZE;
            }
        }

        return width;
    }

    private boolean hasVisible(List<UIIcon> slot)
    {
        for (UIIcon icon : slot)
        {
            if (icon.isVisible())
            {
                return true;
            }
        }

        return false;
    }

    private boolean isVisible(UIIcon icon)
    {
        return icon != null && icon.isVisible();
    }

    /** Width this bar needs, so the tab strip beside it knows where to stop. */
    public int getContentWidth()
    {
        return this.isVisible() ? this.contentWidth : 0;
    }

    @Override
    public void render(UIContext context)
    {
        this.renderHover(context, this.actions);
        this.renderHover(context, this.common);

        if (this.menu != null)
        {
            this.renderHover(context, this.menu);
        }

        if (this.separator.getParent() == this)
        {
            Area area = this.separator.area;
            int x = area.mx();

            context.batcher.box(x, area.y + 3, x + 1, area.ey() - 3, BBSSettings.dividerColor());
        }

        super.render(context);
    }

    private void renderHover(UIContext context, List<UIIcon> slot)
    {
        for (UIIcon icon : slot)
        {
            this.renderHover(context, icon);
        }
    }

    /** The icons paint their own highlight; the bar only shades the one under the cursor. */
    private void renderHover(UIContext context, UIIcon icon)
    {
        Area area = icon.area;

        if (icon.isVisible() && !icon.isHighlighted() && area.isInside(context.mouseX, context.mouseY))
        {
            context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        }
    }
}
