package mchorse.bbs_mod.ui.forms.editors.states.keyframes;

import mchorse.bbs_mod.ui.utils.SplineKeyframeEditor;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TimelineBodyPartSelection;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackSearchEntry;
import mchorse.bbs_mod.ui.film.replays.overlays.UITrackSearchOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.api.client.editor.TrackCategories;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCategoryBar;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import mchorse.bbs_mod.graphics.window.InputCodes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import mchorse.bbs_mod.ui.utils.SplineOverlay;

public class UIAnimationStateEditor extends UIElement
{
    private final SplineOverlay splineOverlay = new SplineOverlay();

    public void renderSplineOverlay(UIContext context)
    {
        SplineEditorUtils.viewportHover(this.keyframeEditor, null);
        if (!this.splineOverlay.begin()) return;
        if (this.state == null || this.root == null || !this.isVisible()) return;
        var renderer = this.editor.renderer;
        var camera = renderer.camera;
        Matrix4f view = new Matrix4f(camera.view).translate((float) -camera.position.x, (float) -camera.position.y, (float) -camera.position.z);
        for (var entry : this.bodyParts.getList())
        {
            Form model = entry.getForm();
            for (var chain : SplineEditorUtils.sources(model))
            {
                Matrix4f parent = SplineEditorUtils.parentMatrix(this.root, renderer.getTargetEntity(), context.getTransition(), model, chain);
                if (parent == null) continue;
                this.splineOverlay.draw(context, new Matrix4f(view).mul(renderer.toSceneMatrix(parent)), camera.projection,
                    renderer.area, chain, SplineEditorUtils.selectedPoints(this.keyframeEditor, chain),
                    SplineEditorUtils.hoveredPoint(this.keyframeEditor, context, chain));
            }
        }
        this.splineOverlay.finish(context, renderer.area);
        if (Window.isCtrlPressed() && !context.isFocused()
            && this.keyframeEditor.editor instanceof SplineKeyframeEditor spline
            && !spline.pointEditor().position.isEditing())
        {
            var insertion = this.splineOverlay.insertionAt(context.mouseX, context.mouseY);
            if (insertion != null && spline.sameSource(insertion.chain())) this.splineOverlay.drawInsertionPreview(context, renderer.area);
        }
        SplineEditorUtils.viewportHover(this.keyframeEditor, renderer.area.isInside(context)
            ? this.splineOverlay.pick(context.mouseX, context.mouseY) : null);
    }

    public boolean pickSplinePoint(UIContext context)
    {
        if (this.state == null || (context.mouseButton != 0 && context.mouseButton != 1) || !this.editor.renderer.area.isInside(context)) return false;
        if (context.mouseButton == 0 && Window.isCtrlPressed() && !context.isFocused()
            && this.keyframeEditor.editor instanceof SplineKeyframeEditor spline
            && !spline.pointEditor().position.isEditing())
        {
            var insertion = this.splineOverlay.insertionAt(context.mouseX, context.mouseY);
            if (insertion != null && spline.sameSource(insertion.chain()))
            {
                spline.pointEditor().insert(insertion.after(), insertion.position());
                return true;
            }
        }
        var hit = this.splineOverlay.pick(context.mouseX, context.mouseY);
        if (hit == null) return false;
        String path = FormUtils.getPropertyPath(hit.point().position);
        String compound = SplineEditorUtils.compoundPath(path);
        for (TrackSearchEntry entry : UIReplaysEditorUtils.formSearchEntries(this.root, this.bodyParts, true))
        {
            if (!entry.key().equals(compound)) continue;
            this.revealTrack(entry, false);
            UIReplaysEditorUtils.pickPropertyTrack(this.keyframeEditor, this.editor, compound, context.mouseButton == 1);
            SplineEditorUtils.selectPoint(this.keyframeEditor, path);
            return true;
        }
        return false;
    }
    private static final int CATEGORY_BAR_WIDTH = 20;
    public UIKeyframeEditor keyframeEditor;

