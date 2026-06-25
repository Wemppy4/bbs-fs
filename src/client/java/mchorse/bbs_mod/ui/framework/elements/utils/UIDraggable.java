package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIDraggable extends UIElement
{
    private Consumer<UIContext> callback;
    private Consumer<UIContext> render;
    private Supplier<Vector2i> reference;
    private Runnable dragEndCallback;
    private boolean dragging;
    private boolean hover;
    private boolean referenceX = true;
    private boolean referenceY = true;
    private int hoverCursor = GLFW.GLFW_HAND_CURSOR;
    private int dragCursor = GLFW.GLFW_HAND_CURSOR;
    private Supplier<Boolean> enabled = () -> true;

    private int mouseX;
    private int mouseY;
    private Vector2i referenceMouse;

    public UIDraggable(Consumer<UIContext> callback)
    {
        this.callback = callback;
    }

    public UIDraggable hoverOnly()
    {
        this.hover = true;

        return this;
    }

    public UIDraggable rendering(Consumer<UIContext> render)
    {
        this.render = render;

        return this;
    }

    public UIDraggable reference(Supplier<Vector2i> reference)
    {
        this.reference = reference;

        return this;
    }

    public UIDraggable referenceAxis(boolean x, boolean y)
    {
        this.referenceX = x;
        this.referenceY = y;

        return this;
    }

    public UIDraggable dragEnd(Runnable callback)
    {
        this.dragEndCallback = callback;

        return this;
    }

    public UIDraggable cursor(int cursor)
    {
        this.hoverCursor = cursor;

        return this;
    }

    public UIDraggable dragCursor(int cursor)
    {
        this.dragCursor = cursor;

        return this;
    }

    public UIDraggable cursors(int hoverCursor, int dragCursor)
    {
        this.hoverCursor = hoverCursor;
        this.dragCursor = dragCursor;

        return this;
    }

    /**
     * Gate interactivity: when the supplier returns {@code false} the draggable is inert &mdash; it
     * neither claims clicks nor requests a hover/drag cursor, so the area behaves as if the handle
     * weren't there. Defaults to always enabled.
     */
    public UIDraggable enabled(Supplier<Boolean> enabled)
    {
        this.enabled = enabled != null ? enabled : () -> true;

        return this;
    }

    public boolean isDragging()
    {
        return this.dragging;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.enabled.get() && this.area.isInside(context) && context.mouseButton == 0)
        {
            this.mouseX = context.mouseX;
            this.mouseY = context.mouseY;
            this.dragging = true;

            if (this.reference != null)
            {
                this.referenceMouse = this.reference.get();
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        boolean wasDragging = this.dragging;

        this.dragging = false;

        if (wasDragging && this.dragEndCallback != null)
        {
            this.dragEndCallback.run();
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.enabled.get())
        {
            if (this.dragging)
            {
                context.requestCursor(this.dragCursor);
            }
            else if (this.area.isInside(context))
            {
                context.requestCursor(this.hoverCursor);
            }
        }

        if (!this.hover || this.area.isInside(context) || this.dragging)
        {
            if (this.render != null)
            {
                this.render.accept(context);
            }
            else
            {
                Scroll.bar(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey());
            }
        }

        if (this.dragging && this.callback != null)
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;

            if (this.referenceMouse != null)
            {
                if (this.referenceX)
                {
                    context.mouseX = this.referenceMouse.x + (mouseX - this.mouseX);
                }

                if (this.referenceY)
                {
                    context.mouseY = this.referenceMouse.y + (mouseY - this.mouseY);
                }
            }

            this.callback.accept(context);

            context.mouseX = mouseX;
            context.mouseY = mouseY;
        }
    }
}
