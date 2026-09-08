package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class EditorLayoutNode
{
    public static final String TYPE_SPLITTER = "splitter";
    public static final String TYPE_PANEL = "panel";
    public static final String TYPE_STACK = "stack";
    public static final String DIR_V = "v";
    public static final String DIR_H = "h";

    /** A split never collapses a side entirely, so every panel keeps a grabbable sliver. */
    public static final float MIN_RATIO = 0.05F;
    public static final float MAX_RATIO = 0.95F;

    /** Share a panel takes when it is split off against another panel. */
    public static final float SPLIT_RATIO = 0.5F;

    /** Drop zone edges for split (left/right = vertical split, top/bottom = horizontal). */
    public static final int EDGE_LEFT = 0;
    public static final int EDGE_RIGHT = 1;
    public static final int EDGE_TOP = 2;
    public static final int EDGE_BOTTOM = 3;

    public abstract BaseType toData();

    /**
     * Reads a tree, returning {@code null} when the data is missing or malformed. Callers decide what
     * an unreadable layout falls back to &mdash; a shared default here would splice one editor's
     * panel ids into another's layout.
     */
    public static EditorLayoutNode fromData(BaseType data)
    {
        if (data == null || !data.isMap())
        {
            return null;
        }

        MapType map = data.asMap();
        String type = map.getString("type", "");

        if (TYPE_SPLITTER.equals(type))
        {
            String dir = map.getString("dir", DIR_V);
            float ratio = MathUtils.clamp(map.getFloat("ratio", 0.5F), MIN_RATIO, MAX_RATIO);
            EditorLayoutNode first = fromData(map.get("first"));
            EditorLayoutNode second = fromData(map.get("second"));

            /* A broken side collapses to the good one rather than discarding the whole tree. */
            if (first == null || second == null)
            {
                return first == null ? second : first;
            }

            return new SplitterNode(DIR_H.equals(dir), ratio, first, second);
        }

        if (TYPE_PANEL.equals(type))
        {
            String id = map.getString("id", "");
            if (id.isEmpty())
            {
                return null;
            }
            return new PanelNode(id);
        }

        if (TYPE_STACK.equals(type))
        {
            List<String> panelIds = new ArrayList<>();

            if (map.has("ids", BaseType.TYPE_LIST))
            {
                for (BaseType typeId : map.getList("ids"))
                {
                    if (typeId == null || !typeId.isString())
                    {
                        continue;
                    }

                    String id = typeId.asString();

                    if (!id.isEmpty())
                    {
                        panelIds.add(id);
                    }
                }
            }
            else if (map.has("id", BaseType.TYPE_STRING))
            {
                String id = map.getString("id", "");

                if (!id.isEmpty())
                {
                    panelIds.add(id);
                }
            }

            panelIds = normalizePanelIds(panelIds);

            if (panelIds.isEmpty())
            {
                return null;
            }

            String active = map.getString("active", panelIds.get(0));

            return new StackNode(panelIds, active);
        }

        return null;
    }

    /**
     * What a fresh install opens the film editor in. The welcome screen offers the very same tree
     * as its "default" card, so {@code assets/presets/layouts/default.json} has to move with this
     * one: the record's properties over the record list down the left, the preview over the
     * timeline in the middle, and the edit area as a column of its own on the right.
     */
    public static EditorLayoutNode defaultFilmLayout()
    {
        return new SplitterNode(
            false,
            0.7916667F,
            new SplitterNode(
                false,
                0.25294888F,
                new SplitterNode(
                    true,
                    0.28479657F,
                    new PanelNode("replayProps"),
                    new PanelNode("replaysList")
                ),
                new SplitterNode(
                    true,
                    0.6167024F,
                    new PanelNode("preview"),
                    new PanelNode("main")
                )
            ),
            new PanelNode("editArea")
        );
    }

    /**
     * Default particle layout: the section-group tabs stacked across the top, with the bottom split
     * between the preview (left) and the MoLang editor (right).
     */
    public static EditorLayoutNode defaultParticleLayout()
    {
        List<String> tabs = new ArrayList<>();
        tabs.add("general");
        tabs.add("emitter");
        tabs.add("particle");
        tabs.add("appearance");

        return new SplitterNode(
            false,
            0.22446808F,
            new StackNode(tabs, "general"),
            new SplitterNode(
                true,
                0.7408994F,
                new PanelNode("preview"),
                new PanelNode("molang")
            )
        );
    }

    /** Returns a new tree with panelId removed; parent splitter is collapsed to its other child. */
    public static EditorLayoutNode copyWithRemovedPanel(EditorLayoutNode root, String panelId)
    {
        if (root == null)
        {
            return null;
        }

        RemoveResult result = removePanel(root, panelId);

        return result.changed ? result.node : root;
    }

    /** Returns a new tree with droppedPanel moved to split at edge of targetPanel. */
    public static EditorLayoutNode copyWithInsertSplitAt(EditorLayoutNode root, String targetPanelId, String droppedPanelId, int edge)
    {
        EditorLayoutNode root2 = copyWithRemovedPanel(root, droppedPanelId);

        if (root2 == null)
        {
            return root;
        }

        boolean horizontal = (edge == EDGE_TOP || edge == EDGE_BOTTOM);
        boolean droppedFirst = (edge == EDGE_LEFT || edge == EDGE_TOP);

        return copyWithInsertedSplitAroundTarget(root2, targetPanelId, droppedPanelId, horizontal, droppedFirst);
    }

    /**
     * Returns a new tree with droppedPanel split off against the layout as a whole, so it spans the
     * full width or height of that edge instead of only the panel it was dropped on.
     */
    public static EditorLayoutNode copyWithInsertSplitAtRoot(EditorLayoutNode root, String droppedPanelId, int edge, float ratio)
    {
        EditorLayoutNode rest = copyWithRemovedPanel(root, droppedPanelId);
        EditorLayoutNode dropped = new PanelNode(droppedPanelId);

        if (rest == null)
        {
            return dropped;
        }

        boolean horizontal = (edge == EDGE_TOP || edge == EDGE_BOTTOM);
        boolean droppedFirst = (edge == EDGE_LEFT || edge == EDGE_TOP);

        return droppedFirst
            ? new SplitterNode(horizontal, ratio, dropped, rest)
            : new SplitterNode(horizontal, 1F - ratio, rest, dropped);
    }

    /** Returns a new tree with droppedPanel added into target panel's stack (center drop behavior). */
    public static EditorLayoutNode copyWithInsertStackAt(EditorLayoutNode root, String targetPanelId, String droppedPanelId)
    {
        EditorLayoutNode root2 = copyWithRemovedPanel(root, droppedPanelId);

        if (root2 == null)
        {
            return root;
        }

        return copyWithInsertedIntoStack(root2, targetPanelId, droppedPanelId);
    }

    /** Returns a new tree with active tab changed in stack that contains panelId. */
    public static EditorLayoutNode copyWithStackActivePanel(EditorLayoutNode root, String panelId, String activePanelId)
    {
        if (root == null || panelId == null || activePanelId == null)
        {
            return root;
        }

        if (root instanceof StackNode)
        {
            StackNode stack = (StackNode) root;

            if (!stack.containsPanel(panelId))
            {
                return root;
            }

            if (!stack.containsPanel(activePanelId))
            {
                return root;
            }

            if (activePanelId.equals(stack.getActivePanelId()))
            {
                return root;
            }

            return stack.copyWithActivePanel(activePanelId);
        }

        if (root instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) root;

            if (containsPanel(splitter.first, panelId))
            {
                EditorLayoutNode first = copyWithStackActivePanel(splitter.first, panelId, activePanelId);

                if (first != splitter.first)
                {
                    return new SplitterNode(splitter.horizontal, splitter.ratio, first, splitter.second);
                }
            }

            if (containsPanel(splitter.second, panelId))
            {
                EditorLayoutNode second = copyWithStackActivePanel(splitter.second, panelId, activePanelId);

                if (second != splitter.second)
                {
                    return new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second);
                }
            }
        }

        return root;
    }

    /**
     * Returns a new tree where each splitter present in {@code ratios} takes its new ratio. Targets
     * are matched by node identity against {@code root}, so all of them must be applied in this one
     * call &mdash; rebuilding the path to one target replaces the nodes along it, which would make
     * the remaining targets unreachable.
     */
    public static EditorLayoutNode copyWithSplitterRatios(EditorLayoutNode root, Map<SplitterNode, Float> ratios)
    {
        if (root == null || ratios == null || ratios.isEmpty())
        {
            return root;
        }

        if (!(root instanceof SplitterNode))
        {
            return root;
        }

        SplitterNode splitter = (SplitterNode) root;
        Float ratio = ratios.get(splitter);
        EditorLayoutNode first = copyWithSplitterRatios(splitter.first, ratios);
        EditorLayoutNode second = copyWithSplitterRatios(splitter.second, ratios);
        float newRatio = ratio == null ? splitter.ratio : MathUtils.clamp(ratio, MIN_RATIO, MAX_RATIO);

        if (newRatio == splitter.ratio && first == splitter.first && second == splitter.second)
        {
            return root;
        }

        return new SplitterNode(splitter.horizontal, newRatio, first, second);
    }

    /** Returns a new tree with the two panel ids exchanged, wherever they sit (splits or stacks). */
    public static EditorLayoutNode copyWithSwappedPanels(EditorLayoutNode root, String id1, String id2)
    {
        if (root == null || id1 == null || id2 == null || id1.equals(id2))
        {
            return root;
        }

        return swapPanels(root, id1, id2);
    }

    private static EditorLayoutNode swapPanels(EditorLayoutNode node, String id1, String id2)
    {
        if (node instanceof PanelNode)
        {
            String id = ((PanelNode) node).getPanelId();
            String swapped = swapId(id, id1, id2);

            return swapped.equals(id) ? node : new PanelNode(swapped);
        }

        if (node instanceof StackNode)
        {
            StackNode stack = (StackNode) node;

            if (!stack.containsPanel(id1) && !stack.containsPanel(id2))
            {
                return node;
            }

            List<String> ids = new ArrayList<>();

            for (String id : stack.getPanelIds())
            {
                ids.add(swapId(id, id1, id2));
            }

            return new StackNode(ids, swapId(stack.getActivePanelId(), id1, id2));
        }

        if (node instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) node;
            EditorLayoutNode first = swapPanels(splitter.first, id1, id2);
            EditorLayoutNode second = swapPanels(splitter.second, id1, id2);

            if (first == splitter.first && second == splitter.second)
            {
                return node;
            }

            return new SplitterNode(splitter.horizontal, splitter.ratio, first, second);
        }

        return node;
    }

    private static String swapId(String id, String id1, String id2)
    {
        return id.equals(id1) ? id2 : id.equals(id2) ? id1 : id;
    }

    /** Collect the ids of every panel the tree places, including the inactive tabs of stacks. */
    public static void collectPanelIds(EditorLayoutNode node, Set<String> out)
    {
        if (node instanceof PanelNode)
        {
            out.add(((PanelNode) node).getPanelId());
        }
        else if (node instanceof StackNode)
        {
            out.addAll(((StackNode) node).getPanelIds());
        }
        else if (node instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) node;

            collectPanelIds(splitter.first, out);
            collectPanelIds(splitter.second, out);
        }
    }

    private static RemoveResult removePanel(EditorLayoutNode node, String panelId)
    {
        if (node == null)
        {
            return new RemoveResult(null, false);
        }

        if (node instanceof PanelNode)
        {
            PanelNode panel = (PanelNode) node;

            if (panel.getPanelId().equals(panelId))
            {
                return new RemoveResult(null, true);
            }

            return new RemoveResult(node, false);
        }

        if (node instanceof StackNode)
        {
            StackNode stack = (StackNode) node;

            if (!stack.containsPanel(panelId))
            {
                return new RemoveResult(node, false);
            }

            List<String> ids = new ArrayList<>(stack.getPanelIds());
            ids.remove(panelId);

            if (ids.isEmpty())
            {
                return new RemoveResult(null, true);
            }

            if (ids.size() == 1)
            {
                return new RemoveResult(new PanelNode(ids.get(0)), true);
            }

            String active = stack.getActivePanelId();

            if (active.equals(panelId) || !ids.contains(active))
            {
                active = ids.get(0);
            }

            return new RemoveResult(new StackNode(ids, active), true);
        }

        SplitterNode splitter = (SplitterNode) node;
        RemoveResult first = removePanel(splitter.first, panelId);

        if (first.changed)
        {
            if (first.node == null)
            {
                return new RemoveResult(splitter.second, true);
            }

            return new RemoveResult(new SplitterNode(splitter.horizontal, splitter.ratio, first.node, splitter.second), true);
        }

        RemoveResult second = removePanel(splitter.second, panelId);

        if (second.changed)
        {
            if (second.node == null)
            {
                return new RemoveResult(splitter.first, true);
            }

            return new RemoveResult(new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second.node), true);
        }

        return new RemoveResult(node, false);
    }

    private static EditorLayoutNode copyWithInsertedSplitAroundTarget(EditorLayoutNode node, String targetPanelId, String droppedPanelId, boolean horizontal, boolean droppedFirst)
    {
        if (node == null)
        {
            return null;
        }

        if (node instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) node;

            if (containsPanel(splitter.first, targetPanelId))
            {
                EditorLayoutNode first = copyWithInsertedSplitAroundTarget(splitter.first, targetPanelId, droppedPanelId, horizontal, droppedFirst);

                if (first != splitter.first)
                {
                    return new SplitterNode(splitter.horizontal, splitter.ratio, first, splitter.second);
                }
            }

            if (containsPanel(splitter.second, targetPanelId))
            {
                EditorLayoutNode second = copyWithInsertedSplitAroundTarget(splitter.second, targetPanelId, droppedPanelId, horizontal, droppedFirst);

                if (second != splitter.second)
                {
                    return new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second);
                }
            }

            return node;
        }

        if (!containsPanel(node, targetPanelId))
        {
            return node;
        }

        EditorLayoutNode dropped = new PanelNode(droppedPanelId);

        return droppedFirst
            ? new SplitterNode(horizontal, SPLIT_RATIO, dropped, node)
            : new SplitterNode(horizontal, 1F - SPLIT_RATIO, node, dropped);
    }

    private static EditorLayoutNode copyWithInsertedIntoStack(EditorLayoutNode node, String targetPanelId, String droppedPanelId)
    {
        if (node == null)
        {
            return null;
        }

        if (node instanceof PanelNode)
        {
            PanelNode panel = (PanelNode) node;

            if (!panel.getPanelId().equals(targetPanelId))
            {
                return node;
            }

            List<String> ids = new ArrayList<>();
            ids.add(panel.getPanelId());
            ids.add(droppedPanelId);

            return new StackNode(ids, droppedPanelId);
        }

        if (node instanceof StackNode)
        {
            StackNode stack = (StackNode) node;

            if (!stack.containsPanel(targetPanelId))
            {
                return node;
            }

            List<String> ids = new ArrayList<>(stack.getPanelIds());

            if (ids.contains(droppedPanelId))
            {
                return stack.copyWithActivePanel(droppedPanelId);
            }

            int targetIndex = ids.indexOf(targetPanelId);

            if (targetIndex < 0 || targetIndex >= ids.size())
            {
                ids.add(droppedPanelId);
            }
            else
            {
                ids.add(targetIndex + 1, droppedPanelId);
            }

            return new StackNode(ids, droppedPanelId);
        }

        SplitterNode splitter = (SplitterNode) node;

        if (containsPanel(splitter.first, targetPanelId))
        {
            EditorLayoutNode first = copyWithInsertedIntoStack(splitter.first, targetPanelId, droppedPanelId);

            if (first != splitter.first)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, first, splitter.second);
            }
        }

        if (containsPanel(splitter.second, targetPanelId))
        {
            EditorLayoutNode second = copyWithInsertedIntoStack(splitter.second, targetPanelId, droppedPanelId);

            if (second != splitter.second)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second);
            }
        }

        return node;
    }

    private static boolean containsPanel(EditorLayoutNode node, String panelId)
    {
        if (node == null)
        {
            return false;
        }

        if (node instanceof PanelNode)
        {
            return ((PanelNode) node).getPanelId().equals(panelId);
        }

        if (node instanceof StackNode)
        {
            return ((StackNode) node).containsPanel(panelId);
        }

        SplitterNode splitter = (SplitterNode) node;

        return containsPanel(splitter.first, panelId) || containsPanel(splitter.second, panelId);
    }

    private static List<String> normalizePanelIds(List<String> panelIds)
    {
        List<String> ids = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        for (String id : panelIds)
        {
            if (id == null || id.isEmpty() || seen.contains(id))
            {
                continue;
            }

            seen.add(id);
            ids.add(id);
        }

        return ids;
    }

    private static class RemoveResult
    {
        public final EditorLayoutNode node;
        public final boolean changed;

        public RemoveResult(EditorLayoutNode node, boolean changed)
        {
            this.node = node;
            this.changed = changed;
        }
    }

    public static class SplitterNode extends EditorLayoutNode
    {
        /** true = horizontal (split by height), false = vertical (split by width). */
        private final boolean horizontal;
        private final float ratio;
        private final EditorLayoutNode first;
        private final EditorLayoutNode second;

        public SplitterNode(boolean horizontal, float ratio, EditorLayoutNode first, EditorLayoutNode second)
        {
            this.horizontal = horizontal;
            this.ratio = MathUtils.clamp(ratio, MIN_RATIO, MAX_RATIO);
            this.first = first;
            this.second = second;
        }

        public boolean isHorizontal()
        {
            return this.horizontal;
        }

        public float getRatio()
        {
            return this.ratio;
        }

        public EditorLayoutNode getFirst()
        {
            return this.first;
        }

        public EditorLayoutNode getSecond()
        {
            return this.second;
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            map.putString("type", TYPE_SPLITTER);
            map.putString("dir", this.horizontal ? DIR_H : DIR_V);
            map.putFloat("ratio", this.ratio);
            map.put("first", this.first.toData());
            map.put("second", this.second.toData());
            return map;
        }
    }

    public static class PanelNode extends EditorLayoutNode
    {
        private final String panelId;

        public PanelNode(String panelId)
        {
            this.panelId = panelId;
        }

        public String getPanelId()
        {
            return this.panelId;
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            map.putString("type", TYPE_PANEL);
            map.putString("id", this.panelId);
            return map;
        }
    }

    public static class StackNode extends EditorLayoutNode
    {
        private final List<String> panelIds;
        private final String activePanelId;

        public StackNode(List<String> panelIds, String activePanelId)
        {
            this.panelIds = normalizePanelIds(panelIds);

            String active = activePanelId;

            if (active == null || active.isEmpty() || !this.panelIds.contains(active))
            {
                active = this.panelIds.isEmpty() ? "" : this.panelIds.get(0);
            }

            this.activePanelId = active;
        }

        public List<String> getPanelIds()
        {
            return this.panelIds;
        }

        public String getActivePanelId()
        {
            return this.activePanelId;
        }

        public boolean containsPanel(String panelId)
        {
            return this.panelIds.contains(panelId);
        }

        public StackNode copyWithActivePanel(String panelId)
        {
            return new StackNode(this.panelIds, panelId);
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            ListType ids = new ListType();

            for (String id : this.panelIds)
            {
                ids.addString(id);
            }

            map.putString("type", TYPE_STACK);
            map.put("ids", ids);
            map.putString("active", this.activePanelId);

            return map;
        }
    }
}
