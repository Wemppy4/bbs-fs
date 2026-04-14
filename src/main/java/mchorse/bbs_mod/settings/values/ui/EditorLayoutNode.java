package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public abstract class EditorLayoutNode
{
    public static final String TYPE_SPLITTER = "splitter";
    public static final String TYPE_PANEL = "panel";
    public static final String TYPE_STACK = "stack";
    public static final String DIR_V = "v";
    public static final String DIR_H = "h";

    /** Drop zone edges for split (left/right = vertical split, top/bottom = horizontal). */
    public static final int EDGE_LEFT = 0;
    public static final int EDGE_RIGHT = 1;
    public static final int EDGE_TOP = 2;
    public static final int EDGE_BOTTOM = 3;

    public abstract BaseType toData();

    /** Fill panel id -> normalized bounds (x, y, w, h in 0..1) relative to parent. */
    public abstract void computeBounds(float x, float y, float w, float h, Map<String, float[]> out);

    /** Return a new tree with panel ids id1 and id2 swapped. */
    public abstract EditorLayoutNode copyWithSwappedIds(String id1, String id2);

    public static EditorLayoutNode fromData(BaseType data)
    {
        if (data == null || !data.isMap())
        {
            return defaultFilmLayout();
        }

        MapType map = data.asMap();
        String type = map.getString("type", "");

        if (TYPE_SPLITTER.equals(type))
        {
            String dir = map.getString("dir", DIR_V);
            float ratio = MathUtils.clamp(map.getFloat("ratio", 0.5F), 0.05F, 0.95F);
            EditorLayoutNode first = fromData(map.get("first"));
            EditorLayoutNode second = fromData(map.get("second"));

            if (first == null || second == null)
            {
                return defaultFilmLayout();
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

        return defaultFilmLayout();
    }

    /** Default: vertical 0.66 -> main | (horizontal 0.5 -> preview / editArea). */
    public static EditorLayoutNode defaultFilmLayout()
    {
        return new SplitterNode(
            false,
            0.1819149F,
            new SplitterNode(
                true,
                0.28659794F,
                new PanelNode("replayProps"),
                new PanelNode("replaysList")
            ),
            new SplitterNode(
                true,
                0.6659794F,
                new SplitterNode(
                    false,
                    0.793238F,
                    new PanelNode("preview"),
                    new PanelNode("editArea")
                ),
                new PanelNode("main")
            )
        );
    }

    /** Returns a new tree with panelId removed; parent splitter is collapsed to its other child. */
    public static EditorLayoutNode copyWithRemovedLeaf(EditorLayoutNode root, String panelId)
    {
        return copyWithRemovedPanel(root, panelId);
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

    /** Returns a new tree with the first leaf matching leafId replaced by newNode. */
    public static EditorLayoutNode copyWithReplacedLeaf(EditorLayoutNode root, String leafId, EditorLayoutNode newNode)
    {
        if (root == null || newNode == null)
        {
            return root;
        }
        if (root instanceof PanelNode)
        {
            return ((PanelNode) root).getPanelId().equals(leafId) ? newNode : root;
        }
        SplitterNode s = (SplitterNode) root;
        EditorLayoutNode f2 = copyWithReplacedLeaf(s.first, leafId, newNode);
        if (f2 != s.first)
        {
            return new SplitterNode(s.horizontal, s.ratio, f2, s.second);
        }
        EditorLayoutNode s2 = copyWithReplacedLeaf(s.second, leafId, newNode);
        if (s2 != s.second)
        {
            return new SplitterNode(s.horizontal, s.ratio, s.first, s2);
        }
        return root;
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

    /** Collect all SplitterNodes in pre-order. */
    public static void collectSplitters(EditorLayoutNode node, List<SplitterNode> out)
    {
        if (node instanceof SplitterNode)
        {
            SplitterNode s = (SplitterNode) node;
            out.add(s);
            collectSplitters(s.first, out);
            collectSplitters(s.second, out);
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
            ? new SplitterNode(horizontal, 0.5F, dropped, node)
            : new SplitterNode(horizontal, 0.5F, node, dropped);
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

    /** Info for one splitter handle: normalized handle rect (hx,hy,hw,hh), parent rect (px,py,pw,ph), and direction. */
    public static class SplitterHandleInfo
    {
        public final float hx, hy, hw, hh;
        public final float px, py, pw, ph;
        public final boolean horizontal;

        public SplitterHandleInfo(float hx, float hy, float hw, float hh, float px, float py, float pw, float ph, boolean horizontal)
        {
            this.hx = hx;
            this.hy = hy;
            this.hw = hw;
            this.hh = hh;
            this.px = px;
            this.py = py;
            this.pw = pw;
            this.ph = ph;
            this.horizontal = horizontal;
        }
    }

    private static final float SPLITTER_HANDLE_MARGIN = 0.003F;
    /** Minimum thickness (normalized) so horizontal and vertical handles have comparable grab size. */
    private static final float SPLITTER_HANDLE_MIN_THICKNESS = 0.02F;

    /** Fill list with handle bounds and parent rect for each splitter (normalized 0..1). */
    public static void computeSplitterHandles(EditorLayoutNode root, float x, float y, float w, float h, List<SplitterHandleInfo> out)
    {
        if (!(root instanceof SplitterNode))
        {
            return;
        }
        SplitterNode s = (SplitterNode) root;
        float thickness = Math.max(2F * SPLITTER_HANDLE_MARGIN, SPLITTER_HANDLE_MIN_THICKNESS);
        if (s.horizontal)
        {
            float h1 = h * s.ratio;
            float hy = y + h1 - thickness * 0.5F;
            float hh = thickness;
            out.add(new SplitterHandleInfo(x, hy, w, hh, x, y, w, h, true));
            computeSplitterHandles(s.first, x, y, w, h1, out);
            computeSplitterHandles(s.second, x, y + h1, w, h - h1, out);
        }
        else
        {
            float w1 = w * s.ratio;
            float hw = thickness;
            float hx = x + w1 - thickness * 0.5F;
            out.add(new SplitterHandleInfo(hx, y, hw, h, x, y, w, h, false));
            computeSplitterHandles(s.first, x, y, w1, h, out);
            computeSplitterHandles(s.second, x + w1, y, w - w1, h, out);
        }
    }

    public static class SplitterNode extends EditorLayoutNode
    {
        /** true = horizontal (split by height), false = vertical (split by width). */
        private final boolean horizontal;
        private float ratio;
        private final EditorLayoutNode first;
        private final EditorLayoutNode second;

        public SplitterNode(boolean horizontal, float ratio, EditorLayoutNode first, EditorLayoutNode second)
        {
            this.horizontal = horizontal;
            this.ratio = MathUtils.clamp(ratio, 0.05F, 0.95F);
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

        public void setRatio(float ratio)
        {
            this.ratio = MathUtils.clamp(ratio, 0.05F, 0.95F);
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

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            if (this.horizontal)
            {
                float h1 = h * this.ratio;
                float h2 = h * (1F - this.ratio);
                this.first.computeBounds(x, y, w, h1, out);
                this.second.computeBounds(x, y + h1, w, h2, out);
            }
            else
            {
                float w1 = w * this.ratio;
                float w2 = w * (1F - this.ratio);
                this.first.computeBounds(x, y, w1, h, out);
                this.second.computeBounds(x + w1, y, w2, h, out);
            }
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            return new SplitterNode(
                this.horizontal,
                this.ratio,
                this.first.copyWithSwappedIds(id1, id2),
                this.second.copyWithSwappedIds(id1, id2)
            );
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

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            out.put(this.panelId, new float[] {x, y, w, h});
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            String id = this.panelId.equals(id1) ? id2 : this.panelId.equals(id2) ? id1 : this.panelId;
            return new PanelNode(id);
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

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            for (String id : this.panelIds)
            {
                out.put(id, new float[] {x, y, w, h});
            }
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            List<String> ids = new ArrayList<>(this.panelIds.size());

            for (String id : this.panelIds)
            {
                ids.add(id.equals(id1) ? id2 : id.equals(id2) ? id1 : id);
            }

            String active = this.activePanelId.equals(id1)
                ? id2
                : this.activePanelId.equals(id2)
                    ? id1
                    : this.activePanelId;

            return new StackNode(ids, active);
        }
    }
}
