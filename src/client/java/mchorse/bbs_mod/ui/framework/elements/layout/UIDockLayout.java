package mchorse.bbs_mod.ui.framework.elements.layout;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
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
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Reusable dockable-panel layout. Owns a set of registered panels and arranges them per an
 * {@link EditorLayoutNode} tree provided by an {@link ILayoutSource}: resizable splitters,
 * tab/stack grouping, lock toggle and reset, and drag-to-dock against either a single panel or
 * the whole layout depending on how close to the dock's rim the panel is dropped.
 *
 * <p>Panels are registered with {@link #addPanel} and become direct children of this element.
 * Film- or particle-specific behavior (which panels exist, default tree, frameless preview,
 * data gating, follow-up visibility) is supplied as configuration so a single implementation
 * serves both editors.
 */
public class UIDockLayout extends UIElement
{
    /** Space a panel's content leaves at the top while unlocked; the drag strip fills all of it. */
    private static final int DRAG_STRIP_HEIGHT_PX = 20;
    private static final int SPLITTER_HANDLE_PX = 14;
    private static final int SPLITTER_HANDLE_LINE_PX = 1;
    private static final int SPLITTER_LINK_HITBOX_PADDING_PX = 8;
    private static final int DROP_ZONE_CENTER = -1;
    /** Outermost band of the dock: dropping here splits against the whole layout, not one panel. */
    private static final int DROP_EDITOR_EDGE_PX = 16;
    /** Edge band of a panel, in pixels so the gesture feels the same on a narrow and a wide panel. */
    private static final int DROP_PANEL_EDGE_PX = 48;
    /** ...but never more than this share of the panel, so the centre stays reachable when it is tiny. */
    private static final float DROP_PANEL_EDGE_MAX = 0.25F;
    /** Share a panel takes when dropped against the whole layout: a side column, not a half. */
    private static final float DROP_ROOT_RATIO = 0.25F;
    /** Solid outline on the highlighted edge; a wash alone would sink into the 3D viewport. */
    private static final int DROP_OUTLINE_PX = 2;
    /** Share of the target the highlight covers. It marks the zone, not the size the panel ends up. */
    private static final float DROP_HIGHLIGHT_RATIO = 0.2F;
    private static final int DOCK_STACK_TABS_HEIGHT_PX = 20;
    /** Splitter drags stop where a side would become too small to use, measured in pixels. */
    private static final int MIN_PANEL_SIZE_PX = 80;
    /** Movement (px, manhattan) before a pressed tab turns into a panel drag instead of a click. */
    private static final int DRAG_START_THRESHOLD_PX = 4;
    /** Dwell time over another stack's tab, mid-drag, before that tab is flipped open. */
    private static final long SPRING_LOAD_DELAY_MS = 500;
    private static final int LAYOUT_UNDO_CAP = 32;
    /** Two clicks on a seam within this window even the split back out. */
    private static final long SPLITTER_DOUBLE_CLICK_MS = 300;
    private static final int PANEL_GAP_PX = 4;
    private static final float PANEL_EDGE_EPS = 0.001F;
    /** Normalized handle thickness, so horizontal and vertical handles have comparable grab size. */
    private static final float SPLITTER_HANDLE_THICKNESS_NORM = 0.02F;
    /** Shared zero gutter for the frameless panel, which is flush with its slot. */
    private static final int[] NO_GUTTER = new int[4];

    private final Map<String, UIDockSlot> slotById = new LinkedHashMap<>();
    private final Map<String, Icon> iconById = new HashMap<>();
    private final Map<String, IKey> labelById = new HashMap<>();
    private final List<UIDraggable> splitterHandles = new ArrayList<>();
    private final List<SplitterHandleInfo> splitterHandleInfos = new ArrayList<>();
    private final List<UIDockStackTabs> dockStackTabs = new ArrayList<>();
    private final Map<String, DockStackInfo> dockStackByPanelId = new HashMap<>();
    private final List<Integer> draggedSplitterIndices = new ArrayList<>();
    /** Undo history of structural layout changes; snapshots are cheap because the tree is immutable. */
    private final List<LayoutSnapshot> layoutUndo = new ArrayList<>();
    /** The tree as it stood when a splitter drag began, pushed as one undo entry at drag end. */
    private EditorLayoutNode splitterDragUndoRoot;
    private int lastSplitterClickIndex = -1;
    private long lastSplitterClickTime;

    private final UIRenderable canvas = new UIRenderable(this::renderCanvas);
    private final UIRenderable dropHighlight = new UIRenderable(this::renderDropZoneHighlight);

    private boolean layoutLocked = true;
    private String draggingPanelId;
    /** Panel the drag would land on, or null when {@link #dropTargetIsRoot} aims at the whole layout. */
    private String dropTargetPanelId;
    private boolean dropTargetIsRoot;
    private int dropTargetZone = DROP_ZONE_CENTER;
    /** Tab strip and tab the drag is over, for the insertion caret between tabs. */
    private UIDockStackTabs dropTargetTabStrip;
    private int dropTargetTabIndex = -1;
    /** Session-only: which panel is blown up to the whole dock; the stored tree is untouched. */
    private String maximizedPanelId;
    /* A pressed tab is a click until it moves DRAG_START_THRESHOLD_PX, then it is a panel drag. */
    private String tabPressPanelId;
    private int tabPressX;
    private int tabPressY;
    private boolean dragFromTab;
    /** Set by Esc: the in-flight drag is dead, ignore it until the mouse is released. */
    private boolean panelDragCancelled;
    private String springTabPanelId;
    private long springTabSince;
    /** Spring-load fires from inside a render pass, so the actual flip waits for the next one. */
    private String pendingSpringPanelId;

    /* Configuration */
    private ILayoutSource source;
    private String framelessPanelId;
    private Supplier<Boolean> gate = () -> true;
    private Runnable onChanged = () -> {};
    private Runnable onLayoutSettled = () -> {};
    private UnaryOperator<EditorLayoutNode> ensureFn = UnaryOperator.identity();

    /* Configuration setters */

    public UIDockLayout source(ILayoutSource source)
    {
        this.source = source;

        return this;
    }

    /** Initial lock state, e.g. restored from settings; the default is locked. */
    public UIDockLayout locked(boolean locked)
    {
        this.layoutLocked = locked;

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

    /**
     * Run once the layout has settled into new bounds: after a drop, a maximize, an undo, a tab
     * switch or the end of a splitter drag &mdash; but not on every frame of that drag. Hosts use
     * it for anything that has to follow panel sizes, such as the auto-sized preview.
     */
    public UIDockLayout onLayoutSettled(Runnable onLayoutSettled)
    {
        this.onLayoutSettled = onLayoutSettled;

        return this;
    }

    /**
     * Preferred placement for panels missing from a loaded tree. Runs before the generic backstop,
     * which appends whatever the hook did not place, so a hook only has to describe the cases it
     * cares about.
     */
    public UIDockLayout ensure(UnaryOperator<EditorLayoutNode> ensureFn)
    {
        this.ensureFn = ensureFn;

        return this;
    }

    /**
     * Register a panel. The panel becomes a direct child of this element and is arranged by the
     * layout. Call {@link #mount()} once after registering all panels.
     */
    public UIDockLayout addPanel(String id, UIElement panel, Icon icon, IKey label)
    {
        this.iconById.put(id, icon == null ? Icons.FILE : icon);
        this.labelById.put(id, label == null ? IKey.EMPTY : label);
        this.slotById.put(id, new UIDockSlot(this, id, panel));

        return this;
    }

    /** Add all children in z-order and run the first layout pass. Call after {@link #addPanel}s. */
    public void mount()
    {
        this.add(this.canvas);

        for (UIDockSlot slot : this.slotById.values())
        {
            this.add(slot);
        }

        this.add(this.dropHighlight);
        this.setupFlex(false);
    }

    /**
     * Space the drag strip occupies at the top of an unlocked panel. Panel content that would sit
     * underneath it has to be pushed down by this much.
     */
    public static int dragStripHeightPx()
    {
        return DRAG_STRIP_HEIGHT_PX;
    }

    public UIElement getPanel(String id)
    {
        UIDockSlot slot = this.slotById.get(id);

        return slot == null ? null : slot.panel;
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

    public boolean isAnySplitterDragging()
    {
        for (UIDraggable handle : this.splitterHandles)
        {
            if (handle.isDragging())
            {
                return true;
            }
        }

        return false;
    }

    /** Re-apply panel/handle/tab visibility, e.g. after the host's gate condition changed. */
    public void refreshVisibility()
    {
        this.updateTabVisibility();
    }

    private Icon getDockPanelIcon(String panelId)
    {
        return this.iconById.getOrDefault(panelId, Icons.FILE);
    }

    public IKey getPanelLabel(String panelId)
    {
        return this.labelById.getOrDefault(panelId, IKey.EMPTY);
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

    /* Public actions */

    /** Full re-read for a source switch: drag state, undo history and maximize don't carry over. */
    public void refresh()
    {
        this.layoutUndo.clear();
        this.maximizedPanelId = null;
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
        this.pushLayoutUndo(this.layoutRoot());
        this.maximizedPanelId = null;
        this.source.setHiddenPanels(new HashSet<>());
        this.setLayoutRoot(this.source.getDefault());
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupFlex(true);
    }

    /** Current layout tree (with all required panels ensured), e.g. for serializing into a preset. */
    public EditorLayoutNode getLayoutRoot()
    {
        return this.ensureLayoutPanels(this.layoutRoot());
    }

    public void applyLayoutRoot(EditorLayoutNode root)
    {
        if (root != null)
        {
            this.pushLayoutUndo(this.layoutRoot());
            this.maximizedPanelId = null;
            this.setLayoutRoot(root);
            this.setupFlex(true);
        }
    }

    /** Steps the layout back to how it stood before the last structural change. */
    public boolean undoLayout()
    {
        if (this.layoutUndo.isEmpty())
        {
            return false;
        }

        LayoutSnapshot snapshot = this.layoutUndo.remove(this.layoutUndo.size() - 1);

        this.maximizedPanelId = null;
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.source.setHiddenPanels(snapshot.hidden);
        this.setLayoutRoot(snapshot.root);
        this.setupFlex(true);

        return true;
    }

    /** Blow the hovered panel up to the whole dock, or restore if one already is. */
    public boolean toggleMaximizeUnderCursor()
    {
        UIContext context = this.getContext();

        if (context == null || !this.gate.get())
        {
            return false;
        }

        if (this.maximizedPanelId != null)
        {
            this.toggleMaximizePanel(this.maximizedPanelId);

            return true;
        }

        for (Map.Entry<String, UIDockSlot> entry : this.slotById.entrySet())
        {
            UIDockSlot slot = entry.getValue();

            if (slot.isVisible() && slot.area.isInside(context.mouseX, context.mouseY))
            {
                this.toggleMaximizePanel(entry.getKey());

                return true;
            }
        }

        return false;
    }

    private void toggleMaximizePanel(String panelId)
    {
        this.maximizedPanelId = panelId.equals(this.maximizedPanelId) ? null : panelId;
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupFlex(true);
    }

    private void pushLayoutUndo(EditorLayoutNode root)
    {
        this.layoutUndo.add(new LayoutSnapshot(root, this.source.getHiddenPanels()));

        while (this.layoutUndo.size() > LAYOUT_UNDO_CAP)
        {
            this.layoutUndo.remove(0);
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

        for (Map.Entry<String, UIDockSlot> entry : this.slotById.entrySet())
        {
            UIDockSlot slot = entry.getValue();

            if (!slot.isVisible() || !slot.area.isInside(context.mouseX, context.mouseY))
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

    /** Drop what this dock cannot show, apply the host's placement hints, then backstop the rest. */
    private EditorLayoutNode ensureLayoutPanels(EditorLayoutNode root)
    {
        EditorLayoutNode out = this.ensureRegisteredPanels(this.ensureFn.apply(this.pruneUnknownPanels(root)));

        this.reconcileHiddenPanels(out);

        return out;
    }

    /**
     * A hidden panel that made it back into the tree anyway — through a preset or an older save —
     * is visibly there, so the hidden flag has to yield, or the panels menu would lie about it.
     */
    private void reconcileHiddenPanels(EditorLayoutNode root)
    {
        Set<String> hidden = this.source.getHiddenPanels();

        if (hidden.isEmpty())
        {
            return;
        }

        HashSet<String> present = new HashSet<>();

        EditorLayoutNode.collectPanelIds(root, present);

        if (hidden.removeAll(present))
        {
            this.source.setHiddenPanels(hidden);
        }
    }

    /**
     * Panel ids with no registered panel — a layout from another editor, or one renamed since it was
     * saved — would otherwise keep their share of the space as a hole that nothing can be dropped
     * into and only a reset can clear.
     */
    private EditorLayoutNode pruneUnknownPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();

        EditorLayoutNode.collectPanelIds(root, ids);

        EditorLayoutNode out = root;

        for (String id : ids)
        {
            if (!this.slotById.containsKey(id))
            {
                out = EditorLayoutNode.copyWithRemovedPanel(out, id);
            }
        }

        return out;
    }

    private EditorLayoutNode ensureRegisteredPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();
        EditorLayoutNode.collectPanelIds(root, ids);

        Set<String> hidden = this.source.getHiddenPanels();
        EditorLayoutNode out = root;

        for (String id : this.slotById.keySet())
        {
            if (!ids.contains(id) && !hidden.contains(id))
            {
                /* Root-level append: a missing panel comes back as a side column, not as half of
                 * whatever leaf happened to be first in the tree. */
                out = EditorLayoutNode.copyWithInsertSplitAtRoot(out, id, EditorLayoutNode.EDGE_RIGHT, DROP_ROOT_RATIO);
            }
        }

        return out;
    }

    public void setupFlex(boolean resize)
    {
        EditorLayoutNode originalRoot = this.layoutRoot();
        EditorLayoutNode root = this.ensureLayoutPanels(originalRoot);

        if (root != originalRoot)
        {
            this.setLayoutRoot(root);
        }

        /* While a panel is maximized the pass runs on a one-panel tree; the stored layout stays intact. */
        LayoutPass pass = this.computeLayoutPass(this.effectiveLayoutTree(root));
        /* Same splitter count means the existing handle elements still map onto the new tree
         * one-for-one; only their bounds and the splitter each one drives have to be refreshed. */
        boolean reuseHandles = resize && pass.handles.size() == this.splitterHandles.size();

        this.splitterHandleInfos.clear();
        this.splitterHandleInfos.addAll(pass.handles);

        if (!reuseHandles)
        {
            this.clearSplitterDragState();

            for (UIDraggable handle : this.splitterHandles)
            {
                handle.removeFromParent();
            }

            this.splitterHandles.clear();

            for (int i = 0; i < pass.handles.size(); i++)
            {
                UIDraggable handle = this.createSplitterHandle(i);

                this.splitterHandles.add(handle);
                this.addBefore(this.dropHighlight, handle);
            }
        }

        this.applyPanelBoundsFromStacks(pass.slots);

        if (!reuseHandles || !this.updateDockStackTabsBoundsOnly(pass.slots))
        {
            this.rebuildDockStackTabs(pass.slots);
        }

        this.syncSplitterHandleBounds();
        this.updateTabVisibility();

        if (resize)
        {
            this.resize();

            /* Mid-drag the bounds change every frame; the host only wants the settled result. */
            if (this.draggedSplitterIndices.isEmpty())
            {
                this.onLayoutSettled.run();
            }
        }
    }

    /**
     * Walks the tree once and produces everything the pass needs: a slot per panel/stack, a handle
     * per splitter, and each slot's gaps. Gaps come last because they depend on where the frameless
     * panel landed, which is only known once every slot has a rectangle.
     */
    private LayoutPass computeLayoutPass(EditorLayoutNode root)
    {
        LayoutPass pass = new LayoutPass();

        this.collectLayout(root, 0F, 0F, 1F, 1F, pass);

        float[] frameless = null;

        if (this.framelessPanelId != null)
        {
            for (DockStackInfo slot : pass.slots)
            {
                if (slot.panelIds.contains(this.framelessPanelId))
                {
                    frameless = new float[] {slot.x, slot.y, slot.w, slot.h};

                    break;
                }
            }
        }

        for (DockStackInfo slot : pass.slots)
        {
            slot.gutter = this.panelGutter(slot, frameless);
        }

        return pass;
    }

    private void collectLayout(EditorLayoutNode node, float x, float y, float w, float h, LayoutPass out)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            String panelId = ((EditorLayoutNode.PanelNode) node).getPanelId();
            List<String> ids = new ArrayList<>();

            ids.add(panelId);
            out.slots.add(new DockStackInfo(ids, panelId, x, y, w, h));

            return;
        }

        if (node instanceof EditorLayoutNode.StackNode)
        {
            EditorLayoutNode.StackNode stack = (EditorLayoutNode.StackNode) node;

            out.slots.add(new DockStackInfo(new ArrayList<>(stack.getPanelIds()), stack.getActivePanelId(), x, y, w, h));

            return;
        }

        if (!(node instanceof EditorLayoutNode.SplitterNode))
        {
            return;
        }

        EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;
        float half = SPLITTER_HANDLE_THICKNESS_NORM * 0.5F;

        if (splitter.isHorizontal())
        {
            float h1 = h * splitter.getRatio();

            out.handles.add(new SplitterHandleInfo(splitter, x, y + h1 - half, w, SPLITTER_HANDLE_THICKNESS_NORM, x, y, w, h, true));
            this.collectLayout(splitter.getFirst(), x, y, w, h1, out);
            this.collectLayout(splitter.getSecond(), x, y + h1, w, h - h1, out);
        }
        else
        {
            float w1 = w * splitter.getRatio();

            out.handles.add(new SplitterHandleInfo(splitter, x + w1 - half, y, SPLITTER_HANDLE_THICKNESS_NORM, h, x, y, w, h, false));
            this.collectLayout(splitter.getFirst(), x, y, w1, h, out);
            this.collectLayout(splitter.getSecond(), x + w1, y, w - w1, h, out);
        }
    }

    private void updateTabVisibility()
    {
        boolean show = this.gate.get();

        for (Map.Entry<String, UIDockSlot> entry : this.slotById.entrySet())
        {
            String panelId = entry.getKey();
            UIDockSlot slot = entry.getValue();
            boolean active = this.isPanelActive(panelId);

            slot.setVisible(show && active);
            slot.dragHandle.setVisible(show && !this.layoutLocked && active);
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.setVisible(show);
        }

        this.onChanged.run();
    }

    /* Splitter handles */

    /**
     * The handle is a fixed-width strip centred on the seam, expressed as the seam's fraction plus
     * a pixel offset. Keeping the pixels in the flex rather than converting them against the current
     * area is what lets a window resize be handled by the flex pass alone.
     */
    private void applySplitterHandleBounds(UIDraggable handle, SplitterHandleInfo info)
    {
        int half = SPLITTER_HANDLE_PX / 2;

        /* Handles are reused across layout changes, so the drag axis has to follow the splitter the
         * handle currently stands for rather than the one it was created for. */
        handle.referenceAxis(!info.horizontal, info.horizontal);

        if (info.horizontal)
        {
            handle.relative(this).x(info.hx).y(info.hy + info.hh * 0.5F, -half).w(info.hw).h(SPLITTER_HANDLE_PX);
        }
        else
        {
            handle.relative(this).x(info.hx + info.hw * 0.5F, -half).y(info.hy).w(SPLITTER_HANDLE_PX).h(info.hh);
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
                if (context.mouseButton == 0 && this.area.isInside(context)
                    && BBSSettings.editorResizablePanels.get()
                    && UIDockLayout.this.consumeSplitterDoubleClick(index))
                {
                    return true;
                }

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
            if (this.splitterDragUndoRoot != null && this.splitterDragUndoRoot != this.layoutRoot())
            {
                this.pushLayoutUndo(this.splitterDragUndoRoot);
            }

            this.clearSplitterDragState();
            this.onLayoutSettled.run();
        });
        handle.reference(() -> this.getSplitterHandleReferencePosition(index));
        handle.rendering((context) -> this.renderSplitter(context, index));

        return handle;
    }

    /**
     * Double-clicking a seam evens its two sides out. Returns true when this click completed a pair,
     * in which case it must not also start a drag.
     */
    private boolean consumeSplitterDoubleClick(int index)
    {
        long now = System.currentTimeMillis();
        boolean paired = index == this.lastSplitterClickIndex && now - this.lastSplitterClickTime <= SPLITTER_DOUBLE_CLICK_MS;

        /* Reset rather than keep the index, so a third click starts a fresh pair. */
        this.lastSplitterClickIndex = paired ? -1 : index;
        this.lastSplitterClickTime = now;

        if (!paired || index < 0 || index >= this.splitterHandleInfos.size())
        {
            return false;
        }

        Map<EditorLayoutNode.SplitterNode, Float> ratios = new HashMap<>();

        ratios.put(this.splitterHandleInfos.get(index).node, EditorLayoutNode.SPLIT_RATIO);

        EditorLayoutNode root = this.layoutRoot();
        EditorLayoutNode next = EditorLayoutNode.copyWithSplitterRatios(root, ratios);

        if (next != root)
        {
            this.pushLayoutUndo(root);
            this.setLayoutRoot(next);
            this.setupFlex(true);
        }

        return true;
    }

    private void beginSplitterDrag(int index, int mouseX, int mouseY)
    {
        if (!BBSSettings.editorResizablePanels.get() || index < 0 || index >= this.splitterHandleInfos.size())
        {
            this.clearSplitterDragState();
            return;
        }

        this.splitterDragUndoRoot = this.layoutRoot();
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
        this.splitterDragUndoRoot = null;
    }

    /**
     * All dragged splitters are applied in one rebuild: they are identified by node, and rebuilding
     * the path to one of them would replace the nodes the others are still pointing at.
     */
    private void applySplitterDrag(int mouseX, int mouseY)
    {
        if (this.draggedSplitterIndices.isEmpty())
        {
            return;
        }

        Map<EditorLayoutNode.SplitterNode, Float> ratios = new HashMap<>();

        for (int draggedIndex : this.draggedSplitterIndices)
        {
            if (draggedIndex < 0 || draggedIndex >= this.splitterHandleInfos.size())
            {
                continue;
            }

            SplitterHandleInfo info = this.splitterHandleInfos.get(draggedIndex);

            ratios.put(info.node, this.getSplitterRatioFromMouse(info, mouseX, mouseY));
        }

        EditorLayoutNode root = this.layoutRoot();
        EditorLayoutNode next = EditorLayoutNode.copyWithSplitterRatios(root, ratios);

        if (next != root)
        {
            this.setLayoutRoot(next);
            this.setupFlex(true);
        }
    }

    private float getSplitterRatioFromMouse(SplitterHandleInfo info, int mouseX, int mouseY)
    {
        int ex = this.area.x;
        int ey = this.area.y;
        int ew = Math.max(1, this.area.w);
        int eh = Math.max(1, this.area.h);
        float ratio = info.horizontal
            ? (mouseY - (ey + info.py * eh)) / (info.ph * eh)
            : (mouseX - (ex + info.px * ew)) / (info.pw * ew);
        float lo = EditorLayoutNode.MIN_RATIO;
        float hi = EditorLayoutNode.MAX_RATIO;
        float lengthPx = info.horizontal ? info.ph * eh : info.pw * ew;
        float need = lengthPx > 0 ? MIN_PANEL_SIZE_PX / lengthPx : 1F;

        /* Keep both sides usable in pixels, not in shares; deep in the tree a share of a share can
         * shrink a panel to nothing. When the pair is too small even for that, the model's own
         * clamp is all that is left. */
        if (need <= 0.5F)
        {
            lo = Math.max(lo, need);
            hi = Math.min(hi, 1F - need);
        }

        return MathUtils.clamp(ratio, lo, hi);
    }

    private Vector2i getSplitterHandleReferencePosition(int index)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return new Vector2i(this.area.x, this.area.y);
        }

        SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        float r = info.node.getRatio();
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
        SplitterHandleInfo info = this.splitterHandleInfos.get(index);
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

        SplitterHandleInfo info = this.splitterHandleInfos.get(index);

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

        int inset = this.layoutLocked ? 0 : dragStripHeightPx();

        for (DockStackInfo info : stackInfos)
        {
            int topOffset = info.isStacked() ? DOCK_STACK_TABS_HEIGHT_PX : 0;

            for (String panelId : info.panelIds)
            {
                UIDockSlot slot = this.slotById.get(panelId);

                if (slot == null)
                {
                    continue;
                }

                int[] g = this.isFrameless(panelId) ? NO_GUTTER : info.gutter;

                slot.relative(this)
                    .x(info.x, g[0])
                    .y(info.y, topOffset + g[1])
                    .w(info.w, -g[0] - g[2])
                    .h(info.h, -topOffset - g[1] - g[3]);
                slot.setContentInset(inset);
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

        for (DockStackInfo info : stackInfos)
        {
            if (!info.isStacked())
            {
                continue;
            }

            UIDockStackTabs tabs = new UIDockStackTabs(this);
            int[] g = info.gutter;

            tabs.configure(info);

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

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);
            int[] g = info.gutter;

            tabs.configure(info);

            tabs.relative(this).x(info.x, g[0]).y(info.y, g[1]).w(info.w, -g[0] - g[2]).h(DOCK_STACK_TABS_HEIGHT_PX);
        }

        return true;
    }

    /* Panel drag-to-dock */

    private void clearPanelDragState()
    {
        this.draggingPanelId = null;
        this.tabPressPanelId = null;
        this.dragFromTab = false;
        this.panelDragCancelled = false;
        this.clearSpringLoad();
        this.clearDropTarget();
    }

    private void clearDropTarget()
    {
        this.dropTargetPanelId = null;
        this.dropTargetIsRoot = false;
        this.dropTargetZone = DROP_ZONE_CENTER;
        this.dropTargetTabStrip = null;
        this.dropTargetTabIndex = -1;
    }

    private boolean hasDropTarget()
    {
        return this.dropTargetIsRoot || this.dropTargetPanelId != null;
    }

    private void applyPanelDropResult(String dragId, String targetId, int zone)
    {
        EditorLayoutNode root = this.layoutRoot();
        EditorLayoutNode newRoot;

        if (targetId == null)
        {
            newRoot = EditorLayoutNode.copyWithInsertSplitAtRoot(root, dragId, zone, DROP_ROOT_RATIO);
        }
        else if (zone == DROP_ZONE_CENTER && Window.isShiftPressed())
        {
            /* Shift turns the stack drop into an exchange: both panels keep their stacks/splits. */
            newRoot = EditorLayoutNode.copyWithSwappedPanels(root, dragId, targetId);
        }
        else if (zone == DROP_ZONE_CENTER)
        {
            newRoot = EditorLayoutNode.copyWithInsertStackAt(root, targetId, dragId);
        }
        else
        {
            newRoot = EditorLayoutNode.copyWithInsertSplitAt(root, targetId, dragId, zone);
        }

        if (newRoot != null && newRoot != root)
        {
            this.pushLayoutUndo(root);
            this.setLayoutRoot(newRoot);
            this.setupFlex(true);
        }
    }

    private UIDraggable createPanelDragHandle(String panelId)
    {
        UIDraggable handle = new UIDraggable((context) ->
        {
            if (this.panelDragCancelled)
            {
                return;
            }

            if (this.draggingPanelId == null)
            {
                this.draggingPanelId = panelId;
            }

            this.updateDropTarget(context.mouseX, context.mouseY);
        });

        handle.dragEnd(this::finishPanelDrag);
        handle.hoverOnly().cursors(GLFW.GLFW_HAND_CURSOR, GLFW.GLFW_HAND_CURSOR).rendering((context) -> this.renderPanelDragHandle(context, handle));

        return handle;
    }

    /** Recomputes what the dragged panel would land on. Runs every frame while a drag is live. */
    private void updateDropTarget(int mouseX, int mouseY)
    {
        this.clearDropTarget();

        /* While maximized the tree on screen is not the tree being edited, so drops are disabled. */
        if (this.maximizedPanelId != null)
        {
            this.clearSpringLoad();

            return;
        }

        /* Tabs are the smallest target, so they win over the bands around them. */
        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            if (!tabs.isVisible() || !tabs.area.isInside(mouseX, mouseY))
            {
                continue;
            }

            int index = tabs.getTabIndex(mouseX);

            if (index >= 0)
            {
                String targetPanelId = tabs.panelIds.get(index);

                this.dropTargetPanelId = targetPanelId;
                this.dropTargetTabStrip = tabs;
                this.dropTargetTabIndex = index;
                this.updateSpringLoad(targetPanelId);

                return;
            }

            break;
        }

        this.clearSpringLoad();

        /* The dock's own rim comes next: right at the screen edge you dock against everything,
         * a little further in against the panel you are over. */
        int editorEdge = nearestEdge(this.area, mouseX, mouseY, DROP_EDITOR_EDGE_PX, DROP_EDITOR_EDGE_PX);

        if (editorEdge != DROP_ZONE_CENTER)
        {
            this.dropTargetIsRoot = true;
            this.dropTargetZone = editorEdge;

            return;
        }

        /* Slots never overlap, so the first hit is the only hit. */
        for (Map.Entry<String, UIDockSlot> e : this.slotById.entrySet())
        {
            UIDockSlot slot = e.getValue();

            if (slot.isVisible() && slot.area.isInside(mouseX, mouseY))
            {
                int zone = this.computeDropZone(slot.area, mouseX, mouseY);

                this.dropTargetPanelId = this.resolveEdgeDropTarget(e.getKey(), zone);
                this.dropTargetZone = zone;

                break;
            }
        }
    }

    /**
     * Dropping a stack's own tab onto an edge of the slot it already lives in means "pull it out and
     * put it on that side". The split has to be built against a panel that stays behind, otherwise
     * the target and the dragged panel are the same one and the drop is discarded as a no-op.
     */
    private String resolveEdgeDropTarget(String panelId, int zone)
    {
        if (zone == DROP_ZONE_CENTER || !panelId.equals(this.draggingPanelId))
        {
            return panelId;
        }

        DockStackInfo stack = this.dockStackByPanelId.get(panelId);

        if (stack == null || !stack.isStacked())
        {
            return panelId;
        }

        for (String id : stack.panelIds)
        {
            if (!id.equals(this.draggingPanelId))
            {
                return id;
            }
        }

        return panelId;
    }

    /** Dwelling on another stack's tab mid-drag flips to it, so covered panels can be aimed into. */
    private void updateSpringLoad(String tabPanelId)
    {
        if (tabPanelId.equals(this.draggingPanelId))
        {
            this.clearSpringLoad();

            return;
        }

        long now = System.currentTimeMillis();

        if (!tabPanelId.equals(this.springTabPanelId))
        {
            this.springTabPanelId = tabPanelId;
            this.springTabSince = now;

            return;
        }

        if (now - this.springTabSince >= SPRING_LOAD_DELAY_MS)
        {
            /* Deferred: this runs while the dock's children are being iterated, and flipping a tab
             * rebuilds the tab strips. */
            this.pendingSpringPanelId = tabPanelId;
            this.clearSpringLoad();
        }
    }

    private void clearSpringLoad()
    {
        this.springTabPanelId = null;
        this.springTabSince = 0L;
        this.pendingSpringPanelId = null;
    }

    private void finishPanelDrag()
    {
        boolean ontoItself = this.draggingPanelId != null && this.draggingPanelId.equals(this.dropTargetPanelId);

        if (!this.panelDragCancelled && this.draggingPanelId != null && this.hasDropTarget() && !ontoItself)
        {
            this.applyPanelDropResult(this.draggingPanelId, this.dropTargetPanelId, this.dropTargetZone);
        }

        this.clearPanelDragState();
    }

    /** Kills the in-flight drag; the cancelled flag mutes the drag until the mouse is released. */
    private void cancelPanelDrag()
    {
        this.draggingPanelId = null;
        this.tabPressPanelId = null;
        this.dragFromTab = false;
        this.panelDragCancelled = true;
        this.clearSpringLoad();
        this.clearDropTarget();
    }

    /* Dragging by a stack tab: press arms it, movement past the threshold starts the drag,
     * release either drops the panel or, if it never moved, activates the tab as a click. */

    private void onTabPressed(String panelId, int mouseX, int mouseY)
    {
        this.tabPressPanelId = panelId;
        this.tabPressX = mouseX;
        this.tabPressY = mouseY;
        this.panelDragCancelled = false;
    }

    private void onTabsReleased()
    {
        String pressed = this.tabPressPanelId;

        if (pressed == null)
        {
            /* A cancelled press keeps its flag until release; this is the release. */
            if (this.draggingPanelId == null)
            {
                this.panelDragCancelled = false;
            }

            return;
        }

        if (this.dragFromTab && this.draggingPanelId != null)
        {
            this.finishPanelDrag();

            return;
        }

        boolean cancelled = this.panelDragCancelled;

        this.tabPressPanelId = null;
        this.panelDragCancelled = false;

        if (!cancelled)
        {
            this.activateDockStackTab(pressed, pressed);
        }
    }

    /**
     * Runs at the top of {@link #render}, before the children are walked, so the layout rebuilds it
     * can trigger are safe here.
     */
    private void updateTabDrag(UIContext context)
    {
        if (this.tabPressPanelId == null || this.panelDragCancelled)
        {
            return;
        }

        /* The release event can miss us when the strip it started on was rebuilt mid-drag, which
         * would leave the press armed and turn the next mouse move into a phantom drag. */
        if (!Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            this.onTabsReleased();

            return;
        }

        if (this.draggingPanelId == null)
        {
            if (this.layoutLocked)
            {
                return;
            }

            int moved = Math.abs(context.mouseX - this.tabPressX) + Math.abs(context.mouseY - this.tabPressY);

            if (moved < DRAG_START_THRESHOLD_PX)
            {
                return;
            }

            this.draggingPanelId = this.tabPressPanelId;
            this.dragFromTab = true;
        }

        if (this.dragFromTab)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            this.updateDropTarget(context.mouseX, context.mouseY);
        }
    }

    /* Hiding and showing panels */

    private boolean canHidePanel(String panelId)
    {
        HashSet<String> present = new HashSet<>();

        EditorLayoutNode.collectPanelIds(this.layoutRoot(), present);

        return present.contains(panelId) && present.size() > 1;
    }

    private void hidePanel(String panelId)
    {
        if (!this.canHidePanel(panelId))
        {
            return;
        }

        EditorLayoutNode root = this.layoutRoot();

        this.pushLayoutUndo(root);

        Set<String> hidden = this.source.getHiddenPanels();

        hidden.add(panelId);
        this.source.setHiddenPanels(hidden);

        if (panelId.equals(this.maximizedPanelId))
        {
            this.maximizedPanelId = null;
        }

        this.setLayoutRoot(EditorLayoutNode.copyWithRemovedPanel(root, panelId));
        this.setupFlex(true);
    }

    private void showPanel(String panelId)
    {
        Set<String> hidden = this.source.getHiddenPanels();

        if (!hidden.remove(panelId))
        {
            return;
        }

        this.pushLayoutUndo(this.layoutRoot());
        this.source.setHiddenPanels(hidden);
        this.setupFlex(true);
    }

    /**
     * Menu entries that bring hidden panels back. Hosts surface these in their own menus too, so a
     * panel hidden while unlocked can still be recovered after the layout is locked again.
     */
    public void fillHiddenPanelsMenu(ContextMenuManager menu)
    {
        Set<String> hidden = this.source.getHiddenPanels();

        for (String id : this.slotById.keySet())
        {
            if (hidden.contains(id))
            {
                menu.action(Icons.VISIBLE, UIKeys.DOCK_SHOW.format(this.getPanelLabel(id).get()), () -> this.showPanel(id));
            }
        }
    }

    /** Right-click menu of a panel's drag strip: maximize, hide, bring hidden panels back. */
    private void fillSlotContextMenu(ContextMenuManager menu, String panelId)
    {
        if (this.layoutLocked || !this.gate.get())
        {
            return;
        }

        boolean maximized = panelId.equals(this.maximizedPanelId);

        menu.action(maximized ? Icons.MINIMIZE : Icons.MAXIMIZE, maximized ? UIKeys.DOCK_RESTORE : UIKeys.DOCK_MAXIMIZE, () -> this.toggleMaximizePanel(panelId));

        if (this.canHidePanel(panelId))
        {
            menu.action(Icons.INVISIBLE, UIKeys.DOCK_HIDE.format(this.getPanelLabel(panelId).get()), () -> this.hidePanel(panelId));
        }

        this.fillHiddenPanelsMenu(menu);
    }

    /** The tree the layout pass actually renders: the stored one, or a single maximized panel. */
    private EditorLayoutNode effectiveLayoutTree(EditorLayoutNode root)
    {
        if (this.maximizedPanelId != null
            && (!this.slotById.containsKey(this.maximizedPanelId) || this.source.getHiddenPanels().contains(this.maximizedPanelId)))
        {
            this.maximizedPanelId = null;
        }

        return this.maximizedPanelId == null ? root : new EditorLayoutNode.PanelNode(this.maximizedPanelId);
    }

    private boolean isSwapDrop()
    {
        return !this.dropTargetIsRoot
            && this.dropTargetPanelId != null
            && this.draggingPanelId != null
            && !this.draggingPanelId.equals(this.dropTargetPanelId)
            && Window.isShiftPressed();
    }

    private void renderPanelDragHandle(UIContext context, UIDraggable handle)
    {
        boolean active = handle.area.isInside(context) || handle.isDragging();
        int color = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.6F);
        context.batcher.icon(Icons.ALL_DIRECTIONS, color, handle.area.mx(), handle.area.my(), 0.5F, 0.5F);
    }

    private int computeDropZone(Area area, int mouseX, int mouseY)
    {
        return nearestEdge(area, mouseX, mouseY, panelEdgeBand(area.w), panelEdgeBand(area.h));
    }

    private static int panelEdgeBand(int size)
    {
        return Math.min(DROP_PANEL_EDGE_PX, (int) (size * DROP_PANEL_EDGE_MAX));
    }

    /**
     * The edge whose band the cursor is in, picked by distance rather than by which check runs
     * first &mdash; near a corner the closer edge is the one the user is aiming at.
     */
    private static int nearestEdge(Area area, int mouseX, int mouseY, int bandX, int bandY)
    {
        int left = mouseX - area.x;
        int right = area.ex() - 1 - mouseX;
        int top = mouseY - area.y;
        int bottom = area.ey() - 1 - mouseY;

        if (left < 0 || right < 0 || top < 0 || bottom < 0)
        {
            return DROP_ZONE_CENTER;
        }

        int zone = DROP_ZONE_CENTER;
        int best = Integer.MAX_VALUE;

        if (left < bandX && left < best)
        {
            best = left;
            zone = EditorLayoutNode.EDGE_LEFT;
        }

        if (right < bandX && right < best)
        {
            best = right;
            zone = EditorLayoutNode.EDGE_RIGHT;
        }

        if (top < bandY && top < best)
        {
            best = top;
            zone = EditorLayoutNode.EDGE_TOP;
        }

        if (bottom < bandY && bottom < best)
        {
            zone = EditorLayoutNode.EDGE_BOTTOM;
        }

        return zone;
    }

    /* Rendering */

    private boolean isFrameless(String panelId)
    {
        return this.framelessPanelId != null && this.framelessPanelId.equals(panelId);
    }

    /** The canvas behind the slots; each slot paints its own recessed surface and border. */
    private void renderCanvas(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.baseSurface());
    }

    @Override
    public void render(UIContext context)
    {
        if (this.pendingSpringPanelId != null)
        {
            String panelId = this.pendingSpringPanelId;

            this.pendingSpringPanelId = null;
            this.activateDockStackTab(panelId, panelId);
        }

        this.updateTabDrag(context);

        super.render(context);

        this.renderDragOverlay(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyCode() == GLFW.GLFW_KEY_ESCAPE && (this.draggingPanelId != null || this.tabPressPanelId != null))
        {
            this.cancelPanelDrag();

            return true;
        }

        return super.subKeyPressed(context);
    }

    /** Insertion caret between tabs plus the ghost of the dragged panel, on top of everything. */
    private void renderDragOverlay(UIContext context)
    {
        if (this.draggingPanelId == null || this.panelDragCancelled || this.maximizedPanelId != null)
        {
            return;
        }

        if (this.dropTargetTabStrip != null && this.dropTargetTabStrip.isVisible() && this.dropTargetTabIndex >= 0)
        {
            UIDockStackTabs strip = this.dropTargetTabStrip;
            int x = Math.min(strip.area.x + (this.dropTargetTabIndex + 1) * strip.getTabSize(), strip.area.ex() - 1);

            context.batcher.box(x - 1, strip.area.y, x + 1, strip.area.ey(), BBSSettings.primaryColor(Colors.A100));
        }

        String label = this.getPanelLabel(this.draggingPanelId).get();

        context.batcher.icon(this.getDockPanelIcon(this.draggingPanelId), Colors.WHITE, context.mouseX + 16, context.mouseY + 16, 0.5F, 0.5F);

        if (!label.isEmpty())
        {
            context.batcher.textCard(label, context.mouseX + 26, context.mouseY + 12);
        }
    }

    /**
     * Marks where the panel would land: a band along the edge it will dock to, densest at that edge
     * and thinning inwards, so it reads as the panel being pulled to that side while leaving the
     * content it passes over legible. Centre drops have no direction, so they get a feathered rim.
     */
    private void renderDropZoneHighlight(UIContext context)
    {
        if (this.layoutLocked || this.draggingPanelId == null || !this.hasDropTarget())
        {
            return;
        }

        Area a = this.area;

        if (!this.dropTargetIsRoot)
        {
            UIDockSlot target = this.slotById.get(this.dropTargetPanelId);

            if (target == null)
            {
                return;
            }

            a = target.area;
        }

        if (this.dropTargetZone != DROP_ZONE_CENTER)
        {
            this.renderDropEdge(context, a, this.dropTargetZone);

            return;
        }

        this.renderDropGlow(context, a);

        if (this.isSwapDrop())
        {
            UIDockSlot dragged = this.slotById.get(this.draggingPanelId);

            if (dragged != null && dragged.isVisible())
            {
                this.renderDropGlow(context, dragged.area);
            }

            context.batcher.icon(Icons.EXCHANGE, Colors.WHITE, a.mx(), a.my(), 0.5F, 0.5F);
        }
    }

    /** A band hugging the edge being docked to: solid line on the edge itself, fading inwards. */
    private void renderDropEdge(UIContext context, Area a, int zone)
    {
        int strong = BBSSettings.primaryColor(Colors.A50);
        int fade = BBSSettings.primaryColor(0);
        int line = BBSSettings.primaryColor(Colors.A100);
        int w = (int) (a.w * DROP_HIGHLIGHT_RATIO);
        int h = (int) (a.h * DROP_HIGHLIGHT_RATIO);

        switch (zone)
        {
            case EditorLayoutNode.EDGE_LEFT:
                context.batcher.gradientHBox(a.x, a.y, a.x + w, a.ey(), strong, fade);
                context.batcher.box(a.x, a.y, a.x + DROP_OUTLINE_PX, a.ey(), line);
                break;
            case EditorLayoutNode.EDGE_RIGHT:
                context.batcher.gradientHBox(a.ex() - w, a.y, a.ex(), a.ey(), fade, strong);
                context.batcher.box(a.ex() - DROP_OUTLINE_PX, a.y, a.ex(), a.ey(), line);
                break;
            case EditorLayoutNode.EDGE_TOP:
                context.batcher.gradientVBox(a.x, a.y, a.ex(), a.y + h, strong, fade);
                context.batcher.box(a.x, a.y, a.ex(), a.y + DROP_OUTLINE_PX, line);
                break;
            case EditorLayoutNode.EDGE_BOTTOM:
                context.batcher.gradientVBox(a.x, a.ey() - h, a.ex(), a.ey(), fade, strong);
                context.batcher.box(a.x, a.ey() - DROP_OUTLINE_PX, a.ex(), a.ey(), line);
                break;
            default:
                this.renderDropGlow(context, a);
                break;
        }
    }

    /**
     * A feathered rim around the whole area: the outline says where the panel goes, while the
     * middle stays clear so the content underneath is still recognisable.
     */
    private void renderDropGlow(UIContext context, Area a)
    {
        int strong = BBSSettings.primaryColor(Colors.A50);
        int fade = BBSSettings.primaryColor(0);
        int rim = BBSSettings.primaryColor(Colors.A100);
        /* Same share per axis as an edge drop, so both highlights feel like one family. */
        int dx = Math.max(1, Math.min((int) (a.w * DROP_HIGHLIGHT_RATIO), a.w / 2));
        int dy = Math.max(1, Math.min((int) (a.h * DROP_HIGHLIGHT_RATIO), a.h / 2));

        context.batcher.gradientVBox(a.x, a.y, a.ex(), a.y + dy, strong, fade);
        context.batcher.gradientVBox(a.x, a.ey() - dy, a.ex(), a.ey(), fade, strong);
        context.batcher.gradientHBox(a.x, a.y, a.x + dx, a.ey(), strong, fade);
        context.batcher.gradientHBox(a.ex() - dx, a.y, a.ex(), a.ey(), fade, strong);

        context.batcher.box(a.x, a.y, a.ex(), a.y + DROP_OUTLINE_PX, rim);
        context.batcher.box(a.x, a.ey() - DROP_OUTLINE_PX, a.ex(), a.ey(), rim);
        context.batcher.box(a.x, a.y, a.x + DROP_OUTLINE_PX, a.ey(), rim);
        context.batcher.box(a.ex() - DROP_OUTLINE_PX, a.y, a.ex(), a.ey(), rim);
    }

    /* Helper types */

    /**
     * The frame a panel sits in. Owns everything the dock draws around a panel &mdash; its surface,
     * its border and its drag handle &mdash; and insets the panel itself so the handle never covers
     * content. That inset is why hosts don't need to know the dock is unlocked.
     */
    private static class UIDockSlot extends UIElement
    {
        private final UIDockLayout layout;
        private final String panelId;
        private final UIElement panel;
        private final UIDraggable dragHandle;

        public UIDockSlot(UIDockLayout layout, String panelId, UIElement panel)
        {
            this.layout = layout;
            this.panelId = panelId;
            this.panel = panel;
            this.dragHandle = layout.createPanelDragHandle(panelId);

            panel.relative(this).x(0F).y(0F).w(1F).h(1F);
            this.dragHandle.relative(this).x(0F).y(0).w(1F).h(DRAG_STRIP_HEIGHT_PX);
            this.dragHandle.context((menu) -> layout.fillSlotContextMenu(menu, panelId));

            this.add(panel, this.dragHandle);
        }

        /** Push the panel down by the drag strip while the layout is unlocked. */
        public void setContentInset(int inset)
        {
            this.panel.y(0F, inset).h(1F, -inset);
        }

        public boolean isFramed()
        {
            return !this.layout.isFrameless(this.panelId);
        }

        @Override
        public void render(UIContext context)
        {
            if (this.isFramed())
            {
                this.area.render(context.batcher, BBSSettings.deepSurface());
            }

            super.render(context);

            /* After the children so the inset shadow shows even over panels that paint opaquely. */
            if (this.isFramed() && BBSSettings.interfaceShadows.get())
            {
                int fade = Colors.setA(Colors.A100, 0F);
                Area a = this.area;

                context.batcher.gradientVBox(a.x, a.y, a.ex(), a.y + 4, Colors.A25, fade);
                context.batcher.gradientVBox(a.x, a.ey() - 4, a.ex(), a.ey(), fade, Colors.A25);
                context.batcher.gradientHBox(a.x, a.y, a.x + 4, a.ey(), Colors.A25, fade);
                context.batcher.gradientHBox(a.ex() - 4, a.y, a.ex(), a.ey(), fade, Colors.A25);
            }
        }
    }

    /** One undo step: the tree plus the hidden set that went with it. */
    private static class LayoutSnapshot
    {
        public final EditorLayoutNode root;
        public final Set<String> hidden;

        public LayoutSnapshot(EditorLayoutNode root, Set<String> hidden)
        {
            this.root = root;
            this.hidden = hidden;
        }
    }

    /** Everything one walk of the tree produces. */
    private static class LayoutPass
    {
        public final List<DockStackInfo> slots = new ArrayList<>();
        public final List<SplitterHandleInfo> handles = new ArrayList<>();
    }

    /** One splitter handle: the node it drives, its normalized rect, and its parent's rect. */
    private static class SplitterHandleInfo
    {
        public final EditorLayoutNode.SplitterNode node;
        public final float hx, hy, hw, hh;
        public final float px, py, pw, ph;
        public final boolean horizontal;

        public SplitterHandleInfo(EditorLayoutNode.SplitterNode node, float hx, float hy, float hw, float hh, float px, float py, float pw, float ph, boolean horizontal)
        {
            this.node = node;
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

    private static class DockStackInfo
    {
        public final List<String> panelIds;
        public final String activePanelId;
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        /** Left, top, right, bottom gaps in pixels; filled in once the whole pass is known. */
        public int[] gutter = NO_GUTTER;

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
                /* Activation waits for the release: the same press may grow into a drag. */
                this.layout.onTabPressed(this.panelIds.get(index), context.mouseX, context.mouseY);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            this.layout.onTabsReleased();

            return super.subMouseReleased(context);
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
                    context.batcher.highlight(Area.SHARED, Direction.BOTTOM);
                }

                context.batcher.icon(icon, Colors.WHITE, (x + ex) / 2, (y + ey) / 2, 0.5F, 0.5F);
            }

            int hovered = this.area.isInside(context) ? this.getTabIndex(context.mouseX) : -1;

            if (hovered >= 0 && this.layout.draggingPanelId == null && this.layout.tabPressPanelId == null)
            {
                String label = this.layout.getPanelLabel(this.panelIds.get(hovered)).get();

                if (!label.isEmpty())
                {
                    int ty = this.area.y - 14;

                    context.batcher.textCard(label, context.mouseX + 6, ty < 2 ? this.area.ey() + 4 : ty);
                }
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

    }
}
