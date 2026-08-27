package mchorse.bbs_mod.ui.framework.elements.input.items;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.ui.utils.Marquee;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * The behaviour every collection of items shares — rows of a list, cells of a grid, nodes
 * of a tree: what a click picks (plain, Ctrl toggles, Shift extends), a band stretched with
 * Shift, a drag that reorders, the keyboard walking the items, and the callback that
 * tells the host what's picked now.
 *
 * <p>Subclasses only describe the geometry: which item sits at a point, where an item's
 * rectangle is, where a drop between items would land, and which item a key step leads
 * to. All of it in <em>content</em> coordinates — the element's own space, with its
 * scrolling taken out, so the same code serves a self-scrolling list and a grid embedded in
 * somebody else's scroll view (see {@link #originX()} / {@link #originY()}).</p>
 *
 * @param <T> what's listed
 */
public abstract class UIItems<T> extends UIElement
{
    /** The scrolling of this element; a subclass embedded in another scroll view leaves it idle. */
    public Scroll scroll;

    public final Selection<T> selection;
    public final ItemDrag<T> drag;
    public final Marquee marquee = new Marquee();
    protected final DoubleClick<T> doubleClick;

    /** Invoked with what's picked whenever the user changes it. */
    public Consumer<List<T>> callback;

    /** Whether more than one item may be picked. */
    public boolean multi;

    /** Whether items may be dragged into a new order. */
    public boolean sorting;

    public int background;

    /** The item the keyboard walks from: the one last clicked or stepped to, as a visible index. */
    protected int cursor = -1;

    /* What was picked when the band went down — Shift keeps it, everything else starts over */
    private final List<T> marqueeBase = new ArrayList<>();

    private final Area cell = new Area();

    public UIItems(Consumer<List<T>> callback)
    {
        this(callback, null);
    }

    /** @param same what makes two items the same one; {@link Object#equals} when null */
    public UIItems(Consumer<List<T>> callback, BiPredicate<T, T> same)
    {
        this(callback, same, null, null);
    }

    /**
     * @param selection a selection shared with other elements (the grids of one browser that
     *                  pick as one), or null for one of this element's own
     * @param drag      likewise — shared, a drag started in one element can drop into another
     */
    public UIItems(Consumer<List<T>> callback, BiPredicate<T, T> same, Selection<T> selection, ItemDrag<T> drag)
    {
        super();

        this.callback = callback;
        this.selection = selection == null ? new Selection<>(same) : selection;
        this.drag = drag == null ? new ItemDrag<>(same) : drag;
        this.doubleClick = new DoubleClick<>(same != null);
        this.scroll = new Scroll(this.area, 20);
    }

    /* Settings */

    public UIItems<T> background()
    {
        return this.background(Colors.A50);
    }

    public UIItems<T> background(int color)
    {
        this.background = color;

        return this;
    }

    public UIItems<T> multi()
    {
        this.multi = true;

        return this;
    }

    public UIItems<T> sorting()
    {
        this.sorting = true;

        return this;
    }

    public UIItems<T> cancelScrollEdge()
    {
        this.scroll.cancelScrollEdge = true;

        return this;
    }

    /* Geometry, in content coordinates */

    /** The items as shown right now, in order — after filtering, flattening, folding. */
    protected abstract List<T> visible();

    /** Index into {@link #visible()} of the item under a point, or -1. */
    protected abstract int indexAt(int x, int y);

    /** The rectangle of a visible item. */
    protected abstract void areaOf(int index, Area out);

    /** The slot a dragged item would land in if dropped at a point: 0 first, {@code visible().size()} last. */
    protected abstract int insertionAt(int x, int y);

    /** How long the content is along the scrolling direction. */
    protected abstract int contentSize();

    /**
     * The visible index an arrow key leads to from {@code index}; -1 when the key means
     * nothing here (a list has no left and right), so it's left to whoever else wants it.
     */
    protected abstract int step(int index, int dx, int dy);

