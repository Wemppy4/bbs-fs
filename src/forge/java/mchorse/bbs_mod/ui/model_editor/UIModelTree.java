package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The model editor's tree: the model's groups with their cubes inside them, the way an outliner
 * lists them — a group's row unfolds into the cubes it carries and then the groups under it.
 * Rows are {@link ModelNode addresses}, so the pick survives the model being rebuilt under the
 * tree, and every row is laid out the same way: the fold arrow's slot, an icon saying what the
 * row is, the name.
 *
 * <p>A group can be dragged anywhere, because in this tree — unlike the body part list — changing
 * a group's parent is allowed. So the caret means what it looks like: dropped between two rows,
 * the group lands right before the row below, whatever depth that row sits at; dropped past the
 * last row it goes to the end of the roots. Dropped ONTO a group's row (its middle, the base
 * list's rule) it goes inside that group. Nothing snaps and nothing is refused except a drop into
 * the group's own subtree, which would unhook it from the model. A cube travels the same way, and
 * lands in the group the caret is inside of — it has nowhere else to live, so a caret at the root
 * is the one drop it has no answer for. What a drop means for the model is the editor's to say
 * ({@link #onReorder}, {@link #onDrop}); the tree only reports it.</p>
 *
 * <p>A search shows its matches flat: without the rows above them, branches would be a lie about
 * the structure — and there is nothing to fold in a flat list, so the arrows go too.</p>
 *
 * <p>A picked group takes its whole branch along — the cubes and groups under it are in the pick
 * without being picked rows themselves, and wear the pick's wash without its bar
 * ({@link RowStyle#carried}). They are worked out from the picked groups rather than added to
 * the list's pick, so a folded branch, a search or a rebuilt model can't lose them.</p>
 */
public class UIModelTree extends UIList<ModelNode>
{
    /** Tall enough for the row's icon; the bone tree's rows are a line of text, these carry a glyph. */
    public static final int ROW = 16;

    /** One level of nesting, the same step as the bone tree's, so the two read alike. */
    public static final int INDENT = 8;

    /**
     * Where a row sits in the tree as it was last built — kept beside the row rather than in it,
     * since a row is an address and the same cube keeps its address when its group moves.
     *
     * @param parent whether the row unfolds into something (a group with cubes or groups in it)
     */
    @com.github.bsideup.jabel.Desugar
    private record Meta(int depth, int lines, boolean last, boolean parent, String label)
    {}

    private final Map<ModelNode, Meta> metas = new HashMap<>();

    /** Which groups are unfolded; it outlives every rebuild of the rows. Everything starts open. */
    private final FoldState<String> folds = new FoldState<>(true);

    /** The model the rows were last built from, for building them again on a fold. */
    private Model model;

    /** The picked groups by name, gathered once per frame of rows, for telling which rows they carry. */
    private final Set<String> pickedGroups = new HashSet<>();

    private BiConsumer<ModelNode, ModelNode> onReorder;
    private BiConsumer<ModelNode, String> onDrop;

    public UIModelTree(Consumer<List<ModelNode>> callback)
    {
        super(callback, Objects::equals);

        this.scroll.scrollItemSize = ROW;
        this.background();
        this.sorting();
        this.multi();
        this.folds.onChange(this::refill);
    }

    /**
     * What to do with a row dropped between rows: the row, and the row it now comes right before —
     * null for past the last row.
     */
    public UIModelTree onReorder(BiConsumer<ModelNode, ModelNode> callback)
    {
        this.onReorder = callback;

        return this;
    }

    /** What to do with a row dropped onto a group's row: the row, and that group's name. */
    public UIModelTree onDrop(BiConsumer<ModelNode, String> callback)
    {
        this.onDrop = callback;

        return this;
    }

    /* Filling */

    /** Lay the model out (null for none): every group, its cubes and then its groups under it, unless folded. */
    public void fill(Model model)
    {
        this.model = model;

        this.clear();
        this.metas.clear();

        if (model != null)
        {
            this.emit(model.topGroups, 0, 0);
        }

        this.update();
    }

    /**
     * Pre-order: a group's row, its cubes, then its child groups, each level a step deeper. The
     * branch drawing is worked out on the way, as the bone tree does it: {@code lines} carries
     * which ancestor columns still run a vertical, {@code last} makes a corner of a tee.
     */
    private void emit(List<ModelGroup> groups, int depth, int lines)
    {
        for (int i = 0; i < groups.size(); i++)
        {
            ModelGroup group = groups.get(i);
            boolean last = i == groups.size() - 1;
            boolean parent = !group.cubes.isEmpty() || !group.children.isEmpty();
            ModelNode node = ModelNode.group(group.id);

            this.metas.put(node, new Meta(depth, lines, last, parent, group.id));
            this.list.add(node);

            if (!parent || !this.folds.isExpanded(group.id))
            {
                continue;
            }

            int childLines = childGuideLines(lines, depth, last);
            int cubes = group.cubes.size();

            for (int c = 0; c < cubes; c++)
            {
                ModelNode cube = ModelNode.cube(group.id, c);
                boolean lastCube = c == cubes - 1 && group.children.isEmpty();

                this.metas.put(cube, new Meta(depth + 1, childLines, lastCube, false, cubeLabel(group.cubes.get(c), c)));
                this.list.add(cube);
            }

            this.emit(group.children, depth + 1, childLines);
        }
    }

    /** How a cube is named: by the name it was given, else by its number in its group. */
    public static String cubeLabel(ModelCube cube, int index)
    {
        return cube.name.isEmpty() ? UIKeys.MODEL_EDITOR_MODEL_CUBE_LABEL.format(index + 1).get() : cube.name;
    }

    /** Unfold every group, or fold them all — the panel's expand and collapse keys. */
    public void setAllExpanded(boolean expanded)
    {
        if (expanded)
        {
            this.folds.expandAll(this.model == null ? java.util.Collections.emptyList() : this.model.getAllGroupKeys());
        }
        else
        {
            this.folds.collapseAll();
        }
    }

    /** Build the rows again from the model, keeping the pick — what a fold changes. */
    private void refill()
    {
        List<ModelNode> picked = new ArrayList<>(this.getCurrent());

        this.fill(this.model);
        this.setCurrent(picked);
    }

    /* Looking rows up */

    /**
     * Bring a row into view: unfold whatever it sits under, then scroll only as far as it takes.
     * The list's own setCurrentScroll puts the picked row at the very top instead, which reads as
     * the list jumping under the cursor when the pick follows a drag or an edit the eye is
     * already watching.
     */
    public void reveal(ModelNode node)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(node.group());

        if (group == null)
        {
            return;
        }

        /* A cube shows only while its group is open; a group, while every group above it is. */
        List<String> above = new ArrayList<>();

        for (ModelGroup parent = node.isCube() ? group : group.parent; parent != null; parent = parent.parent)
        {
            above.add(parent.id);
        }

        this.folds.expandAll(above);

        int index = this.list.indexOf(node);

        if (index >= 0)
        {
            this.scrollIntoView(index);
        }
    }

    /** The row under the cursor, for a row's menu; null off the rows. */
    public ModelNode atCursor(UIContext context)
    {
        int index = this.getIndexAtCursor(context);

        return this.exists(index) ? this.list.get(index) : null;
    }

    /** Whether {@code group} sits somewhere under the group named {@code ancestor}. */
    private static boolean isInside(ModelGroup group, String ancestor)
    {
        for (ModelGroup parent = group.parent; parent != null; parent = parent.parent)
        {
            if (parent.id.equals(ancestor))
            {
                return true;
            }
        }

        return false;
    }

    /* Tree */

    @Override
    protected int indent(ModelNode node)
    {
        Meta meta = this.isFiltering() ? null : this.metas.get(node);

        return meta == null ? 0 : meta.depth * INDENT;
    }

    @Override
    protected int indentStep()
    {
        return INDENT;
    }

    @Override
    protected Boolean branch(ModelNode node)
    {
        Meta meta = this.isFiltering() ? null : this.metas.get(node);

        return meta == null || !meta.parent ? null : this.folds.isExpanded(node.group());
    }

    @Override
    protected void toggle(ModelNode node)
    {
        if (this.branch(node) != null)
        {
            this.folds.toggle(node.group());
        }
    }

    /* Dragging */

    private ModelNode dragged()
    {
        List<ModelNode> items = this.drag.getItems();

        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    protected List<ModelNode> dragPayload(ModelNode item)
    {
        /* One row at a time: the move is told as "this one, before that one", which says nothing
         * about where the rest of a pick would go. */
        return super.dragPayload(item) == null ? null : Collections.singletonList(item);
    }

    /** A group's row takes a drop from any cube, and from any group but itself and the ones inside it. */
    @Override
    protected boolean acceptsDrop(ModelNode row)
    {
        ModelNode dragged = this.dragged();
        ModelGroup target = row.isGroup() && this.model != null ? this.model.getGroup(row.group()) : null;

        if (dragged == null || target == null)
        {
            return false;
        }

        return dragged.isCube() || (!dragged.group().equals(row.group()) && !isInside(target, dragged.group()));
    }

    @Override
    protected void reorder(List<ModelNode> items, int insertion)
    {
        ModelNode dragged = items.isEmpty() ? null : items.get(0);

        if (dragged == null || this.onReorder == null)
        {
            return;
        }

        List<ModelNode> rows = this.visible();
        ModelNode before = insertion >= 0 && insertion < rows.size() ? rows.get(insertion) : null;

        /* The caret above the dragged row itself means "stay where you are". */
        if (dragged.equals(before))
        {
            return;
        }

        this.onReorder.accept(dragged, before);
    }

    @Override
    protected void onDrop(Object target, List<ModelNode> items)
    {
        if (target instanceof ModelNode node && node.isGroup() && !items.isEmpty() && this.onDrop != null)
        {
            this.onDrop.accept(items.get(0), node.group());
        }
    }

    /* Rendering */

    /**
     * Whether a picked group takes the row along: a cube whose own group, or a row with any group
     * above it, is picked. Read off the model rather than the rows' layout, so it holds in a flat
     * search too.
     */
    private boolean isCarried(ModelNode node)
    {
        ModelGroup group = this.pickedGroups.isEmpty() || this.model == null ? null : this.model.getGroup(node.group());

        for (ModelGroup above = group == null ? null : node.isCube() ? group : group.parent; above != null; above = above.parent)
        {
            if (this.pickedGroups.contains(above.id))
            {
                return true;
            }
        }

        return false;
    }

    /** The picked groups are gathered once for the whole pass, not asked of the pick row by row. */
    @Override
    public void renderList(UIContext context)
    {
        this.pickedGroups.clear();

        for (ModelNode node : this.getCurrent())
        {
            if (node.isGroup())
            {
                this.pickedGroups.add(node.group());
            }
        }

        super.renderList(context);
    }

    /** A carried row wears the pick's wash under the row's own marks — see {@link RowStyle#carried}. */
    @Override
    public void renderListElement(UIContext context, ModelNode node, int i, int x, int y, boolean hover, boolean selected)
    {
        if (!selected && this.isCarried(node))
        {
            RowStyle.carried(context.batcher, x, y, this.area.w, this.scroll.scrollItemSize);
        }

        super.renderListElement(context, node, i, x, y, hover, selected);
    }

    /** What a search runs over: a cube is found by its group's name too, since that is where it is. */
    @Override
    protected String elementToString(UIContext context, int i, ModelNode node)
    {
        Meta meta = this.metas.get(node);
        String label = meta == null ? node.group() : meta.label;

        return node.isCube() ? node.group() + "/" + label : label;
    }

    @Override
    protected void renderElementPart(UIContext context, ModelNode node, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        Meta meta = this.isFiltering() ? null : this.metas.get(node);
        int h = this.rowHeight();
        int contentX = x + this.rowContentX(node);
        int iconX = contentX + ARROW_SLOT;
        int textX = iconRowTextX(contentX);
        boolean lit = hover || selected || this.isCarried(node);

        if (meta != null)
        {
            this.renderTreeGuides(context, x, y, meta.depth, meta.lines, meta.last, iconX);
        }

        this.renderArrow(context, node, x, y, lit);
        context.batcher.icon(node.isCube() ? Icons.BLOCK : Icons.FOLDER, RowStyle.iconColor(lit), iconX, y + (h - 16) / 2);

        /* Flat search results say where a cube is; a row in the tree is under its group already. */
        String label = meta == null ? this.elementToString(context, i, node) : meta.label;

        label = font.limitToWidth(label, this.area.ex() - 4 - textX);
        context.batcher.textShadow(label, textX, y + (h - font.getHeight()) / 2, RowStyle.textColor(lit));
    }
}
