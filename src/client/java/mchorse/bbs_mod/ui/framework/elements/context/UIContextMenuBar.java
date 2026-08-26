package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The strip of icon buttons along the top edge of a context menu — the verbs that are the same
 * everywhere in BBS, said with a glyph instead of a row of text.
 *
 * <p>The bar owns the whole look: button size, the rule that a verb's place comes from its
 * {@link MenuVerb.Slot slot} rather than from the order it was registered in, the mark on the
 * destructive ones, and the line that divides the bar from the list below. Menus only say which
 * buttons they have.</p>
 */
public class UIContextMenuBar extends UIElement
{
    public static final int HEIGHT = 20;
    public static final int BUTTON = 20;

    private final List<MenuIcon> icons = new ArrayList<>();
    private final List<UIIcon> buttons = new ArrayList<>();
    private final Runnable close;

    private int contentWidth;
    private boolean dividing;

    public UIContextMenuBar(Runnable close)
    {
        this.close = close;

        this.setVisible(false);
    }

    public void register(MenuIcon icon)
    {
        this.icons.add(icon);
    }

    public boolean isEmpty()
    {
        return this.icons.isEmpty();
    }

    /** Width the bar needs, so the menu around it never comes out narrower than its own buttons. */
    public int getContentWidth()
    {
        return this.contentWidth;
    }

    /** How much of the menu the bar takes, the line separating it from the list included. */
    public int getTotalHeight()
    {
        return HEIGHT + (this.dividing ? 1 : 0);
    }

    /**
     * Build the row out of the registered buttons. Called once the menu knows all of them —
     * right before it is measured and shown. {@code dividing} says whether a list follows: with
     * nothing below, the line under the bar would just be a stray edge along the menu's bottom.
     */
    public void sync(boolean dividing)
    {
        this.dividing = dividing;

        this.removeAll();
        this.icons.sort(Comparator.comparingInt((icon) -> icon.verb.slot.ordinal()));

        this.buttons.clear();

        for (int i = 0; i < this.icons.size(); i++)
        {
            UIIcon button = this.createButton(this.icons.get(i));

            button.relative(this).wh(BUTTON, HEIGHT).xy(i * BUTTON, 0);

            this.buttons.add(button);
            this.add(button);
        }

        this.contentWidth = this.icons.size() * BUTTON;
        this.setVisible(!this.icons.isEmpty());
    }

    private UIIcon createButton(MenuIcon icon)
    {
        UIIcon button = new UIIcon(icon.verb.icon, (b) ->
        {
            icon.runnable.run();
            this.close.run();
        });

        button.tooltip(icon.label);
        button.setEnabled(icon.enabled);

        return button;
    }

    @Override
    public void render(UIContext context)
    {
        this.renderDestructive(context);

        super.render(context);

        if (this.dividing)
        {
            context.batcher.box(this.area.x + 2, this.area.ey(), this.area.ex() - 2, this.area.ey() + 1, Colors.mulRGB(Colors.WHITE, 0.1F));
        }
    }

    /**
     * Mark the destructive buttons the way a destructive row of the list is marked — an edge of
     * colour fading out across the button. It is the same paint as
     * {@link mchorse.bbs_mod.ui.utils.context.ColorfulContextAction ColorfulContextAction}, which happens to be exactly
     * one button wide, so the two read as the same thing said in two places.
     */
    private void renderDestructive(UIContext context)
    {
        for (int i = 0; i < this.buttons.size(); i++)
        {
            UIIcon button = this.buttons.get(i);

            if (!this.icons.get(i).verb.slot.isDestructive() || !button.isEnabled())
            {
                continue;
            }

            Area area = button.area;

            context.batcher.box(area.x, area.y, area.x + 2, area.ey(), Colors.A100 | Colors.NEGATIVE);
            context.batcher.gradientHBox(area.x + 2, area.y, area.ex(), area.ey(), Colors.A25 | Colors.NEGATIVE, Colors.NEGATIVE);
        }
    }
}
