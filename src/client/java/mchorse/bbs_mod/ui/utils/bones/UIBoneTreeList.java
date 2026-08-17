package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bone list that understands hierarchy. The backing list still holds plain string ids
 * (bone keys or attachment paths), so every {@link UIStringList} caller keeps working;
 * this class only adds per-id display metadata, drawn as outliner-style tree branches:
 * a tee for a middle child, a corner for the last one, and pass-through verticals for
 * every ancestor level that continues below. Search results render flat with a full
 * label instead (an attachment bone keeps its form's track name to stay recognizable).
 *
 * <p>Both fill flavors build the same intermediate node tree — {@link #fillBones} from
 * a model's bone hierarchy, {@link #fillAttachments} from a form's attachment keys —
 * so the connector math lives in one place. {@link #setHierarchy} emits only the
 * metadata while the host keeps managing the list contents itself (the pose editor's
 * bone list, which refills on every search keystroke — the metadata survives because
 * {@code clear()} intentionally does not touch it).</p>
 */
public class UIBoneTreeList extends UIStringList
{
    public static final int INDENT = 8;

    private static final int GUIDE_COLOR = Colors.A25 | 0xFFFFFF;

    private static final int MARKER = 4;
    private static final int MARKER_GAP = 2;

    /** One role dot: {@code small} halves it, for a secondary shade of the same role. */
    public record Marker(int color, boolean small)
    {
    }

    private final Map<String, Meta> metas = new HashMap<>();

    private Predicate<String> disabled;

    private Function<String, Marker[]> markers;

    /** Left edge of the dot column as the last drawn row placed it — the legend's gate. */
    private int laneX = Integer.MAX_VALUE;

    /** Hosts that filter by refilling the list (instead of {@link #filter}) set this
     *  while a query is active, so matches render flat like built-in filtering does. */
    private boolean flat;

    public UIBoneTreeList(Consumer<List<String>> callback)
    {
        super(callback);
    }

    /**
     * Mark ids the host refuses to accept (e.g. IK targets that would close a cycle).
     * They render gray and {@link #isDisabled} lets the picker ignore clicks on them.
     */
    public UIBoneTreeList disabled(Predicate<String> predicate)
    {
        this.disabled = predicate;

        return this;
    }

    public boolean isDisabled(String id)
    {
        return this.disabled != null && id != null && !id.isEmpty() && this.disabled.test(id);
    }

    /**
     * Role dots drawn at the row's right edge — what a bone is to the host,
     * readable without selecting it (which chain drives it, that it is somebody's
     * controller). Slot {@code 0} is the rightmost one and every slot keeps its
     * meaning across rows, so the eye reads a column, not a legend; a
     * {@code null} entry leaves its slot empty. The label is trimmed to what the
     * dots leave, so a long bone name never runs under them.
     */
    public UIBoneTreeList markers(Function<String, Marker[]> markers)
    {
        this.markers = markers;

        return this;
    }

    /**
     * Same, with a legend for what the dots mean. It shows only while the cursor
     * is over the dot column: a bone list is hovered all the time, and a
     * paragraph popping up on every row would be noise where the question is
     * only ever asked at the dots.
     */
    public UIBoneTreeList markers(Function<String, Marker[]> markers, IKey legend)
    {
        this.tooltip(new MarkerLegend(legend));

        return this.markers(markers);
    }

    public void flat(boolean flat)
    {
        this.flat = flat;
    }

    /** First row currently on screen (respecting the search filter) — the Enter pick. */
    public String getFirstVisible()
    {
        return this.getElementAt(0);
    }

    /** Insert a special entry (like "None") above the tree, outside of any hierarchy. */
    public void prepend(String id, String label)
    {
        this.list.add(0, id);
        this.metas.put(id, new Meta(0, 0, true, label, label));
        this.update();
    }

    /**
     * Set only the hierarchy metadata from a model, leaving the list contents to the
     * host. Passing a null model clears the metadata (every row renders flat).
     */
    public void setHierarchy(IModel model, Predicate<String> hidden)
    {
        this.metas.clear();

        if (model != null)
        {
            this.emit(boneNodes(model, model.getRootGroupKeys(), hidden), 0, 0, false);
        }
    }

    /**
     * Fill from a model's bone hierarchy, pre-order, skipping hidden bones. A hidden
     * bone's children stay visible and take over its depth, mirroring how the flat
     * lists used to just remove disabled bones from the hierarchy-ordered key list.
     */
    public void fillBones(IModel model, Collection<String> hidden)
    {
        this.clear();
        this.metas.clear();

        if (model != null)
        {
            Predicate<String> predicate = hidden == null ? null : hidden::contains;

            this.emit(boneNodes(model, model.getRootGroupKeys(), predicate), 0, 0, true);
        }

        this.update();
    }

    /**
     * Fill with a plain list of bone names, no hierarchy — the fallback for forms
     * whose bones don't come from an {@link IModel} (e.g. mob forms' model parts).
     */
    public void fillFlat(Collection<String> bones)
    {
        this.clear();
        this.metas.clear();

        this.list.addAll(bones);
        this.update();
    }

    /**
     * Fill from a form's attachment keys (see {@code FormRenderer.collectMatrices}):
     * every form in the body part tree becomes a header row, its model bones nest
     * under it in their own hierarchy order. The key set stays the source of truth —
     * only ids present in it are listed, so the picker can never offer an attachment
     * the matrix cache doesn't actually resolve.
     */
    public void fillAttachments(Form form, Collection<String> keys)
    {
        this.clear();
        this.metas.clear();

        Set<String> keySet = new HashSet<>(keys);

        if (form != null)
        {
            this.emit(formNodes(form, "", keySet), 0, 0, true);
        }

        /* Safety net: keys the static walk didn't reach (exotic renderers) go in flat,
         * so switching from the raw key list to the tree can't lose selectable values. */
        List<String> missed = new ArrayList<>();

        for (String key : keySet)
        {
            if (!this.list.contains(key))
            {
                missed.add(key);
            }
        }

        missed.sort(String::compareToIgnoreCase);
        this.list.addAll(missed);

        this.update();
    }

    /* Building the intermediate node tree */

    /** A bone (and its visible subtree); a hidden bone dissolves into its children in place. */
    private static List<Node> boneNodes(IModel model, Collection<String> bones, Predicate<String> hidden)
    {
        List<Node> nodes = new ArrayList<>();

        for (String bone : bones)
        {
            List<Node> children = boneNodes(model, model.getDirectChildrenKeys(bone), hidden);

            if (hidden == null || !hidden.test(bone))
            {
                Node node = new Node(bone, bone, bone);

                node.children.addAll(children);
                nodes.add(node);
            }
            else
            {
                nodes.addAll(children);
            }
        }

        return nodes;
    }

    /**
     * A form's representation at its parent's level: the form row (when its own key is
     * in the set) with its model bones and sub-forms nested under it — or, for a form
     * the matrix cache doesn't list, those children hoisted in place. The body part
     * index must advance for every part, even form-less ones — that is how
     * collectMatrices numbers the paths.
     */
    private static List<Node> formNodes(Form form, String path, Set<String> keys)
    {
        List<Node> children = new ArrayList<>();

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance instance = ModelFormRenderer.getModel(modelForm);

            if (instance != null && instance.model != null)
            {
                children.addAll(formBoneNodes(form, instance.model, instance.model.getRootGroupKeys(), path, keys));
            }
        }

        int i = 0;

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();

            if (child != null)
            {
                children.addAll(formNodes(child, StringUtils.combinePaths(path, String.valueOf(i)), keys));
            }

            i += 1;
        }

        if (!keys.contains(path))
        {
            return children;
        }

        String trackName = form.getTrackName("");
        String label = trackName.isEmpty() ? form.getFormIdOrName() : trackName;
        Node node = new Node(path, label, label);

        node.children.addAll(children);

        return new ArrayList<>(List.of(node));
    }

    private static List<Node> formBoneNodes(Form owner, IModel model, Collection<String> bones, String formPath, Set<String> keys)
    {
        List<Node> nodes = new ArrayList<>();

        for (String bone : bones)
        {
            String key = StringUtils.combinePaths(formPath, bone);
            List<Node> children = formBoneNodes(owner, model, model.getDirectChildrenKeys(bone), formPath, keys);

            if (keys.contains(key))
            {
                Node node = new Node(key, bone, owner.getTrackName(key));

                node.children.addAll(children);
                nodes.add(node);
            }
            else
            {
                nodes.addAll(children);
            }
        }

        return nodes;
    }

    /**
     * Flatten the node tree into the list (pre-order) and compute each row's branch
     * drawing: {@code lines} carries which ancestor columns still run a vertical
     * (their node wasn't the last sibling), {@code last} picks corner over tee.
     */
    private void emit(List<Node> nodes, int depth, int lines, boolean fill)
    {
        for (int i = 0; i < nodes.size(); i++)
        {
            Node node = nodes.get(i);
            boolean last = i == nodes.size() - 1;

            this.metas.put(node.id, new Meta(depth, lines, last, node.treeLabel, node.fullLabel));

            if (fill)
            {
                this.list.add(node.id);
            }

            /* This node's connector column keeps its vertical running through the
             * whole subtree unless the node closed the level as its last sibling.
             * Roots have no column, so nothing to continue. */
            int childLines = !last && depth > 0 ? lines | (1 << (depth - 1)) : lines;

            this.emit(node.children, depth + 1, childLines, fill);
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, String element)
    {
        Meta meta = this.metas.get(element);

        return meta == null ? element : meta.fullLabel;
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        /* Search results render flat with their full label — branches without the
         * parent rows above them are just a lie about structure. */
        boolean filtering = this.flat || this.isFiltering();
        Meta meta = filtering ? null : this.metas.get(element);
        int depth = meta == null ? 0 : meta.depth;
        int h = this.scroll.scrollItemSize;

        if (meta != null && depth > 0)
        {
            int mid = y + h / 2;
            int textX = x + 4 + depth * INDENT;

            for (int level = 0; level < depth - 1; level++)
            {
                if ((meta.lines & (1 << level)) != 0)
                {
                    int lx = columnX(x, level);

                    context.batcher.box(lx, y, lx + 1, y + h, GUIDE_COLOR);
                }
            }

            /* The connector: a tee for a middle child, a corner for the last one. */
            int lx = columnX(x, depth - 1);

            context.batcher.box(lx, y, lx + 1, meta.last ? mid + 1 : y + h, GUIDE_COLOR);
            context.batcher.box(lx + 1, mid, textX - 2, mid + 1, GUIDE_COLOR);
        }

        String label = meta == null
            ? (filtering ? this.elementToString(context, i, element) : element)
            : meta.treeLabel;
        int color = this.isDisabled(element) ? Colors.GRAY : (hover ? Colors.HIGHLIGHT : Colors.WHITE);
        int textX = x + 4 + depth * INDENT;
        int right = this.renderMarkers(context, element, x, y, h);

        if (right < x + this.area.w)
        {
            label = context.batcher.getFont().limitToWidth(label, right - textX - 2);
        }

        context.batcher.textShadow(label, textX, y + (h - context.batcher.getFont().getHeight()) / 2, color);
    }

    /**
     * Draws the row's role dots from the right edge inwards (past the scrollbar
     * lane) and returns the x the label must stop at.
     */
    private int renderMarkers(UIContext context, String element, int x, int y, int h)
    {
        int right = x + this.area.w - 3 - this.scroll.getScrollbarArea().w;

        if (this.markers == null)
        {
            return x + this.area.w;
        }

        Marker[] markers = this.markers.apply(element);

        if (markers == null || markers.length == 0)
        {
            return x + this.area.w;
        }

        this.laneX = right - markers.length * (MARKER + MARKER_GAP);

        for (int slot = 0; slot < markers.length; slot++)
        {
            Marker marker = markers[slot];

            if (marker == null)
            {
                continue;
            }

            int cell = right - (slot + 1) * (MARKER + MARKER_GAP) + MARKER_GAP;
            int size = marker.small ? MARKER / 2 : MARKER;
            int inset = (MARKER - size) / 2;
            int top = y + (h - size) / 2;

            context.batcher.box(cell + inset, top, cell + inset + size, top + size, marker.color);
        }

        return this.laneX;
    }

    private static int columnX(int x, int level)
    {
        return x + 4 + level * INDENT + 2;
    }

    /** The dot legend, gated on the cursor actually being in the dot column. */
    private class MarkerLegend implements ITooltip
    {
        private final LabelTooltip label;

        public MarkerLegend(IKey legend)
        {
            this.label = new LabelTooltip(legend, 200, Direction.LEFT);
        }

        @Override
        public IKey getLabel()
        {
            return this.label.getLabel();
        }

        @Override
        public void renderTooltip(UIContext context)
        {
            if (context.mouseX >= UIBoneTreeList.this.laneX)
            {
                this.label.renderTooltip(context);
            }
        }
    }

    private static class Node
    {
        public final String id;
        public final String treeLabel;
        public final String fullLabel;
        public final List<Node> children = new ArrayList<>();

        public Node(String id, String treeLabel, String fullLabel)
        {
            this.id = id;
            this.treeLabel = treeLabel;
            this.fullLabel = fullLabel;
        }
    }

    private static class Meta
    {
        public final int depth;

        /** Bit {@code i} — the vertical in ancestor column {@code i} runs through this row. */
        public final int lines;

        /** Last visible sibling — its connector is a corner instead of a tee. */
        public final boolean last;

        public final String treeLabel;
        public final String fullLabel;

        public Meta(int depth, int lines, boolean last, String treeLabel, String fullLabel)
        {
            this.depth = depth;
            this.lines = lines;
            this.last = last;
            this.treeLabel = treeLabel;
            this.fullLabel = fullLabel;
        }
    }
}