    public UIFormEditor editor;
    public UIElement editArea;
    public final UIForms bodyParts;

    private final UIElement sidebar = new UIElement();
    private final UIElement timelineArea = new UIElement()
    {
        @Override
        protected void afterResizeApplied()
        {
            UIAnimationStateEditor.this.layoutCategoryBar();
        }
    };
    private final UITimelineCategoryBar categoryBar = new UITimelineCategoryBar(0);
    private final UIElement partHeader = new UIElement()
    {
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            return this.area.isInside(context);
        }
    };
    private final Map<TrackCategory, UIIcon> categoryButtons = new java.util.LinkedHashMap<>();
    private final List<TrackCategory> visibleCategories = new ArrayList<>();
    private TrackCategory category = TrackCategory.FORM;
    private boolean allMode = true;
    private final UISection bodyPartsSection = new UISection(L10n.lang("bbs.ui.film.replays.body_parts"));
    private Form root;
    private TimelineBodyPartSelection partSelection = new TimelineBodyPartSelection();
    private boolean allBodyPartsSetting;

    private AnimationState state;
    private TrackSearchEntry revealedSearchTrack;
    private final Map<AnimationState, List<String>> recentTrackSearches = new java.util.IdentityHashMap<>();
    private Set<String> keys = new LinkedHashSet<>();
    private int poseOverlayCount;
    private int transformOverlayCount;

    /** Track rows the user has unfolded right now; handed to the dope sheet, which folds them in place. */
    private final FoldState<String> expandedTabs = new FoldState<>();

    public UIAnimationStateEditor(UIFormEditor editor)
    {
        this.editor = editor;
        this.setUndoId("form_animation_state_editor");
        this.bodyParts = new UIForms(this::selectParts).timelineSelection();
        this.sidebar.relative(this).x(BBSSettings.editorLayoutSettings.getStateEditorSizeH()).wTo(this.area, 1F).h(1F);
        this.timelineArea.relative(this).y(1F).anchorY(1F).wTo(this.sidebar.area)
            .h(BBSSettings.editorLayoutSettings.getStateEditorSizeV());

        this.categoryBar.relative(this.timelineArea);
        UIIcon all = new UIIcon(Icons.LIST, button -> this.setCategory(null));
        all.tooltip(UIKeys.FILM_REPLAY_ALL_TRACKS, Direction.RIGHT);
        all.highlight(() -> this.allMode, Direction.LEFT);
        this.categoryBar.add(all);

        for (TrackCategory category : TrackCategories.values())
        {
            if (category == TrackCategory.REPLAY) continue;
            UIIcon button = new UIIcon(category.icon, b -> this.setCategory(category));
            button.tooltip(category.tooltip, Direction.RIGHT);
            button.highlight(() -> !this.allMode && this.category == category, Direction.LEFT);
            this.categoryButtons.put(category, button);
        }

        TrackCategories.registerShortcuts(this.keys(), () -> this.visibleCategories, this::setCategory);
        this.keys().register(Keys.FILM_TRACK_SEARCH, this::openTrackSearch)
            .active(() -> this.state != null && this.isVisible());

        this.partHeader.relative(this.timelineArea).x(CATEGORY_BAR_WIDTH).w(120).h(TimelineRulerRenderer.RULER_BLOCK_HEIGHT);
        this.partHeader.add(new UIRenderable(context -> this.partHeader.area.render(context.batcher, BBSSettings.baseSurface())));
        UILabel partName = new UILabel(this::getSelectedPartName).color(0xffaaaaaa, false).labelAnchor(0F, 0.5F);
        partName.relative(this.partHeader).x(5).w(1F, -UIConstants.ICON_SIZE - 10).h(1F);
        partName.tooltip(() -> L10n.lang("bbs.ui.film.replays.selected_body_part").format(this.getSelectedPartName()).get());
        UIIcon graph = UIKeyframes.modeButton(() -> this.keyframeEditor == null ? null : this.keyframeEditor.view);
        graph.relative(this.partHeader).x(1F, -UIConstants.ICON_SIZE).y(0.5F).anchorY(0.5F);
        this.partHeader.add(partName, graph);
        this.timelineArea.add(this.categoryBar, this.partHeader);

        this.bodyPartsSection.relative(this.sidebar).x(3).y(1F, -3).w(1F, -6).anchorY(1F);
        this.bodyPartsSection.fields.add(this.bodyParts);
        this.bodyPartsSection.setVisible(false);

        this.editArea = new UIElement();
        this.editArea.relative(this.sidebar).w(1F).hTo(this.bodyPartsSection.area);
        /* The section's natural height determines how much space remains for properties. */
        this.sidebar.add(this.bodyPartsSection, this.editArea);

        UIDraggable draggable = new UIDraggable((context) ->
        {
            float fx = (context.mouseX - this.area.x) / (float) this.area.w;
            float fy = (this.area.ey() - context.mouseY) / (float) this.area.h;

            BBSSettings.editorLayoutSettings.setStateEditorSizeV(fy);
            BBSSettings.editorLayoutSettings.setStateEditorSizeH(fx);

            this.timelineArea.h(BBSSettings.editorLayoutSettings.getStateEditorSizeV());
            this.sidebar.x(BBSSettings.editorLayoutSettings.getStateEditorSizeH());
            this.getParent().resize();
        });
        draggable.cursors(InputCodes.CROSSHAIR_CURSOR, InputCodes.CROSSHAIR_CURSOR);

        draggable.reference(() -> new Vector2i(this.sidebar.area.x, this.timelineArea.area.y));
        draggable.rendering((context) ->
        {
            int size = 5;
            int x = this.sidebar.area.x + 3;
            int y = this.timelineArea.area.y + 3;

            context.batcher.box(x, y, x + 1, y + size, Colors.WHITE);
            context.batcher.box(x, y - 1, x + size, y, Colors.WHITE);

            x = this.sidebar.area.x - 3;
            y = this.timelineArea.area.y + 3;

            context.batcher.box(x - 1, y, x, y + size, Colors.WHITE);
            context.batcher.box(x - size, y - 1, x, y, Colors.WHITE);
        });

        draggable.hoverOnly().relative(this.timelineArea).x(1F).w(40).h(6).anchorX(0.5F);

        this.add(this.sidebar, this.timelineArea, draggable);
    }

    public AnimationState getState()
    {
        return this.state;
    }

    private void setCategory(TrackCategory category)
    {
        this.revealedSearchTrack = null;
        this.allMode = category == null;
        if (category != null) this.category = category;
        this.setState(this.state);
    }

    private String getSelectedPartName()
    {
        return this.partSelection.title(this.bodyParts);
    }

    private void selectParts(List<UIForms.FormEntry> parts)
    {
        this.partSelection.select(parts, this.bodyParts.getActivePath());
        this.revealedSearchTrack = null;
        this.setState(this.state);
    }

    public void setState(AnimationState state)
    {
        this.allBodyPartsSetting = TimelineBodyPartSelection.allParts();
        this.poseOverlayCount = BBSSettings.recordingPoseOverlays.get();
        this.transformOverlayCount = BBSSettings.recordingTransformOverlays.get();
        UIKeyframes lastEditor = null;

        if (this.keyframeEditor != null)
        {
            lastEditor = this.keyframeEditor.view;

            this.keyframeEditor.removeFromParent();
            this.keyframeEditor = null;
        }

        if (this.state != state) this.revealedSearchTrack = null;
        this.state = state;

        if (this.root != this.editor.form)
        {
            this.root = this.editor.form;
            this.revealedSearchTrack = null;
            this.recentTrackSearches.clear();
            this.partSelection = new TimelineBodyPartSelection();
            this.expandedTabs.collapseAll();
        }

        double scroll = this.bodyParts.scroll.getScroll();

        this.bodyParts.setForm(this.root);
        this.bodyParts.scroll.setScroll(scroll);
        this.partSelection.restore(this.bodyParts);

        if (this.state == null)
        {
            this.resize();

            return;
        }

        List<UIKeyframeSheet> sheets = new ArrayList<>();

        /* A state lays a form's own values over it; the solver tracks only mean anything inside a
         * film, where something clears them again every frame. */
        List<TrackDescriptor> catalog = new ArrayList<>();

        for (TrackDescriptor track : TrackCatalog.ordered(TrackCatalog.of(this.root, this.state.properties)))
        {
            if (!track.kind().isSolver() && this.partSelection.includes(track.id().formPath()))
            {
                catalog.add(track);
            }
        }

        this.visibleCategories.clear();
        for (var entry : this.categoryButtons.entrySet())
        {
            entry.getValue().removeFromParent();
            TrackCategory candidate = entry.getKey();
            if (candidate == TrackCategory.FORM || catalog.stream()
                .anyMatch(track -> TrackCategories.categoryOf(track.id(), track.owner() != null) == candidate))
            {
                this.visibleCategories.add(candidate);
                this.categoryBar.add(entry.getValue());
            }
        }
        if (!this.visibleCategories.contains(this.category)) this.category = TrackCategory.FORM;

        UIReplaysEditorUtils.buildSheets(catalog, sheets, this.state.properties);

        this.keys.clear();

        for (UIKeyframeSheet sheet : sheets)
        {
            this.keys.add(UIReplaysEditor.getSheetFilterKey(sheet));
        }

        sheets.removeIf((v) -> v.id.equals("anchor"));

        sheets.removeIf(sheet -> !this.allMode && UIReplaysEditor.categoryOf(sheet) != this.category);

        /* The state isn't empty by itself - so if the filter empties it, the timeline has to stay (see below). */
        boolean hadTracks = !sheets.isEmpty();

        sheets.removeIf(sheet -> (this.revealedSearchTrack == null || !this.revealedSearchTrack.key().equals(sheet.id))
            && UIReplaysEditorUtils.isTrackHidden(sheet.id, UIReplaysEditor.getSheetFilterKey(sheet), UIReplaysEditor.getSheetForm(sheet)));

        UIReplaysEditorUtils.pruneTree(sheets);
        this.partSelection.groupSheets(sheets, this.bodyParts, this.expandedTabs);

        /*
         * Filtering every track off used to drop the timeline itself, and the track filter lives in its
         * context menu - so «disable all» locked the user out of the only way back. Keep the (empty)
         * timeline whenever the state had tracks before the filter ran; the dope sheet says why it's blank.
         */
        if (!sheets.isEmpty() || hadTracks || !this.allMode)
        {
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIAnimationStateKeyframes(this.editor, consumer)).target(this.editArea);
            this.keyframeEditor.relative(this.timelineArea).x(CATEGORY_BAR_WIDTH).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
            this.keyframeEditor.setUndoId("form_animation_state_keyframe_editor");
            this.keyframeEditor.view.getDopeSheet().setEmptyState(UIKeys.KEYFRAMES_EMPTY_FILTERED, UIKeys.KEYFRAMES_EMPTY_FILTERED_HINT);

            this.keyframeEditor.view.duration(() -> this.state.duration.get());
            this.keyframeEditor.view.context((menu) ->
            {
                menu.action(Icons.SEARCH, Keys.FILM_TRACK_SEARCH.label, this::openTrackSearch);
                int mouseY = this.getContext().mouseY;
                UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(this.getContext().mouseX, mouseY);

                UIReplaysEditorUtils.addOverlayTrackAction(menu, sheet, parent ->
                {
                    this.expandedTabs.set(parent.toKey(), true);
                    this.setState(this.state);
                });

                IPosedForm posedForm = sheet == null ? null : sheet.getPosedForm();
                if (posedForm != null && sheet.selection.hasAny() && posedForm.hasBoneTracks())
                {
                    menu.action(Icons.LIMB, UIKeys.FILM_REPLAY_CONTEXT_POSES_TO_LIMBS, () ->
                    {
                        if (UIReplaysEditorUtils.posesToLimbTracks(this.state.properties, sheet))
                        {
                            this.expandedTabs.set(sheet.id, true);
                            this.setState(this.state);
                        }
                    });
                }

                ModelForm poseModelForm = sheet == null ? null : sheet.getPoseForm();

                if (poseModelForm != null)
                {
                    menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
                    {
                        ModelInstance model = ModelFormRenderer.getModel(poseModelForm);

                        if (model != null)
                        {
                            UIOverlay.addOverlay(this.getContext(), new UIAnimationToPoseOverlayPanel((animationKey, onlyKeyframes, length, step) ->
                            {
                                float current = this.keyframeEditor.view.getTick();
                                IEntity entity = this.editor.renderer.getTargetEntity();

                                UIReplaysEditorUtils.animationToPoseKeyframes(this.keyframeEditor, sheet, poseModelForm, entity, current, animationKey, onlyKeyframes, length, step);
                            }, poseModelForm, sheet), 200, 197);
                        }
                    });
                }

                menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                {
                    Map<String, Integer> keyToColor = new HashMap<>();
                    for (UIKeyframeSheet listed : this.keyframeEditor.view.getSheets())
                    {
                        keyToColor.put(UIReplaysEditor.getSheetFilterKey(listed), listed.color);
                    }
                    UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(BBSSettings.disabledSheets.get(), this.keys, keyToColor);

                    UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

                    panel.onClose((e) ->
                    {
                        this.revealedSearchTrack = null;
                        this.setState(this.state);
                        BBSSettings.disabledSheets.set(BBSSettings.disabledSheets.get());
                    });
                });

            });

            for (UIKeyframeSheet sheet : sheets)
            {
                this.keyframeEditor.view.addSheet(sheet);
            }

            /* The tracks that fold under another one fold here too: a model form contributes dozens of
             * bone and material rows, and unfolded they bury the form's own properties. */
            this.keyframeEditor.view.getDopeSheet().setExpanded(this.expandedTabs);
            if (lastEditor != null)
            {
                this.keyframeEditor.view.copyViewport(lastEditor);
                this.keyframeEditor.view.copySelection(lastEditor);
            }

            this.timelineArea.add(this.keyframeEditor);
            this.categoryBar.removeFromParent();
            this.partHeader.removeFromParent();
            this.timelineArea.add(this.categoryBar, this.partHeader);
        }

        this.resize();

        if (this.keyframeEditor != null && lastEditor == null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    public void openTrackSearch()
    {
        if (this.state == null || this.root == null) return;
        AnimationState source = this.state;
        List<TrackSearchEntry> entries = UIReplaysEditorUtils.formSearchEntries(this.root, this.bodyParts, true);
        List<String> recent = this.recentTrackSearches.computeIfAbsent(source, id -> new ArrayList<>());
        UITrackSearchOverlayPanel.open(this.getContext(), entries, this.partSelection.activePart, recent,
            (entry, createKeyframe) -> this.state == source && this.revealTrack(entry, createKeyframe));
    }

    private boolean revealTrack(TrackSearchEntry entry, boolean createKeyframe)
    {
        this.partSelection.focus(entry.path());
        this.revealedSearchTrack = entry;
        this.allMode = false;
        this.category = UIReplaysEditor.categoryOf(TrackId.parse(entry.key()), true);
        this.setState(this.state);
        return this.keyframeEditor != null
            && UIReplaysEditorUtils.revealSearchTrack(this.keyframeEditor, entry, createKeyframe, this.getContext());
    }

    private void selectPart(String path)
    {
        if (!this.partSelection.activePart.equals(path))
        {
            this.partSelection.focus(path);
            this.revealedSearchTrack = null;
            this.setState(this.state);
        }
    }

    private void selectForm(Form form)
    {
        if (form == null)
        {
            return;
        }

        for (UIForms.FormEntry entry : this.bodyParts.getList())
        {
            if (entry.getForm() == form)
            {
                this.selectPart(entry.getPath());

                return;
            }
        }
    }

    private void layoutCategoryBar()
    {
        int width = this.categoryBar.getWidthForHeight(this.timelineArea.area.h);

        this.categoryBar.w(width);
        this.partHeader.x(width);

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.x(width).w(1F, -width);
        }
    }

    @Override
    public void resize()
    {
        int maxHeight = Math.min(160, this.getFlex().getH() / 2);

        this.bodyPartsSection.setVisible(!TimelineBodyPartSelection.allParts() && this.state != null && this.bodyParts.getList().size() > 1);
        this.bodyParts.h(Math.max(1, Math.min(this.bodyParts.getList().size() * this.bodyParts.scroll.scrollItemSize,
            maxHeight)));
        this.editArea.hTo(this.bodyPartsSection.isVisible() ? this.bodyPartsSection.area : this.sidebar.area,
            this.bodyPartsSection.isVisible() ? 0F : 1F);

        super.resize();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);
        this.partSelection.write(data);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);
        this.partSelection.read(data);
        this.setState(this.state);
    }

    public boolean clickViewport(UIContext context, StencilFormFramebuffer stencil)
    {
        if (stencil.hasPicked() && this.state != null)
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null)
            {
                return UIReplaysEditorUtils.pickFormWithOffers(context, pair, (form, bone, insert) ->
                {
                    this.pickFormBone(form, bone, insert);
                });
            }
        }

        return false;
    }

    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(this.keyframeEditor);
        GizmoDrag drag = this.buildGizmoDrag(transform, context.getTransition());

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);
    }

    public void pickForm(Form form, String bone)
    {
        this.pickFormBone(form, bone, false);
    }

    private void pickFormBone(Form form, String bone, boolean insert)
    {
        this.selectForm(form);
        if (!this.allMode && this.category != TrackCategory.FORM && form != null
            && (!(form instanceof IPosedForm) || (bone != null && !bone.isEmpty())))
        {
            this.setCategory(TrackCategory.FORM);
        }
        UIReplaysEditorUtils.pickForm(this.keyframeEditor, this.editor, form, bone, insert);
    }

    /**
     * Bone selection for the renderer's deferred sphere pick (a click that didn't turn into a
     * trackball drag). Mirrors the left-click branch of {@link #clickViewport}.
     */
    public void pickFormFromRenderer(Pair<Form, String> pair)
    {
        if (Window.isCtrlPressed()) UIReplaysEditorUtils.offerAdjacent(this.getContext(), pair.a, pair.b, (bone) -> this.pickForm(pair.a, bone));
        else if (Window.isShiftPressed()) UIReplaysEditorUtils.offerHierarchy(this.getContext(), pair.a, pair.b, (bone) -> this.pickForm(pair.a, bone));
        else this.pickForm(pair.a, pair.b);
    }

    private GizmoDrag buildGizmoDrag(UIPropTransform transform, float transition)
    {
        if (transform == null || transform.getTransform() == null)
        {
            return null;
        }

        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.editor.renderer.camera, this.editor.renderer.area);

        if (drag != null)
        {
            float tick = this.editor.getSamplingTick();

            var point = SplineEditorUtils.resolve(this.root, SplineEditorUtils.selectedPath(this.keyframeEditor));
            if (point != null)
            {
                this.editor.applyStateForSampling(tick);
                Matrix4f parent = SplineEditorUtils.parentMatrix(this.root, this.editor.renderer.getTargetEntity(), transition, point.form(), point.chain());
                if (parent == null) return null;
                parent = this.editor.renderer.toSceneMatrix(parent);
                if (Math.abs(parent.determinant()) < 1E-8F) return null;
                drag.setGlobalAxes(this.editor.renderer.getSceneAxes());
                drag.setJacobian(new org.joml.Matrix3f(parent));
                drag.setFrameAxes(parent, parent);
                return drag;
            }

            /* The frame GLOBAL is drawn in — the preview's scene axes (see
             * UIPickableFormRenderer#renderAxes); identity unless the form is
             * being edited inside a rotated model block. */
            drag.setGlobalAxes(this.editor.renderer.getSceneAxes());

            /* The bone matrices come from the previewed form, which only reflects a keyframe edit
             * once the animation state is re-applied. computeRotateAxes / computeTranslateJacobian
             * perturb the keyframe transform, so re-pose the form before each sample (mirroring the
             * film's buildFilmGizmoDrag); otherwise the perturbation leaves no trace and the gizmo
             * axes collapse to identity, breaking the trackball and view rotation. */
            drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                transform.getTransform(),
                UIReplaysEditorUtils.keyframeGizmoSampler(this.keyframeEditor, transform, () ->
                {
                    this.editor.applyStateForSampling(tick);

                    Matrix4f origin = this.getOrigin(transition);

                    /* Into the frame the preview is drawn in, like UIFormEditor's
                     * drag: the gizmo's own origin/axes come from the render
                     * matrix and already carry the renderer's transform. */
                    return origin == null ? new Vector3f() : this.editor.renderer.toSceneMatrix(origin).getTranslation(new Vector3f());
                })
            ));
            drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                transform.getTransform(),
                UIReplaysEditorUtils.keyframeGizmoSampler(this.keyframeEditor, transform, () ->
                {
                    this.editor.applyStateForSampling(tick);

                    /* Always sample the rotation-bearing matrix; the GLOBAL
                     * keyframe variant would otherwise return an origin
                     * matrix without rotation and the axis sampling would
                     * collapse to identity. */
                    Matrix4f origin = this.getOriginMatrix(transition);

                    return origin == null ? new Matrix4f() : MatrixStackUtils.stripScale(this.editor.renderer.toSceneMatrix(origin));
                })
            ));

            /* Both bone frames, so a gesture walked into the other one mid-edit gets its
             * real axes instead of the ones the handles were drawn on. Sampled after the
             * pose is restored below? No — the compute* helpers have already reverted the
             * perturbed transform, so re-posing once here is enough for both reads. */
            this.editor.applyStateForSampling(tick);

            drag.setFrameAxes(
                this.editor.renderer.toSceneMatrix(this.getOriginMatrix(transition)),
                this.editor.renderer.toSceneMatrix(this.getParentOriginMatrix(transition))
            );

            /* Restore the previewed form to the unperturbed pose: the compute* helpers above have
             * already reverted the transform, so re-applying now poses it with the original values. */
            this.editor.applyStateForSampling(tick);
        }

        return drag;
    }

    public Matrix4f getOrigin(float transition)
    {
        return this.getOriginInternal(transition, false);
    }

    /** The frame the states gizmo is drawn in: the active keyframe transform's own, like
     *  the film and the form editor's pose panel read it. 🔴 Never hardcode LOCAL here —
     *  the gizmo would be drawn on the bone's axes while the drag ran in the picked frame. */
    public TransformSpace getGizmoSpace()
    {
        return this.keyframeEditor == null ? TransformSpace.LOCAL : this.keyframeEditor.getBoneSpace();
    }

    /**
     * Same as {@link #getOrigin(float)} but always returns the rotation-bearing
     * matrix regardless of the keyframe's GLOBAL flag. Required for the
     * sampling-based rotation-axis helper in {@link GizmoDrag}.
     */
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOriginInternal(transition, true);
    }

    /**
     * The twin of {@link #getOriginMatrix}: always the origin flavour — the frame
     * before the bone's own rotation, i.e. its parent's. The pair feeds the drag
     * snapshot's two bone frames ({@code GizmoDrag#setFrameAxes}), which is what lets
     * a gesture be walked from LOCAL into PARENT (or back) mid-edit.
     */
    public Matrix4f getParentOriginMatrix(float transition)
    {
        return this.getOriginFlavour(transition, TransformSpace.PARENT.placesOnOwnFrame());
    }

    private Matrix4f getOriginInternal(float transition, boolean forceMatrix)
    {
        var point = SplineEditorUtils.resolve(this.root, SplineEditorUtils.selectedPath(this.keyframeEditor));
        if (point != null)
        {
            Matrix4f matrix = SplineEditorUtils.pointMatrix(this.root, this.editor.renderer.getTargetEntity(), transition, point);
            return matrix == null ? Matrices.EMPTY_4F : matrix;
        }
        if (this.keyframeEditor == null)
        {
            return Matrices.EMPTY_4F;
        }

        Pair<String, TransformSpace> bone = this.keyframeEditor.getBone();

        if (bone == null)
        {
            return Matrices.EMPTY_4F;
        }

        /* Placement flavour, THE shared convention (film's renderAxes, the form
         * editor's pose and body-part paths): LOCAL sits on the bone's own frame
         * (its full matrix), every other space on the origin flavour — the frame
         * BEFORE the bone's own rotation, i.e. the parent frame, which PARENT
         * keeps as-is and GLOBAL/VIEW reorient away from. This editor had the two
         * swapped, so its LOCAL gizmo drew the parent's axes. forceMatrix keeps
         * the rotation-bearing matrix for the axis sampler regardless. */
        return this.getOriginFlavour(transition, forceMatrix || bone.b.placesOnOwnFrame());
    }

    /** One of the bone's two frames: its own ({@code ownFrame}) or its parent's. */
    private Matrix4f getOriginFlavour(float transition, boolean ownFrame)
    {
        var point = SplineEditorUtils.resolve(this.root, SplineEditorUtils.selectedPath(this.keyframeEditor));
        if (point != null)
        {
            Matrix4f matrix = SplineEditorUtils.pointMatrix(this.root, this.editor.renderer.getTargetEntity(), transition, point);
            return matrix == null ? Matrices.EMPTY_4F : matrix;
        }
        if (this.keyframeEditor == null)
        {
            return Matrices.EMPTY_4F;
        }

        Pair<String, TransformSpace> bone = this.keyframeEditor.getBone();

        if (bone == null)
        {
            return Matrices.EMPTY_4F;
        }

        Form root = FormUtils.getRoot(this.editor.form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(this.editor.renderer.getTargetEntity(), transition);
        Matrix4f matrix = ownFrame ? map.get(bone.a).matrix() : map.get(bone.a).origin();

        return matrix == null ? Matrices.EMPTY_4F : matrix;
    }

    @Override
    public void render(UIContext context)
    {
        /* Settings can change while this timeline remains open behind another panel. */
        if (this.state != null && (this.allBodyPartsSetting != TimelineBodyPartSelection.allParts()
            || this.poseOverlayCount != BBSSettings.recordingPoseOverlays.get()
            || this.transformOverlayCount != BBSSettings.recordingTransformOverlays.get()))
        {
            this.setState(this.state);
        }

        this.categoryBar.setVisible(this.state != null);
        this.partHeader.setVisible(this.state != null && this.keyframeEditor != null);
        if (this.partHeader.isVisible())
        {
            int width = Math.min(this.keyframeEditor.view.getLabelWidth(), this.keyframeEditor.view.area.w);
            if (this.partHeader.area.w != width) this.partHeader.w(width).resize();
        }

        if (this.keyframeEditor != null)
        {
            UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(this.keyframeEditor);

            if (transform != null)
            {
                transform.hotkeyDrag(() ->
                {
                    UIContext current = this.getContext();

                    return this.buildGizmoDrag(transform, current == null ? 0F : current.getTransition());
                });
            }

            this.editArea.area.render(context.batcher, Colors.A75);
        }

        super.render(context);
    }
}
