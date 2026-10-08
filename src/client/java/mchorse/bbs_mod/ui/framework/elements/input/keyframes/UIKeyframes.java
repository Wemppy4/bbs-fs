package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays.UIKeyframeStyleOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays.UITrackStyleOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.presets.PresetManager;

public class UIKeyframes extends UITimelineCanvas
{
    /** One control shared by the existing timeline toolbars and embedded camera editors. */
    public static UIIcon modeButton(Supplier<UIKeyframes> editor)
    {
        UIIcon button = new UIIcon(Icons.GRAPH, b ->
        {
            UIKeyframes view = editor.get();
            if (view != null) view.setGraphMode(!view.isEditing());
        });
        button.tooltip(UIKeys.KEYFRAMES_MODE_GRAPH);
        button.highlight(() -> editor.get() != null && editor.get().isEditing(), Direction.BOTTOM);
        button.valueBinding(() -> button.setEnabled(editor.get() != null));
        return button;
    }
    /** Edit shared authored form data from a keyframe panel, through its owning editor. */
    public void editForm(Form form, Runnable edit)
    {
        BaseValue.edit(form, IValueListener.FLAG_UNMERGEABLE, value -> edit.run());
    }

    /* Editing states */

    private int dragging = -1;
    private boolean scrubbing;
    private boolean controlFirst;
    private Pair<Keyframe, KeyframeType> draggingData;
    private boolean scaling;
    private float scalingAnchor;
    private final Map<Keyframe, Keyframe> transformKeys = new IdentityHashMap<>();
    private int transformAxis;
    private boolean scalingValues;
    private double scalingValueAnchor;
    private final UIElement scalingOverlay = new ScalingOverlay();

    private boolean stacking;
    private float stackOffset;

    private float originalT;
    private Object originalV;

    private Runnable changeCallback;
    private final UIKeyframeLoops loops = new UIKeyframeLoops(this);
    private final UIKeyframeMotionShift motionShift = new UIKeyframeMotionShift(this);

    /* Fields */

    public final Area graphArea = new Area();

    private final List<UIKeyframeSheet> sheets = new ArrayList<>();
    private final UIKeyframeDopeSheet dopeSheet = new UIKeyframeDopeSheet(this);
    private final UIKeyframeGraph curveGraph = new UIKeyframeGraph(this);
    private IUIKeyframeGraph currentGraph = this.dopeSheet;

    private final Runnable callback;
    private Consumer<UIContext> backgroundRender;
    private Consumer<UIContext> rulerRender;
    private Supplier<Integer> duration;

    private SheetCache cache;
    private Keyframe pickedKeyframe;
    /** Curves the graph shows: gathered when it opens, joined by tracks picked while it is open.
     * Empty means every visible numeric track. */
    private final List<UIKeyframeSheet> graphTracks = new ArrayList<>();
    private SheetCache valueGesture;
    private final Map<UIKeyframeSheet, List<Integer>> valueSelection = new HashMap<>();

    /** The picked key's track: the value panel edits it and the graph draws it on top. */
    public UIKeyframeSheet getPickedSheet() { return this.dopeSheet.getSheet(this.pickedKeyframe); }

    public boolean isGraphTrack(UIKeyframeSheet sheet) { return this.graphTracks.contains(sheet); }

    /** All available tracks, independent of the current presentation and key operation scope. */
    public List<UIKeyframeSheet> getSheets() { return this.sheets; }

    /** Only these tracks may be changed by selection-based commands and value panels. */
    public List<UIKeyframeSheet> getOperationSheets() { return this.currentGraph.getSheets(); }

    public Keyframe getSelectedKeyframe()
    {
        List<UIKeyframeSheet> sheets = this.getOperationSheets();
        for (UIKeyframeSheet sheet : sheets)
        {
            if (this.pickedKeyframe == null) continue;
            int index = sheet.channel.indexOf(this.pickedKeyframe);
            if (index >= 0 && sheet.selection.has(index))
            {
                this.pickedKeyframe = sheet.channel.get(index);
                return this.pickedKeyframe;
            }
        }
        for (UIKeyframeSheet sheet : sheets)
        {
            Keyframe first = sheet.selection.getFirst();
            if (first != null) return first;
        }
        return null;
    }

    private void addGraphTrack(UIKeyframeSheet sheet)
    {
        if (sheet == null || this.graphTracks.contains(sheet) || !KeyframeFactories.isNumeric(sheet.channel.getFactory())) return;
        this.graphTracks.add(sheet);
        this.curveGraph.resetView();
    }

    /** A label click in the graph shows that track's curve; with Ctrl it adds or removes it. */
    public void pickGraphTrack(UIKeyframeSheet sheet, boolean toggle)
    {
        if (!KeyframeFactories.isNumeric(sheet.channel.getFactory())) return;
        List<UIKeyframeSheet> shown = this.curveGraph.getSheets();
        this.graphTracks.clear();
        if (toggle) this.graphTracks.addAll(shown);
        if (toggle && shown.contains(sheet)) this.graphTracks.remove(sheet);
        else this.graphTracks.add(sheet);
        this.curveGraph.resetView();
    }

