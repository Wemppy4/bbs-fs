package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
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
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.utils.IUIOrbitKeysHandler;
import mchorse.bbs_mod.ui.film.audio.UIAudioRecorder;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.UIFilmUndoHandler;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collection;
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
    private final Position position = new Position(0, 0, 0, 0, 0);
    private final Position lastPosition = new Position(0, 0, 0, 0, 0);

    public List<FilmTab> tabs = new ArrayList<>();
    public int currentTab = -1;
    public UIFilmTabs tabBar;
    public UIFilmSelectionPanel selectionPanel;

    public UIElement main;
    public UIElement editArea;
    private final List<UIDraggable> splitterHandles = new ArrayList<>();
    private final List<EditorLayoutNode.SplitterHandleInfo> splitterHandleInfos = new ArrayList<>();
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
    public UIIcon openActionEditor;
    public UIIcon lockLayoutButton;
    public UIIcon layoutPresetsButton;

    /** When true, docking and resizing are disabled; drag handles and their top offset are hidden. */
    private boolean layoutLocked = true;

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

    /* Docking: layout panels and drag-to-swap/split */
    private final Map<String, UIElement> panelById = new LinkedHashMap<>();
    private final Map<String, UIDraggable> dragHandlesById = new LinkedHashMap<>();
    private static final float DRAG_HANDLE_HEIGHT_NORM = 0.02F;
    private static final float DRAG_HANDLE_TOP_OFFSET_NORM = 0.01F;
    private static final int SPLITTER_HANDLE_PX = 6;
    private static final int DROP_ZONE_CENTER = -1;
    private static final float DROP_EDGE_MARGIN = 0.2F;
    private static final int EDITOR_MIN_SIZE_FOR_PX_HANDLES = 10;
    private static final int DOCK_STACK_TABS_HEIGHT_PX = 20;
    private static final String PANEL_MAIN_ID = "main";
    private static final String PANEL_PREVIEW_ID = "preview";
    private static final String PANEL_EDIT_AREA_ID = "editArea";
    private static final String PANEL_REPLAYS_LIST_ID = "replaysList";
    private static final String PANEL_REPLAY_PROPS_ID = "replayProps";
    /** Top offset (px) for parameters panels when layout is unlocked (space for drag icon). Used for lock button size too. */
    public static final int EDIT_PANEL_TOP_OFFSET_PX = 20;
    public static final int FILM_TABS_HEIGHT_PX = 18;

    private final List<UIDockStackTabs> dockStackTabs = new ArrayList<>();
    private final Map<String, DockStackInfo> dockStackByPanelId = new HashMap<>();
    private UIElement selectedMainEditorPanel;

    private String draggingPanelId;
    private String dropTargetPanelId;
    private int dropTargetZone = DROP_ZONE_CENTER;

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
        private final UIFilmPanel panel;
        private String anchorPanelId;
        private final List<String> panelIds = new ArrayList<>();
        private String activePanelId;

        public UIDockStackTabs(UIFilmPanel panel)
        {
            this.panel = panel;
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
                this.panel.activateDockStackTab(this.anchorPanelId, this.panelIds.get(index));

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

            int tabSize = this.getTabSize();
            int hovered = this.area.isInside(context.mouseX, context.mouseY) ? this.getTabIndex(context.mouseX) : -1;
            int y = this.area.y;
            int ey = this.area.ey();

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

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
                boolean hover = i == hovered;
                Icon icon = this.panel.getDockPanelIcon(panelId);
                int iconColor = active ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.mulRGB(Colors.WHITE, 0.75F));

                if (active)
                {
                    int color = BBSSettings.primaryColor.get();

                    context.batcher.box(x, ey - 2, ex, ey, Colors.A100 | color);
                    context.batcher.gradientVBox(x, y, ex, ey - 2, color, Colors.A75 | color);
                }

                context.batcher.icon(icon, iconColor, (x + ex) / 2, (y + ey) / 2, 0.5F, 0.5F);
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
        this.editArea = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.mulRGB(BBSSettings.primaryColor(Colors.A100), 0.2F));
                super.render(context);
            }
        };
        this.preview = new UIFilmPreview(this);
        this.panelById.put(PANEL_MAIN_ID, this.main);
        this.panelById.put(PANEL_PREVIEW_ID, this.preview);
        this.panelById.put(PANEL_EDIT_AREA_ID, this.editArea);

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
        this.actionEditor.full(this.main).setVisible(false);

        this.panelById.put(PANEL_REPLAYS_LIST_ID, this.replayEditor.replaysList);
        this.panelById.put(PANEL_REPLAY_PROPS_ID, this.replayEditor.replayProperties);
        this.selectedMainEditorPanel = this.cameraEditor;

        /* Icon bar buttons */
        this.openFilmMenu = new UIIcon(Icons.GEAR, (b) ->
        {
            this.getContext().replaceContextMenu(this::fillFilmContextMenu);
        });
        this.openFilmMenu.tooltip(UIKeys.FILM_OPTIONS, Direction.LEFT);
        this.openCameraEditor = new UIIcon(Icons.FRUSTUM, (b) -> this.showPanel(this.cameraEditor));
        this.openCameraEditor.tooltip(UIKeys.FILM_OPEN_CAMERA_EDITOR, Direction.LEFT);
        this.openReplayEditor = new UIIcon(Icons.SCENE, (b) -> this.showPanel(this.replayEditor));
        this.openReplayEditor.tooltip(UIKeys.FILM_OPEN_REPLAY_EDITOR, Direction.LEFT);
        this.openActionEditor = new UIIcon(Icons.ACTION, (b) -> this.showPanel(this.actionEditor));
        this.openActionEditor.tooltip(UIKeys.FILM_OPEN_ACTION_EDITOR, Direction.LEFT);
        this.lockLayoutButton = new UIIcon(() -> this.layoutLocked ? Icons.LOCKED : Icons.UNLOCKED, (b) -> this.toggleLayoutLock());
        this.updateLockButtonTooltip();
        this.lockLayoutButton.relative(this.iconBar).x(0).y(1F, -EDIT_PANEL_TOP_OFFSET_PX).w(EDIT_PANEL_TOP_OFFSET_PX).h(EDIT_PANEL_TOP_OFFSET_PX);

        this.layoutPresetsController = new UICopyPasteController(PresetManager.LAYOUTS, "_CopyFilmLayout")
            .supplier(this::getFilmLayoutPresetData)
            .consumer(this::applyFilmLayoutFromPreset);
        this.layoutPresetsButton = new UIIcon(Icons.LAYOUT, (b) ->
        {
            UIContext ctx = this.getContext();
            this.layoutPresetsController.openPresets(ctx, ctx.mouseX, ctx.mouseY);
        });
        this.layoutPresetsButton.context((menu) -> menu.action(Icons.REFRESH, UIKeys.FILM_LAYOUT_RESET, this::resetFilmLayout));
        this.layoutPresetsButton.tooltip(UIKeys.FILM_LAYOUT_PRESETS, Direction.LEFT);
        this.layoutPresetsButton.relative(this.iconBar).x(0).y(1F, -EDIT_PANEL_TOP_OFFSET_PX * 2).w(EDIT_PANEL_TOP_OFFSET_PX).h(EDIT_PANEL_TOP_OFFSET_PX);

        /* Setup elements */
        this.iconBar.add(this.openFilmMenu, this.openCameraEditor.marginTop(9), this.openReplayEditor, this.openActionEditor);
        this.add(this.lockLayoutButton);
        this.add(this.layoutPresetsButton);

        this.editor.add(this.main, new UIRenderable(this::renderIcons), new UIRenderable(this::renderDropZoneHighlight));
        for (String id : this.panelById.keySet())
        {
            UIDraggable handle = this.createPanelDragHandle(id);
            this.dragHandlesById.put(id, handle);
            this.editor.add(handle);
        }
        this.main.add(this.editArea, this.cameraEditor, this.replayEditor, this.actionEditor, this.preview, this.replayEditor.replaysList, this.replayEditor.replayProperties);
        this.add(this.controller, new UIRenderable(this::renderDividers));
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
            this.showPanel(MathUtils.cycler(this.getPanelIndex() + (Window.isShiftPressed() ? -1 : 1), this.panels));
            UIUtils.playClick();
        }).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_NEXT_DOCK_TAB, () ->
        {
            if (this.cycleDockStackTab(1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_PREV_DOCK_TAB, () ->
        {
            if (this.cycleDockStackTab(-1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);

        this.tabBar = new UIFilmTabs(this);
        this.selectionPanel = new UIFilmSelectionPanel(this);

        this.fill(null);

        this.setupEditorFlex(false);
        this.flightEditTime.mark();

        this.panels.add(this.cameraEditor);
        this.panels.add(this.replayEditor);
        this.panels.add(this.actionEditor);

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
                if (Window.isCtrlPressed() && !UIFilmPanel.this.isFlying())
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
        BBSSettings.videoSettings.width.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.videoSettings.height.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewSizeMode.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomWidth.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomHeight.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewResolutionScale.postCallback(refreshPreviewOnVideoResolution);

        if (this.openOverlay != null)
        {
            this.openOverlay.removeFromParent();
        }

        this.overrideOpenDataManagerKeybind();

        this.iconBar.relative(this).x(1F, -20).y(FILM_TABS_HEIGHT_PX).w(20).h(1F, -FILM_TABS_HEIGHT_PX).column(0).stretch();
        this.editor.relative(this).y(FILM_TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -FILM_TABS_HEIGHT_PX);

        this.tabBar.relative(this).w(1F).h(FILM_TABS_HEIGHT_PX);
        this.selectionPanel.relative(this).y(FILM_TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -FILM_TABS_HEIGHT_PX);

        this.add(this.tabBar);
        this.add(this.selectionPanel);

        this.addTab();
    }

    private void overrideOpenDataManagerKeybind()
    {
        for (mchorse.bbs_mod.ui.utils.keys.Keybind keybind : this.keys().keybinds)
        {
            if (keybind.getLabel() == UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER)
            {
                keybind.callback = this::addTab;
                break;
            }
        }
    }

    public boolean isLayoutLocked()
    {
        return this.layoutLocked;
    }

    /** Top offset (px) for parameters panels; 0 when layout locked. */
    public int getEditPanelTopOffsetPx()
    {
        return this.layoutLocked ? 0 : EDIT_PANEL_TOP_OFFSET_PX;
    }

    public FilmTab getCurrentTab()
    {
        return this.currentTab >= 0 && this.currentTab < this.tabs.size() ? this.tabs.get(this.currentTab) : null;
    }

    public boolean isNewTab(FilmTab tab)
    {
        return tab != null && tab.filmId == null;
    }

    public int findNewTabIndex()
    {
        for (int i = 0, c = this.tabs.size(); i < c; i++)
        {
            if (this.isNewTab(this.tabs.get(i)))
            {
                return i;
            }
        }

        return -1;
    }

    public boolean hasNewTab()
    {
        return this.findNewTabIndex() >= 0;
    }

    public boolean canAddNewTab()
    {
        return !this.hasNewTab();
    }

    public void addTab()
    {
        int index = this.findNewTabIndex();

        if (index >= 0)
        {
            this.switchTab(index);

            return;
        }
        
        this.tabs.add(new FilmTab(null));
        this.switchTab(this.tabs.size() - 1);
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

        boolean changed = false;

        for (FilmTab tab : this.tabs)
        {
            if (from.equals(tab.filmId))
            {
                tab.filmId = to;
                changed = true;
            }
        }

        if (changed)
        {
            this.tabBar.sync();
        }
    }

    public void renameFilmFolder(String fromPath, String name)
    {
        if (fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        DataPath from = new DataPath(fromPath);
        DataPath parent = from.getParent();
        String parentPath = parent.strings.isEmpty() ? "" : parent.toString() + "/";

        String oldPrefix = from.toString() + "/";
        String newPrefix = parentPath + name + "/";

        boolean changed = false;

        if (this.data != null)
        {
            String id = this.data.getId();

            if (id != null && id.startsWith(oldPrefix))
            {
                this.data.setId(newPrefix + id.substring(oldPrefix.length()));
            }
        }

        for (FilmTab tab : this.tabs)
        {
            if (tab.filmId != null && tab.filmId.startsWith(oldPrefix))
            {
                tab.filmId = newPrefix + tab.filmId.substring(oldPrefix.length());
                changed = true;
            }
        }

        if (changed)
        {
            this.tabBar.sync();
        }
    }

    public void deleteFilmIds(Set<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }

        for (FilmTab tab : this.tabs)
        {
            if (tab.filmId != null && ids.contains(tab.filmId))
            {
                tab.filmId = null;
            }
        }

        if (this.data != null && ids.contains(this.data.getId()))
        {
            this.fill(null);
            return;
        }

        this.updateTabVisibility();
        this.tabBar.sync();
    }

    public void deleteFilmFolders(Set<String> folderPaths)
    {
        if (folderPaths == null || folderPaths.isEmpty())
        {
            return;
        }

        for (FilmTab tab : this.tabs)
        {
            if (tab.filmId == null)
            {
                continue;
            }

            for (String folder : folderPaths)
            {
                if (folder == null || folder.isEmpty())
                {
                    continue;
                }

                String prefix = folder.endsWith("/") ? folder : folder + "/";

                if (tab.filmId.startsWith(prefix))
                {
                    tab.filmId = null;
                    break;
                }
            }
        }

        if (this.data != null)
        {
            String id = this.data.getId();

            for (String folder : folderPaths)
            {
                if (folder == null || folder.isEmpty())
                {
                    continue;
                }

                String prefix = folder.endsWith("/") ? folder : folder + "/";

                if (id != null && id.startsWith(prefix))
                {
                    this.fill(null);
                    return;
                }
            }
        }

        this.updateTabVisibility();
        this.tabBar.sync();
    }

    public void closeTab(FilmTab tab)
    {
        if (tab == null)
        {
            return;
        }

        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTab(index);
        }
    }

    public void closeTab(int index)
    {
        if (this.tabs.size() <= 1)
        {
            if (this.data != null)
            {
                this.save();
            }

            this.tabs.get(0).filmId = null;
            this.currentTab = 0;
            this.fill(null);
            return;
        }

        boolean wasCurrent = this.currentTab == index;
        if (wasCurrent && this.data != null)
        {
            this.save();
            this.data = null; // Prevent switchTab from saving this data to wrong tab
        }

        this.tabs.remove(index);
        this.tabBar.sync();

        if (this.currentTab >= index)
        {
            this.currentTab = Math.max(0, this.currentTab - 1);
        }

        if (wasCurrent)
        {
            this.switchTab(this.currentTab, true);
        }
    }

    public void closeOtherTabs(int index)
    {
        this.closeTabsKeeping((i) -> i == index, index);
    }

    public void closeOtherTabs(FilmTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeOtherTabs(index);
        }
    }

    public void closeTabsLeft(int index)
    {
        this.closeTabsKeeping((i) -> i >= index, index);
    }

    public void closeTabsLeft(FilmTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTabsLeft(index);
        }
    }

    public void closeTabsRight(int index)
    {
        this.closeTabsKeeping((i) -> i <= index, index);
    }

    public void closeTabsRight(FilmTab tab)
    {
        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.closeTabsRight(index);
        }
    }

    private void closeTabsKeeping(java.util.function.IntPredicate keep, int targetIndex)
    {
        if (this.tabs.size() <= 1 || targetIndex < 0 || targetIndex >= this.tabs.size())
        {
            return;
        }

        if (this.data != null)
        {
            this.save();
        }

        FilmTab target = this.tabs.get(targetIndex);

        java.util.ArrayList<FilmTab> kept = new java.util.ArrayList<>();

        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (keep.test(i))
            {
                kept.add(this.tabs.get(i));
            }
        }

        if (kept.isEmpty())
        {
            kept.add(target);
        }

        this.tabs.clear();
        this.tabs.addAll(kept);
        this.tabBar.sync();

        int newIndex = this.tabs.indexOf(target);

        if (newIndex < 0)
        {
            newIndex = 0;
        }

        this.currentTab = -1;
        this.switchTab(newIndex, true);
    }

    public void switchTab(int index)
    {
        this.switchTab(index, false);
    }

    public void switchTab(FilmTab tab)
    {
        if (tab == null)
        {
            return;
        }

        int index = this.tabs.indexOf(tab);

        if (index >= 0)
        {
            this.switchTab(index);
        }
    }

    private void switchTab(int index, boolean force)
    {
        if (!force && this.currentTab == index)
        {
            return;
        }

        if (this.currentTab >= 0 && this.currentTab < this.tabs.size() && this.data != null)
        {
            this.save();
            this.tabs.get(this.currentTab).filmId = this.data.getId();
        }

        this.currentTab = index;
        FilmTab tab = this.tabs.get(index);

        if (tab.filmId == null)
        {
            this.fill(null);
        }
        else
        {
            this.requestData(tab.filmId);
            // requestData calls fill() which we will hook into to update visibility
        }
    }

    public void updateTabVisibility()
    {
        boolean hasFilm = this.hasFilmInCurrentTab();

        if (!hasFilm)
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
                boolean active = PANEL_MAIN_ID.equals(entry.getKey()) || this.isDockPanelActive(entry.getKey());

                entry.getValue().setVisible(active);
            }
        }

        this.updateMainEditorVisibility(hasFilm);

        for (Map.Entry<String, UIDraggable> entry : this.dragHandlesById.entrySet())
        {
            DockStackInfo stack = this.dockStackByPanelId.get(entry.getKey());
            boolean active = stack != null && entry.getKey().equals(stack.activePanelId);

            entry.getValue().setVisible(hasFilm && !this.layoutLocked && active);
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.setVisible(hasFilm);
        }

        this.selectionPanel.setVisible(!hasFilm);
    }

    private boolean hasFilmInCurrentTab()
    {
        return this.currentTab >= 0 && this.currentTab < this.tabs.size() && this.tabs.get(this.currentTab).filmId != null;
    }

    private boolean isDockPanelActive(String panelId)
    {
        DockStackInfo stack = this.dockStackByPanelId.get(panelId);

        return stack != null && panelId.equals(stack.activePanelId);
    }

    private boolean isMainPanelActive()
    {
        DockStackInfo mainStack = this.dockStackByPanelId.get(PANEL_MAIN_ID);

        return mainStack == null || PANEL_MAIN_ID.equals(mainStack.activePanelId);
    }

    private boolean isEditAreaPanelActive()
    {
        DockStackInfo editAreaStack = this.dockStackByPanelId.get(PANEL_EDIT_AREA_ID);

        return editAreaStack == null || PANEL_EDIT_AREA_ID.equals(editAreaStack.activePanelId);
    }

    private void updateMainEditorVisibility(boolean hasFilm)
    {
        UIElement selected = this.selectedMainEditorPanel == null ? this.cameraEditor : this.selectedMainEditorPanel;
        boolean mainActive = this.isMainPanelActive();
        boolean editAreaActive = this.isEditAreaPanelActive();
        boolean visible = hasFilm && (mainActive || editAreaActive);
        boolean cameraVisible = visible && selected == this.cameraEditor;
        boolean replayVisible = visible && selected == this.replayEditor;
        boolean actionVisible = visible && selected == this.actionEditor;

        this.cameraEditor.setVisible(cameraVisible);
        this.replayEditor.setVisible(replayVisible);
        this.actionEditor.setVisible(actionVisible);

        this.cameraEditor.setTimelineVisible(mainActive && cameraVisible);
        this.cameraEditor.setPropertiesVisible(editAreaActive && cameraVisible);

        this.replayEditor.setTimelineVisible(mainActive && replayVisible);
        this.replayEditor.setPropertiesVisible(editAreaActive && replayVisible);

        this.actionEditor.setTimelineVisible(mainActive && actionVisible);
        this.actionEditor.setPropertiesVisible(editAreaActive && actionVisible);
    }

    private void toggleLayoutLock()
    {
        this.layoutLocked = !this.layoutLocked;
        this.clearPanelDragState();
        this.updateLockButtonTooltip();
        this.setupEditorFlex(true);
        this.refreshEditPanelOffsets();
    }

    private void updateLockButtonTooltip()
    {
        this.lockLayoutButton.tooltip(this.layoutLocked ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, Direction.LEFT);
    }

    private Icon getDockPanelIcon(String panelId)
    {
        switch (panelId)
        {
            case PANEL_PREVIEW_ID: return Icons.VIDEO_CAMERA;
            case PANEL_EDIT_AREA_ID: return Icons.EDITOR;
            case PANEL_REPLAYS_LIST_ID: return Icons.LIST;
            case PANEL_REPLAY_PROPS_ID: return Icons.PROPERTIES;
            case PANEL_MAIN_ID: return Icons.FILM;
            default: return Icons.FILE;
        }
    }

    private void refreshEditPanelOffsets()
    {
        this.cameraEditor.refreshEditPanelOffset();
        this.actionEditor.refreshEditPanelOffset();
        this.replayEditor.refreshEditPanelOffset();
    }

    private void activateDockStackTab(String stackPanelId, String panelId)
    {
        if (stackPanelId == null || panelId == null)
        {
            return;
        }

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode next = EditorLayoutNode.copyWithStackActivePanel(root, stackPanelId, panelId);

        if (next != root)
        {
            layout.setFilmLayoutRoot(next);
            this.setupEditorFlex(true);
        }
    }

    private boolean cycleDockStackTab(int offset)
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

    private MapType getFilmLayoutPresetData()
    {
        MapType data = new MapType();
        data.put("film_layout", BBSSettings.editorLayoutSettings.getFilmLayoutRoot().toData());
        return data;
    }

    private void applyFilmLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType layoutData = data.get("film_layout");
        if (layoutData == null)
        {
            return;
        }
        EditorLayoutNode root = EditorLayoutNode.fromData(layoutData);
        if (root != null)
        {
            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
            this.setupEditorFlex(true);
        }
    }

    private void resetFilmLayout()
    {
        this.clearPanelDragState();
        BBSSettings.editorLayoutSettings.setFilmLayoutRoot(EditorLayoutNode.defaultFilmLayout());
        this.setupEditorFlex(true);
    }

    private void setupEditorFlex(boolean resize)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode originalRoot = layout.getFilmLayoutRoot();
        EditorLayoutNode root = this.ensureFilmLayoutPanels(originalRoot);

        if (root != originalRoot)
        {
            layout.setFilmLayoutRoot(root);
        }

        List<EditorLayoutNode.SplitterNode> splitters = layout.getFilmSplitters();

        if (!this.layoutLocked && resize && splitters.size() == this.splitterHandles.size())
        {
            this.updateEditorFlexBoundsOnly(root);
            this.resize();
            this.resize();
            return;
        }

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

        if (this.layoutLocked)
        {
            for (UIDraggable h : this.dragHandlesById.values())
            {
                h.setVisible(false);
            }
        }
        else
        {
            this.splitterHandleInfos.clear();
            EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, this.splitterHandleInfos);

            for (int i = 0; i < splitters.size(); i++)
            {
                final int index = i;
                UIDraggable handle = new UIDraggable((context) ->
                {
                    float ratio = this.getSplitterRatioFromMouse(index, context.mouseX, context.mouseY);
                    if (ratio >= 0F)
                    {
                        layout.setFilmSplitterRatio(index, ratio);
                        this.setupEditorFlex(true);
                    }
                });
                handle.hoverOnly().dragEnd(this::applyPreviewSizeToBBS);
                handle.reference(() -> this.getSplitterHandleReferencePosition(index, splitters));
                handle.rendering((context) -> this.renderSplitter(context, index));
                this.applySplitterHandleBounds(handle, this.splitterHandleInfos.get(index));
                this.splitterHandles.add(handle);
                IUIElement insertAfter = index == 0 ? this.main : this.splitterHandles.get(index - 1);
                this.editor.addAfter(insertAfter, handle);
            }

            this.applyDragHandleBoundsFromStacks(stackInfos);
        }

        this.updateTabVisibility();

        if (resize)
        {
            this.resize();
            this.resize();
        }
    }

    private EditorLayoutNode ensureFilmLayoutPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();
        this.collectPanelIds(root, ids);

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

    private void applySplitterHandleBounds(UIDraggable handle, EditorLayoutNode.SplitterHandleInfo info)
    {
        int ew = this.editor.area.w;
        int eh = this.editor.area.h;
        if (ew < EDITOR_MIN_SIZE_FOR_PX_HANDLES || eh < EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            /* Editor not laid out yet (e.g. first open); use normalized bounds so handles are visible. */
            handle.relative(this.editor).x(info.hx).y(info.hy).w(info.hw).h(info.hh);
            return;
        }
        if (info.horizontal)
        {
            float centerY = info.hy + info.hh * 0.5F;
            float hyNew = centerY - (SPLITTER_HANDLE_PX / (2F * eh));
            handle.relative(this.editor).x(info.hx).y(hyNew).w(info.hw).h(SPLITTER_HANDLE_PX);
        }
        else
        {
            float centerX = info.hx + info.hw * 0.5F;
            float hxNew = centerX - (SPLITTER_HANDLE_PX / (2F * ew));
            handle.relative(this.editor).x(hxNew).y(info.hy).w(SPLITTER_HANDLE_PX).h(info.hh);
        }
    }

    private void syncSplitterHandleBounds()
    {
        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            this.applySplitterHandleBounds(this.splitterHandles.get(i), this.splitterHandleInfos.get(i));
        }
    }

    /** @return ratio in [0,1] or -1 if index invalid */
    private float getSplitterRatioFromMouse(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return -1F;
        }
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        float ratio = info.horizontal
            ? (mouseY - (ey + info.py * eh)) / (info.ph * eh)
            : (mouseX - (ex + info.px * ew)) / (info.pw * ew);
        return MathUtils.clamp(ratio, 0.05F, 0.95F);
    }

    private Vector2i getSplitterHandleReferencePosition(int index, List<EditorLayoutNode.SplitterNode> splitters)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size() || index >= splitters.size())
        {
            return new Vector2i(this.editor.area.x, this.editor.area.y);
        }
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        float r = splitters.get(index).getRatio();
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        int hx = ex + (int) ((info.px + (info.horizontal ? info.pw * 0.5F : r * info.pw)) * ew);
        int hy = ey + (int) ((info.py + (info.horizontal ? r * info.ph : info.ph * 0.5F)) * eh);
        return new Vector2i(hx, hy);
    }

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

    private void applyPanelBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        this.dockStackByPanelId.clear();

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

                panel.relative(this.editor).x(info.x).y(info.y, topOffset).w(info.w).h(info.h, -topOffset);
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
            tabs.configure(info);
            tabs.relative(this.editor).x(info.x).y(info.y).w(info.w).h(DOCK_STACK_TABS_HEIGHT_PX);
            this.dockStackTabs.add(tabs);
            this.editor.add(tabs);
        }
    }

    /**
     * Fast path for layout bounds updates: avoid mutating editor children list
     * while rendering by only reconfiguring existing stack tabs.
     */
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
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);

            if (!tabs.matches(info))
            {
                return false;
            }
        }

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);

            tabs.configure(info);
            tabs.relative(this.editor).x(info.x).y(info.y).w(info.w).h(DOCK_STACK_TABS_HEIGHT_PX);
        }

        return true;
    }

    private void applyDragHandleBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.setVisible(false);
        }

        int editorHeight = Math.max(1, this.editor.area.h);

        for (DockStackInfo info : stackInfos)
        {
            UIDraggable handle = this.dragHandlesById.get(info.activePanelId);

            if (handle == null)
            {
                continue;
            }

            float tabsOffset = info.isStacked() ? (float) DOCK_STACK_TABS_HEIGHT_PX / editorHeight : 0F;

            handle.relative(this.editor)
                .x(info.x)
                .y(info.y + tabsOffset + DRAG_HANDLE_TOP_OFFSET_NORM)
                .w(info.w)
                .h(DRAG_HANDLE_HEIGHT_NORM);
            handle.setVisible(!this.layoutLocked);
        }
    }

    private void updateEditorFlexBoundsOnly(EditorLayoutNode root)
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

    private void clearPanelDragState()
    {
        this.draggingPanelId = null;
        this.dropTargetPanelId = null;
        this.dropTargetZone = DROP_ZONE_CENTER;
    }

    private void applyPanelDropResult(String dragId, String targetId, int zone)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode newRoot = zone == DROP_ZONE_CENTER
            ? EditorLayoutNode.copyWithInsertStackAt(root, targetId, dragId)
            : EditorLayoutNode.copyWithInsertSplitAt(root, targetId, dragId, zone);

        if (newRoot != null && newRoot != root)
        {
            layout.setFilmLayoutRoot(newRoot);
            this.setupEditorFlex(true);
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
        handle.hoverOnly().rendering((context) -> this.renderPanelDragHandle(context, handle));
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

    private void renderSplitter(UIContext context, int index)
    {
        if (index < 0 || index >= this.splitterHandles.size() || index >= this.splitterHandleInfos.size())
        {
            return;
        }
        UIDraggable splitter = this.splitterHandles.get(index);
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        boolean active = splitter.area.isInside(context) || splitter.isDragging();
        int lineColor = active ? BBSSettings.primaryColor(Colors.A50) : 0x22ffffff;
        if (active)
        {
            context.batcher.box(splitter.area.x, splitter.area.y, splitter.area.ex(), splitter.area.ey(), lineColor);
        }
        if (info.horizontal)
        {
            int cy = splitter.area.y + splitter.area.h / 2;
            context.batcher.box(splitter.area.x, cy - 1, splitter.area.ex(), cy + 1, lineColor);
        }
        else
        {
            int cx = splitter.area.x + splitter.area.w / 2;
            context.batcher.box(cx - 1, splitter.area.y, cx + 1, splitter.area.ey(), lineColor);
        }
    }

    private void fillFilmContextMenu(ContextMenuManager menu)
    {
        if (this.data == null)
        {
            return;
        }

        menu.action(Icons.LIST, UIKeys.FILM_OPEN_HISTORY, () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIUndoHistoryOverlay(this), 200, 0.6F);
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

            UIOverlay.addOverlay(this.getContext(), panel, 200, 0.9F);
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

                    for (KeyframeChannel channel : replay.properties.properties.values())
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
            UIOverlay.addOverlay(this.getContext(), new UIFilmPlayerSettingsOverlayPanel(this.getData()), 280, 0.8F);
        });

        menu.action(Icons.HELP, L10n.lang("bbs.ui.film.details.button"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFilmDetailsOverlayPanel(this.getData()), 300, 260);
        });
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

        if (this.editor.area.w >= EDITOR_MIN_SIZE_FOR_PX_HANDLES && this.editor.area.h >= EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            if (!this.layoutLocked && this.splitterHandles.size() == this.splitterHandleInfos.size())
            {
                this.syncSplitterHandleBounds();
            }
            this.editor.resize();
        }

        boolean anySplitterDragging = this.splitterHandles.stream().anyMatch(UIDraggable::isDragging);
        if (!this.recorder.isExporting() && !anySplitterDragging
            && this.preview.area.w >= 2 && this.preview.area.h >= 2)
        {
            this.applyPreviewSizeToBBS();
        }
    }

    /**
     * Sets BBS fake window size to export resolution (from video settings).
     * Use when starting record, or when entering F1 fullscreen in film panel.
     */
    public static void applyExportSizeToBBS()
    {
        int w = Math.max(2, BBSSettings.videoSettings.width.get());
        int h = Math.max(2, BBSSettings.videoSettings.height.get());
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
            w = Math.max(2, BBSSettings.videoSettings.width.get());
            h = Math.max(2, BBSSettings.videoSettings.height.get());
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
                int exportW = Math.max(2, BBSSettings.videoSettings.width.get());
                int exportH = Math.max(2, BBSSettings.videoSettings.height.get());
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
        this.showPanel(this.panels.get(index));
    }

    public void showPanel(UIElement element)
    {
        int index = this.getPanelIndex();

        if (index >= 0)
        {
            this.captureTimelineViewport(this.panels.get(index));
        }

        this.selectedMainEditorPanel = element;
        this.updateMainEditorVisibility(this.hasFilmInCurrentTab());
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
        else if (panel == this.actionEditor)
        {
            this.timelineXMin = this.actionEditor.clips.scale.getMinValue();
            this.timelineXMax = this.actionEditor.clips.scale.getMaxValue();
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
        else if (panel == this.actionEditor)
        {
            this.actionEditor.clips.scale.view(this.timelineXMin, this.timelineXMax);
        }
        else if (panel == this.replayEditor && this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.view.getXAxis().view(this.timelineXMin, this.timelineXMax);
        }
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

            for (Replay replay : this.data.replays.getList())
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

                for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet())
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
                        copy.properties.properties.put(newChannel.getId(), newChannel);
                        copy.properties.add(newChannel);
                    }
                }

                data.replays.add(copy);
            }

            this.fill(data);
            this.save();
        }
    }

    @Override
    public void open()
    {
        super.open();

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder == null || recorder.hasNotStarted())
        {
            this.notifyServer(ActionState.RESTART);

            return;
        }

        this.applyRecordedKeyframes(recorder, this.data);
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

        if (rp != null)
        {
            BaseValue.edit(film, (f) ->
            {
                rp.keyframes.copyOver(recorder.keyframes, 0);

                Form form = rp.form.get();

                if (form != null)
                {
                    for (Map.Entry<String, KeyframeChannel> entry : recorder.properties.properties.entrySet())
                    {
                        KeyframeChannel channel = rp.properties.getOrCreate(form, entry.getKey());

                        if (channel != null && entry.getValue() != null)
                        {
                            channel.copyOver(entry.getValue(), 0);
                        }
                    }
                }

                f.inventory.fromData(recorder.inventory.toData());
                f.hp.set(recorder.hp);
                f.hunger.set(recorder.hunger);
                f.xpLevel.set(recorder.xpLevel);
                f.xpProgress.set(recorder.xpProgress);
            });
        }
    }

    @Override
    public void appear()
    {
        super.appear();

        BBSRendering.setCustomSize(true);
        MorphRenderer.hidePlayer = true;

        CameraController cameraController = this.getCameraController();

        this.fillData();
        this.setFlight(false);
        cameraController.add(this.runner);

        if (this.getContext() != null)
        {
            this.getContext().menu.getRoot().add(this.secretPlay);
        }
    }

    @Override
    public void close()
    {
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
        super.fill(data);
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

        this.openFilmMenu.setEnabled(data != null);
        this.openCameraEditor.setEnabled(data != null);
        this.openReplayEditor.setEnabled(data != null);
        this.openActionEditor.setEnabled(data != null);
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

        if (this.currentTab >= 0 && this.currentTab < this.tabs.size())
        {
            this.tabs.get(this.currentTab).filmId = data == null ? null : data.getId();
        }

        this.updateTabVisibility();
        this.tabBar.sync();
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

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        if (this.cameraEditor.isVisible()) UIDashboardPanels.renderHighlightHorizontal(context.batcher, this.openCameraEditor.area);
        if (this.replayEditor.isVisible()) UIDashboardPanels.renderHighlightHorizontal(context.batcher, this.openReplayEditor.area);
        if (this.actionEditor.isVisible()) UIDashboardPanels.renderHighlightHorizontal(context.batcher, this.openActionEditor.area);
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

        this.updateLogic(context);

        int color = BBSSettings.primaryColor.get();

        this.area.render(context.batcher, Colors.mulRGB(color | Colors.A100, 0.2F));

        if (this.editor.isVisible())
        {
            this.preview.area.render(context.batcher, Colors.A75);
        }

        super.render(context);

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

    /**
     * Draw icons for indicating different active states (like syncing
     * or flight mode)
     */
    private void renderIcons(UIContext context)
    {
        int x = this.iconBar.area.ex() - 18;
        int y = this.iconBar.area.ey() - EDIT_PANEL_TOP_OFFSET_PX * 2 - 20;

        if (BBSSettings.editorLoop.get())
        {
            context.batcher.icon(Icons.REFRESH, x, y);
        }
    }

    private void renderDividers(UIContext context)
    {
        Area a1 = this.openFilmMenu.area;

        context.batcher.box(a1.x + 3, a1.ey() + 4, a1.ex() - 3, a1.ey() + 5, 0x22ffffff);
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
