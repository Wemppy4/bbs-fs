package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItems;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
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
import java.util.function.BiPredicate;
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
    public static final int ARROW_SLOT = 10;

    /**
     * The rest of a row's grid: the icon's slot, and the gap between it and the row's text. One
     * place, so a folder tree, a morph category and anything else with an icon line their names up
     * on the same column instead of each picking its own.
     */
    public static final int ICON_SLOT = 16;
    public static final int ICON_GAP = 3;

    /** Where a row's text starts when the row carries an icon after its arrow. */
    public static int iconRowTextX(int contentX)
    {
        return contentX + ARROW_SLOT + ICON_SLOT + ICON_GAP;
    }

    /** The tree guides: a hairline, so the shape of the tree reads without competing with the rows. */
    private static final int GUIDE_COLOR = Colors.A25 | 0xFFFFFF;

    /** Nothing shorter than this, whatever the scale — a row still has to fit a line of text. */
    private static final int ROW_MIN_HEIGHT = 10;

    private static final float ROW_SCALE_MIN = 0.6F;
    private static final float ROW_SCALE_MAX = 3F;
    private static final float ROW_SCALE_STEP = 0.1F;

    /**
     * How tall rows are drawn against the height their list was built with — Alt+wheel over any
     * list, the gesture the timeline already uses for its track height.
     *
     * <p>One number for every list rather than one per list: panels here are rebuilt from scratch
     * on all sorts of actions, and a size that lived in the list would be lost every time. Held as
     * a multiplier, not a height, because lists are built at different sizes on purpose — a row of
     * text is 16, a row with a preview is taller — and a single height would flatten that.</p>
     */
    private static float rowScale = 1F;

    public static void setRowScale(float scale)
    {
        rowScale = MathUtils.clamp(scale, ROW_SCALE_MIN, ROW_SCALE_MAX);
    }

    public static float getRowScale()
    {
        return rowScale;
    }

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

    /** The height this list was built with, which {@link #rowScale} multiplies; 0 until first drawn. */
    private int baseRowHeight;

    /**
     * How tall a row is <em>right now</em>. Rows are resizable (Alt+wheel), so the height a list
     * was built with is a starting point, not a fact — draw against this, never against the
     * constant a list happened to be constructed with.
     */
    public int rowHeight()
    {
        return this.scroll.scrollItemSize;
    }

    /**
     * A row pressed inside a group pick, waiting to see what the press becomes: a drag of the whole
     * group, or a plain click, which narrows the pick to that row. -1 when there is none.
     */
    private int pendingNarrow = -1;
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
     * A list whose rows are rebuilt wrappers around stable data says here what "the same row"
     * means (the replay list's rows wrap replays and category names), so a pick survives the
     * rebuild instead of clinging to row identity.
     */
    public UIList(Consumer<List<T>> callback, BiPredicate<T, T> same)
    {
        super(callback, same);
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

    /* Row appearance */

    /**
     * The row's own colour — a category's, a track's — or 0 when it has none. It tints the bar
     * down the row's left edge and the row's hover, so a coloured row keeps its colour instead of
     * being washed over by the accent. See {@link RowStyle}.
     */
    protected int rowColor(T element)
    {
        return 0;
    }

    /**
     * Whether the row names other rows rather than being one, which lights it permanently — the
     * way a body part heading separates itself from the tracks it holds.
     */
    protected boolean isHeader(T element)
    {
        return false;
    }

    /**
     * Whether Alt+wheel may resize this list's rows. A context menu says no: its row height is cut
     * to its 16px icons and its label, and it is read at a glance rather than worked in.
     */
    protected boolean canScaleRows()
    {
        return true;
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

    /** How far one level of nesting shifts a row; the tree guides are drawn on this grid. */
    protected int indentStep()
    {
        return 0;
    }

    /**
     * Outliner guides down the left of a nested row: a vertical for every ancestor whose own
     * branch continues below this row, and a connector into the row itself — a tee, or a corner
     * when the row is the last thing in its branch.
     *
     * @param depth how many levels in the row sits; nothing is drawn at the root
     * @param lines bit per ancestor level whose vertical still runs past this row
     * @param last  whether the row is the last of its branch, which corners the connector
     * @param textX where the row's own content starts, so the connector reaches it
     */
    protected void renderTreeGuides(UIContext context, int x, int y, int depth, int lines, boolean last, int textX)
    {
        if (depth <= 0)
        {
            return;
        }

        int h = this.scroll.scrollItemSize;
        int mid = y + h / 2;

        for (int level = 0; level < depth - 1; level++)
        {
            if ((lines & (1 << level)) != 0)
            {
                int lx = this.guideX(x, level);

                context.batcher.box(lx, y, lx + 1, y + h, GUIDE_COLOR);
            }
        }

        int lx = this.guideX(x, depth - 1);

        context.batcher.box(lx, y, lx + 1, last ? mid + 1 : y + h, GUIDE_COLOR);
        context.batcher.box(lx + 1, mid, textX - 2, mid + 1, GUIDE_COLOR);
    }

    /** Screen x of the vertical guide of one nesting level. */
    protected int guideX(int x, int level)
    {
        return x + ROW_PADDING + level * this.indentStep() + 2;
    }

    /**
     * The mask a row's children inherit: this row's own column keeps running down past them
     * while the row still has siblings below it.
     */
    public static int childGuideLines(int lines, int depth, boolean last)
    {
        return !last && depth > 0 ? lines | (1 << (depth - 1)) : lines;
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
        this.refilter();
    }

    /**
     * Run the query over the rows again. A list that rebuilds its rows while a search is on (the
     * replay list does it on every change) would otherwise keep showing matches that point at rows
     * it has already thrown away.
     */
    protected void refilter()
    {
        String filter = this.filter;

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
        this.refilter();
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

    /**
     * Alt+wheel resizes the rows — the same gesture, and the same direction, the timeline's track
     * height answers to. The list under the cursor handles it, but the size it sets is everyone's.
     */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.canScaleRows() && Window.isAltPressed() && context.mouseWheel != 0D && this.area.isInside(context))
        {
            setRowScale(rowScale - (float) Math.signum(context.mouseWheel) * ROW_SCALE_STEP);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.applyRowScale();

        super.render(context);
    }

    /**
     * Bring the row height in line with {@link #rowScale}. The height the list was built with is
     * caught the first time this runs — by then every constructor has had its say — and is what
     * the scale multiplies from then on, so scaling back to 1 lands exactly where the list started.
     */
    private void applyRowScale()
    {
        if (!this.canScaleRows())
        {
            return;
        }

        if (this.baseRowHeight == 0)
        {
            this.baseRowHeight = this.scroll.scrollItemSize;
        }

        int height = Math.max(ROW_MIN_HEIGHT, Math.round(this.baseRowHeight * rowScale));

        if (height != this.scroll.scrollItemSize)
        {
            this.scroll.scrollItemSize = height;
            this.update();
        }
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
        else if (!this.selection.contains(this.list.get(index)) || !this.selection.isGroup())
        {
            this.setIndex(index);
        }
        else
        {
            /* A plain press on one of several picked rows keeps the group for now, so the press can
             * carry the whole pick off. Narrowing here would throw the group away before the drag
             * ever started, so it waits for the release to say which the press was. */
            this.pendingNarrow = index;
        }
    }

    /**
     * The button went up. A press inside a group that carried nothing away was a plain click after
     * all, and a plain click on a row means the plainest thing it can mean: pick that row alone.
     */
    @Override
    protected void release()
    {
        int narrow = this.pendingNarrow;
        boolean dragged = this.drag.isActive();

        this.pendingNarrow = -1;

        super.release();

        if (narrow != -1 && !dragged && this.exists(narrow))
        {
            this.setIndex(narrow);
            this.fireCallback();
        }
    }

    @Override
    protected List<T> dragPayload(T item)
    {
        /* A filtered view can't be reordered — the gaps between its rows aren't real */
        if (this.isFiltering())
        {
            return null;
        }

        return super.dragPayload(item);
    }

    /**
     * The dragged rows land at the caret in the order they were shown in, one after another — the
     * pick is carried as a block, the way the grids of the texture browser and the form palette
     * have always carried theirs.
     */
    @Override
    protected void reorder(List<T> items, int insertion)
    {
        int slot = insertion;

        for (T item : this.inViewOrder(items))
        {
            int from = this.indexOfItem(item);

            if (from == -1)
            {
                continue;
            }

            /* The caret sits before the row at {@code slot}; taking the row out first shifts what's after it */
            int to = from < slot ? slot - 1 : slot;

            if (!this.exists(to))
            {
                continue;
            }

            if (to != from)
            {
                this.handleSwap(from, to);
            }

            slot = to + 1;
        }
    }

    /** The given rows in the order they are shown, which is the order a group has to move in. */
    protected List<T> inViewOrder(List<T> items)
    {
        List<T> ordered = new ArrayList<>();

        for (T row : this.list)
        {
            if (this.selection.indexOf(items, row) != -1)
            {
                ordered.add(row);
            }
        }

        return ordered;
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

        if (!this.exists(index))
        {
            return;
        }

        int x = context.mouseX + 6;
        int y = context.mouseY - this.scroll.scrollItemSize / 2;

        this.renderListElement(context, this.list.get(index), index, x, y, true, true);

        /* How many rows are coming along, said the way the grids' ghost says it. The badge sits at
         * the leading corner rather than the trailing one: a row is as wide as the list, and the
         * far end of it is often off the screen. */
        int carried = this.drag.getItems().size();

        if (carried > 1)
        {
            context.batcher.textCard(String.valueOf(carried), x - 4, y - 4, Colors.WHITE, Colors.A100 | BBSSettings.primaryColor.get(), 3);
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

        /* Every row being carried lifts out of the list, not just the one the press went down on:
         * a group that left one row behind would look like half of it was staying. */
        if (y + s < low || (!this.isFiltering() && this.drag.isDragging(element)))
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
        int h = this.scroll.scrollItemSize;

        RowStyle.row(context.batcher, x, y, this.area.w, h, this.rowColor(element), this.isHeader(element), hover, selected);

        /* Where a drop would land inside this row, said the way the caret says "between" */
        if (this.drag.isTarget(element))
        {
            RowStyle.dropTarget(context.batcher, x, y, this.area.w, h);
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
        context.batcher.textShadow(this.elementToString(context, i, element), textX, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, RowStyle.textColor(hover || selected));
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