    /** Screen X of content X 0. */
    protected int originX()
    {
        return this.area.x;
    }

    /** Screen Y of content Y 0: the top of the area, less what's scrolled away. */
    protected int originY()
    {
        return this.area.y - (int) this.scroll.getScroll();
    }

    protected int contentX(UIContext context)
    {
        return context.mouseX - this.originX();
    }

    protected int contentY(UIContext context)
    {
        return context.mouseY - this.originY();
    }

    /** Whether this element scrolls and clips itself, rather than being laid out by a parent. */
    protected boolean hasOwnScroll()
    {
        return true;
    }

    /** The scope Shift-ranges are confined to; none by default. */
    protected Object scope()
    {
        return null;
    }

    /* Hooks */

    /** The user opened an item (double click, Enter); whether that was handled. */
    protected boolean onOpen(T item)
    {
        return false;
    }

    /** The user pressed Delete over a pick; whether that was handled. */
    protected boolean onDelete(List<T> items)
    {
        return false;
    }

    /** Items were dropped between others of this element, before the item now at {@code insertion}. */
    protected void reorder(List<T> items, int insertion)
    {}

    /** Items were dropped onto something a subclass reported through {@link ItemDrag#setTarget(Object)}. */
    protected void onDrop(Object target, List<T> items)
    {}

    /** What's handed to the callback. */
    protected List<T> selected()
    {
        return new ArrayList<>(this.selection.getItems());
    }

