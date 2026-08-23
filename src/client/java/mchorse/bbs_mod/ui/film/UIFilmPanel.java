package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FrozenFilmController;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.utils.IUIOrbitKeysHandler;
import mchorse.bbs_mod.ui.film.audio.UIAudioRecorder;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.UIFilmUndoHandler;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.presets.PresetManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class UIFilmPanel extends UIDataDashboardPanel<Film> implements IFlightSupported, IUIOrbitKeysHandler, ICursor
{
    private static final int PREVIEW_MODE_EXPORT = 0;
    private static final int PREVIEW_MODE_CUSTOM = 1;
    private static final int PREVIEW_MODE_AUTO = 2;

    private static final Logger LOGGER = LogUtils.getLogger();

    private RunnerCameraController runner;
    private boolean lastRunning;
    private boolean restartPending;
    private int lastRestartCursor = -1;
    private final Position position = new Position(0, 0, 0, 0, 0);
    private final Position lastPosition = new Position(0, 0, 0, 0, 0);

    public UIFilmSelectionPanel selectionPanel;

    public UIElement main;
    public UIElement editArea;
    public UIDockLayout dock;
    public UIFilmRecorder recorder;
    public UIFilmPreview preview;

    public UIIcon duplicateFilm;

    /* Main editors */
    public UIClipsPanel cameraEditor;
    public UIReplaysEditor replayEditor;
    public UIClipsPanel actionEditor;

    /* Icon bar buttons */
    public UIIcon openFilmMenu;
    public UIIcon openCameraEditor;
    public UIIcon openReplayEditor;

    private UICopyPasteController layoutPresetsController;

    private Camera camera = new Camera();
    private boolean entered;
    public boolean playerToCamera;

    /* Entity control */
    private UIFilmController controller = new UIFilmController(this);
    private UIFilmUndoHandler undoHandler;

    public final Matrix4f lastView = new Matrix4f();
    public final Matrix4f lastProjection = new Matrix4f();

    private Timer flightEditTime = new Timer(100);
    private long lastTime;
    private double timeSpentActiveAccumulator;
    private final FilmEditorUserActivity filmUserActivity = new FilmEditorUserActivity();

    private List<UIElement> panels = new ArrayList<>();
    private UIElement secretPlay;

    private boolean newFilm;
    private double timelineXMin = Double.NaN;
    private double timelineXMax = Double.NaN;
    /** Vertical timeline scroll per film, so switching film tabs restores where each one was left. */
    private final Map<String, FilmTimelineScroll> timelineScrollByFilm = new HashMap<>();

    private FilmQueueExporter queueExporter;

    /* Docking: panel ids arranged by the dock layout */
    private static final String PANEL_MAIN_ID = "main";
    private static final String PANEL_PREVIEW_ID = "preview";
    private static final String PANEL_EDIT_AREA_ID = "editArea";
    private static final String PANEL_REPLAYS_LIST_ID = "replaysList";
    private static final String PANEL_REPLAY_PROPS_ID = "replayProps";
    private UIElement selectedMainEditorPanel;

    /**
     * Initialize the camera editor with a camera profile.
     */
    public UIFilmPanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.playerToCamera = BBSSettings.editorPlayerFollowsCamera.get();

        this.runner = new RunnerCameraController(this, (playing) ->
        {
            this.notifyServer(playing ? ActionState.PLAY : ActionState.PAUSE);
        });
        this.runner.getContext().captureSnapshots();

        this.recorder = new UIFilmRecorder(this);

        this.main = new UIElement();
        this.editArea = new UIElement();
        this.preview = new UIFilmPreview(this);

        /* Editors */
        this.cameraEditor = new UIClipsPanel(this, BBSMod.getFactoryCameraClips()).target(this.editArea);
        this.cameraEditor.full(this.main);

        this.cameraEditor.clips.context((menu) ->
        {
            UIAudioRecorder.addOption(this, menu);
        });

        this.replayEditor = new UIReplaysEditor(this);
        this.replayEditor.full(this.main).setVisible(false);
        this.actionEditor = new UIClipsPanel(this, BBSMod.getFactoryActionClips()).target(this.editArea);
        this.actionEditor.setVisible(false);
        this.replayEditor.attachActionTimeline(this.actionEditor);

        this.selectedMainEditorPanel = this.cameraEditor;

        /* Film panel keeps common CRUD actions inside the film menu instead of the action bar. */
        this.actions().dismiss(this.saveIcon);

        this.openFilmMenu = new UIIcon(Icons.MORE, (b) ->
        {
            this.getContext().replaceContextMenu(this::fillFilmContextMenu);
        });
        this.openCameraEditor = new UIIcon(Icons.FRUSTUM, (b) -> this.showPanel(this.cameraEditor));
        this.openReplayEditor = new UIIcon(Icons.SCENE, (b) -> this.showPanel(this.replayEditor));

        this.layoutPresetsController = new UICopyPasteController(PresetManager.LAYOUTS, "_CopyFilmLayout")
            .supplier(this::getFilmLayoutPresetData)
            .consumer(this::applyFilmLayoutFromPreset);

        this.openFilmMenu.tooltip(UIKeys.FILM_OPTIONS);
        this.openCameraEditor.tooltip(UIKeys.FILM_OPEN_CAMERA_EDITOR);
        this.openReplayEditor.tooltip(UIKeys.FILM_OPEN_REPLAY_EDITOR);

        this.actions()
            .action(this.openCameraEditor, this.cameraEditor::isVisible)
            .action(this.openReplayEditor, this.replayEditor::isVisible)
            .menu(this.openFilmMenu);

        /* Setup elements */

        this.main.add(this.cameraEditor, this.replayEditor);
        this.add(this.controller);
        this.overlay.namesList.setFileIcon(Icons.FILM);

        /* Register keybinds */
        IKey modes = UIKeys.CAMERA_EDITOR_KEYS_MODES_TITLE;
        IKey editor = UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE;
        IKey looping = UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE;
        Supplier<Boolean> active = () -> !this.isFlying();

        this.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(active).category(editor);
        this.keys().register(Keys.NEXT_CLIP, () -> this.setCursor(this.data.camera.findNextTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.PREV_CLIP, () -> this.setCursor(this.data.camera.findPreviousTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.NEXT, () -> this.setCursor(this.getCursor() + 1)).active(active).category(editor);
        this.keys().register(Keys.PREV, () -> this.setCursor(this.getCursor() - 1)).active(active).category(editor);
        this.keys().register(Keys.UNDO, this::undo).category(editor);
        this.keys().register(Keys.REDO, this::redo).category(editor);
        this.keys().register(Keys.FLIGHT, this::toggleFlight).active(() -> this.data != null).category(modes);
        this.keys().register(Keys.LOOPING, () ->
        {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            this.getContext().notifyInfo(UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TOGGLE_NOTIFICATION);
        }).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MIN, () -> this.cameraEditor.clips.setLoopMin()).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MAX, () -> this.cameraEditor.clips.setLoopMax()).active(active).category(looping);
        this.keys().register(Keys.JUMP_FORWARD, () -> this.setCursor(this.getCursor() + BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.JUMP_BACKWARD, () -> this.setCursor(this.getCursor() - BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, () ->
        {
            this.showPanel(MathUtils.cycler(this.getPanelIndex() + 1, this.panels));
            UIUtils.playClick();
        }).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ACTIONS, () ->
        {
            this.showPanel(this.replayEditor);
            this.replayEditor.setActionsMode(!this.replayEditor.isActionsMode());
            UIUtils.playClick();
        }).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_NEXT_DOCK_TAB, () ->
        {
            if (this.dock.cycleDockStackTab(1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_PREV_DOCK_TAB, () ->
        {
            if (this.dock.cycleDockStackTab(-1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);
        this.keys().register(Keys.DOCK_MAXIMIZE, () ->
        {
            if (this.dock.toggleMaximizeUnderCursor())
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);
        this.keys().register(Keys.DOCK_UNDO_LAYOUT, () ->
        {
            if (this.dock.undoLayout())
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);

        this.selectionPanel = new UIFilmSelectionPanel(this);

        /* Dockable layout, shared with the particle editor. */
        this.dock = new UIDockLayout();
        this.dock.relative(this.editor).w(1F).h(1F);
        this.dock.source(this.createLayoutSource())
            .locked(!BBSSettings.editorLayoutSettings.isDockUnlocked(ValueEditorLayout.FILM))
            .frameless(PANEL_PREVIEW_ID)
            .gate(this::hasFilmInCurrentTab)
            .ensure(this::ensureFilmLayoutPanels)
            .onChanged(this::onDockLayoutChanged)
            .onLayoutSettled(this::applyPreviewSizeToBBS);
        this.dock.addPanel(PANEL_EDIT_AREA_ID, this.editArea, Icons.EDITOR, UIKeys.FILM_PANELS_EDIT_AREA);
        this.dock.addPanel(PANEL_MAIN_ID, this.main, Icons.FILM, UIKeys.FILM_PANELS_MAIN);
        this.dock.addPanel(PANEL_PREVIEW_ID, this.preview, Icons.VIDEO_CAMERA, UIKeys.FILM_PANELS_PREVIEW);
        this.dock.addPanel(PANEL_REPLAYS_LIST_ID, this.replayEditor.replaysList, Icons.LIST, UIKeys.FILM_PANELS_REPLAYS_LIST);
        this.dock.addPanel(PANEL_REPLAY_PROPS_ID, this.replayEditor.replayProperties, Icons.PROPERTIES, UIKeys.FILM_PANELS_REPLAY_PROPS);
        this.dock.mount();
        this.editor.add(this.dock);

        this.fill(null);

        this.flightEditTime.mark();

        this.panels.add(this.cameraEditor);
        this.panels.add(this.replayEditor);

        this.secretPlay = new UIElement();
        this.secretPlay.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(() -> !this.isFlying() && !this.canBeSeen() && this.data != null).category(editor);

        this.setUndoId("film_panel");
        this.cameraEditor.setUndoId("camera_editor");
        this.replayEditor.setUndoId("replay_editor");
        this.actionEditor.setUndoId("action_editor");

        UIElement element = new UIElement()
        {
            @Override
            protected boolean subMouseScrolled(UIContext context)
            {
                if (Window.isCtrlPressed() && !UIFilmPanel.this.isFlying() && UIFilmPanel.this.isCursorOverTimeline(context))
                {
                    int magnitude = Window.isShiftPressed() ? BBSSettings.editorJump.get() : 1;
                    int newCursor = UIFilmPanel.this.getCursor() + (int) Math.copySign(magnitude, context.mouseWheel);

                    UIFilmPanel.this.setCursor(newCursor);

                    return true;
                }

                return super.subMouseScrolled(context);
            }
        };

        this.add(element);
        this.add(new UIFilmPanelUndoKeys(this).full(this));

        IValueListener refreshPreviewOnVideoResolution = (v, f) ->
        {
            if (this.isVisible()) this.applyPreviewSizeToBBS();
        };
        BBSSettings.videoWidth.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.videoHeight.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewSizeMode.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomWidth.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomHeight.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewResolutionScale.postCallback(refreshPreviewOnVideoResolution);

        this.add(this.layoutUnderTopBar(this.selectionPanel));
    }

    private boolean isCursorOverTimeline(UIContext context)
    {
        return this.isCursorOverClipsTimeline(this.cameraEditor, context)
            || this.isCursorOverClipsTimeline(this.actionEditor, context)
            || this.isCursorOverReplayTimeline(context);
    }

    private boolean isCursorOverClipsTimeline(UIClipsPanel panel, UIContext context)
    {
        return panel != null
            && panel.isVisible()
            && panel.clips != null
            && panel.clips.isVisible()
            && panel.clips.area.isInside(context);
    }

    private boolean isCursorOverReplayTimeline(UIContext context)
    {
        return this.replayEditor != null
            && this.replayEditor.isVisible()
            && this.replayEditor.keyframeEditor != null
            && this.replayEditor.keyframeEditor.view != null
            && this.replayEditor.keyframeEditor.view.isVisible()
            && this.replayEditor.keyframeEditor.view.area.isInside(context);
    }

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.FILM_TABS_NEW_TAB;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.FILM;
    }

    public void renameFilmId(String from, String to)
    {
        if (from == null || to == null || from.equals(to))
        {
            return;
        }

        if (this.data != null && from.equals(this.data.getId()))
        {
            this.data.setId(to);
        }

        this.onDataRenamed(from, to);
    }

    public void renameFilmFolder(String fromPath, String name)
    {
        if (fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        String oldPrefix = fromPath + "/";
        int slash = fromPath.lastIndexOf('/');
        String parentPath = slash >= 0 ? fromPath.substring(0, slash + 1) : "";
        String newPrefix = parentPath + name + "/";

        if (this.data != null)
        {
            String id = this.data.getId();

            if (id != null && id.startsWith(oldPrefix))
            {
                this.data.setId(newPrefix + id.substring(oldPrefix.length()));
            }
        }

        this.onDataFolderRenamed(fromPath, name);
    }

    public void deleteFilmIds(Set<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }

        for (String id : ids)
        {
            this.onDataRemoved(id);
        }

        this.updateTabVisibility();
    }

    public void deleteFilmFolders(Set<String> folderPaths)
    {
        if (folderPaths == null || folderPaths.isEmpty())
        {
            return;
        }

        for (String folder : folderPaths)
        {
            if (folder != null && !folder.isEmpty())
            {
                this.onDataFolderRemoved(folder);
            }
        }

        this.updateTabVisibility();
    }

    public void updateTabVisibility()
    {
        this.dock.refreshVisibility();
    }

    /** Runs after every dock layout pass; the dock owns panel visibility, this owns what's inside them. */
    private void onDockLayoutChanged()
    {
        boolean hasFilm = this.hasFilmInCurrentTab();

        this.updateMainEditorVisibility(hasFilm);
        this.selectionPanel.setVisible(!hasFilm);
    }

    private boolean hasFilmInCurrentTab()
    {
        return this.tabs.getCurrentId() != null;
    }

    private void updateMainEditorVisibility(boolean hasFilm)
    {
        UIElement selected = this.selectedMainEditorPanel == null ? this.cameraEditor : this.selectedMainEditorPanel;
        boolean mainActive = this.dock.isPanelActive(PANEL_MAIN_ID);
        boolean editAreaActive = this.dock.isPanelActive(PANEL_EDIT_AREA_ID);
        boolean visible = hasFilm && (mainActive || editAreaActive);
        boolean cameraVisible = visible && selected == this.cameraEditor;
        boolean replayVisible = visible && selected == this.replayEditor;

        this.cameraEditor.setVisible(cameraVisible);
        this.replayEditor.setVisible(replayVisible);

        this.cameraEditor.setTimelineVisible(mainActive && cameraVisible);
        this.cameraEditor.setPropertiesVisible(editAreaActive && cameraVisible);

        /* The recording editor toggles internally between its keyframe and action
         * timelines; both share main (timeline) and editArea (parameters). */
        this.replayEditor.setTimelineVisible(mainActive && replayVisible);
        this.replayEditor.setPropertiesVisible(editAreaActive && replayVisible);
    }

    private ValueEditorLayout getFilmLayoutSettings()
    {
        return BBSSettings.editorLayoutSettings;
    }

    private void toggleLayoutLock()
    {
        this.dock.toggleLock();
        this.getFilmLayoutSettings().setDockUnlocked(ValueEditorLayout.FILM, !this.dock.isLocked());
    }

    /** Which editor's own layout id the current view corresponds to. */
    private String currentEditorLayoutId()
    {
        return this.selectedMainEditorPanel == this.replayEditor
            ? ValueEditorLayout.FILM_REPLAY
            : ValueEditorLayout.FILM_CAMERA;
    }

    /** Bound editors read and write their own tree; the rest share one. */
    private String currentLayoutId()
    {
        String id = this.currentEditorLayoutId();

        return this.getFilmLayoutSettings().isBound(id) ? id : ValueEditorLayout.FILM;
    }

    private EditorLayoutNode getCurrentFilmLayoutRoot()
    {
        return this.getFilmLayoutSettings().getLayout(this.currentLayoutId(), EditorLayoutNode::defaultFilmLayout);
    }

    private void setCurrentFilmLayoutRoot(EditorLayoutNode root)
    {
        this.getFilmLayoutSettings().setLayout(this.currentLayoutId(), root);
    }

    private boolean isCurrentFilmLayoutBound()
    {
        return this.getFilmLayoutSettings().isBound(this.currentEditorLayoutId());
    }

    private void toggleCurrentFilmLayoutBinding()
    {
        ValueEditorLayout layout = this.getFilmLayoutSettings();
        String id = this.currentEditorLayoutId();

        /* Binding starts from whatever is on screen rather than from the default. */
        layout.setBound(id, !layout.isBound(id), this.getCurrentFilmLayoutRoot());
        this.dock.refresh();
    }

    /**
     * Which layout tree the dock reads and writes. The camera and recording editors can each be
     * bound to their own tree, so the source resolves per call rather than being swapped out.
     */
    private ILayoutSource createLayoutSource()
    {
        return new ILayoutSource()
        {
            @Override
            public EditorLayoutNode getRoot()
            {
                return UIFilmPanel.this.getCurrentFilmLayoutRoot();
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                UIFilmPanel.this.setCurrentFilmLayoutRoot(root);
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return EditorLayoutNode.defaultFilmLayout();
            }

            @Override
            public Set<String> getHiddenPanels()
            {
                return UIFilmPanel.this.getFilmLayoutSettings().getHiddenPanels(UIFilmPanel.this.currentLayoutId());
            }

            @Override
            public void setHiddenPanels(Set<String> hidden)
            {
                UIFilmPanel.this.getFilmLayoutSettings().setHiddenPanels(UIFilmPanel.this.currentLayoutId(), hidden);
            }
        };
    }

    private MapType getFilmLayoutPresetData()
    {
        MapType data = new MapType();

        data.put("film_layout", this.dock.getLayoutRoot().toData());

        return data;
    }

    private void applyFilmLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType layoutData = data.get("film_layout");
        if (layoutData == null)
        {
            return;
        }
        this.dock.applyLayoutRoot(EditorLayoutNode.fromData(layoutData));
    }

    private void resetFilmLayout()
    {
        this.dock.resetLayout();
    }

    /**
     * Preferred placement for the recording panels when an older layout, saved before they existed,
     * is loaded: the replay list under the edit area, its properties to the right of the list.
     */
    private EditorLayoutNode ensureFilmLayoutPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();
        EditorLayoutNode.collectPanelIds(root, ids);

        boolean hasList = ids.contains(PANEL_REPLAYS_LIST_ID);
        boolean hasProps = ids.contains(PANEL_REPLAY_PROPS_ID);

        if (hasList && hasProps)
        {
            return root;
        }

        EditorLayoutNode out = root;

        if (!hasList)
        {
            out = EditorLayoutNode.copyWithInsertSplitAt(out, PANEL_EDIT_AREA_ID, PANEL_REPLAYS_LIST_ID, EditorLayoutNode.EDGE_BOTTOM);
        }

        if (!hasProps)
        {
            out = EditorLayoutNode.copyWithInsertSplitAt(out, PANEL_REPLAYS_LIST_ID, PANEL_REPLAY_PROPS_ID, EditorLayoutNode.EDGE_RIGHT);
        }

        return out;
    }

    private void fillFilmContextMenu(ContextMenuManager menu)
    {
        menu.action(Icons.FILM, UIKeys.FILM_TITLE, this::openFilmListOverlay);

        if (this.data == null)
        {
            return;
        }

        menu.action(Icons.SAVED, UIKeys.GENERAL_SAVE, this::save);
        menu.action(Icons.LAYOUT, UIKeys.FILM_LAYOUT_PRESETS, this::openLayoutPresetsMenu);
        menu.action(Icons.LINK, UIKeys.FILM_LAYOUT_BIND_TO_EDITOR, this.isCurrentFilmLayoutBound(), this::toggleCurrentFilmLayoutBinding);
        menu.action(Icons.REFRESH, UIKeys.FILM_LAYOUT_RESET, this::resetFilmLayout);
        this.dock.fillHiddenPanelsMenu(menu);
        boolean locked = this.dock.isLocked();

        menu.action(locked ? Icons.UNLOCKED : Icons.LOCKED, locked ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, locked, this::toggleLayoutLock);

        menu.action(Icons.LIST, UIKeys.FILM_OPEN_HISTORY, () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIUndoHistoryOverlay(UIKeys.FILM_HISTORY_TITLE, this.getUndoHandler().getUndoManager(), this::getData, null), 200, 0.6F);
        });

        menu.action(Icons.ARROW_RIGHT, UIKeys.FILM_MOVE_TITLE, () ->
        {
            UIFilmMoveOverlayPanel panel = new UIFilmMoveOverlayPanel((vector) ->
            {
                int topLayer = this.data.camera.getTopLayer() + 1;
                int duration = this.data.camera.calculateDuration();
                double dx = vector.x;
                double dy = vector.y;
                double dz = vector.z;

                BaseValue.edit(this.data, (__) ->
                {
                    TranslateClip clip = new TranslateClip();

                    clip.layer.set(topLayer);
                    clip.duration.set(duration);
                    clip.translate.get().set(dx, dy, dz);
                    __.camera.addClip(clip);

                    for (Replay replay : __.replays.getList())
                    {
                        for (Keyframe<Double> keyframe : replay.keyframes.x.getKeyframes()) keyframe.setValue(keyframe.getValue() + dx);
                        for (Keyframe<Double> keyframe : replay.keyframes.y.getKeyframes()) keyframe.setValue(keyframe.getValue() + dy);
                        for (Keyframe<Double> keyframe : replay.keyframes.z.getKeyframes()) keyframe.setValue(keyframe.getValue() + dz);

                        replay.actions.shift(dx, dy, dz);
                    }
                });
            });

            panel.difference(this::getMoveToPlayerOffset);

            UIOverlay.addOverlay(this.getContext(), panel, 240, 140);
        });

        menu.action(Icons.TIME, UIKeys.FILM_INSERT_SPACE_TITLE, () ->
        {
            UINumberOverlayPanel panel = new UINumberOverlayPanel(UIKeys.FILM_INSERT_SPACE_TITLE, UIKeys.FILM_INSERT_SPACE_DESCRIPTION, (d) ->
            {
                if (d.intValue() <= 0)
                {
                    return;
                }

                for (Replay replay : this.data.replays.getList())
                {
                    for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                    {
                        channel.insertSpace(this.getCursor(), d.intValue());
                    }

                    for (KeyframeChannel channel : replay.properties.tracks.values())
                    {
                        channel.insertSpace(this.getCursor(), d.intValue());
                    }
                }
            });

            panel.value.limit(1).integer().setValue(1D);

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        menu.action(Icons.GEAR, UIKeys.FILM_PLAYER_SETTINGS, () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFilmPlayerSettingsOverlayPanel(this.getData(), this.getCursor()), 280, 0.4F);
        });

        menu.action(Icons.HELP, L10n.lang("bbs.ui.film.details.button"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFilmDetailsOverlayPanel(this.getData()), 300, 260);
        });
    }

    /**
     * Relative move that snaps the scene's first position keyframe onto the
     * player's current position ({@code round} optionally snaps the player's
     * position to whole coordinates) — offered through the move overlay's menu.
     */
    private Vector3d getMoveToPlayerOffset(boolean round)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return new Vector3d();
        }

        Vector3d first = this.getFirstReplayPosition();
        double px = round ? Math.round(player.getX()) : player.getX();
        double py = round ? Math.round(player.getY()) : player.getY();
        double pz = round ? Math.round(player.getZ()) : player.getZ();

        return new Vector3d(px - first.x, py - first.y, pz - first.z);
    }

    private Vector3d getFirstReplayPosition()
    {
        Replay selected = this.replayEditor.getReplay();

        if (selected != null && (!selected.keyframes.x.isEmpty() || !selected.keyframes.y.isEmpty() || !selected.keyframes.z.isEmpty()))
        {
            return new Vector3d(
                selected.keyframes.x.isEmpty() ? 0 : selected.keyframes.x.get(0).getValue(),
                selected.keyframes.y.isEmpty() ? 0 : selected.keyframes.y.get(0).getValue(),
                selected.keyframes.z.isEmpty() ? 0 : selected.keyframes.z.get(0).getValue()
            );
        }

        for (Replay replay : this.data.replays.getList())
        {
            if (!replay.keyframes.x.isEmpty() || !replay.keyframes.y.isEmpty() || !replay.keyframes.z.isEmpty())
            {
                return new Vector3d(
                    replay.keyframes.x.isEmpty() ? 0 : replay.keyframes.x.get(0).getValue(),
                    replay.keyframes.y.isEmpty() ? 0 : replay.keyframes.y.get(0).getValue(),
                    replay.keyframes.z.isEmpty() ? 0 : replay.keyframes.z.get(0).getValue()
                );
            }
        }

        return new Vector3d();
    }

    private void openFilmListOverlay()
    {
        UIOverlay.addOverlay(this.getContext(), this.overlay, 200, 0.9F);
    }

    private void openLayoutPresetsMenu()
    {
        UIContext context = this.getContext();

        this.layoutPresetsController.openPresets(context, context.mouseX, context.mouseY);
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void resize()
    {
        super.resize();
        this.updateTabVisibility();

        if (!this.recorder.isExporting() && !this.dock.isAnySplitterDragging()
            && this.preview.area.w >= 2 && this.preview.area.h >= 2)
        {
            this.applyPreviewSizeToBBS();
        }
    }

    /**
     * Returns the currently-active queue exporter, or {@code null} when no
     * multi-film export is in progress.
     */
    public FilmQueueExporter getQueueExporter()
    {
        return this.queueExporter;
    }

    /**
     * Builds a queue exporter from all currently-open film tabs and starts it.
     * If a single-film export is already in progress, or no tabs hold a film,
     * the call is a no-op and notifies the user.
     */
    public void startQueueExportFromOpenTabs()
    {
        UIContext context = this.getContext();

        if (this.recorder.isExporting() || this.queueExporter != null)
        {
            return;
        }

        FilmQueueExporter exporter = FilmQueueExporter.fromOpenTabs(this);

        if (exporter == null)
        {
            if (context != null)
            {
                context.notifyError(UIKeys.FILM_RENDER_QUEUE_EMPTY);
            }

            return;
        }

        this.queueExporter = exporter;

        if (context != null)
        {
            context.notifyInfo(UIKeys.FILM_RENDER_QUEUE_STARTED.format(exporter.totalCount()));
        }

        exporter.start();
    }

    /**
     * Invoked by {@link FilmQueueExporter} once it has fully shut down.
     * Guarded so a stale exporter cannot clear a newer one.
     */
    public void clearQueueExporter(FilmQueueExporter exporter)
    {
        if (this.queueExporter == exporter)
        {
            this.queueExporter = null;
        }
    }

    /**
     * Sets BBS fake window size to export resolution (from video settings).
     * Use when starting record, or when entering F1 fullscreen in film panel.
     */
    public static void applyExportSizeToBBS()
    {
        int w = Math.max(2, BBSSettings.videoWidth.get());
        int h = Math.max(2, BBSSettings.videoHeight.get());
        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;
        BBSRendering.setCustomSize(true, w, h);
    }

    /**
     * Restores BBS fake window size to the preview block size. Call after recording
     * ends so the preview is no longer at export resolution.
     */
    public void restorePreviewSize()
    {
        this.applyPreviewSizeToBBS();
    }

    /**
     * Applies the preview or export size to BBSRendering. When the camera editor is
     * visible, uses export resolution so the preview matches export proportions.
     * Otherwise uses the UI preview area size. Called when the user finishes resizing
     * the preview, when the panel is laid out, and when switching to/from camera editor.
     */
    private void applyPreviewSizeToBBS()
    {
        if (this.recorder.isExporting())
        {
            return;
        }

        int w;
        int h;

        int previewMode = BBSSettings.editorPreviewSizeMode.get();

        if (previewMode == PREVIEW_MODE_EXPORT)
        {
            w = Math.max(2, BBSSettings.videoWidth.get());
            h = Math.max(2, BBSSettings.videoHeight.get());
        }
        else if (previewMode == PREVIEW_MODE_CUSTOM)
        {
            w = Math.max(2, BBSSettings.editorPreviewCustomWidth.get());
            h = Math.max(2, BBSSettings.editorPreviewCustomHeight.get());
        }
        else
        {
            float scale = BBSSettings.editorPreviewResolutionScale.get();

            if (this.cameraEditor.isVisible())
            {
                int previewW = Math.max(2, this.preview.area.w);
                int previewH = Math.max(2, this.preview.area.h);
                int exportW = Math.max(2, BBSSettings.videoWidth.get());
                int exportH = Math.max(2, BBSSettings.videoHeight.get());
                Vector2i resized = Vectors.resize(exportW / (float) exportH, previewW, previewH);

                w = Math.max(2, (int) (resized.x * scale));
                h = Math.max(2, (int) (resized.y * scale));
            }
            else
            {
                int previewW = this.preview.area.w;
                int previewH = this.preview.area.h;
                w = Math.max(2, (int) (previewW * scale));
                h = Math.max(2, (int) (previewH * scale));
            }
        }

        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;

        boolean applied = w != BBSRendering.getVideoWidth() || h != BBSRendering.getVideoHeight();
        LOGGER.info("[BBS film] applyPreviewSizeToBBS mode={} cameraEditor={} -> w={} h={} applied={}",
            previewMode, this.cameraEditor.isVisible(), w, h, applied);

        if (applied)
        {
            BBSRendering.setCustomSize(true, w, h);
        }
    }

    public void pickClip(Clip clip, UIClipsPanel panel)
    {
        if (panel == this.cameraEditor)
        {
            this.setFlight(false);
        }
    }

    public int getPanelIndex()
    {
        for (int i = 0; i < this.panels.size(); i++)
        {
            if (this.panels.get(i).isVisible())
            {
                return i;
            }
        }

        return -1;
    }

    public void showPanel(int index)
    {
        if (index >= 0 && index < this.panels.size())
        {
            this.showPanel(this.panels.get(index));
        }
    }

    public void showPanel(UIElement element)
    {
        this.cameraEditor.clips.embedView(null);

        EditorLayoutNode previousRoot = this.getCurrentFilmLayoutRoot();
        int index = this.getPanelIndex();

        if (index >= 0)
        {
            this.captureTimelineViewport(this.panels.get(index));
        }

        this.selectedMainEditorPanel = element;

        /* Switching editors switches the layout tree too when each one is bound to its own. */
        if (previousRoot != this.getCurrentFilmLayoutRoot())
        {
            this.dock.refresh();
        }
        else
        {
            this.updateMainEditorVisibility(this.hasFilmInCurrentTab());
        }

        this.applyTimelineViewport(element);
        this.applyPreviewSizeToBBS();

        if (this.isFlying())
        {
            this.toggleFlight();
        }
    }

    private void captureTimelineViewport(UIElement panel)
    {
        if (panel == this.cameraEditor)
        {
            this.timelineXMin = this.cameraEditor.clips.scale.getMinValue();
            this.timelineXMax = this.cameraEditor.clips.scale.getMaxValue();
        }
        else if (panel == this.replayEditor && this.replayEditor.keyframeEditor != null)
        {
            this.timelineXMin = this.replayEditor.keyframeEditor.view.getXAxis().getMinValue();
            this.timelineXMax = this.replayEditor.keyframeEditor.view.getXAxis().getMaxValue();
        }
    }

    private void applyTimelineViewport(UIElement panel)
    {
        if (Double.isNaN(this.timelineXMin) || Double.isNaN(this.timelineXMax) || this.timelineXMin >= this.timelineXMax)
        {
            return;
        }

        if (panel == this.cameraEditor)
        {
            this.cameraEditor.clips.scale.view(this.timelineXMin, this.timelineXMax);
        }
        else if (panel == this.replayEditor && this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.view.getXAxis().view(this.timelineXMin, this.timelineXMax);
        }
    }

    private void captureTimelineScroll()
    {
        if (this.data == null || this.data.getId() == null)
        {
            return;
        }

        FilmTimelineScroll scroll = this.timelineScrollByFilm.computeIfAbsent(this.data.getId(), (id) -> new FilmTimelineScroll());

        scroll.camera = this.cameraEditor.clips.vertical.getScroll();
        scroll.action = this.actionEditor.clips.vertical.getScroll();

        if (this.replayEditor.keyframeEditor != null)
        {
            scroll.replay = this.replayEditor.keyframeEditor.view.getDopeSheet().getYAxis().getScroll();
        }
    }

    private void restoreTimelineScroll()
    {
        if (this.data == null || this.data.getId() == null)
        {
            return;
        }

        FilmTimelineScroll scroll = this.timelineScrollByFilm.get(this.data.getId());

        if (scroll == null)
        {
            return;
        }

        this.restoreClipsScroll(this.cameraEditor.clips, scroll.camera);
        this.restoreClipsScroll(this.actionEditor.clips, scroll.action);

        if (this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.view.getDopeSheet().getYAxis().setScroll(scroll.replay);
        }
    }

    private void restoreClipsScroll(UIClips clips, double scroll)
    {
        if (clips.getClips() == null)
        {
            return;
        }

        /* Scroll size depends on the freshly loaded clips, so recompute it before clamping the restored scroll. */
        clips.updateScrollSize();
        clips.vertical.setScroll(scroll);
    }

    private static class FilmTimelineScroll
    {
        public double camera;
        public double action;
        public double replay;
    }

    public UIFilmController getController()
    {
        return this.controller;
    }

    public UIFilmUndoHandler getUndoHandler()
    {
        return this.undoHandler;
    }

    public RunnerCameraController getRunner()
    {
        return this.runner;
    }

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        UIFilmOverlayPanel crudPanel = new UIFilmOverlayPanel(this.getTitle(), this, this::pickData);

        this.duplicateFilm = new UIIcon(Icons.SCENE, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> this.dupeData(crudPanel.namesList.getPath(str).toString())
            );

            panel.text.setText(crudPanel.namesList.getCurrentFirst().getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        crudPanel.icons.add(this.duplicateFilm);

        return crudPanel;
    }

    private void dupeData(String name)
    {
        if (this.getData() != null && !this.overlay.namesList.hasInHierarchy(name))
        {
            this.save();
            this.overlay.namesList.addFile(name);

            Film data = this.createDuplicateFilm(name, this.data);

            this.fill(data);
            this.save();
        }
    }

    public void dupeCurrentFilmTo(String name)
    {
        this.dupeData(name);
    }

    public void dupeFilmTo(String sourceId, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            return;
        }

        Film current = this.getData();

        if (current != null && (sourceId == null || sourceId.equals(current.getId())))
        {
            this.dupeData(name);

            return;
        }

        if (sourceId == null || sourceId.trim().isEmpty() || this.overlay.namesList.hasInHierarchy(name))
        {
            return;
        }

        this.save();

        this.getType().getRepository().load(sourceId, (loaded) ->
        {
            Film source = (Film) loaded;

            if (source == null)
            {
                return;
            }

            Film duplicated = this.createDuplicateFilm(name, source);

            this.fill(duplicated);
            this.save();
            this.requestNames();
        });
    }

    private Film createDuplicateFilm(String name, Film source)
    {
        Film data = new Film();
        Position position = new Position();
        IdleClip idle = new IdleClip();
        int tick = this.getCursor();

        position.set(this.getCamera());
        idle.duration.set(BBSSettings.getDefaultDuration());
        idle.position.set(position);
        data.camera.addClip(idle);
        data.setId(name);
        data.stampCreationTimeNow();

        for (Replay replay : source.replays.getList())
        {
            Replay copy = new Replay(replay.getId());

            copy.form.set(FormUtils.copy(replay.form.get()));

            for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
            {
                if (!channel.isEmpty())
                {
                    KeyframeChannel newChannel = (KeyframeChannel) copy.keyframes.get(channel.getId());

                    newChannel.insert(0, channel.interpolate(tick));
                }
            }

            for (Map.Entry<TrackId, KeyframeChannel> entry : replay.properties.tracks.entrySet())
            {
                KeyframeChannel channel = entry.getValue();

                if (channel.isEmpty())
                {
                    continue;
                }

                KeyframeChannel newChannel = new KeyframeChannel(channel.getId(), channel.getFactory());
                KeyframeSegment segment = channel.find(tick);

                if (segment != null)
                {
                    newChannel.insert(0, segment.createInterpolated());
                }

                if (!newChannel.isEmpty())
                {
                    copy.properties.put(entry.getKey(), newChannel);
                }
            }

            data.replays.add(copy);
        }

        return data;
    }

    /**
     * Runs for every panel the dashboard owns, not just the one being looked at - so nothing here may
     * touch the world. Playback is started from {@link #appear()} instead: a film left open in this
     * panel used to be replayed into the world the moment any BBS screen was opened, damage control
     * and all, while the user was in the model editor.
     */
    @Override
    public void open()
    {
        super.open();

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder != null && !recorder.hasNotStarted())
        {
            this.applyRecordedKeyframes(recorder, this.data);
        }
    }

    public void receiveActions(String filmId, int replayId, int tick, BaseType clips)
    {
        Film film = this.data;

        if (film != null && film.getId().equals(filmId) && CollectionUtils.inRange(film.replays.getList(), replayId))
        {
            BaseValue.edit(film.replays.getList().get(replayId), IValueListener.FLAG_UNMERGEABLE, (replay) ->
            {
                Clips newClips = new Clips("", BBSMod.getFactoryActionClips());

                newClips.fromData(clips);
                replay.actions.copyOver(newClips, tick);
            });
        }

        this.save();
    }

    public void applyRecordedKeyframes(Recorder recorder, Film film)
    {
        int replayId = recorder.exception;
        Replay rp = CollectionUtils.getSafe(film.replays.getList(), replayId);

        recorder.keyframes.compressItemChannels();

        if (rp != null)
        {
            BaseValue.edit(film, (f) ->
            {
                rp.keyframes.copyOver(recorder.keyframes, 0);

                Form form = rp.form.get();

                if (form != null)
                {
                    for (Map.Entry<TrackId, KeyframeChannel> entry : recorder.properties.tracks.entrySet())
                    {
                        KeyframeChannel channel = rp.properties.getOrCreate(form, entry.getKey());

                        if (channel != null && entry.getValue() != null)
                        {
                            channel.copyOver(entry.getValue(), 0);
                        }
                    }
                }

                f.hp.set(recorder.hp);
                f.hunger.set(recorder.hunger);
                f.xpLevel.set(recorder.xpLevel);
                f.xpProgress.set(recorder.xpProgress);
            });
        }

        this.applyRecordedMobs(recorder, film);
    }

    /**
     * Turn every mob captured during recording into its own replay (with a mob form
     * and the recorded position/rotation keyframes), then refresh the replay list.
     */
    private void applyRecordedMobs(Recorder recorder, Film film)
    {
        if (recorder.mobs.isEmpty())
        {
            return;
        }

        BaseValue.edit(film, (f) ->
        {
            for (Recorder.RecordedMob mob : recorder.mobs)
            {
                Replay replay = f.replays.addReplay();

                mob.keyframes.compressItemChannels();

                replay.category.set("");
                replay.form.set(mob.form);
                replay.keyframes.copyOver(mob.keyframes, 0);
            }
        });

        this.replayEditor.replaysList.replays.refreshReplayList();
        this.controller.createEntities();
    }

    @Override
    public void appear()
    {
        super.appear();

        /* appear() also fires while the dashboard is being lazily constructed (the
         * teleport/record keybinds create it on first use), at which point there's no
         * context and the editor isn't actually shown. Running the side effects below
         * there leaks editor state into the plain world — most importantly it adds the
         * film camera controller (runner) to the GLOBAL camera controller, which then
         * hijacks the world view and is never removed (the screen is never closed),
         * freezing the screen until another BBS screen resets the camera controller.
         * So only do this once the panel is genuinely on screen. */
        if (this.getContext() == null)
        {
            return;
        }

        /* The editor renders the film itself again, so the frame it left standing in the world when
         * it last closed (see freezeFrame) has to go, or it would double every replay. */
        if (this.data != null)
        {
            BBSModClient.getFilms().unfreeze(this.data.getId());
        }

        BBSRendering.setCustomSize(true);
        MorphRenderer.hidePlayer = true;

        CameraController cameraController = this.getCameraController();

        this.fillData();
        this.setFlight(false);
        cameraController.add(this.runner);

        this.getContext().menu.getRoot().add(this.secretPlay);

        /* The server drives the film - actors, actions, damage control - only while the editor is the
         * panel on screen, so this is where playback is picked up and disappear() is where it is let go. */
        this.notifyServer(ActionState.RESTART);
    }

    @Override
    public void close()
    {
        if (this.queueExporter != null)
        {
            this.queueExporter.cancel();
        }

        super.close();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        CameraController cameraController = this.getCameraController();

        this.cameraEditor.embedView(null);
        this.setFlight(false);
        cameraController.remove(this.runner);

        this.disableContext();
        this.replayEditor.close();

        this.notifyServer(ActionState.STOP);

        this.freezeFrame();
    }

    /**
     * Opt-in: instead of vanishing with the editor, the tick that was on screen stays in the world,
     * handed over to a controller that keeps rendering it (see {@link FrozenFilmController}).
     *
     * <p>Only when the film editor is the panel being looked at &mdash; {@link #close()} runs for
     * every panel the dashboard owns, so a film nobody had open must not pop into the world on the
     * way out of, say, the model editor. For the same reason a frame frozen on an earlier exit is
     * left alone there: it is taken down when the editor genuinely comes back (see {@link #appear()}).
     */
    private void freezeFrame()
    {
        /* No world to leave the frame in: the editor's screen is also torn down on disconnect, and
         * the replay entities the frozen controller builds would have nowhere to live. */
        if (this.data == null || MinecraftClient.getInstance().world == null || this.dashboard.getPanels().panel != this)
        {
            return;
        }

        if (BBSSettings.editorKeepFrameOnExit.get())
        {
            /* "Freeze when paused" ruled the forms on a stopped timeline while the editor was open,
             * so it rules them once the frame is left behind too — isPaused() is that toggle off. */
            BBSModClient.getFilms().freeze(this.data, this.getCursor(), this.controller.isPaused());
        }
        else
        {
            BBSModClient.getFilms().unfreeze(this.data.getId());
        }
    }

    @Override
    public void disappear()
    {
        super.disappear();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        this.setFlight(false);
        this.getCameraController().remove(this.runner);

        this.disableContext();
        this.secretPlay.removeFromParent();

        this.notifyServer(ActionState.STOP);
    }

    private void disableContext()
    {
        this.runner.getContext().shutdown();
    }

    @Override
    public boolean needsBackground()
    {
        return true;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canRefresh()
    {
        return false;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.FILMS;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    public void fillDefaultData(Film data)
    {
        super.fillDefaultData(data);

        IdleClip clip = new IdleClip();
        Camera camera = new Camera();
        MinecraftClient mc = MinecraftClient.getInstance();

        camera.set(mc.player, MathUtils.toRad(mc.options.getFov().getValue()));

        clip.layer.set(8);
        clip.duration.set(BBSSettings.getDefaultDuration());
        clip.fromCamera(camera);
        data.camera.addClip(clip);

        data.stampCreationTimeNow();

        this.newFilm = true;
    }

    @Override
    public void fill(Film data)
    {
        this.notifyServer(ActionState.STOP);
        this.captureTimelineScroll();
        super.fill(data);
        this.restoreTimelineScroll();
        this.notifyServer(ActionState.RESTART);
    }

    @Override
    protected void fillData(Film data)
    {
        if (this.data != null)
        {
            this.disableContext();
        }

        if (data != null)
        {
            this.undoHandler = new UIFilmUndoHandler(this);

            data.preCallback(this.undoHandler::handlePreValues);
        }
        else
        {
            this.undoHandler = null;
        }

        this.openFilmMenu.setEnabled(true);
        this.openCameraEditor.setEnabled(data != null);
        this.openReplayEditor.setEnabled(data != null);
        this.duplicateFilm.setEnabled(data != null);

        this.actionEditor.setClips(null);
        this.runner.setWork(data == null ? null : data.camera);
        this.cameraEditor.setClips(data == null ? null : data.camera);
        this.replayEditor.setFilm(data);
        this.cameraEditor.pickClip(null);

        this.fillData();
        this.controller.createEntities();

        if (this.newFilm)
        {
            Clip main = this.data.camera.get(0);

            this.cameraEditor.clips.setSelected(main);
            this.cameraEditor.pickClip(main);
        }

        this.entered = data != null;
        this.newFilm = false;

        if (data != null)
        {
            this.filmUserActivity.onFilmOpened();
        }
        else
        {
            this.filmUserActivity.reset();
        }

        this.updateTabVisibility();
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        if (this.selectionPanel != null)
        {
            this.selectionPanel.fillNames(names);
        }
    }

    public void undo()
    {
        if (this.data != null && this.undoHandler.getUndoManager().undo(this.data)) UIUtils.playClick();
    }

    public void redo()
    {
        if (this.data != null && this.undoHandler.getUndoManager().redo(this.data)) UIUtils.playClick();
    }

    public boolean isFlying()
    {
        return this.dashboard.orbitUI.canControl();
    }

    public void toggleFlight()
    {
        this.setFlight(!this.isFlying());
    }

    /**
     * Set flight mode
     */
    public void setFlight(boolean flight)
    {
        if (flight)
        {
            this.controller.stopGizmoInteraction();
        }

        if (!this.isRunning() || !flight)
        {
            if (!flight)
            {
                this.persistFlightFov();
                if (this.undoHandler != null)
                {
                    this.undoHandler.getUndoManager().markLastUndoNoMerging();
                }
                else
                {
                    this.lastPosition.set(Position.ZERO);
                }
            }
            else
            {
                this.lastPosition.set(Position.ZERO);
            }

            this.runner.setManual(flight ? this.position : null);
            this.dashboard.orbitUI.setControl(flight);
        }
    }

    private void persistFlightFov()
    {
        if (BBSSettings.fov != null)
        {
            BBSSettings.fov.set(this.position.angle.fov);
        }
    }

    public Vector2i getLoopingRange()
    {
        Clip clip = this.cameraEditor.getClip();

        int min = -1;
        int max = -1;

        if (clip != null)
        {
            min = clip.tick.get();
            max = min + clip.duration.get();
        }

        UIClips clips = this.cameraEditor.clips;

        if (clips.loopMin != clips.loopMax && clips.loopMin >= 0 && clips.loopMin < clips.loopMax)
        {
            min = clips.loopMin;
            max = clips.loopMax;
        }

        max = Math.min(max, this.data.camera.calculateDuration());

        return new Vector2i(min, max);
    }

    @Override
    public void update()
    {
        if (this.getContext() != null && this.secretPlay.getParent() == null)
        {
            this.getContext().menu.getRoot().add(this.secretPlay);
        }

        this.playerToCamera = BBSSettings.editorPlayerFollowsCamera.get();
        this.controller.update();
        this.updateRestartOnSeek();

        if (this.playerToCamera && this.data != null && !this.controller.isControlling())
        {
            this.teleportToCamera();
        }

        super.update();
    }

    /* Rendering code */

    @Override
    public void renderPanelBackground(UIContext context)
    {
        super.renderPanelBackground(context);

        Texture texture = BBSRendering.getTexture();

        if (texture != null)
        {
            context.batcher.box(0, 0, context.menu.width, context.menu.height, Colors.A100);

            int w = context.menu.width;
            int h = context.menu.height;
            Vector2i resize = Vectors.resize(texture.width / (float) texture.height, w, h);
            Area area = new Area();

            area.setSize(resize.x, resize.y);
            area.setPos((w - area.w) / 2, (h - area.h) / 2);

            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        this.updateLogic(context);
    }

    /**
     * Draw everything on the screen
     */
    @Override
    public void render(UIContext context)
    {
        if (this.lastTime == 0)
        {
            this.lastTime = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        long diff = now - this.lastTime;

        this.lastTime = now;

        if (this.getData() != null)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (this.filmUserActivity.shouldAccumulateActiveTime(mc, context, now))
            {
                this.timeSpentActiveAccumulator += diff;
            }

            /* Batch updates to once per second to avoid undo history pollution
             * and reduce set() overhead; display already refreshes every 1s */
            if (this.timeSpentActiveAccumulator >= 1000)
            {
                long ticks = (long) (this.timeSpentActiveAccumulator / 50);

                this.getData().timeSpentActive.set(this.getData().timeSpentActive.get() + ticks);
                this.timeSpentActiveAccumulator -= ticks * 50;
            }
        }

        if (this.controller.isControlling())
        {
            context.mouseX = context.mouseY = -1;
        }

        this.controller.orbit.update(context);

        if (this.undoHandler != null)
        {
            this.undoHandler.submitUndo();
        }

        if (this.queueExporter != null)
        {
            this.queueExporter.tick(context);
        }

        this.updateLogic(context);

        this.area.render(context.batcher, BBSSettings.baseSurface());

        if (this.editor.isVisible())
        {
            this.preview.area.render(context.batcher, Colors.A75);
        }

        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = false;
        }

        if (this.entered)
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Vec3d pos = player.getPos();
            Vector3d cameraPos = this.camera.position;
            double distance = cameraPos.distance(pos.x, pos.y, pos.z);
            int value = MinecraftClient.getInstance().options.getViewDistance().getValue();

            if (distance > value * 12)
            {
                this.getContext().notifyError(UIKeys.FILM_TELEPORT_DESCRIPTION);
            }

            this.entered = false;
        }
    }

    /**
     * Update logic for such components as repeat fixture, minema recording,
     * sync mode, flight mode, etc.
     */
    private void updateLogic(UIContext context)
    {
        Clip clip = this.cameraEditor.getClip();

        /* Loop fixture */
        if (BBSSettings.editorLoop.get() && this.isRunning())
        {
            Vector2i loop = this.getLoopingRange();
            int min = loop.x;
            int max = loop.y;
            int ticks = this.getCursor();

            if (!this.recorder.isRecording() && !this.controller.isRecording() && min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min))
            {
                this.setCursor(min);
            }
        }

        /* Animate flight mode */
        if (this.dashboard.orbitUI.canControl())
        {
            this.dashboard.orbit.apply(this.position);

            Position current = new Position(this.getCamera());
            boolean check = this.flightEditTime.check();

            if (this.cameraEditor.getClip() != null && this.cameraEditor.isVisible() && this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FREE)
            {
                if (!this.lastPosition.equals(current) && check)
                {
                    this.cameraEditor.editClip(current);
                }
            }

            if (check)
            {
                this.lastPosition.set(current);
            }
        }
        else
        {
            this.dashboard.orbit.setup(this.getCamera());
        }

        /* Rewind playback back to 0 */
        if (this.lastRunning && !this.isRunning())
        {
            this.lastRunning = this.runner.isRunning();

            if (BBSSettings.editorRewind.get())
            {
                this.setCursor(0);
                this.notifyServer(ActionState.RESTART);
            }
        }
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        this.controller.startRenderFrame(tickDelta);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        if (!BBSRendering.isIrisShadowPass())
        {
            this.lastProjection.set(RenderSystem.getProjectionMatrix());
            this.lastView.set(context.matrixStack().peek().getPositionMatrix());
        }

        this.controller.renderFrame(context);
    }

    /* IUICameraWorkDelegate implementation */

    public void notifyServer(ActionState state)
    {
        if (this.data == null || !ClientNetwork.isIsBBSModOnServer())
        {
            return;
        }

        String id = this.data.getId();
        int tick = this.getCursor();

        ClientNetwork.sendActionState(id, state, tick);
    }

    public Camera getCamera()
    {
        return this.camera;
    }

    public Camera getWorldCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    public CameraController getCameraController()
    {
        return BBSModClient.getCameraController();
    }

    @Override
    public int getCursor()
    {
        return this.runner.ticks;
    }

    @Override
    public void setCursor(int value)
    {
        this.flightEditTime.mark();
        this.lastPosition.set(Position.ZERO);

        this.runner.ticks = Math.max(0, value);

        this.notifyServer(ActionState.SEEK);

        if (BBSSettings.editorRestartOnSeek.get())
        {
            this.restartPending = true;
        }
    }

    /**
     * Restart the actions and recreate the actors, the same way {@link Keys#FILM_CONTROLLER_RESTART_ACTIONS}
     * does it manually.
     */
    public void restartActions()
    {
        this.restartPending = false;

        this.notifyServer(ActionState.RESTART);
        this.controller.createEntities();
    }

    /**
     * Automatic restart of the actions upon scrubbing the cursor (see the "restart on seek" setting).
     * <p>
     * Both restarting the actions on the server and recreating the actors are way too expensive to
     * run them on every frame of a scrubbing drag, so the restart waits until the cursor stops
     * moving for a tick and only then fires once.
     */
    private void updateRestartOnSeek()
    {
        int cursor = this.getCursor();
        boolean settled = cursor == this.lastRestartCursor;

        this.lastRestartCursor = cursor;

        if (!this.restartPending || !settled)
        {
            return;
        }

        if (!BBSSettings.editorRestartOnSeek.get() || !this.canRestartOnSeek())
        {
            this.restartPending = false;

            return;
        }

        this.restartActions();
    }

    /**
     * Recreating the actors stops the recording and drops the character control, and both the
     * playback and the video export move the cursor on their own, so an automatic restart must
     * stay out of all of those.
     */
    private boolean canRestartOnSeek()
    {
        return this.data != null
            && !this.isRunning()
            && !this.controller.isRecording()
            && !this.controller.isControlling()
            && !this.recorder.isRecording()
            && !this.recorder.isExporting();
    }

    public boolean isRunning()
    {
        return this.runner.isRunning();
    }

    public void togglePlayback()
    {
        this.setFlight(false);

        this.runner.toggle(this.getCursor());
        this.lastRunning = this.runner.isRunning();

        if (this.runner.isRunning())
        {
            this.cameraEditor.clips.scale.shiftIntoMiddle(this.getCursor());

            if (this.replayEditor.keyframeEditor != null)
            {
                this.replayEditor.keyframeEditor.view.getXAxis().shiftIntoMiddle(this.getCursor());
            }
        }
    }

    public boolean canUseKeybinds()
    {
        return !this.isFlying();
    }

    /**
     * Whether a visible clips timeline has a clip selected — i.e. the clip
     * keybinds (like {@code M} for duration) would claim the key. Preview
     * shortcuts that share a key with a clip keybind (the motion path toggle)
     * step aside when this is true, since the clips editor sits before the
     * controller in the key dispatch and would otherwise never see the press.
     */
    public boolean hasSelectedClip()
    {
        return (this.cameraEditor != null && this.cameraEditor.isVisible() && this.cameraEditor.getClip() != null)
            || (this.actionEditor != null && this.actionEditor.isVisible() && this.actionEditor.getClip() != null);
    }

    public void fillData()
    {
        this.cameraEditor.fillData();
        this.actionEditor.fillData();

        if (this.replayEditor.keyframeEditor != null && this.replayEditor.keyframeEditor.editor != null)
        {
            this.replayEditor.keyframeEditor.editor.update();
        }
    }

    public void teleportToCamera()
    {
        Camera camera = this.getCamera();
        Vector3d cameraPos = camera.position;
        double x = cameraPos.x;
        double y = cameraPos.y;
        double z = cameraPos.z;

        PlayerUtils.teleport(x, y, z, MathUtils.toDeg(camera.rotation.y) - 180F, MathUtils.toDeg(camera.rotation.x));
    }

    public void setPlayerToCamera(boolean value)
    {
        this.playerToCamera = value;
        BBSSettings.editorPlayerFollowsCamera.set(value);
    }

    public boolean checkShowNoCamera()
    {
        boolean noCamera = this.getData().camera.calculateDuration() <= 0;

        if (noCamera)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                UIKeys.FILM_NO_CAMERA_TITLE,
                UIKeys.FILM_NO_CAMERA_DESCRIPTION
            ));
        }

        return noCamera;
    }

    public void updateActors(String filmId, Map<String, Integer> actors)
    {
        if (this.data != null && this.data.getId().equals(filmId))
        {
            this.controller.updateActors(actors);
        }
    }

    @Override
    public boolean handleKeyPressed(UIContext context)
    {
        return this.controller.orbit.keyPressed(context, this.preview.area);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.showPanel(data.getInt("panel"));
        this.setCursor(data.getInt("tick"));
        this.controller.createEntities();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("panel", this.getPanelIndex());
        data.putInt("tick", this.getCursor());
    }

    @Override
    protected boolean canSave(UIContext context)
    {
        return !this.recorder.isRecording();
    }
}
