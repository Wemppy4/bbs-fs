package mchorse.bbs_mod.ui.framework.elements.layout;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Reusable dockable-panel layout. Owns a set of registered panels and arranges them per an
 * {@link EditorLayoutNode} tree provided by an {@link ILayoutSource}: resizable splitters,
 * drag-to-dock with edge/center drop zones, tab/stack grouping, lock toggle and reset.
 *
 * <p>Panels are registered with {@link #addPanel} and become direct children of this element.
 * Film- or particle-specific behavior (which panels exist, default tree, frameless preview,
 * data gating, follow-up visibility) is supplied as configuration so a single implementation
 * serves both editors.
 */
public class UIDockLayout extends UIElement
{
    private static final float DRAG_HANDLE_HEIGHT_NORM = 0.02F;
    private static final float DRAG_HANDLE_TOP_OFFSET_NORM = 0.01F;
    private static final int SPLITTER_HANDLE_PX = 14;
    private static final int SPLITTER_HANDLE_LINE_PX = 1;
    private static final int SPLITTER_LINK_HITBOX_PADDING_PX = 8;
    private static final int DROP_ZONE_CENTER = -1;
    private static final float DROP_EDGE_MARGIN = 0.2F;
    private static final int EDITOR_MIN_SIZE_FOR_PX_HANDLES = 10;
    private static final int DOCK_STACK_TABS_HEIGHT_PX = 20;
    private static final int PANEL_GAP_PX = 4;
    private static final float PANEL_EDGE_EPS = 0.001F;

    private final Map<String, UIElement> panelById = new LinkedHashMap<>();
    private final Map<String, Icon> iconById = new HashMap<>();
    private final Map<String, UIDraggable> dragHandlesById = new LinkedHashMap<>();
    private final List<UIDraggable> splitterHandles = new ArrayList<>();
    private final List<EditorLayoutNode.SplitterHandleInfo> splitterHandleInfos = new ArrayList<>();
    private final List<UIDockStackTabs> dockStackTabs = new ArrayList<>();
    private final Map<String, DockStackInfo> dockStackByPanelId = new HashMap<>();
    private final List<Integer> draggedSplitterIndices = new ArrayList<>();

    private final UIRenderable surfaces = new UIRenderable(this::renderPanelSurfaces);
    private final UIRenderable borders = new UIRenderable(this::renderPanelBorders);
    private final UIRenderable dropHighlight = new UIRenderable(this::renderDropZoneHighlight);

    private boolean layoutLocked = true;
    private String draggingPanelId;
    private String dropTargetPanelId;
    private int dropTargetZone = DROP_ZONE_CENTER;

    /* Configuration */
    private ILayoutSource source;
    private String framelessPanelId;
    private Supplier<Boolean> gate = () -> true;
    private Runnable onChanged = () -> {};
    private Runnable onSplitterDragEnd = () -> {};
    private UnaryOperator<EditorLayoutNode> ensureFn = UnaryOperator.identity();
    private Function<String, Icon> iconFn;

    public UIDockLayout()
    {
        this.ensureFn = this::ensureRegisteredPanels;
    }

    /* Configuration setters */

    public UIDockLayout source(ILayoutSource source)
    {
        this.source = source;

        return this;
    }

    /** Panel id whose surface/borders/gutter are skipped (e.g. a frameless 3D preview viewport). */
    public UIDockLayout frameless(String panelId)
    {
        this.framelessPanelId = panelId;

        return this;
    }

    public UIDockLayout gate(Supplier<Boolean> gate)
    {
        this.gate = gate;

        return this;
    }

    /** Run after every layout rebuild so the host can re-sync its own visibility. */
    public UIDockLayout onChanged(Runnable onChanged)
    {
        this.onChanged = onChanged;

        return this;
    }

    public UIDockLayout onSplitterDragEnd(Runnable onSplitterDragEnd)
    {
        this.onSplitterDragEnd = onSplitterDragEnd;

        return this;
    }

    /** Override how missing required panels are inserted into a loaded tree (default: append-split). */
    public UIDockLayout ensure(UnaryOperator<EditorLayoutNode> ensureFn)
    {
        this.ensureFn = ensureFn;

        return this;
    }

    public UIDockLayout icons(Function<String, Icon> iconFn)
    {
        this.iconFn = iconFn;

        return this;
    }

    /**
     * Register a panel. The panel becomes a direct child of this element and is arranged by the
     * layout. Call {@link #mount()} once after registering all panels.
     */
    public UIDockLayout addPanel(String id, UIElement panel, Icon icon)
    {
        this.panelById.put(id, panel);
        this.iconById.put(id, icon == null ? Icons.FILE : icon);
        this.dragHandlesById.put(id, this.createPanelDragHandle(id));

        return this;
    }

    /** Add all children in z-order and run the first layout pass. Call after {@link #addPanel}s. */
    public void mount()
    {
        this.add(this.surfaces);

        for (UIElement panel : this.panelById.values())
        {
            this.add(panel);
        }

        this.add(this.borders, this.dropHighlight);

        for (UIDraggable handle : this.dragHandlesById.values())
        {
            this.add(handle);
        }

        this.setupFlex(false);
    }

    public UIElement getPanel(String id)
    {
        return this.panelById.get(id);
    }

    public boolean isLocked()
    {
        return this.layoutLocked;
    }

    public boolean isPanelActive(String panelId)
    {
        DockStackInfo stack = this.dockStackByPanelId.get(panelId);

        return stack != null && panelId.equals(stack.activePanelId);
    }

    private Icon getDockPanelIcon(String panelId)
    {
        if (this.iconFn != null)
        {
            return this.iconFn.apply(panelId);
        }

        return this.iconById.getOrDefault(panelId, Icons.FILE);
    }

    /* Layout settings access */

    private EditorLayoutNode layoutRoot()
    {
        return this.source.getRoot();
    }

    private void setLayoutRoot(EditorLayoutNode root)
    {
        this.source.setRoot(root);
    }

    private List<EditorLayoutNode.SplitterNode> layoutSplitters()
    {
        return this.source.getSplitters();
    }

    private List<EditorLayoutNode.SplitterNode> layoutSplittersForWrite()
    {
        return this.source.getSplittersForWrite();
    }

    /* Public actions */

    public void refresh()
    {
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupFlex(true);
    }

    public void toggleLock()
    {
        this.layoutLocked = !this.layoutLocked;
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupFlex(true);
    }

    public void resetLayout()
    {
        this.setLayoutRoot(this.source.getDefault());
        this.refresh();
    }

    /** Current layout tree (with all required panels ensured), e.g. for serializing into a preset. */
    public EditorLayoutNode getLayoutRoot()
    {
        return this.ensureFn.apply(this.layoutRoot());
    }

    public void applyLayoutRoot(EditorLayoutNode root)
    {
        if (root != null)
        {
            this.setLayoutRoot(root);
            this.setupFlex(true);
        }
    }

    public boolean cycleDockStackTab(int offset)
    {
        if (offset == 0)
        {
            return false;
        }

        DockStackInfo stack = this.resolveDockStackForKeyboardCycle();

        if (stack == null || !stack.isStacked() || stack.panelIds.isEmpty())
        {
            return false;
        }

        int currentIndex = stack.panelIds.indexOf(stack.activePanelId);

        if (currentIndex < 0)
        {
            currentIndex = 0;
        }

        int size = stack.panelIds.size();
        int nextIndex = (currentIndex + offset) % size;

        if (nextIndex < 0)
        {
            nextIndex += size;
        }

        this.activateDockStackTab(stack.getAnchorPanelId(), stack.panelIds.get(nextIndex));

        return true;
    }

    private DockStackInfo resolveDockStackForKeyboardCycle()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return null;
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            if (!tabs.isVisible() || !tabs.area.isInside(context.mouseX, context.mouseY))
            {
                continue;
            }

            DockStackInfo hoveredStack = this.dockStackByPanelId.get(tabs.anchorPanelId);

            if (hoveredStack != null && hoveredStack.isStacked())
            {
                return hoveredStack;
            }
        }

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            UIElement panel = entry.getValue();

            if (!panel.isVisible() || !panel.area.isInside(context.mouseX, context.mouseY))
            {
                continue;
            }

            DockStackInfo stack = this.dockStackByPanelId.get(entry.getKey());

            if (stack != null && stack.isStacked())
            {
                return stack;
            }
        }

        return null;
    }

    private void activateDockStackTab(String stackPanelId, String panelId)
    {
        if (stackPanelId == null || panelId == null)
        {
            return;
        }

        EditorLayoutNode root = this.layoutRoot();
        EditorLayoutNode next = EditorLayoutNode.copyWithStackActivePanel(root, stackPanelId, panelId);

        if (next != root)
        {
            this.setLayoutRoot(next);
            this.setupFlex(true);
        }
    }

    /* Layout build */

    private EditorLayoutNode ensureRegisteredPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();
        this.collectPanelIds(root, ids);

        EditorLayoutNode out = root;
        String anchor = null;

        for (String id : ids)
        {
            anchor = id;
            break;
        }

        for (String id : this.panelById.keySet())
        {
            if (!ids.contains(id))
            {
                if (anchor == null)
                {
                    out = new EditorLayoutNode.PanelNode(id);
                    anchor = id;
                }
                else
                {
                    out = EditorLayoutNode.copyWithInsertSplitAt(out, anchor, id, EditorLayoutNode.EDGE_RIGHT);
                }
            }
        }

        return out;
    }

    private void collectPanelIds(EditorLayoutNode node, HashSet<String> out)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            out.add(((EditorLayoutNode.PanelNode) node).getPanelId());
        }
        else if (node instanceof EditorLayoutNode.StackNode)
        {
            out.addAll(((EditorLayoutNode.StackNode) node).getPanelIds());
        }
        else if (node instanceof EditorLayoutNode.SplitterNode)
        {
            EditorLayoutNode.SplitterNode s = (EditorLayoutNode.SplitterNode) node;
            this.collectPanelIds(s.getFirst(), out);
            this.collectPanelIds(s.getSecond(), out);
        }
    }

    public void setupFlex(boolean resize)
    {
        EditorLayoutNode originalRoot = this.layoutRoot();
        EditorLayoutNode root = this.ensureFn.apply(originalRoot);

        if (root != originalRoot)
        {
            this.setLayoutRoot(root);
        }

        List<EditorLayoutNode.SplitterNode> splitters = this.layoutSplitters();

        if (resize && splitters.size() == this.splitterHandles.size())
        {
            this.updateFlexBoundsOnly(root);
            this.resize();
            this.resize();
            return;
        }

        this.clearSplitterDragState();

        List<DockStackInfo> stackInfos = new ArrayList<>();
        this.collectDockStacks(root, 0F, 0F, 1F, 1F, stackInfos);

        for (UIElement el : this.panelById.values())
        {
            el.resetFlex();
        }

        for (UIDraggable h : this.splitterHandles)
        {
            h.removeFromParent();
        }

        this.splitterHandles.clear();

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.removeFromParent();
        }

        this.dockStackTabs.clear();
        this.dockStackByPanelId.clear();

        for (UIDraggable h : this.dragHandlesById.values())
        {
            h.resetFlex();
        }

        this.applyPanelBoundsFromStacks(stackInfos);
        this.rebuildDockStackTabs(stackInfos);

        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, this.splitterHandleInfos);

        for (int i = 0; i < splitters.size(); i++)
        {
            UIDraggable handle = this.createSplitterHandle(i);
            this.splitterHandles.add(handle);
            this.addBefore(this.borders, handle);
        }

        if (this.layoutLocked)
        {
            for (UIDraggable h : this.dragHandlesById.values())
            {
                h.setVisible(false);
            }
        }
        else
        {
            this.applyDragHandleBoundsFromStacks(stackInfos);
        }

        this.updateTabVisibility();

        if (resize)
        {
            this.resize();
            this.resize();
        }
    }

    private void updateFlexBoundsOnly(EditorLayoutNode root)
    {
        List<DockStackInfo> stackInfos = new ArrayList<>();

        this.collectDockStacks(root, 0F, 0F, 1F, 1F, stackInfos);
        this.applyPanelBoundsFromStacks(stackInfos);

        if (!this.updateDockStackTabsBoundsOnly(stackInfos))
        {
            this.rebuildDockStackTabs(stackInfos);
        }

        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, this.splitterHandleInfos);
        this.syncSplitterHandleBounds();
        this.applyDragHandleBoundsFromStacks(stackInfos);
        this.updateTabVisibility();
    }

    private void updateTabVisibility()
    {
        boolean show = this.gate.get();

        if (!show)
        {
            for (UIElement panel : this.panelById.values())
            {
                panel.setVisible(false);
            }
        }
        else
        {
            for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
            {
                entry.getValue().setVisible(this.isPanelActive(entry.getKey()));
            }
        }

        for (Map.Entry<String, UIDraggable> entry : this.dragHandlesById.entrySet())
        {
            DockStackInfo stack = this.dockStackByPanelId.get(entry.getKey());
            boolean active = stack != null && entry.getKey().equals(stack.activePanelId);

            entry.getValue().setVisible(show && !this.layoutLocked && active);
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.setVisible(show);
        }

        this.onChanged.run();
    }

    /* Splitter handles */

    private void applySplitterHandleBounds(UIDraggable handle, EditorLayoutNode.SplitterHandleInfo info)
    {
        int ew = this.area.w;
        int eh = this.area.h;

        if (ew < EDITOR_MIN_SIZE_FOR_PX_HANDLES || eh < EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            handle.relative(this).x(info.hx).y(info.hy).w(info.hw).h(info.hh);
            return;
        }

        if (info.horizontal)
        {
            float centerY = info.hy + info.hh * 0.5F;
            float hyNew = centerY - (SPLITTER_HANDLE_PX / (2F * eh));
            handle.relative(this).x(info.hx).y(hyNew).w(info.hw).h(SPLITTER_HANDLE_PX);
        }
        else
        {
            float centerX = info.hx + info.hw * 0.5F;
            float hxNew = centerX - (SPLITTER_HANDLE_PX / (2F * ew));
            handle.relative(this).x(hxNew).y(info.hy).w(SPLITTER_HANDLE_PX).h(info.hh);
        }
    }

    private void syncSplitterHandleBounds()
    {
        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            this.applySplitterHandleBounds(this.splitterHandles.get(i), this.splitterHandleInfos.get(i));
        }
    }

    private UIDraggable createSplitterHandle(int index)
    {
        UIDraggable handle = new UIDraggable((context) -> this.applySplitterDrag(context.mouseX, context.mouseY))
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIDockLayout.this.beginSplitterDrag(index, context.mouseX, context.mouseY);
                boolean handled = super.subMouseClicked(context);

                if (!handled)
                {
                    UIDockLayout.this.clearSplitterDragState();
                }

                return handled;
            }
        };

        /* Disable the handle entirely (no click, no resize cursor) when panel resizing is turned off. */
        handle.enabled(() -> BBSSettings.editorResizablePanels.get());

        handle.dragEnd(() ->
        {
            this.clearSplitterDragState();
            this.onSplitterDragEnd.run();
        });
        handle.reference(() -> this.getSplitterHandleReferencePosition(index))
            .referenceAxis(!this.splitterHandleInfos.get(index).horizontal, this.splitterHandleInfos.get(index).horizontal);
        handle.rendering((context) -> this.renderSplitter(context, index));
        this.applySplitterHandleBounds(handle, this.splitterHandleInfos.get(index));

        return handle;
    }

    private void beginSplitterDrag(int index, int mouseX, int mouseY)
    {
        if (!BBSSettings.editorResizablePanels.get() || index < 0 || index >= this.splitterHandleInfos.size())
        {
            this.clearSplitterDragState();
            return;
        }

        this.draggedSplitterIndices.clear();
        this.draggedSplitterIndices.add(index);
        boolean horizontal = this.splitterHandleInfos.get(index).horizontal;

        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            if (i == index || this.splitterHandleInfos.get(i).horizontal == horizontal)
            {
                continue;
            }

            UIDraggable handle = this.splitterHandles.get(i);

            if (this.isInsideSplitterIntersectionHitbox(handle, mouseX, mouseY))
            {
                this.draggedSplitterIndices.add(i);
            }
        }
    }

    private boolean isInsideSplitterIntersectionHitbox(UIDraggable handle, int mouseX, int mouseY)
    {
        int padding = SPLITTER_LINK_HITBOX_PADDING_PX;

        return mouseX >= handle.area.x - padding
            && mouseX < handle.area.ex() + padding
            && mouseY >= handle.area.y - padding
            && mouseY < handle.area.ey() + padding;
    }

    private void clearSplitterDragState()
    {
        this.draggedSplitterIndices.clear();
    }

    private void applySplitterDrag(int mouseX, int mouseY)
    {
        if (this.draggedSplitterIndices.isEmpty())
        {
            return;
        }

        BaseValue.edit(this.source.value(), (__) ->
        {
            List<EditorLayoutNode.SplitterNode> splitters = this.layoutSplittersForWrite();

            for (int draggedIndex : this.draggedSplitterIndices)
            {
                this.applySplitterRatioFromMouse(splitters, draggedIndex, mouseX, mouseY);
            }
        });

        this.setupFlex(true);
    }

    private void applySplitterRatioFromMouse(List<EditorLayoutNode.SplitterNode> splitters, int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= splitters.size())
        {
            return;
        }

        float ratio = this.getSplitterRatioFromMouse(index, mouseX, mouseY);

        if (ratio >= 0F)
        {
            splitters.get(index).setRatio(ratio);
        }
    }

    private float getSplitterRatioFromMouse(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return -1F;
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int ex = this.area.x;
        int ey = this.area.y;
        int ew = Math.max(1, this.area.w);
        int eh = Math.max(1, this.area.h);
        float ratio = info.horizontal
            ? (mouseY - (ey + info.py * eh)) / (info.ph * eh)
            : (mouseX - (ex + info.px * ew)) / (info.pw * ew);

        return MathUtils.clamp(ratio, 0.05F, 0.95F);
    }

    private Vector2i getSplitterHandleReferencePosition(int index)
    {
        List<EditorLayoutNode.SplitterNode> splitters = this.layoutSplitters();

        if (index < 0 || index >= this.splitterHandleInfos.size() || index >= splitters.size())
        {
            return new Vector2i(this.area.x, this.area.y);
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        float r = splitters.get(index).getRatio();
        int ex = this.area.x;
        int ey = this.area.y;
        int ew = Math.max(1, this.area.w);
        int eh = Math.max(1, this.area.h);
        int hx = ex + (int) ((info.px + (info.horizontal ? info.pw * 0.5F : r * info.pw)) * ew);
        int hy = ey + (int) ((info.py + (info.horizontal ? r * info.ph : info.ph * 0.5F)) * eh);

        return new Vector2i(hx, hy);
    }

    private void renderSplitter(UIContext context, int index)
    {
        if (index < 0 || index >= this.splitterHandles.size() || index >= this.splitterHandleInfos.size())
        {
            return;
        }

        UIDraggable splitter = this.splitterHandles.get(index);
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int lineColor = BBSSettings.primaryColor(Colors.A100);

        if ((splitter.isDragging() || splitter.area.isInside(context)) && BBSSettings.editorResizablePanels.get())
        {
            context.requestCursor(this.getSplitterCursor(index, context.mouseX, context.mouseY));
        }

        if (!splitter.isDragging() && !this.draggedSplitterIndices.contains(index))
        {
            return;
        }

        if (info.horizontal)
        {
            int cy = splitter.area.y + splitter.area.h / 2;
            int half = SPLITTER_HANDLE_LINE_PX / 2;
            context.batcher.box(splitter.area.x, cy - half, splitter.area.ex(), cy - half + SPLITTER_HANDLE_LINE_PX, lineColor);
        }
        else
        {
            int cx = splitter.area.x + splitter.area.w / 2;
            int half = SPLITTER_HANDLE_LINE_PX / 2;
            context.batcher.box(cx - half, splitter.area.y, cx - half + SPLITTER_HANDLE_LINE_PX, splitter.area.ey(), lineColor);
        }
    }

    private int getSplitterCursor(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return GLFW.GLFW_ARROW_CURSOR;
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);

        return this.isInsideSplitterIntersection(index, mouseX, mouseY)
            ? GLFW.GLFW_CROSSHAIR_CURSOR
            : info.horizontal
            ? GLFW.GLFW_VRESIZE_CURSOR
            : GLFW.GLFW_HRESIZE_CURSOR;
    }

    private boolean isInsideSplitterIntersection(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return false;
        }

        boolean horizontal = this.splitterHandleInfos.get(index).horizontal;

        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            if (i == index || this.splitterHandleInfos.get(i).horizontal == horizontal)
            {
                continue;
            }

            if (this.isInsideSplitterIntersectionHitbox(this.splitterHandles.get(i), mouseX, mouseY))
            {
                return true;
            }
        }

        return false;
    }

    /* Dock stacks */

    private void collectDockStacks(EditorLayoutNode node, float x, float y, float w, float h, List<DockStackInfo> out)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            String panelId = ((EditorLayoutNode.PanelNode) node).getPanelId();
            List<String> ids = new ArrayList<>();
            ids.add(panelId);
            out.add(new DockStackInfo(ids, panelId, x, y, w, h));

            return;
        }

        if (node instanceof EditorLayoutNode.StackNode)
        {
            EditorLayoutNode.StackNode stack = (EditorLayoutNode.StackNode) node;
            out.add(new DockStackInfo(new ArrayList<>(stack.getPanelIds()), stack.getActivePanelId(), x, y, w, h));

            return;
        }

        if (!(node instanceof EditorLayoutNode.SplitterNode))
        {
            return;
        }

        EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;

        if (splitter.isHorizontal())
        {
            float h1 = h * splitter.getRatio();

            this.collectDockStacks(splitter.getFirst(), x, y, w, h1, out);
            this.collectDockStacks(splitter.getSecond(), x, y + h1, w, h - h1, out);
        }
        else
        {
            float w1 = w * splitter.getRatio();

            this.collectDockStacks(splitter.getFirst(), x, y, w1, h, out);
            this.collectDockStacks(splitter.getSecond(), x + w1, y, w - w1, h, out);
        }
    }

    private float[] framelessStackRect(List<DockStackInfo> stackInfos)
    {
        if (this.framelessPanelId == null)
        {
            return null;
        }

        for (DockStackInfo info : stackInfos)
        {
            if (info.panelIds.contains(this.framelessPanelId))
            {
                return new float[] {info.x, info.y, info.w, info.h};
            }
        }

        return null;
    }

    /**
     * Per-edge gaps so seams between panels don't double up: a full gap where a side does not get
     * a matching half from the other side (the outer edge or the frameless panel), and a half gap
     * where a regular neighbour meets it. Returns left, top, right, bottom offsets in pixels.
     */
    private int[] panelGutter(DockStackInfo info, float[] frameless)
    {
        int half = PANEL_GAP_PX / 2;
        float x = info.x, y = info.y, w = info.w, h = info.h;

        boolean left = x <= PANEL_EDGE_EPS;
        boolean top = y <= PANEL_EDGE_EPS;
        boolean right = x + w >= 1F - PANEL_EDGE_EPS;
        boolean bottom = y + h >= 1F - PANEL_EDGE_EPS;

        if (frameless != null)
        {
            float vx = frameless[0], vy = frameless[1], vw = frameless[2], vh = frameless[3];
            boolean spanY = y < vy + vh - PANEL_EDGE_EPS && y + h > vy + PANEL_EDGE_EPS;
            boolean spanX = x < vx + vw - PANEL_EDGE_EPS && x + w > vx + PANEL_EDGE_EPS;

            left |= spanY && Math.abs(x - (vx + vw)) <= PANEL_EDGE_EPS;
            right |= spanY && Math.abs((x + w) - vx) <= PANEL_EDGE_EPS;
            top |= spanX && Math.abs(y - (vy + vh)) <= PANEL_EDGE_EPS;
            bottom |= spanX && Math.abs((y + h) - vy) <= PANEL_EDGE_EPS;
        }

        return new int[] {
            left ? PANEL_GAP_PX : half,
            top ? PANEL_GAP_PX : half,
            right ? PANEL_GAP_PX : half,
            bottom ? PANEL_GAP_PX : half
        };
    }

    private void applyPanelBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        this.dockStackByPanelId.clear();

        float[] frameless = this.framelessStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            int topOffset = info.isStacked() ? DOCK_STACK_TABS_HEIGHT_PX : 0;

            for (String panelId : info.panelIds)
            {
                UIElement panel = this.panelById.get(panelId);

                if (panel == null)
                {
                    continue;
                }

                int[] g = this.isFrameless(panelId) ? new int[4] : this.panelGutter(info, frameless);

                panel.relative(this)
                    .x(info.x, g[0])
                    .y(info.y, topOffset + g[1])
                    .w(info.w, -g[0] - g[2])
                    .h(info.h, -topOffset - g[1] - g[3]);
                this.dockStackByPanelId.put(panelId, info);
            }
        }
    }

    private void rebuildDockStackTabs(List<DockStackInfo> stackInfos)
    {
        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.removeFromParent();
        }

        this.dockStackTabs.clear();

        float[] frameless = this.framelessStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            if (!info.isStacked())
            {
                continue;
            }

            UIDockStackTabs tabs = new UIDockStackTabs(this);
            tabs.configure(info);
            int[] g = this.panelGutter(info, frameless);

            tabs.relative(this).x(info.x, g[0]).y(info.y, g[1]).w(info.w, -g[0] - g[2]).h(DOCK_STACK_TABS_HEIGHT_PX);
            this.dockStackTabs.add(tabs);
            this.add(tabs);
        }
    }

    private boolean updateDockStackTabsBoundsOnly(List<DockStackInfo> stackInfos)
    {
        List<DockStackInfo> stackedInfos = new ArrayList<>();

        for (DockStackInfo info : stackInfos)
        {
            if (info.isStacked())
            {
                stackedInfos.add(info);
            }
        }

        if (stackedInfos.size() != this.dockStackTabs.size())
        {
            return false;
        }

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            if (!this.dockStackTabs.get(i).matches(stackedInfos.get(i)))
            {
                return false;
            }
        }

        float[] frameless = this.framelessStackRect(stackInfos);

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);

            tabs.configure(info);
            int[] g = this.panelGutter(info, frameless);

            tabs.relative(this).x(info.x, g[0]).y(info.y, g[1]).w(info.w, -g[0] - g[2]).h(DOCK_STACK_TABS_HEIGHT_PX);
        }

        return true;
    }

    private void applyDragHandleBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.setVisible(false);
        }

        int editorHeight = Math.max(1, this.area.h);
        float[] frameless = this.framelessStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            UIDraggable handle = this.dragHandlesById.get(info.activePanelId);

            if (handle == null)
            {
                continue;
            }

            float tabsOffset = info.isStacked() ? (float) DOCK_STACK_TABS_HEIGHT_PX / editorHeight : 0F;
            int[] g = this.isFrameless(info.activePanelId) ? new int[4] : this.panelGutter(info, frameless);

            handle.relative(this)
                .x(info.x, g[0])
                .y(info.y + tabsOffset + DRAG_HANDLE_TOP_OFFSET_NORM, g[1])
                .w(info.w, -g[0] - g[2])
                .h(DRAG_HANDLE_HEIGHT_NORM);
            handle.setVisible(!this.layoutLocked);
        }
    }

    /* Panel drag-to-dock */

    private void clearPanelDragState()
    {
        this.draggingPanelId = null;
        this.dropTargetPanelId = null;
        this.dropTargetZone = DROP_ZONE_CENTER;
    }

    private void applyPanelDropResult(String dragId, String targetId, int zone)
    {
        EditorLayoutNode root = this.layoutRoot();
        EditorLayoutNode newRoot = zone == DROP_ZONE_CENTER
            ? EditorLayoutNode.copyWithInsertStackAt(root, targetId, dragId)
            : EditorLayoutNode.copyWithInsertSplitAt(root, targetId, dragId, zone);

        if (newRoot != null && newRoot != root)
        {
            this.setLayoutRoot(newRoot);
            this.setupFlex(true);
        }
    }

    private UIDraggable createPanelDragHandle(String panelId)
    {
        UIDraggable handle = new UIDraggable((context) ->
        {
            if (this.draggingPanelId == null)
            {
                this.draggingPanelId = panelId;
            }

            this.dropTargetPanelId = null;
            this.dropTargetZone = DROP_ZONE_CENTER;

            for (UIDockStackTabs tabs : this.dockStackTabs)
            {
                if (tabs.isVisible() && tabs.area.isInside(context.mouseX, context.mouseY))
                {
                    String targetPanelId = tabs.getPanelIdAt(context.mouseX);

                    if (targetPanelId != null)
                    {
                        this.dropTargetPanelId = targetPanelId;
                        this.dropTargetZone = DROP_ZONE_CENTER;

                        return;
                    }

                    break;
                }
            }

            for (Map.Entry<String, UIElement> e : this.panelById.entrySet())
            {
                if (!e.getValue().isVisible())
                {
                    continue;
                }

                if (e.getValue().area.isInside(context.mouseX, context.mouseY))
                {
                    this.dropTargetPanelId = e.getKey();
                    this.dropTargetZone = this.computeDropZone(e.getValue().area, context.mouseX, context.mouseY);
                    break;
                }
            }
        });

        handle.dragEnd(() ->
        {
            if (this.draggingPanelId == null || this.dropTargetPanelId == null || this.draggingPanelId.equals(this.dropTargetPanelId))
            {
                this.clearPanelDragState();
                return;
            }

            this.applyPanelDropResult(this.draggingPanelId, this.dropTargetPanelId, this.dropTargetZone);
            this.clearPanelDragState();
        });
        handle.hoverOnly().cursors(GLFW.GLFW_HAND_CURSOR, GLFW.GLFW_HAND_CURSOR).rendering((context) -> this.renderPanelDragHandle(context, handle));

        return handle;
    }

    private void renderPanelDragHandle(UIContext context, UIDraggable handle)
    {
        boolean active = handle.area.isInside(context) || handle.isDragging();
        int color = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.6F);
        int cx = handle.area.mx();
        int cy = handle.area.y + handle.area.h / 2 + 4;
        context.batcher.icon(Icons.ALL_DIRECTIONS, color, cx, cy, 0.5F, 0.5F);
    }

    private int computeDropZone(Area area, int mouseX, int mouseY)
    {
        int ax = area.x;
        int ay = area.y;
        int aw = area.w;
        int ah = area.h;
        float nx = aw <= 0 ? 0.5F : (mouseX - ax) / (float) aw;
        float ny = ah <= 0 ? 0.5F : (mouseY - ay) / (float) ah;

        if (nx < DROP_EDGE_MARGIN)
        {
            return EditorLayoutNode.EDGE_LEFT;
        }

        if (nx > 1F - DROP_EDGE_MARGIN)
        {
            return EditorLayoutNode.EDGE_RIGHT;
        }

        if (ny < DROP_EDGE_MARGIN)
        {
            return EditorLayoutNode.EDGE_TOP;
        }

        if (ny > 1F - DROP_EDGE_MARGIN)
        {
            return EditorLayoutNode.EDGE_BOTTOM;
        }

        return DROP_ZONE_CENTER;
    }

    /* Rendering */

    private boolean isFrameless(String panelId)
    {
        return this.framelessPanelId != null && this.framelessPanelId.equals(panelId);
    }

    private void renderPanelSurfaces(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.baseSurface());

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            UIElement panel = entry.getValue();

            if (panel.isVisible() && !this.isFrameless(entry.getKey()))
            {
                panel.area.render(context.batcher, BBSSettings.deepSurface());
            }
        }
    }

    private void renderPanelBorders(UIContext context)
    {
        if (!BBSSettings.interfaceShadows.get())
        {
            return;
        }

        int fade = Colors.setA(Colors.A100, 0F);

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            UIElement panel = entry.getValue();

            if (!panel.isVisible() || this.isFrameless(entry.getKey()))
            {
                continue;
            }

            Area a = panel.area;

            context.batcher.gradientVBox(a.x, a.y, a.ex(), a.y + 4, Colors.A25, fade);
            context.batcher.gradientVBox(a.x, a.ey() - 4, a.ex(), a.ey(), fade, Colors.A25);
            context.batcher.gradientHBox(a.x, a.y, a.x + 4, a.ey(), Colors.A25, fade);
            context.batcher.gradientHBox(a.ex() - 4, a.y, a.ex(), a.ey(), fade, Colors.A25);
        }
    }

    private void renderDropZoneHighlight(UIContext context)
    {
        if (this.layoutLocked || this.draggingPanelId == null || this.dropTargetPanelId == null)
        {
            return;
        }

        UIElement target = this.panelById.get(this.dropTargetPanelId);

        if (target == null)
        {
            return;
        }

        Area a = target.area;
        int border = BBSSettings.primaryColor(Colors.A50);
        int fill = BBSSettings.primaryColor(Colors.A25);

        if (this.dropTargetZone == DROP_ZONE_CENTER)
        {
            this.renderDropZoneRect(context, a, border, fill);
            return;
        }

        float m = DROP_EDGE_MARGIN;
        int strip = 2;

        switch (this.dropTargetZone)
        {
            case EditorLayoutNode.EDGE_LEFT:
                context.batcher.box(a.x, a.y, a.x + (int) (a.w * m), a.ey(), fill);
                context.batcher.box(a.x + (int) (a.w * m) - strip, a.y, a.x + (int) (a.w * m) + strip, a.ey(), border);
                break;
            case EditorLayoutNode.EDGE_RIGHT:
                context.batcher.box(a.ex() - (int) (a.w * m), a.y, a.ex(), a.ey(), fill);
                context.batcher.box(a.ex() - (int) (a.w * m) - strip, a.y, a.ex() - (int) (a.w * m) + strip, a.ey(), border);
                break;
            case EditorLayoutNode.EDGE_TOP:
                context.batcher.box(a.x, a.y, a.ex(), a.y + (int) (a.h * m), fill);
                context.batcher.box(a.x, a.y + (int) (a.h * m) - strip, a.ex(), a.y + (int) (a.h * m) + strip, border);
                break;
            case EditorLayoutNode.EDGE_BOTTOM:
                context.batcher.box(a.x, a.ey() - (int) (a.h * m), a.ex(), a.ey(), fill);
                context.batcher.box(a.x, a.ey() - (int) (a.h * m) - strip, a.ex(), a.ey() - (int) (a.h * m) + strip, border);
                break;
            default:
                this.renderDropZoneRect(context, a, border, fill);
                break;
        }
    }

    private void renderDropZoneRect(UIContext context, Area a, int border, int fill)
    {
        context.batcher.box(a.x, a.y, a.ex(), a.ey(), fill);
        int t = 2;
        context.batcher.box(a.x, a.y, a.ex(), a.y + t, border);
        context.batcher.box(a.x, a.ey() - t, a.ex(), a.ey(), border);
        context.batcher.box(a.x, a.y, a.x + t, a.ey(), border);
        context.batcher.box(a.ex() - t, a.y, a.ex(), a.ey(), border);
    }

    /* Helper types */

    private static class DockStackInfo
    {
        public final List<String> panelIds;
        public final String activePanelId;
        public final float x;
        public final float y;
        public final float w;
        public final float h;

        public DockStackInfo(List<String> panelIds, String activePanelId, float x, float y, float w, float h)
        {
            this.panelIds = panelIds;
            this.activePanelId = activePanelId;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean isStacked()
        {
            return this.panelIds.size() > 1;
        }

        public String getAnchorPanelId()
        {
            return this.panelIds.isEmpty() ? "" : this.panelIds.get(0);
        }
    }

    private static class UIDockStackTabs extends UIElement
    {
        private final UIDockLayout layout;
        private String anchorPanelId = "";
        private final List<String> panelIds = new ArrayList<>();
        private String activePanelId;

        public UIDockStackTabs(UIDockLayout layout)
        {
            this.layout = layout;
        }

        public void configure(DockStackInfo info)
        {
            this.anchorPanelId = info.getAnchorPanelId();
            this.panelIds.clear();
            this.panelIds.addAll(info.panelIds);
            this.activePanelId = info.activePanelId;
            this.setVisible(info.isStacked());
        }

        public boolean matches(DockStackInfo info)
        {
            return this.anchorPanelId.equals(info.getAnchorPanelId()) && this.panelIds.equals(info.panelIds);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.isVisible() || context.mouseButton != 0 || !this.area.isInside(context) || this.panelIds.isEmpty())
            {
                return super.subMouseClicked(context);
            }

            int index = this.getTabIndex(context.mouseX);

            if (index >= 0 && index < this.panelIds.size())
            {
                this.layout.activateDockStackTab(this.anchorPanelId, this.panelIds.get(index));

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            if (!this.isVisible() || this.panelIds.isEmpty())
            {
                return;
            }

            if (this.area.isInside(context))
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            int tabSize = this.getTabSize();
            int y = this.area.y;
            int ey = this.area.ey();

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());

            for (int i = 0; i < this.panelIds.size(); i++)
            {
                int x = this.area.x + i * tabSize;

                if (x >= this.area.ex())
                {
                    break;
                }

                int ex = Math.min(this.area.ex(), x + tabSize);
                String panelId = this.panelIds.get(i);
                boolean active = panelId.equals(this.activePanelId);
                Icon icon = this.layout.getDockPanelIcon(panelId);

                if (active)
                {
                    Area.SHARED.set(x, y, ex - x, ey - y);
                    UIDashboardPanels.renderHighlight(context.batcher, Area.SHARED, Direction.BOTTOM);
                }

                context.batcher.icon(icon, Colors.WHITE, (x + ex) / 2, (y + ey) / 2, 0.5F, 0.5F);
            }

            super.render(context);
        }

        private int getTabSize()
        {
            return Math.max(1, this.area.h);
        }

        private int getTabIndex(int mouseX)
        {
            int index = (mouseX - this.area.x) / this.getTabSize();

            if (index < 0 || index >= this.panelIds.size())
            {
                return -1;
            }

            return index;
        }

        public String getPanelIdAt(int mouseX)
        {
            if (this.panelIds.isEmpty())
            {
                return this.anchorPanelId;
            }

            int index = this.getTabIndex(mouseX);

            if (index < 0)
            {
                return null;
            }

            return this.panelIds.get(index);
        }
    }
}
