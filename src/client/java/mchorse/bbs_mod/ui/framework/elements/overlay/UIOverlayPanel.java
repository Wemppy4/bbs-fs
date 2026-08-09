package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIOverlayCloseEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIOverlayPanel extends UIElement
{
    public UILabel title;
    public UIElement icons;
    public UIIcon close;
    public UIElement content;

    private boolean moving;
    private int lastX;
    private int lastY;

    private int initialOffsetX;
    private int initialOffsetY;

    public UIOverlayPanel(IKey title)
    {
        super();

        this.title = UI.label(title);
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.close());
        this.content = new UIElement();
        this.icons = new UIElement();

        this.title.labelAnchor(0, 0.5F).relative(this).xy(6, 0).w(0.6F).h(20);
        this.icons.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();
        this.content.relative(this).xy(0, 20).w(1F, -20).h(1F, -20);

        this.icons.add(this.close);

        this.add(this.title, this.icons, this.content);

        this.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    public void setInitialOffset(int x, int y)
    {
        this.initialOffsetX = x;
        this.initialOffsetY = y;
    }

    public void onClose(Consumer<UIOverlayCloseEvent> callback)
    {
        this.events.register(UIOverlayCloseEvent.class, callback);
    }

    public void close()
    {
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            ((UIOverlay) parent).closeItself();
        }
    }

    public void confirm()
    {}

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.title.area.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.flex.x.offset = this.initialOffsetX;
                this.flex.y.offset = this.initialOffsetY;

                this.getParent().resize();

                return true;
            }

            this.moving = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.moving = super.subMouseReleased(context);

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused())
        {
            if (context.isPressed(Keys.CLOSE))
            {
                this.close();

                return true;
            }
            else if (context.isPressed(Keys.CONFIRM))
            {
                this.confirm();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.moving && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            int lastX = this.area.x;
            int lastY = this.area.y;

            this.flex.x.offset += dx;
            this.flex.y.offset += dy;

            this.getParent().resize();

            if (lastX == this.area.x) this.flex.x.offset -= dx;
            if (lastY == this.area.y) this.flex.y.offset -= dy;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        this.renderBackground(context);

        super.render(context);
    }

    protected void renderBackground(UIContext context)
    {
        if (BBSSettings.hasOverlayGradientBorder())
        {
            context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());
        }

        this.area.render(context.batcher, BBSSettings.raisedSurface());
        this.icons.area.render(context.batcher, BBSSettings.chromeSurface());

        if (this.close.area.isInside(context))
        {
            this.close.area.render(context.batcher, Colors.RED | Colors.A100);
        }

        if (this.title.area.isInside(context))
        {
            context.batcher.icon(Icons.ALL_DIRECTIONS, Colors.GRAY, this.area.mx(), this.title.area.my(), 0.5F, 0.5F);
        }
    }

    public void onClose()
    {
        this.events.emit(new UIOverlayCloseEvent(this));
    }
}
