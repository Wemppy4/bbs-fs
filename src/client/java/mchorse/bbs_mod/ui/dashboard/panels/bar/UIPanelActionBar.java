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
 * <p>Buttons sit in fixed slots, always laid out in the same order so that the same thing is in
 * the same place in every panel: {@link #editor which of the panel's editors is open}, then
 * {@link #action panel actions}, then a separator, then {@link #layout what acts on the panel's
 * own layout}, then {@link #common the buttons every panel shares} — the list of what the panel
 * edits, then save — and finally {@link #menu the panel's menu} at the very end.</p>
 *
 * <p>Past the separator is everything that is <em>about</em> the panel rather than about what it
 * edits, which is why the layout lock sits there rather than among the actions.</p>
 *
 * <p>The three blocks — the editors, the actions, the rest — are told apart by a separator each,
 * and an empty block simply lets the two around it share one.</p>
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

    private final List<UIIcon> editors = new ArrayList<>();
    private final List<UIIcon> actions = new ArrayList<>();
    private final List<UIIcon> layout = new ArrayList<>();
    private final List<UIIcon> common = new ArrayList<>();
    private final UIElement editorSeparator = new UIElement();
    private final UIElement separator = new UIElement();

    private UIIcon menu;
    private int contentWidth;

    public UIPanelActionBar()
    {
        this.editorSeparator.wh(SEPARATOR_WIDTH, BUTTON_SIZE);
        this.separator.wh(SEPARATOR_WIDTH, BUTTON_SIZE);

        this.row(0);
        this.sync();
    }

    /**
     * Add a button that opens one of the panel's own editors, staying highlighted while its editor
     * is the one on screen — the film panel's camera and replay editors, the model panel's config
     * and model ones.
     *
     * <p>First in the bar and set apart from the actions: these say what the panel is showing,
     * where an action does something to what it shows.</p>
     */
    public UIPanelActionBar editor(UIIcon icon, BooleanSupplier active)
    {
        this.editors.add(this.adopt(icon, active));
        this.sync();

        return this;
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

    /**
     * Add a button that acts on the panel's own layout rather than on what it edits — the lock of
     * a dockable editor. Sits past the separator, ahead of the shared buttons.
     */
    public UIPanelActionBar layout(UIIcon icon, BooleanSupplier active)
    {
        this.layout.add(this.adopt(icon, active));
        this.sync();

        return this;
    }

    /** Add a button every panel shares, such as save. Sits between the layout buttons and the menu. */
    public UIPanelActionBar common(UIIcon icon)
    {
        this.common.add(this.adopt(icon, null));
        this.sync();

        return this;
    }

    /**
     * Set the button that opens this panel's menu — the last button of the bar. Only a panel with
     * more to offer than its buttons (the film editor and its options) has one.
     */
    public UIPanelActionBar menu(UIIcon icon)
    {
        this.menu = this.adopt(icon, null);
        this.sync();

        return this;
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

        int width = this.addSlot(this.editors, 0);

        if (width > 0 && this.hasVisible(this.actions))
        {
            this.add(this.editorSeparator);

            width += SEPARATOR_WIDTH;
        }

        width = this.addSlot(this.actions, width);

        if (width > 0 && (this.hasVisible(this.layout) || this.hasVisible(this.common) || this.isVisible(this.menu)))
        {
            this.add(this.separator);

            width += SEPARATOR_WIDTH;
        }

        width = this.addSlot(this.layout, width);
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
        this.renderHover(context, this.editors);
        this.renderHover(context, this.actions);
        this.renderHover(context, this.layout);
        this.renderHover(context, this.common);

        if (this.menu != null)
        {
            this.renderHover(context, this.menu);
        }

        this.renderSeparator(context, this.editorSeparator);
        this.renderSeparator(context, this.separator);

        super.render(context);
    }

    /** The divider between two blocks of buttons, drawn only while that block boundary is in the bar. */
    private void renderSeparator(UIContext context, UIElement separator)
    {
        if (separator.getParent() != this)
        {
            return;
        }

        Area area = separator.area;
        int x = area.mx();

        context.batcher.box(x, area.y + 3, x + 1, area.ey() - 3, BBSSettings.dividerColor());
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
