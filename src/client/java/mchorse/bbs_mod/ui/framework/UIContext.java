package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybinds;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.IViewportStack;
import mchorse.bbs_mod.ui.framework.elements.utils.UIViewportStack;
import mchorse.bbs_mod.ui.framework.notifications.Notification;
import mchorse.bbs_mod.ui.framework.notifications.UINotifications;
import mchorse.bbs_mod.ui.framework.tooltips.UITooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public class UIContext implements IViewportStack
{
    public UIRenderingContext render;
    public Batcher2D batcher;

    /* GUI elements */
    public final UIBaseMenu menu;
    public final UITooltip tooltip;
    public final UIKeybinds keybinds;
    public final UINotifications notifications;
    public IFocusedUIElement activeElement;
    public UIContextMenu contextMenu;

    /* Mouse states */
    public int mouseX;
    public int mouseY;
    public int mouseButton;
    public double mouseWheel;
    public double mouseWheelHorizontal;
    public long lastScroll;
    private boolean lastScrollUpdate;

    /* Keyboard states */
    private int keyCode;
    private int scanCode;
    private KeyAction keyAction = KeyAction.RELEASED;

    private char inputCharacter;

    /* Render states */
    private float transition;
    private long tick;
    private int cursorShape = GLFW.GLFW_ARROW_CURSOR;

    public UIViewportStack viewportStack = new UIViewportStack();

    public UIContext(UIBaseMenu menu)
    {
        this.menu = menu;
        this.tooltip = new UITooltip();
        this.keybinds = new UIKeybinds();
        this.notifications = new UINotifications();
    }

    public long getTick()
    {
        return this.tick;
    }

    public void setTransition(float transition)
    {
        this.transition = transition;
    }

    public float getTransition()
    {
        return this.transition;
    }

    public float getTickTransition()
    {
        return this.tick + this.transition;
    }

    public void setup(UIRenderingContext context)
    {
        this.render = context;
        this.batcher = context.batcher;
    }

    public void setMouse(int mouseX, int mouseY)
    {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.viewportStack.reset();
    }

    public void setMouse(int mouseX, int mouseY, int mouseButton)
    {
        this.setMouse(mouseX, mouseY);
        this.mouseButton = mouseButton;
    }

    public void setMouseWheel(int mouseX, int mouseY, double mouseWheel, double mouseWheelHorizontal)
    {
        this.setMouse(mouseX, mouseY);
        this.mouseWheel = mouseWheel;
        this.mouseWheelHorizontal = mouseWheelHorizontal;
    }

    public void setKeyEvent(int keyCode, int scanCode, int action)
    {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.keyAction = KeyAction.get(action);
    }

    public void setKeyTyped(char character)
    {
        this.inputCharacter = character;
    }

    public void reset()
    {
        this.viewportStack.reset();
        this.resetTooltip();
        this.resetCursor();
    }

    public void resetTooltip()
    {
        this.tooltip.set(null, null);

        if (this.activeElement instanceof UIElement && !((UIElement) this.activeElement).canBeSeen())
        {
            this.unfocus();
        }
    }

    public void markUpdateScroll()
    {
        this.lastScrollUpdate = true;
    }

    public void updateScroll()
    {
        if (this.lastScrollUpdate)
        {
            this.lastScroll = System.currentTimeMillis();
            this.lastScrollUpdate = false;
        }
    }

    public boolean hasNotScrolledForMore(long millis)
    {
        long l = System.currentTimeMillis() - this.lastScroll;

        return l > millis;
    }

    /* Keys */

    public int getKeyCode()
    {
        return this.keyCode;
    }

    public int getScanCode()
    {
        return this.scanCode;
    }

    public KeyAction getKeyAction()
    {
        return this.keyAction;
    }

    public char getInputCharacter()
    {
        return this.inputCharacter;
    }

    public boolean isPressed(int keyCode)
    {
        return this.keyCode == keyCode && this.keyAction == KeyAction.PRESSED;
    }

    public boolean isPressed(KeyCombo combo)
    {
        for (int i = 1; i < combo.keys.size(); i++)
        {
            if (!Window.isKeyPressed(combo.keys.get(i)))
            {
                return false;
            }
        }

        return this.isPressed(combo.getMainKey());
    }

    public boolean isReleased(int keyCode)
    {
        return this.keyCode == keyCode && this.keyAction == KeyAction.RELEASED;
    }

    public boolean isRepeated(int keyCode)
    {
        return this.keyCode == keyCode && this.keyAction == KeyAction.REPEAT;
    }

    public boolean isHeld(int keyCode)
    {
        return this.keyCode == keyCode && this.keyAction != KeyAction.RELEASED;
    }

    public void toggleKeybinds()
    {
        if (this.keybinds.hasParent())
        {
            this.keybinds.removeFromParent();
        }
        else
        {
            this.menu.overlay.add(this.keybinds);
            this.keybinds.resize();
        }
    }

    /* Tooltip */

    public void notifyOrUpdate(IKey message, int background)
    {
        List<Notification> list = this.notifications.notifications;

        if (!list.isEmpty() && list.get(list.size() - 1).background == (background | Colors.A100))
        {
            Notification last = list.get(list.size() - 1);

            last.message = message;
            last.tick = Math.max(last.tick, Notification.TOTAL_LENGTH - 20);
        }
        else
        {
            this.notifications.post(message, background);
        }
    }

    public void notifyInfo(IKey message)
    {
        this.notify(message, Colors.BLUE);
    }

    public void notifySuccess(IKey message)
    {
        this.notify(message, Colors.mulRGB(Colors.GREEN, 0.75F));
    }

    public void notifyError(IKey message)
    {
        this.notify(message, Colors.RED);
    }

    public void notify(IKey message, int background)
    {
        this.notifications.post(message, background);
    }

    public void notify(IKey message, int background, int color)
    {
        this.notifications.post(message, background, color);
    }

    public void postRender()
    {
        this.updateScroll();

        this.tooltip.render(this);
        this.notifications.render(this);
    }

    public void requestCursor(int shape)
    {
        this.cursorShape = shape;
    }

    public void resetCursor()
    {
        this.cursorShape = GLFW.GLFW_ARROW_CURSOR;
    }

    public void applyCursor()
    {
        Window.setStandardCursor(this.cursorShape);
    }

    /* Element focusing */

    public boolean isFocused()
    {
        return this.activeElement != null;
    }

    public void focus(IFocusedUIElement element)
    {
        this.focus(element, false);
    }

    public void focus(IFocusedUIElement element, boolean select)
    {
        if (this.activeElement == element)
        {
            return;
        }

        if (this.activeElement != null)
        {
            this.activeElement.unfocus(this);

            if (select)
            {
                this.activeElement.unselect(this);
            }
        }

        this.activeElement = element;

        if (this.activeElement != null)
        {
            this.activeElement.focus(this);
            this.adjustScroll((UIElement) element);

            if (select)
            {
                this.activeElement.selectAll(this);
            }
        }
    }

    private void adjustScroll(UIElement element)
    {
        UIScrollView scroll = null;
        UIElement original = element;
        int i = 10;

        while (scroll == null && element != null && i >= 0)
        {
            element = element.getParent();

            if (element instanceof UIScrollView)
            {
                scroll = (UIScrollView) element;
            }

            i -= 1;
        }

        if (scroll == null)
        {
            return;
        }

        ScrollDirection direction = scroll.scroll.direction;
        int target = direction.getPosition(original.area, 0) - direction.getPosition(scroll.area, 0);

        scroll.scroll.scrollIntoView(target, direction.getSide(original.area) + 5, 5);
    }

    public void unfocus()
    {
        this.focus(null);
    }

    public boolean focus(UIElement parent, int factor)
    {
        UIElement p = parent.getParentContainer();
        List<IFocusedUIElement> focused = p.getChildren(IFocusedUIElement.class);
        int i = focused.indexOf(this.activeElement);

        if (i >= 0)
        {
            i = MathUtils.cycler(i + factor, focused);

            this.focus(focused.get(i), true);
        }

        return i >= 0;
    }

    /* Context menu */

    public boolean hasContextMenu()
    {
        if (this.contextMenu == null)
        {
            return false;
        }

        if (!this.contextMenu.hasParent())
        {
            this.contextMenu = null;
        }

        return this.contextMenu != null;
    }

    public void setContextMenu(UIContextMenu menu)
    {
        if (this.hasContextMenu() || menu == null)
        {
            return;
        }

        menu.setMouse(this);
        menu.resize();

        this.contextMenu = menu;
        this.menu.overlay.add(menu);
    }

    public void replaceContextMenu(Consumer<ContextMenuManager> consumer)
    {
        ContextMenuManager manager = new ContextMenuManager();

        if (consumer != null)
        {
            consumer.accept(manager);
        }

        this.replaceContextMenu(manager.create());
    }

    public void replaceContextMenu(UIContextMenu menu)
    {
        if (menu == null)
        {
            return;
        }

        if (this.contextMenu != null)
        {
            this.contextMenu.removeFromParent();
        }

        menu.setMouse(this);
        menu.resize();

        this.contextMenu = menu;
        this.menu.overlay.add(menu);
    }

    public void closeContextMenu()
    {
        if (this.contextMenu != null)
        {
            this.contextMenu.removeFromParent();
            this.contextMenu = null;
        }
    }

    /* Viewport */

    /**
     * Get absolute X coordinate of the mouse without the
     * scrolling areas applied
     */
    public int mouseX()
    {
        return this.globalX(this.mouseX);
    }

    /**
     * Get absolute Y coordinate of the mouse without the
     * scrolling areas applied
     */
    public int mouseY()
    {
        return this.globalY(this.mouseY);
    }

    @Override
    public int getShiftX()
    {
        return this.mouseX;
    }

    @Override
    public int getShiftY()
    {
        return this.mouseY;
    }

    @Override
    public int globalX(int x)
    {
        return this.viewportStack.globalX(x);
    }

    @Override
    public int globalY(int y)
    {
        return this.viewportStack.globalY(y);
    }

    @Override
    public int localX(int x)
    {
        return this.viewportStack.localX(x);
    }

    @Override
    public int localY(int y)
    {
        return this.viewportStack.localY(y);
    }

    @Override
    public void shiftX(int x)
    {
        this.mouseX += x;
        this.render.batcher.getContext().getMatrices().translate(-x, 0, 0);
        this.viewportStack.shiftX(x);
    }

    @Override
    public void shiftY(int y)
    {
        this.mouseY += y;
        this.render.batcher.getContext().getMatrices().translate(0, -y, 0);
        this.viewportStack.shiftY(y);
    }

    @Override
    public void pushViewport(Area viewport)
    {
        this.viewportStack.pushViewport(viewport);
    }

    @Override
    public void popViewport()
    {
        this.viewportStack.popViewport();
    }

    @Override
    public Area getViewport()
    {
        return this.viewportStack.getViewport();
    }

    public void resetMatrix()
    {
        this.render.batcher.getContext().getMatrices().loadIdentity();
    }

    public void update()
    {
        this.tick += 1;

        this.notifications.update();
    }
}
