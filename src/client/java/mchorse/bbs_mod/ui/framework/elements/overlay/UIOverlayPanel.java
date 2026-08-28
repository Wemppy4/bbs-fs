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
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIOverlayPanel extends UIElement
{
    /** Side of the square grip in the bottom right corner. */
    private static final int GRIP = 10;

    /** Nothing useful fits into a panel smaller than this. */
    private static final int MIN_WIDTH = 160;
    private static final int MIN_HEIGHT = 60;

    public UILabel title;
    public UIElement icons;
    public UIIcon close;
    public UIElement content;

    private boolean moving;
    private int lastX;
    private int lastY;

    private int initialOffsetX;
    private int initialOffsetY;

    /**
     * Whether the overlay opened this panel without a size. Only then is the panel's own opinion
     * about its height in charge; a call site that passed one meant it.
     */
    private boolean sizeless;

    /** The corner grip, on the panels that offer sizing; null on the ones that don't. */
    private UIDraggable grip;

    /** Whether the user sized this panel, i.e. whether the size is worth remembering. */
    private boolean resized;

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

    /** Marks the panel as opened without a size; called by {@link UIOverlay}. */
    public void sizeless()
    {
        this.sizeless = true;
    }

    /**
     * Whether the user may size this panel. A panel that measures itself has nothing to gain from
     * being stretched &mdash; its height gets recomputed from its content anyway &mdash; so only
     * the ones that take the size they were given offer a grip.
     */
    public boolean isResizable()
    {
        return true;
    }

    public boolean wasResized()
    {
        return this.resized;
    }

    /**
     * Adds the corner grip. Called by {@link UIOverlay} rather than from the constructor: whether a
     * panel is resizable can depend on fields its subclass sets after {@code super(...)} has run.
     */
    public void setupResize()
    {
        if (this.grip != null || !this.isResizable())
        {
            return;
        }

        this.grip = new UIDraggable(this::dragSize);
        this.grip.rendering(this::renderGrip);
        this.grip.cursors(GLFW.GLFW_RESIZE_NWSE_CURSOR, GLFW.GLFW_RESIZE_NWSE_CURSOR);
        this.grip.relative(this).x(1F, -GRIP).y(1F, -GRIP).wh(GRIP, GRIP);

        /* Last child: children are offered the click in reverse, so the grip gets it before the
         * content underneath it does */
        this.add(this.grip);
    }

    private void dragSize(UIContext context)
    {
        UIElement parent = this.getParent();
        float anchorX = this.flex.x.anchor;
        float anchorY = this.flex.y.anchor;

        /* Past the overlay's own size the bounds resizer starts sliding the panel to keep it on
         * screen, which moves the anchor the corner is solved against, and the grip runs away from
         * the hand */
        int maxWidth = Math.max(parent.area.w, MIN_WIDTH);
        int maxHeight = Math.max(parent.area.h, MIN_HEIGHT);

        /* The panel is placed by its anchor, so a centered one grows in both directions and its
         * corner travels at half the speed of the size. Solving for the corner rather than adding
         * the mouse delta keeps the grip under the hand. */
        if (anchorX < 1F)
        {
            int origin = this.area.x + (int) (this.area.w * anchorX);
            int width = (int) ((context.mouseX - origin) / (1F - anchorX));

            this.flex.w.max = 0;
            this.flex.w.set(0F, MathUtils.clamp(width, MIN_WIDTH, maxWidth));
        }

        if (anchorY < 1F)
        {
            int origin = this.area.y + (int) (this.area.h * anchorY);
            int height = (int) ((context.mouseY - origin) / (1F - anchorY));

            this.flex.h.max = 0;
            this.flex.h.set(0F, MathUtils.clamp(height, MIN_HEIGHT, maxHeight));
        }

        this.resized = true;

        parent.resize();
    }

    private void renderGrip(UIContext context)
    {
        int color = this.grip.area.isInside(context) ? Colors.LIGHTEST_GRAY : Colors.GRAY;
        int x = this.grip.area.x + 1;
        int y = this.grip.area.y + 1;

        /* The lower right triangle of a 3x3 dot grid, i.e. three diagonal ticks */
        for (int row = 0; row < 3; row ++)
        {
            for (int column = 2 - row; column < 3; column ++)
            {
                context.batcher.box(x + column * 3, y + row * 3, x + column * 3 + 2, y + row * 3 + 2, color);
            }
        }
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

    /**
     * The width this panel asks for when it is opened without one; a negative value leaves the
     * overlay's own default in charge.
     */
    public int getPreferredWidth()
    {
        return -1;
    }

    /**
     * The height this panel wants once its children are laid out, or -1 to keep whatever height it
     * was opened with. A panel that knows what is inside it &mdash; a message with one block under
     * it &mdash; returns the sum, so a two line question doesn't get a half screen box with the
     * button parked at the bottom of it.
     */
    public int getContentHeight()
    {
        return -1;
    }

    @Override
    public void resize()
    {
        super.resize();

        int height = this.getContentHeight();

        /* The pass above laid the children out, so their heights are known now: take the sum and
         * lay them out again against it. One extra pass settles it &mdash; the width doesn't change
         * with the height, and neither does the text wrap the sum is made of. */
        if (this.sizeless && height > 0 && height != this.area.h)
        {
            /* The sum counted everything that has to fit, so the screen share's cap no longer
             * applies: capping it here would cut the block off the bottom of a long message */
            this.flex.h.max = 0;
            this.flex.h.set(0F, height);

            super.resize();
        }
    }

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
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());

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