    /** The graph opens on the tracks with selected keys, otherwise on every visible numeric one. */
    private void gatherGraphTracks()
    {
        this.graphTracks.clear();
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.selection.hasAny() && KeyframeFactories.isNumeric(sheet.channel.getFactory()))
            {
                this.graphTracks.add(sheet);
            }
        }
    }

    @Override
    protected void onRemove(UIElement parent)
    {
        this.finishScaling(true);
        if (this.dragging >= 0) this.cancelKeyframes();
        this.endValueGesture();
        super.onRemove(parent);
    }

    public void beginValueGesture()
    {
        if (this.valueGesture != null) return;
        this.valueGesture = new SheetCache(this.getSheets(), true);
        this.valueSelection.clear();
        for (UIKeyframeSheet sheet : this.getSheets())
            this.valueSelection.put(sheet, new ArrayList<>(sheet.selection.getIndices()));
    }

    public void applyValueChange(UIKeyframeSheet sheet, Runnable edit)
    {
        if (this.valueGesture == null) sheet.channel.preNotify(this.getAutoKeyframeTick() == null
            ? IValueListener.FLAG_DEFAULT : IValueListener.FLAG_BATCH);
        edit.run();
        if (this.valueGesture == null) sheet.channel.postNotify();
    }

    public void endValueGesture()
    {
        if (this.valueGesture == null) return;
        SheetCache beforeState = this.valueGesture;
        this.valueGesture = null;
        Map<UIKeyframeSheet, BaseType> after = new HashMap<>();
        Map<UIKeyframeSheet, List<Integer>> selection = new HashMap<>();
        for (Pair<BaseType, UIKeyframeSheet> before : beforeState.data)
        {
            selection.put(before.b, new ArrayList<>(before.b.selection.getIndices()));
            before.b.selection.clear();
            before.b.selection.addAll(this.valueSelection.get(before.b));
            BaseType data = before.b.channel.toData();
            if (before.a.equals(data)) continue;
            after.put(before.b, data);
            before.b.channel.fromData(before.a);
        }
        for (UIKeyframeSheet sheet : after.keySet()) sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        for (UIKeyframeSheet sheet : selection.keySet())
        {
            sheet.selection.clear();
            sheet.selection.addAll(selection.get(sheet));
        }
        for (UIKeyframeSheet sheet : after.keySet())
        {
            sheet.channel.fromData(after.get(sheet));
            sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
        this.valueSelection.clear();
        this.triggerChange();
    }

    public void cancelValueGesture()
    {
        if (this.valueGesture == null) return;
        for (Pair<BaseType, UIKeyframeSheet> before : this.valueGesture.data)
        {
            before.b.channel.fromData(before.a);
            before.b.selection.clear();
            before.b.selection.addAll(this.valueSelection.get(before.b));
        }
        this.valueGesture = null;
        this.valueSelection.clear();
        this.triggerChange();
    }

    private UICopyPasteController copyPasteController;

    private UIDraggable labelResizer;

    /** Default width of the names column when no layout setting is available. */
    public static final int LABEL_WIDTH_DEFAULT = 120;

    public UIKeyframes(Runnable callback)
    {
        /* The time strip excludes the dope sheet's label column, so the axis maps pixels
         * over the graph area rather than the whole element. */
        this.xAxis.area = this.graphArea;

        this.callback = callback;
        this.tooltip = new UIKeyframePreviewTooltip(this);

        this.labelResizer = new UIDraggable((context) ->
        {
            int w = context.mouseX - this.area.x;
            BBSSettings.editorLayoutSettings.setKeyframeLabelWidth(w);
            this.resize();
            /* Notify parents so e.g. replay editor can sync its category bar width */
            for (UIElement p = this.getParent(); p != null; p = p.getParent())
            {
                p.resize();
            }
        });
        this.labelResizer.hoverOnly().cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        this.add(this.labelResizer);

        this.copyPasteController = new UICopyPasteController(PresetManager.KEYFRAMES, "_CopyKeyframes")
            .supplier(this::serializeKeyframes)
            .consumer((data, mouseX, mouseY) ->
            {
                double offset = BBSSettings.editorSnapToTicks.get() ? Math.round(this.fromGraphX(mouseX)) : this.fromGraphX(mouseX);

                this.pasteKeyframes(parseKeyframes(data), (float) offset, mouseY);
            })
            .canCopy(() -> this.currentGraph.getSelected() != null)
            .labels(UIKeys.KEYFRAMES_CONTEXT_COPY, UIKeys.KEYFRAMES_CONTEXT_PASTE);

        /* Context menu items */
        this.context((menu) ->
        {
            UIContext context = this.getContext();
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            boolean hasSelected = this.currentGraph.getSelected() != null;

            this.copyPasteController.install(menu, context, mouseX, mouseY);
            this.loops.menu(menu, context);

            menu.icon(MenuVerb.REMOVE, () -> this.currentGraph.removeSelected()).label(UIKeys.KEYFRAMES_CONTEXT_REMOVE).enabled(hasSelected);

            UIKeyframeSheet hovered = this.currentGraph.getSheet(mouseX, mouseY);

            if (this.isEditing())
            {
                menu.action(Icons.KEY, UIKeys.KEYFRAMES_MODE_KEYS, () -> this.setGraphMode(false));
            }
            else
            {
                menu.action(Icons.GRAPH, UIKeys.KEYFRAMES_MODE_GRAPH, () -> this.setGraphMode(true));
            }

            if (hovered != null)
            {
                menu.action(Icons.BUCKET, UIKeys.KEYFRAMES_CONTEXT_TRACK_STYLE, () -> UIOverlay.addOverlay(
                    this.getContext(),
                    new UITrackStyleOverlayPanel(hovered, this::refreshTrackStyles),
                    220, 160
                ));
            }

            if (hasSelected)
            {
                menu.action(Icons.SHAPES, UIKeys.KEYFRAMES_CONTEXT_KEYFRAME_STYLE, this::editKeyframeStyle);
            }

            menu.action(Icons.SEARCH, UIKeys.KEYFRAMES_CONTEXT_ADJUST_VALUES, () -> this.adjustValues());
            menu.action(Icons.ARROW_LEFT, UIKeys.KEYFRAMES_KEYS_SELECT_LEFT, () -> this.selectAfter(mouseX, mouseY, -1));
            menu.action(Icons.ARROW_RIGHT, UIKeys.KEYFRAMES_KEYS_SELECT_RIGHT, () -> this.selectAfter(mouseX, mouseY, 1));

            menu.action(Icons.MAXIMIZE, this.isEditing() ? UIKeys.KEYFRAMES_GRAPH_FIT_ALL : UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, this::resetView);
            if (this.isEditing() && hasSelected)
            {
                menu.action(Icons.SEARCH, UIKeys.KEYFRAMES_GRAPH_FIT_SELECTED, this.curveGraph::fitSelection);
                menu.action(Icons.SCALE, UIKeys.KEYFRAMES_GRAPH_SCALE_TIME, () -> this.startScaling(false));
                menu.action(Icons.SCALE, UIKeys.KEYFRAMES_GRAPH_SCALE_VALUE, () -> this.startScaling(true));
            }
            menu.action(Icons.FULLSCREEN, UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, () -> this.currentGraph.selectAll());

            if (hasSelected)
            {
                menu.action(Icons.EXCHANGE, UIKeys.KEYFRAMES_CONTEXT_FLIP, this::flipKeyframes);
                menu.action(Icons.CONVERT, UIKeys.KEYFRAMES_CONTEXT_SPREAD, this::spreadKeyframes);
                menu.action(Icons.OUTLINE_SPHERE, UIKeys.KEYFRAMES_CONTEXT_ROUND, () ->
                {
                    for (UIKeyframeSheet sheet : this.getOperationSheets())
                    {
                        List<Keyframe> selected = sheet.selection.getSelected();

                        if (selected.isEmpty())
                        {
                            continue;
                        }

                        sheet.channel.preNotify();

                        for (Keyframe kf : selected)
                        {
                            kf.setTick(sheet.channel.constrainKeyframeTick(kf, Math.round(kf.getTick())), false);
                        }

                        sheet.channel.postNotify();
                    }
                });
            }
        });

        /* Keys */
        IKey category = UIKeys.KEYFRAMES_KEYS_CATEGORY;
        Supplier<Boolean> canModify = () -> !this.isInteracting() && this.valueGesture == null;

        this.keys().register(Keys.KEYFRAMES_ENABLE, this::toggleEnabled).inside().category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView).inside().category(category);
        this.keys().register(Keys.KEYFRAMES_FIT_SELECTED, this.curveGraph::fitSelection).inside().category(category).active(this::isEditing);
        this.keys().register(Keys.KEYFRAMES_SELECT_ALL, () -> this.currentGraph.selectAll()).inside().category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_TRACK, this::selectAllOnTrackUnderCursor).inside().category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_TRACK_LEFT, () -> this.selectTrackSideUnderCursor(-1)).inside().category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_TRACK_RIGHT, () -> this.selectTrackSideUnderCursor(1)).inside().category(category).active(canModify);
        this.keys().register(Keys.COPY, () ->
        {
            if (this.copyPasteController.copy()) UIUtils.playClick();
        }).inside().category(category);
        this.keys().register(Keys.PASTE, () ->
        {
            UIContext context = this.getContext();

            if (this.copyPasteController.paste(context.mouseX, context.mouseY)) UIUtils.playClick();
        }).inside().category(category).active(canModify);
        /* .inside() is load-bearing: the action opens the presets popup AT the mouse, so it only
         * makes sense over this timeline. Without it the bind fired anywhere and, since this
         * subtree is walked before the parameters dock, it swallowed Ctrl+Shift+V from the
         * transform panel, where that combo is the flipped paste (see UITransform#getVector). */
        this.keys().register(Keys.PRESETS, () ->
        {
            UIContext context = this.getContext();

            if (this.copyPasteController.canPreviewPresets())
            {
                this.copyPasteController.openPresets(context, context.mouseX, context.mouseY);
                UIUtils.playClick();
            }
        }).inside().category(category).active(canModify);
        this.keys().register(Keys.DELETE, () -> this.currentGraph.removeSelected()).inside().category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_LEFT, () ->
        {
            UIContext context = this.getContext();

            this.selectAfter(context.mouseX, context.mouseY, -1);
        }).category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_RIGHT, () ->
        {
            UIContext context = this.getContext();

            this.selectAfter(context.mouseX, context.mouseY, 1);
        }).category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SELECT_SAME, this::selectSame).category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_SCALE_TIME, this::scaleTime).inside().category(category);
        this.keys().register(Keys.TRANSFORMATIONS_X, () -> this.transformAxis = this.transformAxis == 1 ? 0 : 1)
            .category(category).active(() -> this.isEditing() && this.dragging >= 0 && this.draggingData != null && this.draggingData.b == KeyframeType.REGULAR);
        this.keys().register(Keys.TRANSFORMATIONS_Y, () -> this.transformAxis = this.transformAxis == 2 ? 0 : 2)
            .category(category).active(() -> this.isEditing() && this.dragging >= 0 && this.draggingData != null && this.draggingData.b == KeyframeType.REGULAR);
        this.keys().register(Keys.KEYFRAMES_STACK_KEYFRAMES, () -> this.stackKeyframes(false)).inside().category(category);
        this.keys().register(Keys.KEYFRAMES_SELECT_PREV, () -> this.selectNextKeyframe(-1)).category(category);
        this.keys().register(Keys.KEYFRAMES_SELECT_NEXT, () -> this.selectNextKeyframe(1)).category(category);
        this.keys().register(Keys.KEYFRAMES_SPREAD, this::spreadKeyframes).category(category);
        this.keys().register(Keys.KEYFRAMES_FLIP, this::flipKeyframes).category(category).active(canModify);
        this.keys().register(Keys.KEYFRAMES_ADJUST_VALUES, this::adjustValues).category(category);
    }

    public int getLabelWidth()
    {
        return BBSSettings.editorLayoutSettings.getKeyframeLabelWidth();
    }

    /**
     * Restyle every selected keyframe at once, starting from the style of the first of them. The
     * panel edits one style and this writes it to all of them, so a mixed selection ends up uniform
     * - which is what "restyle these" means and what the old per-field controls did too.
     */
    private void editKeyframeStyle()
    {
        Keyframe selected = this.currentGraph.getSelected();

        if (selected == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIKeyframeStyleOverlayPanel(selected.getStyle(), (style) ->
        {
            for (UIKeyframeSheet sheet : this.getOperationSheets())
            {
                for (Keyframe keyframe : sheet.selection.getSelected())
                {
                    keyframe.setStyle(style);
                }
            }
        }), 220, 200);
    }

    private void toggleEnabled()
    {
        this.setSelectedEnabled(null);
    }

    /** Null inverts each key independently, just like the clip shortcut. */
    public void setSelectedEnabled(Boolean enabled)
    {
        List<UIKeyframeSheet> selectedSheets = new ArrayList<>();
        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            if (sheet.selection.hasAny()) selectedSheets.add(sheet);
        }
        if (selectedSheets.isEmpty()) return;

        for (UIKeyframeSheet sheet : selectedSheets) sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        for (UIKeyframeSheet sheet : selectedSheets)
        {
            for (Keyframe keyframe : sheet.selection.getSelected()) keyframe.setEnabled(enabled == null ? !keyframe.isEnabled() : enabled);
        }
        for (UIKeyframeSheet sheet : selectedSheets) sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        this.triggerChange();
    }

    private void adjustValues()
    {
        this.getContext().replaceContextMenu((menu2) ->
        {
            menu2.autoKeys();
            menu2.action(Icons.ARROW_LEFT, UIKeys.KEYFRAMES_CONTEXT_ADJUST_VALUES_LEFT, () -> this.adjustValues(false));
            menu2.action(Icons.ARROW_RIGHT, UIKeys.KEYFRAMES_CONTEXT_ADJUST_VALUES_RIGHT, () -> this.adjustValues(true));
        });
    }

    private void adjustValues(boolean last)
    {
        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            List<Keyframe> selected = sheet.selection.getSelected();
            IKeyframeFactory factory = sheet.channel.getFactory();

            if (selected.size() < 2 || !KeyframeFactories.isNumeric(factory))
            {
                continue;
            }

            sheet.channel.preNotify();

            int index = last ? selected.size() - 1 : 0;
            int previous = last ? selected.size() - 2 : 1;

            Keyframe kf = selected.get(index);
            Keyframe prevKf = selected.get(previous);

            double difference = factory.getY(kf.getValue()) - factory.getY(prevKf.getValue());

            selected.remove(index);

            for (Keyframe keyframe : selected)
            {
                keyframe.setValue(factory.yToValue(factory.getY(keyframe.getValue()) + difference));
            }

            sheet.channel.postNotify();
        }
    }

    public UIKeyframes changed(Runnable runnable)
    {
        this.changeCallback = runnable;

        return this;
    }

    public void triggerChange()
    {
        if (this.changeCallback != null)
        {
            this.changeCallback.run();

        }
    }

    public UIKeyframeDopeSheet getDopeSheet()
    {
        return this.dopeSheet;
    }

    private void selectAllOnTrackUnderCursor()
    {
        UIContext context = this.getContext();
        UIKeyframeSheet sheet = this.currentGraph.getSheet(context.mouseX, context.mouseY);

        if (sheet != null)
        {
            this.currentGraph.clearSelection();
            sheet.selection.all();
            this.currentGraph.pickSelected();
        }
    }

    /**
     * Like {@link #selectAllOnTrackUnderCursor()} but only keyframes on one side of the cursor time
     * (same behavior as Ctrl+, / Ctrl+. but scoped to the track under the mouse).
     */
    private void selectTrackSideUnderCursor(int direction)
    {
        UIContext context = this.getContext();
        UIKeyframeSheet sheet = this.currentGraph.getSheet(context.mouseX, context.mouseY);

        if (sheet != null)
        {
            float tick = (float) this.fromGraphX(context.mouseX);

            this.currentGraph.clearSelection();
            sheet.selection.after(tick, direction);
            this.currentGraph.pickSelected();
        }
    }

    protected void selectNextKeyframe(int direction)
    {
        IUIKeyframeGraph graph = this.getGraph();
        Keyframe keyframe = graph.getSelected();

        if (keyframe == null)
        {
            UIContext context = this.getContext();
            UIKeyframeSheet sheet = this.getGraph().getSheet(context.mouseX, context.mouseY);

            if (sheet == null)
            {
                return;
            }

            KeyframeSegment segment = sheet.channel.find((float) this.fromGraphX(context.mouseX));

            if (segment != null)
            {
                keyframe = direction < 0 ? segment.a : segment.b;

                graph.clearSelection();
                graph.selectKeyframe(keyframe);

                return;
            }
        }

        if (keyframe != null)
        {
            KeyframeChannel channel = (KeyframeChannel) keyframe.getParent();
            int existingIndex = channel.getKeyframes().indexOf(keyframe);
            int index = MathUtils.cycler(existingIndex + direction, channel.getAll());
            Keyframe nextKeyframe = channel.get(index);

            graph.clearSelection();
            graph.selectKeyframe(nextKeyframe);
        }
    }

    private void selectAfter(int mouseX, int mouseY, int direction)
    {
        float tick = (float) this.fromGraphX(mouseX);

        if (!Window.isShiftPressed())
        {
            this.currentGraph.selectAfter(tick, direction);
        }
        else
        {
            UIKeyframeSheet sheet = this.currentGraph.getSheet(mouseX, mouseY);

            /* There is no track under the cursor when it sits below the last one, and asking that
             * empty strip to select something used to throw. */
            if (sheet == null)
            {
                return;
            }

            sheet.selection.after(tick, direction);
            this.currentGraph.pickSelected();
        }
    }

    private void selectSame()
    {
        UIContext context = this.getContext();
        Pair<Keyframe, KeyframeType> keyframe = this.currentGraph.findKeyframe(context.mouseX, context.mouseY);

        if (keyframe != null)
        {
            if (!Window.isShiftPressed())
            {
                this.currentGraph.clearSelection();
            }

            for (UIKeyframeSheet sheet : this.getOperationSheets())
            {
                List<Keyframe> list = sheet.channel.getList();

                for (int i = 0; i < list.size(); i++)
                {
                    Keyframe kf = list.get(i);

                    if (kf.getFactory() == keyframe.a.getFactory() && kf.getFactory().compare(keyframe.a.getValue(), kf.getValue()))
                    {
                        sheet.selection.add(i);
                    }
                }
            }

            this.currentGraph.pickSelected();
        }
    }

    private void scaleTime()
    {
        if (this.scaling) this.finishScaling(false);
        else this.startScaling(false);
    }

    private void startScaling(boolean values)
    {
        if (this.isInteracting() || this.getSelectedKeyframe() == null) return;
        this.cacheKeyframes();
        this.scaling = true;
        this.scalingValues = values;
        this.scalingAnchor = this.isEditing() ? this.getTick() : Float.MAX_VALUE;
        this.scalingValueAnchor = 0D;
        this.initialX = this.getContext().mouseX;
        this.initialY = this.getContext().mouseY;
        for (Keyframe key : this.transformKeys.keySet())
        {
            if (!this.isEditing()) this.scalingAnchor = Math.min(this.scalingAnchor, key.getTick());
        }
        this.getContext().menu.overlay.add(this.scalingOverlay);
    }

    private void finishScaling(boolean cancel)
    {
        if (!this.scaling) return;
        this.scaling = false;
        this.scalingOverlay.removeFromParent();
        if (cancel) this.cancelKeyframes();
        else this.submitKeyframes(true);
        this.triggerChange();
        this.currentGraph.pickSelected();
    }

    private class ScalingOverlay extends UIElement
    {
        public ScalingOverlay()
        {
            this.keys().register(Keys.TRANSFORMATIONS_X, () -> UIKeyframes.this.scalingValues = false);
            this.keys().register(Keys.TRANSFORMATIONS_Y, () -> UIKeyframes.this.scalingValues = true)
                .active(UIKeyframes.this::isEditing);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 || context.mouseButton == 1)
            {
                UIKeyframes.this.handleMouse(context);
                UIKeyframes.this.finishScaling(context.mouseButton == 1);
            }
            return true;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE)) UIKeyframes.this.finishScaling(true);
            else if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)) UIKeyframes.this.finishScaling(false);
            else this.keybindsKeyPressed(context);
            return true;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context) { return true; }
    }

    /** Transform from the press snapshot, so mixed numeric factories never accumulate rounding. */
    public void moveSelectedKeys(float time, double value)
    {
        for (Map.Entry<Keyframe, Keyframe> entry : this.transformKeys.entrySet())
        {
            Keyframe key = entry.getKey(), original = entry.getValue();
            KeyframeChannel channel = (KeyframeChannel) key.getParent();
            key.setTick(channel.constrainKeyframeTick(key, original.getTick() + (this.transformAxis == 2 ? 0 : time)), false);
            if (KeyframeFactories.isNumeric(key.getFactory()))
                key.setValue(key.getFactory().yToValue(original.getY() + (this.transformAxis == 1 ? 0 : value)), false);
        }
    }

    public void cancelKeyframes()
    {
        if (this.cache == null) return;
        UIKeyframeSheet focused = this.dopeSheet.getSheet(this.pickedKeyframe);
        int index = focused == null ? -1 : focused.channel.indexOf(this.pickedKeyframe);
        for (Pair<BaseType, UIKeyframeSheet> entry : this.cache.data) entry.b.channel.fromData(entry.a);
        if (focused != null) this.pickedKeyframe = focused.channel.get(index);
        this.cache = null;
        this.transformKeys.clear();
        this.triggerChange();
    }

    private void stackKeyframes(boolean cancel)
    {
        if (this.stacking)
        {
            this.stacking = false;

            if (!cancel)
            {
                UIContext context = this.getContext();
                List<UIKeyframeSheet> sheets = new ArrayList<>();
                float currentTick = (float) this.fromGraphX(context.mouseX);

                for (UIKeyframeSheet sheet : this.getOperationSheets())
                {
                    if (sheet.selection.hasAny())
                    {
                        sheets.add(sheet);
                    }
                }

                for (UIKeyframeSheet current : sheets)
                {
                    List<Keyframe> selected = current.selection.getSelected();
                    float mMin = Integer.MAX_VALUE;
                    float mMax = Integer.MIN_VALUE;

                    for (Keyframe keyframe : selected)
                    {
                        mMin = Math.min(keyframe.getTick(), mMin);
                        mMax = Math.max(keyframe.getTick(), mMax);
                    }

                    float length = mMax - mMin + this.getStackOffset();
                    int times = (int) Math.max(1, Math.ceil((currentTick - mMax) / length));
                    float x = 0;

                    current.selection.clear();

                    for (int i = 0; i < times; i++)
                    {
                        for (Keyframe keyframe : selected)
                        {
                            float tick = mMax + this.getStackOffset() + (keyframe.getTick() - mMin) + x;
                            int index = current.channel.insert(tick, keyframe.getFactory().copy(keyframe.getValue()));
                            Keyframe kf = current.channel.get(index);
                            
                            kf.copy(keyframe);
                            kf.setTick(current.channel.getSourceTick(tick));
                            current.selection.add(index);
                        }

                        x += length;
                    }
                }
            }

            return;
        }

        this.stacking = true;
        this.stackOffset = 1;
    }

    public boolean isStacking()
    {
        return this.stacking;
    }

    public float getStackOffset()
    {
        return this.stackOffset;
    }

    /**
     * Flip (mirror in time) currently selected keyframes around a pivot that is shared
     * across every track. The pivot is the center of the selection's global tick range,
     * so the flip preserves the timing relationships between tracks instead of mirroring
     * each track around its own local center.
     */
    private void flipKeyframes()
    {
        /* Find the global tick range of the selection across all tracks */
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                min = Math.min(min, keyframe.getTick());
                max = Math.max(max, keyframe.getTick());
            }
        }

        if (min > max)
        {
            return;
        }

        float pivot = min + max;

        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            if (!sheet.selection.hasAny())
            {
                continue;
            }

            sheet.channel.preNotify();

            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                keyframe.setTick(sheet.channel.constrainKeyframeTick(keyframe, pivot - keyframe.getTick()), false);
            }

            sheet.channel.postNotify();

            /* Re-sort the channel (the flip reverses the order) and re-sync the selection indices */
            sheet.sort();
        }

        this.getGraph().pickSelected();
    }

    private void spreadKeyframes()
    {
        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            List<Keyframe> selected = sheet.selection.getSelected();

            if (selected.isEmpty())
            {
                continue;
            }

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;

            for (Keyframe keyframe : selected)
            {
                int index = sheet.channel.getKeyframes().indexOf(keyframe);

                min = Math.min(min, index);
                max = Math.max(max, index);
            }

            Keyframe minKf = sheet.channel.get(min);
            Keyframe maxKf = sheet.channel.get(max);
            int count = max - min;
            float distance = (maxKf.getTick() - minKf.getTick()) / count;

            sheet.channel.preNotify();

            for (int i = 1; i < count; i++)
            {
                int index = i + min;
                Keyframe kf = sheet.channel.get(index);

                kf.setTick(sheet.channel.constrainKeyframeTick(kf, minKf.getTick() + i * distance));
            }

            sheet.channel.postNotify();

            sheet.selection.clear();

            for (int i = min; i <= max; i++)
            {
                sheet.selection.add(i);
            }
        }

        this.getGraph().pickSelected();
    }

    /* Sheet editing */

    public boolean isEditing()
    {
        return this.currentGraph != this.dopeSheet;
    }

    public void setGraphMode(boolean graph)
    {
        if (this.isInteracting() || graph == this.isEditing()) return;
        this.endValueGesture();
        this.motionShift.release(false);
        this.loops.reset();
        this.xAxis.stopZoom();
        this.currentGraph.stopZoom();
        if (graph) this.gatherGraphTracks();
        else this.graphTracks.clear();
        this.currentGraph = graph ? this.curveGraph : this.dopeSheet;
        this.resize();
        if (graph && !this.curveGraph.getSheets().isEmpty()) this.curveGraph.resetView();
        if (this.callback != null) this.callback.run();
    }

    /* Caching keyframes */

    public void cacheKeyframes()
    {
        this.cache = new SheetCache(this.getOperationSheets());
        this.transformKeys.clear();
        for (UIKeyframeSheet sheet : this.getOperationSheets())
        {
            for (Keyframe key : sheet.selection.getSelected())
            {
                Keyframe original = new Keyframe("", key.getFactory(), key.getTick(), key.getValue());
                original.copy(key);
                this.transformKeys.put(key, original);
            }
        }
    }

    public void submitKeyframes()
    {
        this.submitKeyframes(false);
    }

    private void submitKeyframes(boolean overwrite)
    {
        if (this.cache == null) return;
        UIKeyframeSheet focused = this.dopeSheet.getSheet(this.pickedKeyframe);
        int focusedBefore = focused == null ? -1 : focused.channel.indexOf(this.pickedKeyframe);
        Map<UIKeyframeSheet, Pair<List<Integer>, List<Integer>>> selection = new HashMap<>();
        Map<UIKeyframeSheet, BaseType> changed = new java.util.LinkedHashMap<>();
        for (Pair<BaseType, UIKeyframeSheet> before : this.cache.data)
        {
            UIKeyframeSheet sheet = before.b;
            List<Integer> last = sheet.sort(overwrite);
            selection.put(sheet, new Pair<>(last, new ArrayList<>(sheet.selection.getIndices())));
            BaseType after = sheet.channel.toData();
            if (!before.a.equals(after)) changed.put(sheet, after);
        }
        int focusedAfter = focused == null ? -1 : focused.channel.indexOf(this.pickedKeyframe);

        /* Restore the entire before state before any listener captures the UI history. */
        for (Pair<BaseType, UIKeyframeSheet> before : this.cache.data)
        {
            if (!changed.containsKey(before.b)) continue;
            before.b.channel.fromData(before.a);
            before.b.selection.clear();
            before.b.selection.addAll(selection.get(before.b).a);
        }
        if (focused != null) this.pickedKeyframe = focused.channel.get(focusedBefore);
        for (UIKeyframeSheet sheet : changed.keySet()) sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        for (Map.Entry<UIKeyframeSheet, BaseType> after : changed.entrySet())
        {
            after.getKey().channel.fromData(after.getValue());
            after.getKey().selection.clear();
            after.getKey().selection.addAll(selection.get(after.getKey()).b);
        }
        if (focused != null) this.pickedKeyframe = focused.channel.get(focusedAfter);
        for (UIKeyframeSheet sheet : changed.keySet()) sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        this.cache = null;
        this.transformKeys.clear();
        this.triggerChange();
    }

    /* Copy-pasting */

    /**
     * Parses keyframe data from the given map structure into a map of keyframes corresponding to their keys.
     *
     * @param data the map data containing keyframe information. Each key in the map represents
     *             a collection of keyframe data, where the associated value contains type and keyframe details.
     *             If null, the method returns an empty map.
     * @return a map where each key corresponds to a collection of parsed {@link PastedKeyframes}.
     *         If no keyframe data is provided or the input is null, an empty map is returned.
     */
    public static Map<String, PastedKeyframes> parseKeyframes(MapType data)
    {
        if (data == null)
        {
            return Collections.emptyMap();
        }

        Map<String, PastedKeyframes> temp = new HashMap<>();

        for (String key : data.keys())
        {
            MapType map = data.getMap(key);
            ListType list = map.getList("keyframes");
            IKeyframeFactory serializer = KeyframeFactories.FACTORIES.get(map.getString("type"));

            for (int i = 0, c = list.size(); i < c; i++)
            {
                PastedKeyframes pastedKeyframes = temp.computeIfAbsent(key, k -> new PastedKeyframes(serializer));
                Keyframe keyframe = new Keyframe("", serializer);

                keyframe.fromData(list.getMap(i));
                pastedKeyframes.keyframes.add(keyframe);
            }
        }

        return temp;
    }

    private MapType serializeKeyframes()
    {
        MapType keyframes = new MapType();

        for (UIKeyframeSheet property : this.getOperationSheets())
        {
            List<Keyframe> selected = property.selection.getSelected();

            if (selected.isEmpty())
            {
                continue;
            }

            MapType data = new MapType();
            ListType list = new ListType();

            data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, property.channel.getFactory()));
            data.put("keyframes", list);

            for (Keyframe keyframe : selected)
            {
                list.add(keyframe.toData());
            }

            if (!list.isEmpty())
            {
                keyframes.put(property.id, data);
            }
        }

        return keyframes;
    }

    /**
     * Paste copied keyframes to clipboard
     */
    protected void pasteKeyframes(Map<String, PastedKeyframes> keyframes, float offset, int mouseY)
    {
        this.pasteKeyframes(keyframes, offset, mouseY, false);
    }

    private void pasteKeyframes(Map<String, PastedKeyframes> keyframes, float offset, int mouseY, boolean keepTracks)
    {
        List<UIKeyframeSheet> sheets = this.getOperationSheets();

        this.currentGraph.clearSelection();

        if (keyframes.size() == 1 && !keepTracks)
        {
            UIKeyframeSheet current = this.isEditing() ? this.currentGraph.getLastSheet() : this.currentGraph.getSheet(this.getContext().mouseX, mouseY);
            if (current != null && !sheets.contains(current)) return;

            if (current == null)
            {
                current = this.currentGraph.getFirstTrackSheet();
            }

            if (current == null)
            {
                return;
            }

            this.pasteKeyframesTo(current, keyframes.get(keyframes.keySet().iterator().next()), offset);
        }
        else
        {
            float min = Float.MAX_VALUE;

            for (Map.Entry<String, PastedKeyframes> entry : keyframes.entrySet())
            {
                if (entry.getValue().keyframes.isEmpty())
                {
                    continue;
                }

                entry.getValue().keyframes.sort((a, b) -> Float.compare(a.getTick(), b.getTick()));

                min = Math.min(min, entry.getValue().keyframes.get(0).getTick());
            }

            for (Map.Entry<String, PastedKeyframes> entry : keyframes.entrySet())
            {
                float entryMin = entry.getValue().keyframes.get(0).getTick();

                for (UIKeyframeSheet property : sheets)
                {
                    if (!property.id.equals(entry.getKey()))
                    {
                        continue;
                    }

                    float d = min == Float.MAX_VALUE ? 0F : entryMin - min;

                    this.pasteKeyframesTo(property, entry.getValue(), offset + d);
                }
            }
        }

        this.currentGraph.pickSelected();
    }

    private void pasteKeyframesTo(UIKeyframeSheet sheet, PastedKeyframes pastedKeyframes, float offset)
    {
        if (sheet.channel.getFactory() != pastedKeyframes.factory)
        {
            return;
        }

        float firstX = pastedKeyframes.keyframes.get(0).getTick();
        List<Keyframe> toSelect = new ArrayList<>();

        for (Keyframe keyframe : pastedKeyframes.keyframes)
        {
            keyframe.setTick(sheet.channel.getSourceTick(keyframe.getTick() - firstX + offset));

            int index = sheet.channel.insert(keyframe.getTick(), keyframe.getValue());
            Keyframe inserted = sheet.channel.get(index);

            inserted.copy(keyframe);
            toSelect.add(inserted);
        }

        for (Keyframe select : toSelect)
        {
            sheet.selection.add(sheet.channel.getKeyframes().indexOf(select));
        }
    }

    /* Getters & setters */

    public UIKeyframes rulerRenderer(Consumer<UIContext> rulerRender)
    {
        this.rulerRender = rulerRender;

        return this;
    }

    public UIKeyframes duration(Supplier<Integer> duration)
    {
        this.duration = duration;

        return this;
    }

    public IUIKeyframeGraph getGraph()
    {
        return this.currentGraph;
    }

    public int getDuration()
    {
        return this.duration == null ? 0 : this.duration.get();
    }

    public float getTick()
    {
        return (float) this.fromGraphX(this.getContext().mouseX);
    }

    /** Exact visible playhead time, including fractions entered with Shift while snapping. */
    public float getPlayheadTick(UIContext context)
    {
        return this.getTick();
    }

    /**
     * The tick auto-keyframing writes at, or {@code null} when an edit should land on the
     * keyframes it was made on.
     *
     * <p>Auto-keyframing turns every value edit into a key at the playhead instead of a rewrite of
     * whatever keyframe happens to be selected, so posing at a tick where the track has no keyframe
     * yet makes one rather than dragging the past along with it. A timeline without a playhead has
     * no tick to key at, so it never auto-keyframes. Film and animation-state timelines supply it.
     */
    public Float getAutoKeyframeTick()
    {
        return null;
    }

    /** Pause a manual value edit at the playhead; continued dragging edits its new key. */
    public boolean stopPlaybackOnValueChange()
    {
        return false;
    }

    public boolean isSelecting()
    {
        return this.marquee.isPressed();
    }

    /** Whether the user is in the middle of any mouse interaction (dragging, selecting, navigating, scaling or stacking). */
    public boolean isInteracting()
    {
        return this.scrubbing || this.loops.isDragging() || this.motionShift.isDragging() || this.dragging >= 0 || this.marquee.isPressed() || this.navigating || this.scaling || this.stacking;
    }

    /* Sheet management */

    public void removeAllSheets()
    {
        this.endValueGesture();
        this.graphTracks.clear();
        this.motionShift.release(false);
        this.loops.reset();
        this.dopeSheet.removeAllSheets();
        this.pickKeyframe(null);
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.dopeSheet.addSheet(sheet);
    }

    /**
     * Re-read the user's track name and colour overrides into the tracks already on screen. The overrides
     * are global, so a single track's edit can move a whole family of rows (every {@code x}, every
     * {@code head} bone) - all of them get refreshed rather than only the one that was edited.
     */
    public void refreshTrackStyles()
    {
        for (UIKeyframeSheet sheet : this.dopeSheet.getSheets())
        {
            sheet.applyStyle();
        }
    }

    public void pickKeyframe(Keyframe keyframe)
    {
        UIKeyframeSheet previous = this.getPickedSheet();
        UIKeyframeSheet sheet = this.dopeSheet.getSheet(keyframe);

        this.pickedKeyframe = keyframe;
        this.getGraph().onCallback(keyframe);
        if (sheet != previous) this.endValueGesture();
        if (this.isEditing() && !this.graphTracks.isEmpty()) this.addGraphTrack(sheet);
        if (this.callback != null) this.callback.run();
    }

    /* Graphing */

    public void resetView()
    {
        this.currentGraph.resetView();
    }

    public void resetViewX()
    {
        int c = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        /* Find minimum and maximum */
        for (UIKeyframeSheet property : this.getOperationSheets())
        {
            List keyframes = property.channel.getKeyframes();

            for (Object object : keyframes)
            {
                Keyframe frame = (Keyframe) object;

                min = Integer.min((int) frame.getTick(), min);
                max = Integer.max((int) frame.getTick(), max);
            }

            c = Math.max(c, keyframes.size());
            if (!property.channel.getLoops().isEmpty())
            {
                max = Math.max(max, (int) Math.ceil(property.channel.getLength()));
                c = Math.max(c, 2);
            }
        }

        if (c <= 1)
        {
            min = 0;
            max = this.getDuration();
        }

        if (Math.abs(max - min) > 0.01F)
        {
            this.xAxis.viewOffset(min, max, this.graphArea.w, 30);
        }
        else
        {
            this.xAxis.set(0, 2);
        }
    }

    /** The band being stretched, with a little slack so a keyframe grazed by its edge counts. */
    public Area getGrabbingArea(UIContext context)
    {
        Area area = new Area();

        area.copy(this.marquee.getArea());
        area.offset(3);

        return area;
    }

    /* User input */

    @Override
    public void resize()
    {
        /* Save horizontal view range, and restore it after resize */
        double minValue = this.xAxis.getMinValue();
        double maxValue = this.xAxis.getMaxValue();

        int labelWidth = this.getLabelWidth();
        this.labelResizer.relative(this).x(labelWidth - 3).y(0.35F).w(6).h(0.3F);

        super.resize();

        this.graphArea.copy(this.area);
        this.graphArea.x += labelWidth;
        this.graphArea.w -= labelWidth;

        this.currentGraph.resize();

        if (!Operation.equals(minValue, maxValue))
        {
            this.xAxis.view(minValue, maxValue);
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.updateModifierOrder();

        if (this.area.isInside(context))
        {
            this.xAxis.stopZoom();
            this.currentGraph.stopZoom();
        }
        if (this.hasCursor() && !this.scaling && !this.stacking && context.mouseButton == 0
            && !Window.isAltPressed() && !Window.isCtrlPressed()
            && this.graphArea.isInside(context)
            && context.mouseY < TimelineRulerRenderer.getRulerBottom(this.area))
        {
            this.scrubbing = true;
            this.moveNoKeyframes(context);

            return true;
        }
        if (!this.scaling && !this.stacking && context.mouseButton == 0
            && this.graphArea.isInside(context) && (this.isDuplicatingAtPlayhead() || this.isCreatingAtPlayhead()))
        {
            if (this.isCreatingAtPlayhead())
            {
                this.removeOrCreateKeyframe(context);
            }
            else
            {
                this.duplicateOrSelectColumn(context);
            }

            return true;
        }
        if (!this.scaling && !this.stacking && this.motionShift.mouseClicked(context)) return true;
        if (!this.scaling && !this.stacking && this.loops.mouseClicked(context)) return true;
        if (this.scaling)
        {
            this.finishScaling(context.mouseButton == 1);
            return true;
        }
        if (this.currentGraph.mouseClicked(context))
        {
            return true;
        }

        if (this.stacking)
        {
            this.stackKeyframes(context.mouseButton == 1);

            return true;
        }

        if (this.graphArea.isInside(context))
        {
            this.lastX = this.initialX = context.mouseX;
            this.lastY = this.initialY = context.mouseY;

            if (Window.isCtrlPressed() && context.mouseButton == 0)
            {
                this.removeOrCreateKeyframe(context);
            }
            else if (Window.isAltPressed() && context.mouseButton == 0)
            {
                this.duplicateOrSelectColumn(context);
            }
            else if (context.mouseButton == 0)
            {
                this.pickOrStartSelectingKeyframes(context);
            }
            else if (context.mouseButton == 2)
            {
                this.navigating = true;
            }

            return context.mouseButton != 1;
        }

        return super.subMouseClicked(context);
    }

    private void removeOrCreateKeyframe(UIContext context)
    {
        if (this.isCreatingAtPlayhead())
        {
            this.currentGraph.addKeyframeAt(this.getCreationTick(context), context.mouseY);
            return;
        }

        List<Keyframe> keyframes = this.currentGraph.findKeyframes(context.mouseX, context.mouseY);

        if (!keyframes.isEmpty())
        {
            this.currentGraph.removeKeyframes(keyframes);
        }
        else
        {
            this.currentGraph.addKeyframe(context.mouseX, context.mouseY);
        }
    }

    private void duplicateOrSelectColumn(UIContext context)
    {
        if (this.currentGraph.getSelected() != null && !Window.isShiftPressed())
        {
            /* Duplicate */
            this.pasteKeyframes(this.parseKeyframes(this.serializeKeyframes()), this.getDuplicationTick(context),
                context.mouseY, this.isEditing() || this.isDuplicatingAtPlayhead());

            return;
        }

        /* Select a column */
        this.currentGraph.selectByX(context.mouseX);
    }

    public boolean isDuplicatingAtPlayhead()
    {
        this.updateModifierOrder();

        return !this.controlFirst && Window.isAltPressed() && Window.isCtrlPressed() && !Window.isShiftPressed()
            && this.currentGraph.getSelected() != null;
    }

    private void updateModifierOrder()
    {
        /* Keep the first modifier's mode while both are held, even after creating a
         * key selects it. Releasing one modifier lets the remaining one choose again. */
        if (!Window.isAltPressed() || !Window.isCtrlPressed())
        {
            this.controlFirst = Window.isCtrlPressed();
        }
    }

    public boolean isCreatingAtPlayhead()
    {
        return this.hasCursor() && Window.isCtrlPressed() && Window.isAltPressed() && !this.isDuplicatingAtPlayhead();
    }

    public boolean isRemovingKeyframe()
    {
        return Window.isCtrlPressed() && !this.isDuplicatingAtPlayhead() && !this.isCreatingAtPlayhead();
    }

    public float getCreationTick(UIContext context)
    {
        return this.isCreatingAtPlayhead() ? this.getPlayheadTick(context) : this.fromGraphCursor(context.mouseX);
    }

    public float getDuplicationTick(UIContext context)
    {
        return this.isDuplicatingAtPlayhead() ? this.getPlayheadTick(context) : this.fromGraphCursor(context.mouseX);
    }

    private void pickOrStartSelectingKeyframes(UIContext context)
    {
        /* Picking keyframe or initiating selection */
        Pair<Keyframe, KeyframeType> pair = this.currentGraph.findKeyframe(context.mouseX, context.mouseY);
        Keyframe found = pair == null ? null : pair.a;
        boolean shift = Window.isShiftPressed();

        if (shift && found == null)
        {
            this.marquee.press(context.mouseX, context.mouseY);
        }

        if (found != null)
        {
            List<Keyframe> clicked = this.currentGraph.findKeyframes(context.mouseX, context.mouseY);

            if (!shift && !this.isAllSelected(clicked))
            {
                this.currentGraph.clearSelection();
            }

            for (Keyframe keyframe : clicked)
            {
                this.currentGraph.getSheet(keyframe).selection.add(keyframe);
            }

            this.pickKeyframe(found);
        }
        else if (!this.marquee.isPressed())
        {
            this.currentGraph.clearSelection();
            this.pickKeyframe(null);
        }

        if (!this.marquee.isPressed())
        {
            this.dragging = 0;
            this.transformAxis = 0;
            this.draggingData = pair;

            if (pair != null && pair.b != KeyframeType.REGULAR)
            {
                found = pair.a;
            }

            this.cacheKeyframes();

            if (found != null)
            {
                this.originalT = found.getTick();
                this.originalV = found.getFactory().copy(found.getValue());
            }
        }
    }

    private boolean isAllSelected(List<Keyframe> keyframes)
    {
        for (Keyframe keyframe : keyframes)
        {
            if (!this.currentGraph.getSheet(keyframe).selection.has(keyframe)) return false;
        }

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 0 && this.motionShift.isDragging())
        {
            this.motionShift.handleMouse(context);
            return this.motionShift.release(false);
        }
        if (this.scrubbing && context.mouseButton == 0)
        {
            this.moveNoKeyframes(context);
            this.scrubbing = false;

            return true;
        }

        if (this.loops.release(false)) return true;
        this.currentGraph.mouseReleased(context);

        if (this.marquee.isPressed())
        {
            this.marquee.update(context.mouseX, context.mouseY);
            this.currentGraph.selectInArea(this.getGrabbingArea(context));
        }

        if (this.dragging > 0)
        {
            this.submitKeyframes(true);
            this.currentGraph.pickSelected();
        }

        this.navigating = false;
        this.marquee.reset();
        this.dragging = -1;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.motionShift.isDragging()) return true;
        if (this.area.isInside(context) && this.stacking)
        {
            this.stackOffset = (float) Math.max(0.05F, this.stackOffset + Math.copySign(Window.isShiftPressed() ? 0.05F : 1, context.mouseWheel));

            return true;
        }

        if (this.area.isInside(context) && !this.navigating && !this.scaling)
        {
            this.currentGraph.mouseScrolled(context);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.motionShift.keyPressed(context)) return true;
        this.updateModifierOrder();

        if (Window.isCtrlPressed() && (context.isPressed(GLFW.GLFW_KEY_LEFT_ALT) || context.isPressed(GLFW.GLFW_KEY_RIGHT_ALT)))
        {
            this.controlFirst = true;
        }
        else if (Window.isAltPressed() && (context.isPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || context.isPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)))
        {
            this.controlFirst = false;
        }

        this.xAxis.stopZoom();
        this.currentGraph.stopZoom();
        if (this.loops.keyPressed(context)) return true;
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            if (this.dragging >= 0)
            {
                this.cancelKeyframes();
                this.dragging = -1;
                this.currentGraph.pickSelected();
                return true;
            }
            if (this.stacking)
            {
                this.stackKeyframes(true);
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        this.updateModifierOrder();

        if (this.isInteracting())
        {
            this.xAxis.stopZoom();
            this.currentGraph.stopZoom();
        }
        else
        {
            this.xAxis.updateZoom();
            this.currentGraph.updateZoom();
        }

        super.render(context);

        BBSProfiler.begin(BBSProfiler.Timer.UI_TIMELINE);

        this.handleMouse(context);

        context.batcher.clip(this.area, context);

        this.renderBackground(context);
        this.currentGraph.render(context);

        this.renderMarquee(context);

        this.currentGraph.postRender(context);
        this.renderOverlay(context);

        context.batcher.unclip(context);

        /* Draw label resizer on top so it is not covered by the semi-transparent background */
        if (this.labelResizer.isVisible() && (this.labelResizer.area.isInside(context) || this.labelResizer.isDragging()))
        {
            Area a = this.labelResizer.area;
            Scroll.bar(context.batcher, a.x, a.y, a.ex(), a.ey());
        }

        BBSProfiler.end(BBSProfiler.Timer.UI_TIMELINE);
    }

    protected void renderOverlay(UIContext context)
    {
        this.loops.render(context);
        this.motionShift.render(context);
        this.currentGraph.renderTopmostKeyframes(context);
        this.loops.renderStatus(context);
    }

    public void renderRuler(UIContext context)
    {
        if (this.rulerRender != null)
        {
            this.rulerRender.accept(context);
        }
    }

    /**
     * Handle any related mouse logic during rendering
     */
    protected void handleMouse(UIContext context)
    {
        if (this.motionShift.isDragging())
        {
            this.motionShift.handleMouse(context);
            return;
        }
        if (this.scrubbing)
        {
            this.moveNoKeyframes(context);
            return;
        }

        if (this.loops.isDragging())
        {
            this.loops.handleMouse(context);
            return;
        }
        this.currentGraph.handleMouse(context, this.lastX, this.lastY);

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        boolean mouseHasMoved = Math.abs(mouseX - this.initialX) > 2 || Math.abs(mouseY - this.initialY) > 2;

        if (this.scaling)
        {
            /* A 100 px displacement doubles the spread around the gesture's fixed pivot. */
            double ratio = Math.pow(2D, (this.scalingValues ? this.initialY - mouseY : mouseX - this.initialX) / 100D);
            if (!this.isEditing())
            {
                double origin = this.fromGraphX(this.initialX) - this.scalingAnchor;
                ratio = Math.abs(origin) < 1E-6D ? 1D : (this.fromGraphX(mouseX) - this.scalingAnchor) / origin;
            }
            for (Map.Entry<Keyframe, Keyframe> entry : this.transformKeys.entrySet())
            {
                Keyframe key = entry.getKey(), original = entry.getValue();
                float time = this.scalingValues ? original.getTick()
                    : (float) (this.scalingAnchor + (original.getTick() - this.scalingAnchor) * ratio);
                if (!this.scalingValues && (this.isEditing() ? this.isSnappingToTicks() : Window.isCtrlPressed())) time = Math.round(time);
                key.setTick(((KeyframeChannel) key.getParent()).constrainKeyframeTick(key, time), false);
                if (KeyframeFactories.isNumeric(key.getFactory()))
                {
                    key.setValue(key.getFactory().yToValue(this.scalingValues
                        ? this.scalingValueAnchor + (original.getY() - this.scalingValueAnchor) * ratio : original.getY()), false);
                    if (!this.isEditing()) continue;
                    key.lx = original.lx * (this.scalingValues ? 1F : (float) ratio);
                    key.rx = original.rx * (this.scalingValues ? 1F : (float) ratio);
                    key.ly = original.ly * (this.scalingValues ? (float) ratio : 1F);
                    key.ry = original.ry * (this.scalingValues ? (float) ratio : 1F);
                }
            }
            this.triggerChange();
        }
        else if (this.dragging == 0 && mouseHasMoved)
        {
            this.dragging = 1;
        }
        else if (this.dragging == 1)
        {
            if (this.currentGraph.getSelected() != null)
            {
                this.currentGraph.dragKeyframes(context, this.draggingData, this.initialX, this.initialY, this.originalT, this.originalV);
            }
            else
            {
                this.moveNoKeyframes(context);
            }
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    protected void moveNoKeyframes(UIContext context)
    {}

    protected boolean hasCursor()
    {
        return false;
    }

    /**
     * Render background, specifically backdrop and borders if the duration is present
     */
    protected void renderBackground(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.baseSurface());
        this.graphArea.render(context.batcher, BBSSettings.deepSurface());

        int duration = this.getDuration();

        if (duration > 0)
        {
            int leftBorder = this.toGraphX(0);

            if (leftBorder > this.graphArea.x)
            {
                int leftEx = Math.min(this.graphArea.ex(), leftBorder);

                context.batcher.box(this.graphArea.x, this.graphArea.y, leftEx, this.graphArea.y + this.graphArea.h, BBSSettings.sunkenSurface());
            }
        }

        if (this.backgroundRender != null)
        {
            context.batcher.clip(this.graphArea, context);
            this.backgroundRender.accept(context);
            context.batcher.unclip(context);
        }
    }

    /* Caching state */

    public KeyframeState cacheState()
    {
        KeyframeState state = new KeyframeState();

        ListType tracks = new ListType();
        for (UIKeyframeSheet sheet : this.graphTracks) tracks.addString(sheet.id);
        state.extra.put("graph_tracks", tracks);
        state.extra.putDouble("x_min", this.xAxis.getMinValue());
        state.extra.putDouble("x_max", this.xAxis.getMaxValue());
        state.extra.putBool("graph", this.isEditing());
        this.dopeSheet.saveState(state.extra);
        this.curveGraph.saveState(state.extra);
        MapType selection = new MapType();
        for (UIKeyframeSheet sheet : this.getSheets())
            selection.put(sheet.id, DataStorageUtils.intListToData(sheet.selection.getIndices()));
        state.extra.put("key_selection", selection);

        UIKeyframeSheet pickedSheet = this.dopeSheet.getSheet(this.pickedKeyframe);
        if (pickedSheet != null)
        {
            state.extra.putString("picked_track", pickedSheet.id);
            state.extra.putInt("picked_key", pickedSheet.channel.indexOf(this.pickedKeyframe));
        }

        return state;
    }

    public void applyState(KeyframeState state)
    {
        this.xAxis.view(state.extra.getDouble("x_min"), state.extra.getDouble("x_max"));
        this.currentGraph = state.extra.getBool("graph") ? this.curveGraph : this.dopeSheet;
        this.dopeSheet.restoreState(state.extra);
        this.curveGraph.restoreState(state.extra);
        this.pickedKeyframe = null;
        List<UIKeyframeSheet> properties = this.getSheets();

        MapType selections = state.extra.getMap("key_selection");
        for (UIKeyframeSheet sheet : properties)
        {
            sheet.selection.clear();
            if (selections.has(sheet.id)) sheet.selection.addAll(DataStorageUtils.intListFromData(selections.get(sheet.id)));
        }

        this.graphTracks.clear();
        for (BaseType id : state.extra.getList("graph_tracks"))
        {
            UIKeyframeSheet sheet = this.dopeSheet.getSheet(id.asString());
            if (sheet != null) this.graphTracks.add(sheet);
        }
        UIKeyframeSheet picked = this.dopeSheet.getSheet(state.extra.getString("picked_track"));
        this.pickedKeyframe = picked == null ? null : picked.channel.get(state.extra.getInt("picked_key"));
        this.currentGraph.pickSelected();
        this.resize();
    }

    /** Restore picks only for channels retained by the same editor/actor. */
    public void copySelection(UIKeyframes previous)
    {
        this.graphTracks.clear();
        for (UIKeyframeSheet sheet : this.dopeSheet.getSheets())
        {
            for (UIKeyframeSheet old : previous.dopeSheet.getSheets())
            {
                if (old.channel != sheet.channel) continue;
                sheet.selection.addAll(old.selection.getIndices());
                if (previous.isGraphTrack(old)) this.graphTracks.add(sheet);
                if (previous.pickedKeyframe != null && old.selection.has(previous.pickedKeyframe)) this.pickedKeyframe = previous.pickedKeyframe;
                break;
            }
        }

        this.currentGraph.pickSelected();
    }

    public void copyViewport(UIKeyframes lastEditor)
    {
        this.graphArea.copy(lastEditor.graphArea);
        this.currentGraph = lastEditor.isEditing() ? this.curveGraph : this.dopeSheet;
        this.curveGraph.copyViewport(lastEditor.curveGraph);
        this.getDopeSheet().setTrackHeight(lastEditor.getDopeSheet().getTrackHeight());
        this.getXAxis().copy(lastEditor.getXAxis());
        this.getDopeSheet().getYAxis().setScroll(lastEditor.getDopeSheet().getYAxis().getScroll());
    }

    public static class PastedKeyframes
    {
        public IKeyframeFactory factory;
        public List<Keyframe> keyframes = new ArrayList<>();

        public PastedKeyframes(IKeyframeFactory factory)
        {
            this.factory = factory;
        }
    }

    private static class SheetCache
    {
        public List<Pair<BaseType, UIKeyframeSheet>> data = new ArrayList<>();

        public SheetCache(Collection<UIKeyframeSheet> sheets)
        {
            this(sheets, false);
        }

        public SheetCache(Collection<UIKeyframeSheet> sheets, boolean all)
        {
            for (UIKeyframeSheet sheet : sheets)
            {
                if (all || sheet.selection.hasAny())
                {
                    this.data.add(new Pair<>(sheet.channel.toData(), sheet));
                }
            }
        }
    }
}