    protected void fireCallback()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.selected());
        }
    }

    public void selectAll()
    {
        if (this.multi)
        {
            this.selection.setAll(this.visible());
        }
    }

    /** Whether a press over nothing drops the pick (grids do, lists keep it). */
    protected boolean clearsOnEmpty()
    {
        return false;
    }

    /**
     * How a left click over an item changes the pick. A plain click on one of several
     * picked items keeps the group, so it can be dragged as a whole.
     */
    protected void applySelectionOnClick(T item, int index)
    {
        if (this.multi && Window.isShiftPressed() && !this.selection.isEmpty())
        {
            this.selection.range(item, this.scope(), this.visible());
        }
        else if (this.multi && Window.isCtrlPressed())
        {
            this.selection.toggle(item, this.scope());
        }
        else if (!this.selection.contains(item) || !this.selection.isGroup())
        {
            this.selection.set(item, this.scope());
        }
    }

    /** What a press over this item would carry, or null when it can't be dragged. */
    protected List<T> dragPayload(T item)
    {
        if (!this.sorting || !this.selection.contains(item))
        {
            return null;
        }

        return this.selection.isGroup() ? new ArrayList<>(this.selection.getItems()) : Collections.singletonList(item);
    }

    protected void scrollIntoView(int index)
    {
        if (this.hasOwnScroll())
        {
            this.areaOf(index, this.cell);
            this.scroll.scrollIntoView(this.scroll.direction.getPosition(this.cell, 0F), this.scroll.direction.getSide(this.cell));
        }
    }

    /* Input */

    @Override
    public void resize()
    {
        super.resize();

        this.scroll.clamp();
        this.scroll.updateTarget();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.hasOwnScroll() && this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return false;
        }

        int x = this.contentX(context);
        int y = this.contentY(context);
        int index = this.indexAt(x, y);

        if (index >= 0 && index < this.visible().size())
        {
            return this.pressItem(index, context);
        }

        return this.pressEmpty(context);
    }

    /** A left press over a visible item. */
    protected boolean pressItem(int index, UIContext context)
    {
        T item = this.visible().get(index);
        boolean twice = this.doubleClick.hit(item);

        this.applySelectionOnClick(item, index);
        this.cursor = index;

        List<T> payload = this.dragPayload(item);

        if (payload != null && !payload.isEmpty())
        {
            this.drag.start(payload, context.mouseX, context.mouseY);
        }

        this.fireCallback();

        if (twice)
        {
            this.onOpen(item);
        }

        return true;
    }

    /**
     * A left press over nothing. The band is a Shift gesture everywhere in the mod — the
     * clips of the timeline, the keyframes, the form list — so a plain press only drops the
     * pick, and Shift (or Ctrl) is what stretches a rectangle over several items.
     */
    protected boolean pressEmpty(UIContext context)
    {
        boolean extending = Window.isShiftPressed() || Window.isCtrlPressed();

        if (this.clearsOnEmpty() && !extending)
        {
            this.selection.clear();
            this.fireCallback();
        }

        if (!this.multi || !extending)
        {
            return this.clearsOnEmpty();
        }

        this.marqueeBase.clear();
        this.marqueeBase.addAll(this.selection.getItems());

        this.marquee.press(this.contentX(context), this.contentY(context));

        return true;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        return this.hasOwnScroll() && this.scroll.mouseScroll(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.release();
        this.scroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    /** The button went up: finish whatever gesture was on. */
    protected void release()
    {
        if (this.marquee.isPressed())
        {
            boolean applied = this.marquee.isActive();

            this.marquee.reset();
            this.marqueeBase.clear();

            if (applied)
            {
                this.fireCallback();
            }
        }

        if (this.drag.isPressed())
        {
            if (this.drag.isActive())
            {
                this.drop();
            }

            this.drag.reset();
        }
    }

    private void drop()
    {
        List<T> items = new ArrayList<>(this.drag.getItems());
        Object target = this.drag.getTarget();

        if (target == this)
        {
            if (this.drag.getInsertion() >= 0)
            {
                this.reorder(items, this.drag.getInsertion());
            }
        }
        else if (target != null)
        {
            this.onDrop(target, items);
        }
    }

    /** While something is dragged over this element, say where it would land. */
    protected void reportDropTarget(int x, int y)
    {
        this.drag.setTarget(this, this.insertionAt(x, y));
    }

    /**
     * Refresh where the drag would land, every frame it's active. An element with a drag of
     * its own owns the whole answer; one sharing the drag with others overrides this to speak
     * only for its own area, so it doesn't wipe what a sibling under the cursor reported.
     */
    protected void updateDropTarget(boolean inside, int x, int y)
    {
        this.drag.clearTarget();

        if (inside)
        {
            this.reportDropTarget(x, y);
        }
    }

    /** Feed the cursor to the gestures in progress; called every frame before painting. */
    protected void updateGestures(UIContext context)
    {
        if (this.hasOwnScroll())
        {
            this.scroll.drag(context);
        }

        /* A release swallowed by another element (an overlay, a button) must not leave a gesture hanging */
        if ((this.drag.isPressed() || this.marquee.isPressed()) && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            this.release();
        }

        boolean inside = this.area.isInside(context);
        int x = this.contentX(context);
        int y = this.contentY(context);

        if (this.drag.isPressed())
        {
            this.drag.update(context.mouseX, context.mouseY);

            if (this.drag.isActive())
            {
                this.updateDropTarget(inside, x, y);
            }
        }

        if (this.marquee.isPressed())
        {
            this.marquee.update(x, y);

            if (this.marquee.isActive())
            {
                this.applyMarquee();
            }
        }

        /* Something carried or stretched to the edge scrolls the view along, so it can
         * reach what's out of sight */
        if (this.hasOwnScroll() && (this.drag.isActive() || this.marquee.isActive()))
        {
            this.scroll.autoScrollAt(context.mouseX, context.mouseY, Scroll.AUTO_SCROLL_EDGE, Scroll.AUTO_SCROLL_SPEED);
        }
    }

    /** The pick follows the band live: what was kept at the press, plus every item the band covers. */
    protected void applyMarquee()
    {
        List<T> visible = this.visible();
        Area band = this.marquee.getArea();

        this.selection.setAll(this.marqueeBase);

        for (int i = 0; i < visible.size(); i++)
        {
            this.areaOf(i, this.cell);

            if (this.cell.intersects(band))
            {
                this.selection.add(visible.get(i), this.scope());
            }
        }
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        /* Someone is typing somewhere, or the cursor is elsewhere: the keys are theirs */
        if (context.isFocused() || !this.area.isInside(context) || context.getKeyAction() == KeyAction.RELEASED)
        {
            return false;
        }

        List<T> visible = this.visible();

        if (visible.isEmpty())
        {
            return false;
        }

        int key = context.getKeyCode();
        boolean pressed = context.getKeyAction() == KeyAction.PRESSED;

        if (key == GLFW.GLFW_KEY_A && Window.isCtrlPressed() && pressed)
        {
            if (!this.multi)
            {
                return false;
            }

            this.selectAll();
            this.fireCallback();

            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER && pressed)
        {
            T first = this.selection.getFirst();

            return first != null && this.onOpen(first);
        }

        if (key == GLFW.GLFW_KEY_DELETE && pressed)
        {
            return !this.selection.isEmpty() && this.onDelete(new ArrayList<>(this.selection.getItems()));
        }

        int last = visible.size() - 1;
        int target;

        if (key == GLFW.GLFW_KEY_HOME)
        {
            target = 0;
        }
        else if (key == GLFW.GLFW_KEY_END)
        {
            target = last;
        }
        else
        {
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : (key == GLFW.GLFW_KEY_RIGHT ? 1 : 0);
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : (key == GLFW.GLFW_KEY_DOWN ? 1 : 0);

            if (dx == 0 && dy == 0)
            {
                return false;
            }

            int from = this.focusIndex();

            target = from < 0 ? (dx + dy > 0 ? 0 : last) : this.step(from, dx, dy);
        }

        if (target < 0 || target > last)
        {
            return false;
        }

        T item = visible.get(target);

        if (this.multi && Window.isShiftPressed())
        {
            this.selection.range(item, this.scope(), visible);
        }
        else
        {
            this.selection.set(item, this.scope());
        }

        this.cursor = target;
        this.scrollIntoView(target);
        this.fireCallback();

        return true;
    }

    /** Where the keyboard stands: the cursor, or failing that the anchor of the pick. */
    protected int focusIndex()
    {
        List<T> visible = this.visible();

        if (this.cursor >= 0 && this.cursor < visible.size())
        {
            return this.cursor;
        }

        T anchor = this.selection.getAnchor() != null ? this.selection.getAnchor() : this.selection.getFirst();

        return this.selection.indexOf(visible, anchor);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        this.updateGestures(context);

        if (Colors.getA(this.background) > 0)
        {
            this.area.render(context.batcher, this.background);
        }

        if (this.hasOwnScroll())
        {
            context.batcher.clip(this.area, context);
        }

        this.renderContent(context);

        if (this.drag.isActive() && this.drag.getTarget() == this)
        {
            this.renderInsertion(context, this.drag.getInsertion());
        }

        this.marquee.render(context, this.originX(), this.originY());

        if (this.hasOwnScroll())
        {
            this.scroll.renderScrollbar(context.batcher);
            context.batcher.unclip(context);
        }

        this.renderLockedArea(context);

        super.render(context);

        if (this.drag.isActive())
        {
            this.renderDragGhost(context);
        }
    }

    /** Paint the items; the area is clipped when this element scrolls itself. */
    protected abstract void renderContent(UIContext context);

    /** What's carried, beside the cursor. Drawn after everything else, outside the clip. */
    protected void renderDragGhost(UIContext context)
    {}

    /** The line between two rows where a drop would land. */
    protected void renderInsertion(UIContext context, int insertion)
    {
        int size = this.visible().size();

        if (insertion < 0 || size == 0)
        {
            return;
        }

        int y;

        if (insertion < size)
        {
            this.areaOf(insertion, this.cell);
            y = this.cell.y;
        }
        else
        {
            this.areaOf(size - 1, this.cell);
            y = this.cell.ey();
        }

        int sy = this.originY() + y;

        context.batcher.box(this.area.x, sy - 1, this.area.ex(), sy + 1, Colors.A100 | BBSSettings.primaryColor.get());
    }
}
