package mchorse.bbs_mod.ui.framework.elements;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.EventManager;
import mchorse.bbs_mod.ui.framework.elements.events.UIAddedEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeybindManager;
import mchorse.bbs_mod.ui.utils.resizers.ChildResizer;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import mchorse.bbs_mod.ui.utils.resizers.Margin;
import mchorse.bbs_mod.ui.utils.resizers.constraint.BoundsResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.ColumnResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.ui.utils.resizers.layout.RowResizer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.undo.IUndoElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIElement implements IUIElement, IUndoElement
{
    private String undoId = "";

    /**
     * Area of this element (i.e. position and size) 
     */
    public Area area = new Area();

    /**
     * Element's margin (it's used only by layout resizers)
     */
    public final Margin margin = new Margin();

    /**
     * Whether this element grows into the space its parent's layout has left over
     * (it's used only by layout resizers), see {@link #expand()}
     */
    protected boolean expand;

    /**
     * Flex resizer of this class
     */
    protected Flex flex = new Flex();

    /**
     * Resizer of this class
     */
    protected IResizer resizer = this.flex;

    /**
     * Tooltip instance
     */
    public ITooltip tooltip;

    /**
     * Keybind manager
     */
    private KeybindManager keybinds;

    /**
     * Context menu supplier
     */
    private Supplier<UIContextMenu> contextSupplier;

    /**
     * Context menu options
     */
    private List<Consumer<ContextMenuManager>> contextOptions;

    /**
     * Reads the property this element edits back into it — see
     * {@link mchorse.bbs_mod.ui.utils.values.UIValues}. Run on every frame the element is drawn,
     * so a field shows what its property holds now rather than what someone last poured into it.
     *
     * <p>Null for an element bound to nothing, which is most of them.</p>
     */
    private Runnable valueBinding;

    /**
     * Whether this element can be culled if it's out of viewport
     */
    public boolean culled = true;

    /**
     * Whether this element is a container
     */
    protected boolean container;

    /**
     * Determines how mouse events will be propagated
     */
    protected EventPropagation mousePropagation = EventPropagation.PASS;

    /**
     * Determines how keyboard events will be propagated
     */
    protected EventPropagation keyboardPropagation = EventPropagation.PASS;

    /**
     * Parent GUI element
     */
    protected UIElement parent;

    /**
     * Children elements
     */
    private List<IUIElement> children = new ArrayList<>();

    /**
     * Whether this element or anything under it listens to tree events. {@link #onAdd} and
     * {@link #onRemove} used to walk the WHOLE subtree of every element being attached, looking
     * for {@link IUITreeEventListener}s — with exactly one implementor in the codebase, that
     * made building a large screen O(n²) of pure nothing. The flag rides up the ancestors when
     * a listener-carrying child is attached; a removal may leave it stale at {@code true},
     * which only costs the walk it would have done anyway.
     */
    private boolean treeListeners = this instanceof IUITreeEventListener;

    /**
     * Whether this element is enabled (can handle any input) 
     */
    protected boolean enabled = true;

    /**
     * Whether this element is visible 
     */
    protected boolean visible = true;

    protected EventManager events = new EventManager();

    /**
     * Custom data that can be stored within this UI element
     */
    public EventManager getEvents()
    {
        return this.events;
    }

    /* Hierarchy management */

    public UIBaseMenu.UIRootElement getRoot()
    {
        UIElement element = this;

        while (element.getParent() != null)
        {
            element = element.getParent();
        }

        return element instanceof UIBaseMenu.UIRootElement ? (UIBaseMenu.UIRootElement) element : null;
    }

    public UIContext getContext()
    {
        UIBaseMenu.UIRootElement root = this.getRoot();

        return root == null ? null : root.getContext();
    }

    public UIElement getParent()
    {
        return this.parent;
    }

    public <T extends UIElement> T getParent(Class<T> clazz)
    {
        UIElement element = this.getParent();

        while (element != null)
        {
            if (element.getClass() == clazz)
            {
                return (T) element;
            }

            element = element.getParent();
        }

        return null;
    }

    /**
     * The nearest ancestor of the given type. Unlike {@link #getParent(Class)}, which matches the
     * exact class, this accepts subclasses and interfaces — so an element can ask for a role
     * ("whoever owns the bone selection") instead of naming a widget class.
     */
    public <T> T getAncestor(Class<T> clazz)
    {
        UIElement element = this.getParent();

        while (element != null)
        {
            if (clazz.isInstance(element))
            {
                return (T) element;
            }

            element = element.getParent();
        }

        return null;
    }

    public boolean hasParent()
    {
        return this.parent != null;
    }

    public boolean isDescendant(UIElement element)
    {
        if (this == element)
        {
            return false;
        }

        while (element != null)
        {
            if (element.parent == this)
            {
                return true;
            }

            element = element.parent;
        }

        return false;
    }

    public List<IUIElement> getChildren()
    {
        return this.children;
    }

    public <T> List<T> getChildren(Class<T> clazz)
    {
        return this.getChildren(clazz, new ArrayList<>());
    }

    public <T> List<T> getChildren(Class<T> clazz, List<T> list)
    {
        return this.getChildren(clazz, list, false);
    }

    public <T> List<T> getChildren(Class<T> clazz, List<T> list, boolean includeItself)
    {
        if (includeItself && clazz.isAssignableFrom(this.getClass()))
        {
            list.add(clazz.cast(this));
        }

        for (IUIElement element : this.getChildren())
        {
            if (clazz.isAssignableFrom(element.getClass()))
            {
                list.add(clazz.cast(element));
            }

            if (element instanceof UIElement)
            {
                /* Never with includeItself: this loop has already considered the
                 * child, and passing the flag down would list it a second time. */
                ((UIElement) element).getChildren(clazz, list, false);
            }
        }

        return list;
    }

    public <T> void visitChildren(Class<T> clazz, boolean includeItself, Consumer<T> consumer)
    {
        if (consumer == null)
        {
            return;
        }

        if (includeItself && clazz.isAssignableFrom(this.getClass()))
        {
            consumer.accept(clazz.cast(this));
        }

        for (IUIElement element : this.getChildren())
        {
            if (clazz.isAssignableFrom(element.getClass()))
            {
                consumer.accept(clazz.cast(element));
            }

            if (element instanceof UIElement)
            {
                /* See getChildren: the flag must not travel down, or every
                 * descendant is handed over twice. */
                ((UIElement) element).visitChildren(clazz, false, consumer);
            }
        }
    }

    public void prepend(IUIElement element)
    {
        if (element != null)
        {
            this.children.add(0, element);
            this.markChild(element);
        }
    }

    public void add(IUIElement element)
    {
        if (element != null)
        {
            this.children.add(element);
            this.markChild(element);
        }
    }

    public void add(IUIElement... elements)
    {
        for (IUIElement element : elements)
        {
            if (element != null)
            {
                this.children.add(element);
                this.markChild(element);
            }
        }
    }

    public void addAfter(IUIElement target, IUIElement element)
    {
        int index = this.children.indexOf(target);

        if (index != -1 && element != null)
        {
            if (index + 1 >= this.children.size())
            {
                this.children.add(element);
            }
            else
            {
                this.children.add(index + 1, element);
            }

            this.markChild(element);
        }
    }

    public void addBefore(IUIElement target, IUIElement element)
    {
        int index = this.children.indexOf(target);

        if (index != -1 && element != null)
        {
            this.children.add(index, element);

            this.markChild(element);
        }
    }

    private void markChild(IUIElement element)
    {
        if (element instanceof UIElement)
        {
            UIElement child = (UIElement) element;

            child.parent = this;

            if (child.treeListeners)
            {
                for (UIElement ancestor = this; ancestor != null && !ancestor.treeListeners; ancestor = ancestor.parent)
                {
                    ancestor.treeListeners = true;
                }
            }

            child.onAdd(this);

            if (this.resizer != null)
            {
                this.resizer.add(this, child);
            }

            this.invalidateLayout();
        }
    }

    public void removeAll()
    {
        for (IUIElement uiElement : this.children)
        {
            if (uiElement instanceof UIElement)
            {
                UIElement element = (UIElement) uiElement;

                if (this.resizer != null)
                {
                    this.resizer.remove(this, element);
                }

                element.onRemove(element.parent);
                element.parent = null;
            }
        }

        this.children.clear();
        this.invalidateLayout();
    }

    public void removeFromParent()
    {
        if (this.hasParent())
        {
            this.parent.remove(this);
        }
    }

    public void remove(IUIElement element)
    {
        this.children.remove(element);
    }

    public void remove(UIElement element)
    {
        if (this.children.remove(element))
        {
            if (this.resizer != null)
            {
                this.resizer.remove(this, element);
            }

            element.onRemove(element.parent);
            element.parent = null;

            this.invalidateLayout();
        }
    }

    /**
     * Mark this element's layout stale: it gets resized once before the next frame (see
     * {@link UIContext#flushLayout()}). Nothing happens while detached — attaching to a
     * tree resizes anyway.
     */
    public void invalidateLayout()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        UIElement target = this;

        /* An element placed by its parent's row/column/grid can't be laid out alone: that
         * pass owns the running cursor, and resizing one child reads it where the last full
         * pass left it, throwing the child to the end of the row. Climb to the first element
         * that owns its own placement. */
        while (target.resizer instanceof ChildResizer && target.parent != null)
        {
            target = target.parent;
        }

        context.invalidateLayout(target);
    }

    protected void onAdd(UIElement parent)
    {
        this.events.emit(new UIAddedEvent(this));

        if (this.treeListeners)
        {
            for (IUITreeEventListener listener : this.getChildren(IUITreeEventListener.class))
            {
                listener.onAddedToTree(this);
            }
        }
    }

    protected void onRemove(UIElement parent)
    {
        this.events.emit(new UIRemovedEvent(this));

        if (this.treeListeners)
        {
            for (IUITreeEventListener listener : this.getChildren(IUITreeEventListener.class))
            {
                listener.onRemovedFromTree(this);
            }
        }
    }

    public UIElement eventPropagataion(EventPropagation propagation)
    {
        return this.mouseEventPropagataion(propagation).keyboardEventPropagataion(propagation);
    }

    public UIElement mouseEventPropagataion(EventPropagation propagation)
    {
        this.mousePropagation = propagation;

        return this;
    }

    public UIElement keyboardEventPropagataion(EventPropagation propagation)
    {
        this.keyboardPropagation = propagation;

        return this;
    }

    /* Custom data */

    /* Setters */

    public UIElement removeTooltip()
    {
        this.tooltip = null;

        return this;
    }

    public UIElement tooltip(ITooltip tooltip)
    {
        this.tooltip = tooltip;

        return this;
    }

    public UIElement tooltip(IKey label)
    {
        return this.tooltip(label, Direction.BOTTOM);
    }

    public UIElement tooltip(IKey label, Direction direction)
    {
        return this.tooltip(new LabelTooltip(label, direction));
    }

    public UIElement tooltip(IKey label, int width, Direction direction)
    {
        return this.tooltip(new LabelTooltip(label, width, direction));
    }

    public UIElement noCulling()
    {
        this.culled = false;

        return this;
    }

    /* Keybind manager */

    public KeybindManager keys()
    {
        if (this.keybinds == null)
        {
            this.keybinds = new KeybindManager();
        }

        return this.keybinds;
    }

    /* Container stuff */

    public UIElement markContainer()
    {
        this.container = true;

        return this;
    }

    public boolean isContainer()
    {
        return this.container;
    }

    public UIElement getParentContainer()
    {
        UIElement element = this.getParent();

        while (element != null && !element.isContainer())
        {
            element = element.getParent();
        }

        return element;
    }

    public UIElement context(Supplier<UIContextMenu> supplier)
    {
        if (supplier != null)
        {
            this.contextSupplier = supplier;
        }

        return this;
    }

    public UIElement context(Consumer<ContextMenuManager> consumer)
    {
        if (consumer != null)
        {
            if (this.contextOptions == null)
            {
                this.contextOptions = new ArrayList<>();
            }

            this.contextOptions.add(consumer);
        }

        return this;
    }

    /**
     * Create a context menu instance
     *
     * Some subclasses of UIElement might want to override this method in order to create their
     * own context menus.
     */
    public UIContextMenu createContextMenu(UIContext context)
    {
        if (this.contextSupplier != null)
        {
            return this.contextSupplier.get();
        }

        if (this.contextOptions == null)
        {
            return null;
        }

        ContextMenuManager manager = new ContextMenuManager();

        for (Consumer<ContextMenuManager> consumer : this.contextOptions)
        {
            consumer.accept(manager);
        }

        return manager.create();
    }

    /* Resizer methods */

    public Flex getFlex()
    {
        return this.flex;
    }

    public IResizer resizer()
    {
        return this.resizer;
    }

    public UIElement resizer(IResizer resizer)
    {
        this.resizer = resizer;

        return this;
    }

    public UIElement resetFlex()
    {
        this.flex.x.reset();
        this.flex.y.reset();
        this.flex.w.reset();
        this.flex.h.reset();

        this.flex.relative = this.flex.post = null;

        return this;
    }

    public UIElement set(int x, int y, int w, int h)
    {
        this.flex.x.set(0, x);
        this.flex.y.set(0, y);
        this.flex.w.set(0, w);
        this.flex.h.set(0, h);

        return this;
    }

    /* X */

    public UIElement x(int offset)
    {
        this.flex.x.set(0, offset);

        return this;
    }

    public UIElement x(float value)
    {
        this.flex.x.set(value, 0);

        return this;
    }

    public UIElement x(float value, int offset)
    {
        this.flex.x.set(value, offset);

        return this;
    }

    /* Y */

    public UIElement y(int offset)
    {
        this.flex.y.set(0, offset);

        return this;
    }

    public UIElement y(float value)
    {
        this.flex.y.set(value, 0);

        return this;
    }

    public UIElement y(float value, int offset)
    {
        this.flex.y.set(value, offset);

        return this;
    }

    /* Width */

    public UIElement w(int offset)
    {
        this.flex.w.set(0, offset);

        return this;
    }

    public UIElement w(float value)
    {
        this.flex.w.set(value, 0);

        return this;
    }

    public UIElement w(float value, int offset)
    {
        this.flex.w.set(value, offset);

        return this;
    }

    public UIElement wTo(IResizer flex)
    {
        this.flex.w.target = flex;

        return this;
    }

    public UIElement wTo(IResizer flex, int offset)
    {
        this.flex.w.target = flex;
        this.flex.w.offset = offset;

        return this;
    }

    public UIElement wTo(IResizer flex, float anchor)
    {
        this.flex.w.target = flex;
        this.flex.w.targetAnchor = anchor;

        return this;
    }

    public UIElement wTo(IResizer flex, float anchor, int offset)
    {
        this.flex.w.target = flex;
        this.flex.w.targetAnchor = anchor;
        this.flex.w.offset = offset;

        return this;
    }

    /* Height */

    public UIElement h(int offset)
    {
        this.flex.h.set(0, offset);

        return this;
    }

    public UIElement h(float value)
    {
        this.flex.h.set(value, 0);

        return this;
    }

    public UIElement h(float value, int offset)
    {
        this.flex.h.set(value, offset);

        return this;
    }

    public UIElement hTo(IResizer target)
    {
        return this.hTo(target, 0);
    }

    public UIElement hTo(IResizer target, int offset)
    {
        return this.hTo(target, 0F, offset);
    }

    public UIElement hTo(IResizer target, float anchor)
    {
        return this.hTo(target, anchor, 0);
    }

    public UIElement hTo(IResizer target, float anchor, int offset)
    {
        this.flex.h.target = target;
        this.flex.h.targetAnchor = anchor;
        this.flex.h.offset = offset;

        return this;
    }

    /* Expansion */

    /**
     * Grow into whatever vertical space the parent layout has left over.
     *
     * <p>A marker rather than a size, because how much is left over is only known while the parent
     * lays itself out: {@link ColumnResizer} hands every child marked this way an equal share of
     * the height it did not spend on the others, and {@link RowResizer} gives it the full height of
     * the row. The height the element asks for on its own ({@link #h(int)} and friends) stays as
     * its minimum &mdash; the share is added on top of it, and when there is nothing left over
     * (the content already overflows, e.g. a scroll view scrolls) it keeps exactly that height.</p>
     *
     * <p>Expansion does not pass through a layer that hasn't asked for it: to let a list at the
     * bottom of a nested column fill a scroll view, every element on the way down &mdash; the
     * column and the list &mdash; has to be marked. That is what keeps the marker local: an element
     * can only ever take space its own parent had spare.</p>
     */
    public UIElement expand()
    {
        return this.expand(true);
    }

    public UIElement expand(boolean expand)
    {
        this.expand = expand;

        return this;
    }

    public boolean isExpanding()
    {
        return this.expand;
    }

    /* Other variations */

    public UIElement xy(int x, int y)
    {
        this.flex.x.set(0, x);
        this.flex.y.set(0, y);

        return this;
    }

    public UIElement xy(float x, float y)
    {
        this.flex.x.set(x);
        this.flex.y.set(y);

        return this;
    }

    public UIElement wh(int w, int h)
    {
        this.flex.w.set(0, w);
        this.flex.h.set(0, h);

        return this;
    }

    public UIElement full(IResizer relative)
    {
        return this.relative(relative).wh(1F, 1F);
    }

    public UIElement full(UIElement relative)
    {
        return this.relative(relative).wh(1F, 1F);
    }

    public UIElement wh(float w, float h)
    {
        this.flex.w.set(w);
        this.flex.h.set(h);

        return this;
    }

    public UIElement minW(int max)
    {
        this.flex.w.min = max;

        return this;
    }

    public UIElement maxW(int max)
    {
        this.flex.w.max = max;

        return this;
    }

    public UIElement maxH(int max)
    {
        this.flex.h.max = max;

        return this;
    }

    public UIElement anchor(float x)
    {
        return this.anchor(x, x);
    }

    public UIElement anchor(float x, float y)
    {
        this.flex.x.anchor = x;
        this.flex.y.anchor = y;

        return this;
    }

    public UIElement anchorX(float x)
    {
        this.flex.x.anchor = x;

        return this;
    }

    public UIElement anchorY(float y)
    {
        this.flex.y.anchor = y;

        return this;
    }

    /* Post resizers convenience methods */

    public RowResizer row()
    {
        return this.row(UIConstants.MARGIN);
    }

    public RowResizer row(int margin)
    {
        if (this.flex.post instanceof RowResizer)
        {
            return (RowResizer) this.flex.post;
        }

        return RowResizer.apply(this, margin);
    }

    public ColumnResizer column()
    {
        return this.column(UIConstants.MARGIN);
    }

    public ColumnResizer column(int margin)
    {
        if (this.flex.post instanceof ColumnResizer)
        {
            return (ColumnResizer) this.flex.post;
        }

        return ColumnResizer.apply(this, margin);
    }

    public GridResizer grid(int margin)
    {
        if (this.flex.post instanceof GridResizer)
        {
            return (GridResizer) this.flex.post;
        }

        return GridResizer.apply(this, margin);
    }

    public BoundsResizer bounds(UIElement target, int padding)
    {
        if (this.flex.post instanceof BoundsResizer boundsResizer)
        {
            boundsResizer.target = target;
            boundsResizer.padding = padding;

            return boundsResizer;
        }

        return BoundsResizer.apply(this, target, padding);
    }

    /* Hierarchy */

    public UIElement relative(UIElement element)
    {
        this.flex.relative = element.area;

        return this;
    }

    public UIElement relative(IResizer relative)
    {
        this.flex.relative = relative;

        return this;
    }

    public UIElement post(IResizer post)
    {
        this.flex.post = post;

        return this;
    }

    /* Margin */

    public UIElement margin(int all)
    {
        return this.margin(all, all);
    }

    public UIElement margin(int horizontal, int vertical)
    {
        return this.margin(horizontal, vertical, horizontal, vertical);
    }

    public UIElement margin(int left, int top, int right, int bottom)
    {
        this.margin.all(left, top, right, bottom);

        return this;
    }

    public UIElement marginTop(int top)
    {
        this.margin.top(top);

        return this;
    }

    public UIElement marginBottom(int bottom)
    {
        this.margin.bottom(bottom);

        return this;
    }

    /* Enabled methods */

    @Override
    public boolean isEnabled()
    {
        return this.enabled && this.visible;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public boolean isVisible()
    {
        return this.visible;
    }

    public void setVisible(boolean visible)
    {
        if (this.visible == visible)
        {
            return;
        }

        this.visible = visible;

        /* Layout resizers skip hidden children, so the parent's layout is what changed */
        if (this.parent != null)
        {
            this.parent.invalidateLayout();
        }
    }

    public void toggleVisible()
    {
        this.setVisible(!this.visible);
    }

    /**
     * Whether element can be seen on the screen
     */
    public boolean canBeSeen()
    {
        if (!this.hasParent() || !this.isVisible())
        {
            return false;
        }

        UIElement element = this;

        while (true)
        {
            if (!element.isVisible())
            {
                return false;
            }

            UIElement parent = element.getParent();

            if (parent == null)
            {
                break;
            }

            element = parent;
        }

        return element instanceof UIBaseMenu.UIRootElement;
    }

    /* Overriding those methods so it would be much easier to 
     * override only needed methods in subclasses */

    @Override
    public void resize()
    {
        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        this.afterResizeApplied();

        for (IUIElement element : this.children)
        {
            element.resize();
        }

        if (this.resizer != null)
        {
            this.resizer.postApply(this.area);
        }
    }

    protected void afterResizeApplied()
    {}

    public void clickItself()
    {
        this.clickItself(this.getContext());
    }

    public void clickItself(int mouseButton)
    {
        this.clickItself(this.getContext(), mouseButton);
    }

    public void clickItself(UIContext context)
    {
        this.clickItself(context, 0);
    }

    public void clickItself(UIContext context, int mouseButton)
    {
        if (!this.isEnabled())
        {
            return;
        }

        if (context == null)
        {
            this.clickItselfWithoutContext(mouseButton);

            return;
        }

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        int button = context.mouseButton;

        context.mouseX = this.area.x + 1;
        context.mouseY = this.area.y + 1;
        context.mouseButton = mouseButton;

        this.mouseClicked(context);

        context.mouseX = mouseX;
        context.mouseY = mouseY;
        context.mouseButton = button;
    }

    protected void clickItselfWithoutContext(int mouseButton)
    {}

    /* Handling input events
     *
     * These methods are final to prevent changing the pipeline. You're free to
     * subclass children*, sub* or misc. event handling methods! */

    @Override
    public final IUIElement mouseClicked(UIContext context)
    {
        IUIElement element = this.childrenMouseClicked(context);

        if (element != null)
        {
            return element;
        }

        return this.subMouseClicked(context) || this.keybindsMouseClicked(context) || this.mouseClickedContextMenu(context) || this.cantPropagate(this.mousePropagation, context) ? this : null;
    }

    @Override
    public final IUIElement mouseScrolled(UIContext context)
    {
        IUIElement element = this.childrenMouseScrolled(context);

        if (element != null)
        {
            return element;
        }

        return this.subMouseScrolled(context) || this.cantPropagate(this.mousePropagation, context) ? this : null;
    }

    @Override
    public final IUIElement mouseReleased(UIContext context)
    {
        IUIElement element = this.childrenMouseReleased(context);

        if (element != null)
        {
            return element;
        }

        return this.subMouseReleased(context) || this.cantPropagate(this.mousePropagation, context) ? this : null;
    }

    @Override
    public final IUIElement keyPressed(UIContext context)
    {
        IUIElement element = this.childrenKeyPressed(context);

        if (element != null)
        {
            return element;
        }

        return this.subKeyPressed(context) || this.keybindsKeyPressed(context) || this.cantPropagate(this.keyboardPropagation, context) ? this : null;
    }

    @Override
    public final IUIElement textInput(UIContext context)
    {
        IUIElement element = this.childrenTextInput(context);

        if (element != null)
        {
            return element;
        }

        return this.subTextInput(context) || this.cantPropagate(this.keyboardPropagation, context) ? this : null;
    }

    /* Handling children input events */

    protected IUIElement childrenMouseClicked(UIContext context)
    {
        for (int i = this.children.size() - 1; i >= 0; i--)
        {
            if (i >= this.children.size())
            {
                continue;
            }

            IUIElement element = this.children.get(i);

            if (element.isEnabled())
            {
                IUIElement anElement = element.mouseClicked(context);

                if (anElement != null)
                {
                    return anElement;
                }
            }
        }

        return null;
    }

    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        for (int i = this.children.size() - 1; i >= 0; i--)
        {
            if (i >= this.children.size())
            {
                continue;
            }

            IUIElement element = this.children.get(i);

            if (element.isEnabled())
            {
                IUIElement anElement = element.mouseScrolled(context);

                if (anElement != null)
                {
                    return anElement;
                }
            }
        }

        return null;
    }

    protected IUIElement childrenMouseReleased(UIContext context)
    {
        for (int i = this.children.size() - 1; i >= 0; i--)
        {
            if (i >= this.children.size())
            {
                continue;
            }

            IUIElement element = this.children.get(i);

            if (element.isEnabled())
            {
                IUIElement anElement = element.mouseReleased(context);

                if (anElement != null)
                {
                    return anElement;
                }
            }
        }

        return null;
    }

    protected IUIElement childrenKeyPressed(UIContext context)
    {
        for (int i = this.children.size() - 1; i >= 0; i--)
        {
            if (i >= this.children.size())
            {
                continue;
            }

            IUIElement element = this.children.get(i);

            if (element.isEnabled())
            {
                IUIElement anElement = element.keyPressed(context);

                if (anElement != null)
                {
                    return anElement;
                }
            }
        }

        return null;
    }

    protected IUIElement childrenTextInput(UIContext context)
    {
        for (int i = this.children.size() - 1; i >= 0; i--)
        {
            if (i >= this.children.size())
            {
                continue;
            }

            IUIElement element = this.children.get(i);

            if (element.isEnabled())
            {
                IUIElement anElement = element.textInput(context);

                if (anElement != null)
                {
                    return anElement;
                }
            }
        }

        return null;
    }

    /* Subclasses' input event handling */

    protected boolean subMouseClicked(UIContext context)
    {
        return false;
    }

    protected boolean subMouseScrolled(UIContext context)
    {
        return false;
    }

    protected boolean subMouseReleased(UIContext context)
    {
        return false;
    }

    protected boolean subKeyPressed(UIContext context)
    {
        return false;
    }

    protected boolean subTextInput(UIContext context)
    {
        return false;
    }

    /* Misc. input event handling */

    /**
     * Handle creating a context menu (when right clicked in the area, a context
     * menu may appear, if configured)
     */
    protected boolean mouseClickedContextMenu(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 1 && !context.hasContextMenu())
        {
            UIContextMenu menu = this.createContextMenu(context);

            if (menu != null && !menu.isEmpty())
            {
                context.setContextMenu(menu);

                return true;
            }
        }

        return false;
    }

    /**
     * Handle keybind manager's keybinds
     */
    protected boolean keybindsMouseClicked(UIContext context)
    {
        return this.keybinds != null && this.keybinds.checkMouse(context, this.area.isInside(context));
    }

    /**
     * Handle keybind manager's keybinds
     */
    protected boolean keybindsKeyPressed(UIContext context)
    {
        return this.keybinds != null && this.keybinds.check(context, this.area.isInside(context));
    }

    /**
     * Checks whether an input event can be propagated
     */
    protected boolean cantPropagate(EventPropagation propagation, UIContext context)
    {
        if (propagation == EventPropagation.BLOCK)
        {
             return true;
        }

        return propagation == EventPropagation.BLOCK_INSIDE && this.area.isInside(context);
    }

    /* Rendering */

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return !this.culled || viewport.intersects(this.area);
    }

    /**
     * Bind this element to the property it edits: the given step reads that property into the
     * widget, and runs on every frame the element is drawn.
     *
     * <p>Writing was always the easy half — a widget is handed a callback and writes through it.
     * This is the other half, and without it every panel had to pour values into its widgets by
     * hand whenever the thing being edited changed underneath.</p>
     */
    public UIElement valueBinding(Runnable valueBinding)
    {
        this.valueBinding = valueBinding;

        return this;
    }

    /**
     * Whether the user is working inside this element right now, in which case
     * {@link #valueBinding(Runnable)} leaves it alone: re-reading the property under a half-typed
     * number or a drag in progress would fight the very input about to write it.
     *
     * <p>The question is asked of the whole subtree, not of this element alone. A composite widget
     * is bound as one piece — a transform, a point, an angle — so the field the user is actually
     * working in is never the element holding the binding, and only the group's own read can be
     * held off. For a bound leaf (which is all the binding had until now) the walk finds nothing
     * and the answer is unchanged.
     */
    public boolean isUserEditing()
    {
        if (this instanceof IFocusedUIElement focused && focused.isFocused())
        {
            return true;
        }

        for (IUIElement child : this.children)
        {
            if (child instanceof UIElement element && element.isUserEditing())
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.valueBinding != null && !this.isUserEditing())
        {
            this.valueBinding.run();
        }

        if (this.keybinds != null && this.isEnabled())
        {
            this.keybinds.add(context, this.area.isInside(context));
        }

        if (this.tooltip != null && this.area.isInside(context))
        {
            context.tooltip.set(context, this);
        }
        else if ((this.container || this.mousePropagation != EventPropagation.PASS) && this.area.isInside(context))
        {
            context.resetTooltip();
        }

        for (IUIElement element : this.children)
        {
            if (element.isVisible() && element.canBeRendered(context.getViewport()))
            {
                element.render(context);
            }
        }
    }

    public void renderTooltip(UIContext context, Area area)
    {
        context.tooltip.render(this.tooltip, context);
    }

    /**
     * Generic method for rendering locked (disabled) state of an input field
     */
    public void renderLockedArea(UIContext context)
    {
        if (!this.isEnabled())
        {
            this.area.render(context.batcher, Colors.A50);

            context.batcher.outlinedIcon(Icons.LOCKED, this.area.mx(), this.area.my(), 0.5F, 0.5F);
        }
    }

    /* IUndoElement implementation */

    @Override
    public String getUndoId()
    {
        return this.undoId;
    }

    public void setUndoId(String undoId)
    {
        this.undoId = undoId;
    }

    @Override
    public void applyUndoData(MapType data)
    {}

    public void applyAllUndoData(MapType data)
    {
        this.visitChildren(IUndoElement.class, true, (child) ->
        {
            String id = child.getUndoId();

            if (!id.isEmpty() && data.has(id))
            {
                child.applyUndoData(data.getMap(id));
            }
        });
    }

    @Override
    public void collectUndoData(MapType data)
    {}

    public MapType collectAllUndoData()
    {
        MapType uiData = new MapType();

        this.visitChildren(IUndoElement.class, true, (child) ->
        {
            String id = child.getUndoId();

            if (!id.isEmpty())
            {
                MapType data = new MapType();

                child.collectUndoData(data);
                uiData.put(id, data);
            }
        });

        return uiData;
    }
}
