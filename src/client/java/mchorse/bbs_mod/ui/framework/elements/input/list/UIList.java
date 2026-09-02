package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItems;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.renderers.EmptyStateRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Abstract GUI list element: rows of equal height down a scrolling area.
 *
 * <p>Selection lives in {@link #selection} as the row objects themselves; {@link #current}
 * is the same pick seen as backing-list indices, for the many callers that think in
 * indices. Rows are told apart by identity, so two rows that merely look alike are two
 * rows — the way indices always told them apart.</p>
 *
 * <p>A list can also be a tree: a subclass says how far a row is {@link #indent(Object) indented}
 * and whether it is a {@link #branch(Object) branch}, and the list draws the fold arrow, turns a
 * click on it and Left/Right on the focused row into {@link #toggle(Object)}. Flattening the tree
 * into rows stays with the subclass.</p>
 */
public abstract class UIList <T> extends UIItems<T>
{
    /** Left padding of a row's content, before the indent. */
    public static final int ROW_PADDING = 4;

    /** Width of the fold arrow's slot at the start of a branch row. */
    public static final int ARROW_SLOT = 12;

    /**
     * List of elements
     */
    protected List<T> list = new ArrayList<>();

    /**
     * List for copying
     */
    private List<T> copy = new ArrayList<>();

    /**
     * Selected elements, as indices into {@link #list}. A live view over {@link #selection}:
     * adding an index picks that row, removing one drops it.
     */
    public List<Integer> current = new CurrentIndices();

    private String filter = "";
    private List<Pair<T, Integer>> filtered = new ArrayList<>();

    /* The filtered rows without their indices, for the geometry that only wants items */
    private List<T> filteredItems = new ArrayList<>();

    /**
     * What an empty list says instead of showing nothing, and how; null keeps it silent, which
     * is what a list that fills itself (a folder's contents, a picker) wants.
     */
    private IKey emptyLabel;

    /** The rung the empty state paints itself on; the chrome, unless a list sits deeper. */
    private IntSupplier emptyBackground = BBSSettings::chromeSurface;

    public UIList(Consumer<List<T>> callback)
    {
        super(callback, (a, b) -> a == b);
    }

    /**
     * Say what to do to get something into this list, while it has nothing in it. The label is
     * drawn with a pointer right clicking under it, so use it where a right click on the list
     * is what adds a row; where rows come from elsewhere, the pointer would be a lie.
     */
    public UIList<T> emptyState(IKey label)
    {
        this.emptyLabel = label;

        return this;
    }

    /** Same, for a list that sits on a rung of its own — the film editor's is a rung deeper. */
    public UIList<T> emptyState(IKey label, IntSupplier background)
    {
        this.emptyBackground = background;

        return this.emptyState(label);
    }

    /* List element settings */

    @Override
    public UIList<T> background()
    {
        return this.background(Colors.A50);
    }

    @Override
    public UIList<T> background(int color)
    {
        this.background = color;

        return this;
    }

    @Override
    public UIList<T> multi()
    {
        this.multi = true;

        return this;
    }

    @Override
    public UIList<T> sorting()
    {
        this.sorting = true;

        return this;
    }

    @Override
    public UIList<T> cancelScrollEdge()
    {
        this.scroll.cancelScrollEdge = true;

        return this;
    }

    /* Tree support */

    /** How far a row is pushed right, in pixels; 0 for a flat list. */
    protected int indent(T element)
    {
        return 0;
    }

    /** Null for a leaf (no arrow), otherwise whether the branch is unfolded. */
    protected Boolean branch(T element)
    {
        return null;
    }

    /** Fold or unfold a branch; called for the arrow, and for Left/Right on the focused row. */
    protected void toggle(T element)
    {}

    /** Content X where a row's content starts: past the padding and the indent. */
    protected int rowContentX(T element)
    {
        return ROW_PADDING + this.indent(element);
    }

    /** Whether a content X lands in the fold arrow's slot of a branch row. */
    protected boolean hitsArrow(T element, int contentX)
    {
        return this.branch(element) != null && contentX < this.rowContentX(element) + ARROW_SLOT;
    }

    /** Draw the fold arrow of a branch row at screen {@code x}/{@code y}; nothing for a leaf. */
    protected void renderArrow(UIContext context, T element, int x, int y)
    {
        Boolean expanded = this.branch(element);

        if (expanded != null)
        {
            UISection.renderArrow(context, x + this.rowContentX(element) + ARROW_SLOT / 2, y + this.scroll.scrollItemSize / 2, expanded);
        }
    }

    /* Drops: into a row, or between two of them */

    /**
     * How much of a row, at its top and its bottom, reads as "between the rows" rather than as
     * the row itself. Every row of a list is a place to drop <em>beside</em>; only some are a
     * place to drop <em>into</em>, and those need both meanings out of the same 20 pixels.
     */
    public static final float DROP_EDGE = 0.25F;

    /**
     * Whether items dropped over the middle of this row go inside it &mdash; a category that
     * holds replays, a form that holds body parts. The row's edges still give the caret, so
     * such a row can be dropped beside as well as into.
     */
    protected boolean acceptsDrop(T element)
    {
        return false;
    }

    /** How deep the caret sits for a drop beside this row; as deep as the row's own content. */
    protected int dropInset(T element)
    {
        return this.rowContentX(element);
    }

    /**
     * The caret goes as deep as the deeper of the two rows it runs between: under the last
     * child of a group it stays with the children, not with whatever group starts next.
     */
    @Override
    protected int insertionInset(int insertion)
    {
        List<T> visible = this.visible();
        int inset = 0;

        if (insertion > 0 && insertion - 1 < visible.size())
        {
            inset = this.dropInset(visible.get(insertion - 1));
        }

        if (insertion >= 0 && insertion < visible.size())
        {
            inset = Math.max(inset, this.dropInset(visible.get(insertion)));
        }

        return inset;
    }

    @Override
    protected void reportDropTarget(int x, int y)
    {
        int index = this.indexAt(x, y);

        if (index != -1)
        {
            T row = this.visible().get(index);
            int size = this.scroll.scrollItemSize;
            int within = y - index * size;

            if (within > size * DROP_EDGE && within < size * (1F - DROP_EDGE)
                && this.acceptsDrop(row) && !this.drag.isDragging(row))
            {
                this.drag.setTarget(row);

                return;
            }
        }

        super.reportDropTarget(x, y);
    }

    /* Filtering elements */

    public void filter(String filter)
    {
        filter = filter.toLowerCase();

        if (this.filter.equals(filter))
        {
            return;
        }

        this.filter = filter;
        this.filtered.clear();
        this.filteredItems.clear();

        if (filter.isEmpty())
        {
            this.update();

            return;
        }

        String qwerty = KeyCodes.cyrillicToQwerty(filter);

        for (int i = 0; i < this.list.size(); i ++)
        {
            T element = this.list.get(i);
            String target = this.elementToString(this.getContext(), i, element).toLowerCase();

            if (target.contains(filter) || target.contains(qwerty))
            {
                this.filtered.add(new Pair<>(element, i));
                this.filteredItems.add(element);
            }
        }

        this.update();
        this.scroll.updateTarget();
    }

    public boolean isFiltering()
    {
        return !this.filter.isEmpty();
    }

    /**
     * Get the element displayed at the given visible row index,
     * taking filtering into account.
     */
    protected T getElementAt(int visibleIndex)
    {
        if (visibleIndex < 0)
        {
            return null;
        }

        if (!this.isFiltering())
        {
            return this.exists(visibleIndex) ? this.list.get(visibleIndex) : null;
        }

        return this.exists(this.filtered, visibleIndex) ? this.filtered.get(visibleIndex).a : null;
    }

    /* Geometry */

    @Override
    protected List<T> visible()
    {
        return this.isFiltering() ? this.filteredItems : this.list;
    }

    @Override
    protected int indexAt(int x, int y)
    {
        if (y < 0)
        {
            return -1;
        }

        int index = y / this.scroll.scrollItemSize;

        return index < this.visible().size() ? index : -1;
    }

    @Override
    protected void areaOf(int index, Area out)
    {
        int s = this.scroll.scrollItemSize;

        out.set(0, index * s, this.area.w, s);
    }

    @Override
    protected int insertionAt(int x, int y)
    {
        int s = this.scroll.scrollItemSize;

        return MathUtils.clamp((y + s / 2) / s, 0, this.visible().size());
    }

    @Override
    protected int contentSize()
    {
        return this.visible().size() * this.scroll.scrollItemSize;
    }

    @Override
    protected int step(int index, int dx, int dy)
    {
        /* Rows go up and down only; left and right belong to whoever else listens */
        if (dx != 0)
        {
            return -1;
        }

        return MathUtils.clamp(index + dy, 0, this.visible().size() - 1);
    }

    /** Index into {@link #list} of a row, by identity; -1 when it isn't there. */
    protected int indexOfItem(T item)
    {
        for (int i = 0; i < this.list.size(); i++)
        {
            if (this.list.get(i) == item)
            {
                return i;
            }
        }

        return -1;
    }

    /* Index and current value(s) methods */

    public boolean isSelected()
    {
        return !this.isDeselected();
    }

    public boolean isDeselected()
    {
        if (this.current.isEmpty())
        {
            return true;
        }

        for (Integer index : this.current)
        {
            if (this.exists(index))
            {
                return false;
            }
        }

        return true;
    }

    public List<Integer> getCurrentIndices()
    {
        return this.current;
    }

    public List<T> getCurrent()
    {
        this.copy.clear();

        for (T item : this.selection.getItems())
        {
            if (this.indexOfItem(item) != -1)
            {
                this.copy.add(item);
            }
        }

        return this.copy;
    }

    @Override
    protected List<T> selected()
    {
        return this.getCurrent();
    }

    public T getCurrentFirst()
    {
        if (!this.current.isEmpty())
        {
            int index = this.current.get(0);

            if (this.exists(index))
            {
                return this.list.get(index);
            }
        }

        return null;
    }

    public int getIndex()
    {
        if (this.current.isEmpty())
        {
            return -1;
        }

        int index = this.current.get(0);

        return this.exists(index) ? index : -1;
    }

    public int getHoveredIndex(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return -1;
        }

        return (context.mouseY - this.area.y + (int) this.scroll.getScroll()) / this.scroll.scrollItemSize;
    }

    /**
     * Backing list index under the cursor (for context menus). When filtering, maps the visible row to {@link #list}.
     */
    protected int getIndexAtCursor(UIContext context)
    {
        int row = this.getHoveredIndex(context);

        if (row < 0)
        {
            return -1;
        }

        if (this.isFiltering())
        {
            if (row >= this.filtered.size())
            {
                return -1;
            }

            return this.filtered.get(row).b;
        }

        return this.exists(row) ? row : -1;
    }

    public void deselect()
    {
        this.setIndex(-1);
    }

    public void setIndex(int index)
    {
        this.current.clear();
        this.addIndex(index);
    }

    public void addIndex(int index)
    {
        if (this.exists(index) && !this.current.contains(index))
        {
            this.current.add(index);
        }
    }

    public void toggleIndex(int index)
    {
        if (this.exists(index))
        {
            int i = this.current.indexOf(index);

            if (i == -1)
            {
                this.current.add(index);
            }
            else
            {
                this.current.remove(i);
            }
        }
    }

    public void setCurrent(T element)
    {
        this.current.clear();

        int index = this.list.indexOf(element);

        if (this.exists(index))
        {
            this.current.add(index);
        }
    }

    public void setCurrent(List<T> elements)
    {
        if (!this.multi && !elements.isEmpty())
        {
            this.setCurrent(elements.get(0));

            return;
        }

        this.current.clear();

        for (T element : elements)
        {
            int index = this.list.indexOf(element);

            if (this.exists(index))
            {
                this.current.add(index);
            }
        }
    }

    public void setCurrentScroll(T element)
    {
        this.setCurrent(element);

        if (!this.current.isEmpty())
        {
            this.scroll.setScroll(this.current.get(0) * this.scroll.scrollItemSize);
        }
    }

    public boolean pick(int index)
    {
        if (index < 0 || index >= this.list.size())
        {
            return false;
        }

        this.setIndex(index);
        this.fireCallback();

        return true;
    }

    @Override
    public void selectAll()
    {
        if (!this.multi)
        {
            return;
        }

        this.selection.setAll(this.list);
    }

    public List<T> getList()
    {
        return this.list;
    }

    /* Content management */

    public void clear()
    {
        this.filter("");

        this.current.clear();
        this.list.clear();
        this.update();
    }

    public void add(T element)
    {
        this.list.add(element);
        this.update();
    }

    public void add(Collection<T> elements)
    {
        this.list.addAll(elements);
        this.update();
    }

    public void replace(T element)
    {
        int index = this.current.size() == 1 ? this.current.get(0) : -1;

        if (this.exists(index))
        {
            this.list.set(index, element);

            /* The pick is the row object, so it must follow the row into its new value */
            this.selection.set(element, null);
        }
    }

    public void setList(List<T> list)
    {
        if (list == null)
        {
            return;
        }

        this.list = list;
        this.update();
    }

    public void remove(T element)
    {
        this.list.remove(element);
        this.update();
    }

    /**
     * Sort elements in this array, the subsclasses should implement
     * the other sorting method in order for it to work. The pick is made of
     * the rows themselves, so it follows them wherever they land.
     */
    public final void sort()
    {
        this.sortElements();
    }

    /**
     * Sort elements
     */
    protected boolean sortElements()
    {
        return false;
    }

    /* Miscellaneous methods */

    public void update()
    {
        this.scroll.setSize(this.visible().size());
        this.scroll.clamp();
    }

    public boolean exists(int index)
    {
        return this.exists(this.list, index);
    }

    public boolean exists(List list, int index)
    {
        return index >= 0 && index < list.size();
    }

    public boolean isDragging()
    {
        return this.drag.isActive();
    }

    /** Index into {@link #list} of the row being dragged, or -1. */
    public int getDraggingIndex()
    {
        List<T> items = this.drag.getItems();

        return items.isEmpty() ? -1 : this.indexOfItem(items.get(0));
    }

    /** Arm dragging the given row from where the button went down. */
    protected void startDrag(int index, UIContext context)
    {
        if (this.exists(index))
        {
            this.drag.start(Collections.singletonList(this.list.get(index)), context.mouseX, context.mouseY);
        }
    }

    /* Input */

    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        if (this.pressArrow(index, context))
        {
            return true;
        }

        return super.pressItem(index, context);
    }

    /**
     * A press on the fold arrow of a visible row toggles it and takes the press; whether it did.
     * Subclasses that handle presses themselves ask this first, so the arrow behaves the same.
     */
    protected boolean pressArrow(int index, UIContext context)
    {
        T element = this.visible().get(index);

        if (!this.hitsArrow(element, this.contentX(context)))
        {
            return false;
        }

        this.cursor = index;
        this.toggle(element);

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        /* Left folds and Right unfolds the focused branch; rows have no sideways step, so nothing else wants the keys */
        if (!context.isFocused() && this.area.isInside(context) && context.getKeyAction() != KeyAction.RELEASED)
        {
            int key = context.getKeyCode();
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : (key == GLFW.GLFW_KEY_RIGHT ? 1 : 0);
            int focus = dx == 0 ? -1 : this.focusIndex();

            if (focus >= 0 && focus < this.visible().size())
            {
                T element = this.visible().get(focus);
                Boolean expanded = this.branch(element);

                if (expanded != null && expanded != (dx > 0))
                {
                    this.cursor = focus;
                    this.toggle(element);

                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void applySelectionOnClick(T item, int index)
    {
        this.applySelectionOnClick(this.isFiltering() ? this.filtered.get(index).b : index);
    }

    /**
     * Updates {@link #current} for a left-click on the given list index. Override in subclasses to change
     * multi-select behaviour (e.g. pose bone list: Shift toggles like Ctrl instead of range-select).
     */
    protected void applySelectionOnClick(int index)
    {
        if (this.multi && Window.isShiftPressed() && this.isSelected())
        {
            this.selection.range(this.list.get(index), null, this.visible());
        }
        else if (this.multi && Window.isCtrlPressed())
        {
            this.toggleIndex(index);
        }
        else
        {
            this.setIndex(index);
        }
    }

    @Override
    protected List<T> dragPayload(T item)
    {
        /* A filtered view can't be reordered — the gaps between its rows aren't real */
        if (!this.sorting || this.isFiltering() || this.selection.size() != 1 || !this.selection.contains(item))
        {
            return null;
        }

        return Collections.singletonList(item);
    }

    @Override
    protected void reorder(List<T> items, int insertion)
    {
        int from = this.indexOfItem(items.get(0));

        if (from == -1)
        {
            return;
        }

        /* The caret sits before the row at {@code insertion}; taking the row out first shifts what's after it */
        int to = insertion > from ? insertion - 1 : insertion;

        if (to != from && this.exists(to))
        {
            this.handleSwap(from, to);
        }
    }

    protected void handleSwap(int from, int to)
    {
        T value = this.list.remove(from);

        this.list.add(to, value);
        this.setIndex(to);
    }

    /* Rendering */

    @Override
    protected void renderContent(UIContext context)
    {
        this.renderList(context);

        if (this.emptyLabel != null && this.list.isEmpty())
        {
            EmptyStateRenderer.renderRightClickHere(context, this.area, this.emptyLabel, this.emptyBackground.getAsInt());
        }
    }

    @Override
    protected void renderDragGhost(UIContext context)
    {
        int index = this.getDraggingIndex();

        if (this.exists(index))
        {
            this.renderListElement(context, this.list.get(index), index, context.mouseX + 6, context.mouseY - this.scroll.scrollItemSize / 2, true, true);
        }
    }

    public void renderList(UIContext context)
    {
        int i = 0;

        if (this.isFiltering())
        {
            for (Pair<T, Integer> element : this.filtered)
            {
                i = this.renderElement(context, element.a, i, element.b, false);

                if (i == -1)
                {
                    break;
                }
            }
        }
        else
        {
            for (T element : this.list)
            {
                i = this.renderElement(context, element, i, i, false);

                if (i == -1)
                {
                    break;
                }
            }
        }
    }

    public int renderElement(UIContext context, T element, int i, int index, boolean postDraw)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        int s = this.scroll.scrollItemSize;

        int xSide = this.area.w;
        int ySide = this.scroll.scrollItemSize;

        int x = this.area.x;
        int y = this.area.y + i * s - (int) this.scroll.getScroll();

        int low = this.area.y;
        int high =this.area.ey();

        if (y + s < low || (!this.isFiltering() && this.isDragging() && this.getDraggingIndex() == i))
        {
            return i + 1;
        }

        if (y >= high)
        {
            return -1;
        }

        boolean hover = mouseX >= x && mouseY >= y && mouseX < x + xSide && mouseY < y + ySide;
        boolean selected = this.current.contains(index);

        if (postDraw)
        {
            this.renderPostListElement(context, element, index, x, y, hover, selected);
        }
        else
        {
            this.renderListElement(context, element, index, x, y, hover, selected);
        }

        return i + 1;
    }

    /**
     * Draw second pass of individual list element
     */
    public void renderPostListElement(UIContext context, T element, int i, int x, int y, boolean hover, boolean selected)
    {}

    /**
     * Draw individual element (with selection)
     */
    public void renderListElement(UIContext context, T element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (selected)
        {
            context.batcher.box(x, y, x + this.area.w, y + this.scroll.scrollItemSize, Colors.A50 | BBSSettings.primaryColor.get());
        }

        /* Where a drop would land inside this row, said the way the caret says "between" */
        if (this.drag.isTarget(element))
        {
            context.batcher.box(x, y, x + this.area.w, y + this.scroll.scrollItemSize, Colors.A25 | BBSSettings.primaryColor.get());
        }

        this.renderElementPart(context, element, i, x, y, hover, selected);
    }

    /**
     * Draw only the main part (without selection or any hover elements)
     */
    protected void renderElementPart(UIContext context, T element, int i, int x, int y, boolean hover, boolean selected)
    {
        int textX = x + this.rowContentX(element) + (this.branch(element) != null ? ARROW_SLOT : 0);

        this.renderArrow(context, element, x, y);
        context.batcher.textShadow(this.elementToString(context, i, element), textX, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }

    /**
     * Convert element to string
     */
    protected String elementToString(UIContext context, int i, T element)
    {
        return element.toString();
    }

    /**
     * {@link #current}: the pick as indices. Reads look every picked row up in {@link #list};
     * writes pick or drop the row at that index. Positions passed to {@code add} are ignored —
     * the pick keeps the order rows were picked in.
     */
    private class CurrentIndices extends AbstractList<Integer>
    {
        @Override
        public Integer get(int index)
        {
            return UIList.this.indexOfItem(UIList.this.selection.getItems().get(index));
        }

        @Override
        public int size()
        {
            return UIList.this.selection.size();
        }

        @Override
        public boolean add(Integer index)
        {
            if (index == null || !UIList.this.exists(index))
            {
                return false;
            }

            T item = UIList.this.list.get(index);

            if (UIList.this.selection.contains(item))
            {
                return false;
            }

            UIList.this.selection.add(item, null);

            return true;
        }

        @Override
        public void add(int position, Integer index)
        {
            this.add(index);
        }

        @Override
        public Integer remove(int index)
        {
            T item = UIList.this.selection.getItems().get(index);
            int removed = UIList.this.indexOfItem(item);

            UIList.this.selection.remove(item);

            return removed;
        }

        @Override
        public void clear()
        {
            UIList.this.selection.clear();
        }

        @Override
        public boolean contains(Object o)
        {
            return this.indexOf(o) != -1;
        }

        @Override
        public int indexOf(Object o)
        {
            if (!(o instanceof Integer index) || !UIList.this.exists(index))
            {
                return -1;
            }

            return UIList.this.selection.indexOf(UIList.this.selection.getItems(), UIList.this.list.get(index));
        }
    }
}
