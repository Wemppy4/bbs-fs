package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIDataTabElement extends UIClickable<UIDataTabElement>
{
    private static final int RIGHT_GAP = 0;
    private static final int ICON_X = 4;
    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 4;
    private static final int TEXT_X = ICON_X + ICON_SIZE + ICON_GAP;
    private static final int TEXT_RIGHT_PADDING = 6;
    private static final int CLOSE_SIZE = 12;
    private static final int CLOSE_GAP = 4;
    private static final int CLOSE_ZONE = CLOSE_SIZE + TEXT_RIGHT_PADDING;

    private int index;
    private IKey label;
    private Icon icon;
    private final IUITabs host;
    private final UIIcon close;

    public UIDataTabElement(IUITabs host, int h)
    {
        super(null);

        this.host = host;
        this.h(h);
        this.label = IKey.raw("");
        this.icon = Icons.SEARCH;

        this.callback = (b) -> this.host.switchTab(this.index);

        this.close = new UIIcon(Icons.CLOSE, (b) -> this.host.closeTab(this.index));
        this.close.relative(this).x(1F, -(CLOSE_SIZE + CLOSE_GAP + RIGHT_GAP)).y(0.5F).w(CLOSE_SIZE).h(CLOSE_SIZE).anchor(0, 0.5F);

        this.add(this.close);

        this.context((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.PANELS_TABS_CONTEXT_CLOSE_OTHERS, () -> this.host.closeOtherTabs(this.index));
            menu.action(Icons.ARROW_LEFT, UIKeys.PANELS_TABS_CONTEXT_CLOSE_LEFT, () -> this.host.closeTabsLeft(this.index));
            menu.action(Icons.ARROW_RIGHT, UIKeys.PANELS_TABS_CONTEXT_CLOSE_RIGHT, () -> this.host.closeTabsRight(this.index));
        });
    }

    public static int measureWidth(FontRenderer font, IKey label)
    {
        return TEXT_X + font.getWidth(label.get()) + CLOSE_ZONE + RIGHT_GAP;
    }

    public void setTab(int index, IKey label, IKey tooltip, Icon icon)
    {
        this.index = index;
        this.label = label;
        this.icon = icon;

        if (tooltip != null)
        {
            this.tooltip(tooltip, Direction.BOTTOM);
        }
    }

    @Override
    protected UIDataTabElement get()
    {
        return this;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 2 && this.host.canCloseTab(this.index))
        {
            this.host.closeTab(this.index);

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        boolean active = this.index == this.host.getCurrentTab();
        boolean hover = this.hover;

        boolean showClose = this.host.canCloseTab(this.index) && (active || hover);
        this.close.setVisible(showClose);

        int ex = this.area.ex() - RIGHT_GAP;

        if (active)
        {
            context.batcher.box(this.area.x, this.area.y, ex, this.area.ey(), BBSSettings.baseSurface());
        }
        else if (hover)
        {
            context.batcher.box(this.area.x, this.area.y, ex, this.area.ey(), BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        }

        FontRenderer font = context.batcher.getFont();
        int iconColor = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.7F);
        context.batcher.icon(this.icon, iconColor, this.area.x + ICON_X, this.area.my(), 0F, 0.5F);

        int right = showClose ? CLOSE_ZONE : TEXT_RIGHT_PADDING;
        String text = font.limitToWidth(this.label.get(), this.area.w - RIGHT_GAP - TEXT_X - right);
        int textColor = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.7F);

        context.batcher.text(text, this.area.x + TEXT_X, this.area.my() - font.getHeight() / 2, textColor);
    }
}
