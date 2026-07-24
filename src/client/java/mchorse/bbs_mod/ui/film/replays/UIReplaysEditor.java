package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.FormControlKeys;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.PoseForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UIReplaysEditor extends UIElement
{
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static String lastFilm = "";
    private static int lastReplay;

    public UIReplaysListPanel replaysList;
    public UIReplayPropertiesPanel replayProperties;

    private static final int CATEGORY_BAR_WIDTH = 20;

    public UIElement iconBar;
    public Map<ReplayCategory, UIIcon> tabButtons = new HashMap<>();
    private ReplayCategory category = ReplayCategory.PLAYER;

    /* Keyframes */
    public UIKeyframeEditor keyframeEditor;

    /* Action clips share the timeline area; the toggle below the categories switches to them. */
    private UIClipsPanel actionTimeline;
    private UIIcon actionsToggle;
    private boolean actionsMode;
    /* «All tracks» view: shows every category's tracks at once, bypassing the category filter. */
    private UIIcon allToggle;
    private boolean allMode;

    /* Clips */
    private UIFilmPanel filmPanel;
    private Film film;
    private Replay replay;
    private boolean settingReplay;
    private Pair<Form, String> pendingPick;
    private boolean timelineVisible = true;
    private boolean propertiesVisible = true;
    private Set<String> keys = new LinkedHashSet<>();
    private final Map<String, Integer> keyToColor = new HashMap<>();
    private final Map<String, String> keyToLabel = new HashMap<>();
    private final Map<String, Set<String>> expandedPoseTabsByReplay = new HashMap<>();

    public enum ReplayCategory
    {
        PLAYER(Icons.PLAYER, L10n.lang("bbs.ui.film.replays.category.player"), L10n.lang("bbs.ui.film.replays.category.player.tooltip")),
        MODEL(Icons.BLOCK, L10n.lang("bbs.ui.film.replays.category.model"), L10n.lang("bbs.ui.film.replays.category.model.tooltip")),
        POSE(Icons.POSE, L10n.lang("bbs.ui.film.replays.category.pose"), L10n.lang("bbs.ui.film.replays.category.pose.tooltip")),
        IK(Icons.LIMB, L10n.lang("bbs.ui.film.replays.category.ik"), L10n.lang("bbs.ui.film.replays.category.ik.tooltip")),
        PHYSICS(Icons.DROP, L10n.lang("bbs.ui.film.replays.category.physics"), L10n.lang("bbs.ui.film.replays.category.physics.tooltip"));

        public final Icon icon;
        public final IKey label;
        public final IKey tooltip;

        private ReplayCategory(Icon icon, IKey label, IKey tooltip)
        {
            this.icon = icon;
            this.label = label;
            this.tooltip = tooltip;
        }
    }

    static
    {
        setupColors();
        setupIcons();
    }

    private static void setupColors()
    {
        putColors(Colors.RED, "x", "vX", "stick_lx", "stick_rx", "extra1_x", "extra2_x", "user1", "user5", "frequency", "offset_x");
        putColors(Colors.GREEN, "y", "vY", "stick_ly", "stick_ry", "trigger_l", "trigger_r", "extra1_y", "extra2_y", "user3", "count", "offset_y", "transform");
        putColors(Colors.BLUE, "z", "vZ", "user4", "offset_z");
        putColors(Colors.YELLOW, "yaw", "lighting");
        putColors(Colors.CYAN, "pitch");
        putColors(Colors.MAGENTA, "bodyYaw", "actions", "settings");
        putColors(Colors.ORANGE, "pose_overlay", "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet", "user2", "user6");

        COLORS.put("visible", Colors.WHITE & Colors.RGB);
        COLORS.put("pose", Colors.RED);
        COLORS.put("physics_targets", Colors.MAGENTA);
        COLORS.put("transform_overlay", 0xaaff00);
        COLORS.put("color", Colors.INACTIVE);
        COLORS.put("shape_keys", Colors.PINK);
    }

    private static void putColors(int color, String... keys)
    {
        for (String key : keys) COLORS.put(key, color);
    }

    private static void setupIcons()
    {
        ICONS.put("x", Icons.X);
        ICONS.put("y", Icons.Y);
        ICONS.put("z", Icons.Z);
        ICONS.put("pitch", Icons.VERTICAL);
        ICONS.put("headYaw", Icons.HORIZONTAL);
        ICONS.put("visible", Icons.VISIBLE);
        ICONS.put("texture", Icons.MATERIAL);
        ICONS.put("pose", Icons.POSE);
        ICONS.put("transform", Icons.ALL_DIRECTIONS);
        ICONS.put("color", Icons.BUCKET);
        ICONS.put("lighting", Icons.LIGHT);
        ICONS.put("actions", Icons.CONVERT);
        ICONS.put("shape_keys", Icons.HEART_ALT);
        ICONS.put("text", Icons.FONT);
        ICONS.put("stick_lx", Icons.LEFT_STICK);
        ICONS.put("stick_rx", Icons.RIGHT_STICK);
        ICONS.put("trigger_l", Icons.TRIGGER);
        ICONS.put("extra1_x", Icons.CURVES);
        ICONS.put("extra2_x", Icons.CURVES);
        ICONS.put("item_main_hand", Icons.LIMB);
        ICONS.put("user1", Icons.PARTICLE);
        ICONS.put("paused", Icons.TIME);
        ICONS.put("frequency", Icons.STOPWATCH);
        ICONS.put("count", Icons.BUCKET);
        ICONS.put("settings", Icons.GEAR);
        ICONS.put("physics_targets", Icons.TIME);
    }

    public static Icon getIcon(String key)
    {
        String topLevel = StringUtils.fileName(key);

        return ICONS.getOrDefault(topLevel, Icons.NONE);
    }

    public static int getColor(String key)
    {
        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return COLORS.get("pose_overlay");
        if (topLevel.startsWith("transform_overlay")) return COLORS.get("transform_overlay");
        if (COLORS.containsKey(topLevel)) return COLORS.get(topLevel);

        return Colors.BLUE;
    }

    /** The key a sheet is identified by in track filters (global and per-form). */
    public static String getSheetFilterKey(UIKeyframeSheet sheet)
    {
        if (sheet.isBoneTrack)
        {
            PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);

            if (path != null)
            {
                return path.formPath().isEmpty() ? path.bone() : path.formPath() + "/" + path.bone();
            }

            return sheet.title.get();
        }

        return StringUtils.fileName(sheet.id);
    }

    /** The form a sheet belongs to, whether it backs a form property or carries its owner directly (bones, materials, IK). */
    public static Form getSheetForm(UIKeyframeSheet sheet)
    {
        if (sheet.form != null)
        {
            return sheet.form;
        }

        return sheet.property == null ? null : FormUtils.getForm(sheet.property);
    }

    /** Single home of the category rule: tabs only filter now, so collectors always gather and this decides where a sheet lands. */
    public static ReplayCategory categoryOf(UIKeyframeSheet sheet)
    {
        String id = sheet.id;

        if (FormControlKeys.isIKControlChannel(id) || PerLimbService.isIKTargetChannel(id) || PerLimbService.isPoleTargetChannel(id))
        {
            return ReplayCategory.IK;
        }

        if (FormControlKeys.isPhysicsControlChannel(id) || PerLimbService.isPhysicsTargetChannel(id) || FormControlKeys.isWindControlChannel(id))
        {
            return ReplayCategory.PHYSICS;
        }

        if (PerLimbService.isPoseBoneChannel(id))
        {
            return ReplayCategory.POSE;
        }

        if (PerLimbService.isMaterialTextureChannel(id))
        {
            return ReplayCategory.MODEL;
        }

        if (sheet.property == null && sheet.form == null)
        {
            return ReplayCategory.PLAYER;
        }

        return FormUtils.isPoseProperty(StringUtils.fileName(id)) ? ReplayCategory.POSE : ReplayCategory.MODEL;
    }

    public static void renderRuler(UIContext context, UIKeyframes keyframes, UIClipsPanel clipsPanel, Clips camera, int clipOffset)
    {
        Area area = keyframes.graphArea;

        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);

        if (rulerBottom <= area.y)
        {
            return;
        }

        context.batcher.clip(area.x, area.y, area.ex(), rulerBottom, context);

        renderRulerAudio(context, keyframes, camera, clipOffset, area, rulerBottom);
        renderRulerClipGradient(context, keyframes, clipsPanel, clipOffset, area, rulerBottom);

        context.batcher.unclip(context);
    }

    private static boolean renderRulerAudio(UIContext context, UIKeyframes keyframes, Clips camera, int clipOffset, Area area, int rulerBottom)
    {
        if (!BBSSettings.audioWaveformVisibleInKeyframes.get())
        {
            return false;
        }

        Scale scale = keyframes.getXAxis();
        boolean renderedOnce = false;
        int y = area.y + 1;
        int h = Math.max(1, rulerBottom - y - 1);

        for (Clip clip : camera.get())
        {
            if (!(clip instanceof AudioClip audioClip))
            {
                continue;
            }

            Link link = audioClip.audio.get();

            if (link == null)
            {
                continue;
            }

            SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

            if (buffer == null || buffer.getWaveform() == null)
            {
                continue;
            }

            Waveform wave = buffer.getWaveform();
            int audioOffset = audioClip.offset.get();
            float offset = audioClip.tick.get() - clipOffset;
            int duration = Math.min((int) (wave.getDuration() * 20), clip.duration.get());
            int x1 = (int) scale.to(offset);
            int x2 = (int) scale.to(offset + duration);

            if (x2 <= area.x || x1 >= area.ex())
            {
                continue;
            }

            wave.render(context.batcher, Colors.WHITE, x1, y, x2 - x1, h, TimeUtils.toSeconds(audioOffset), TimeUtils.toSeconds(audioOffset + duration));

            renderedOnce = true;
        }

        return renderedOnce;
    }

    private static void renderRulerClipGradient(UIContext context, UIKeyframes keyframes, UIClipsPanel clipsPanel, int clipOffset, Area area, int rulerBottom)
    {
        Clip clip = clipsPanel.getClip();

        if (clip == null || clip instanceof AudioClip || !BBSSettings.editorClipPreview.get())
        {
            return;
        }

        Scale scale = keyframes.getXAxis();
        int x1 = (int) scale.to(clip.tick.get() - clipOffset);
        int x2 = (int) scale.to(clip.tick.get() + clip.duration.get() - clipOffset);

        if (x2 <= area.x || x1 >= area.ex())
        {
            return;
        }

        int color = clipsPanel.clips.getFactory().getData(clip).color;
        int left = Math.max(area.x, x1);
        int right = Math.min(area.ex(), x2);
        int top = area.y + 1;
        int bottom = Math.max(top + 1, rulerBottom - 1);

        context.batcher.gradientVBox(left, top, right, bottom, Colors.setA(color, 0.03F), Colors.setA(color, 0.78F));
        context.batcher.box(left, Math.max(top, bottom - 2), right, bottom, Colors.setA(color, 0.92F));
    }

    public UIReplaysEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;
        this.replayProperties = new UIReplayPropertiesPanel(filmPanel);
        this.replaysList = new UIReplaysListPanel(filmPanel, (l) -> this.setReplay(l.isEmpty() ? null : l.get(0), false, OrbitReaction.SWITCH), this.replayProperties.getFormConsumer());
        this.replayProperties.attachReplayList(this.replaysList.replays);

        this.iconBar = new UIElement();
        this.iconBar.relative(this).x(0).y(0).w(CATEGORY_BAR_WIDTH).h(1F).column(0).stretch();

        this.iconBar.add(new UIRenderable((context) ->
        {
            Area area = this.iconBar.area;

            context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.chromeSurface());

            /* Highlight the active category on the left edge (the actions toggle when in actions mode). */
            UIIcon activeIcon = this.actionsMode ? this.actionsToggle : (this.allMode ? this.allToggle : this.tabButtons.get(this.category));

            if (activeIcon != null && activeIcon.getParent() != null)
            {
                UIDashboardPanels.renderHighlight(context.batcher, activeIcon.area, Direction.LEFT);
            }
        }));

        for (ReplayCategory category : ReplayCategory.values())
        {
            UIIcon button = new UIIcon(category.icon, b -> this.setCategory(category));

            button.tooltip(category.tooltip, Direction.RIGHT);
            this.iconBar.add(button);
            this.tabButtons.put(category, button);
        }

        /* «All tracks» + actions toggles, pinned to the bottom of the category bar. */
        this.allToggle = new UIIcon(Icons.LIST, b -> this.setAllTracks());
        this.allToggle.tooltip(UIKeys.FILM_REPLAY_ALL_TRACKS, Direction.RIGHT);

        this.actionsToggle = new UIIcon(Icons.ACTION, b -> this.toggleActionsMode());
        this.actionsToggle.tooltip(UIKeys.FILM_REPLAY_ACTIONS_TIMELINE, Direction.RIGHT);
        this.layoutBottomToggles();

        this.setCategory(ReplayCategory.PLAYER);

        this.keys().register(Keys.REPLAYS_TAB_1, () -> this.setCategoryByPosition(0))
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_TAB_2, () -> this.setCategoryByPosition(1))
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_TAB_3, () -> this.setCategoryByPosition(2))
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_TAB_4, () -> this.setCategoryByPosition(3))
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_TAB_5, () -> this.setCategoryByPosition(4))
            .category(UIKeys.FILM_REPLAY_TITLE);

        this.add(this.iconBar, this.allToggle, this.actionsToggle);
        this.markContainer();
    }

    private void setCategory(ReplayCategory c)
    {
        this.actionsMode = false;
        this.allMode = false;
        this.category = c;
        this.updateChannelsList();
    }

    /** Show every category's tracks at once, bypassing the category filter. */
    private void setAllTracks()
    {
        this.actionsMode = false;
        this.allMode = true;
        this.updateChannelsList();
    }

    /**
     * Select the category sitting at the given visual position in the tab bar. The IK and physics tabs are only
     * present when the record has IK / physics, so a fixed key-to-category mapping would point past the gap; the
     * number keys instead follow the tabs as the user sees them, top to bottom.
     */
    private void setCategoryByPosition(int index)
    {
        List<ReplayCategory> present = new ArrayList<>();

        for (ReplayCategory category : ReplayCategory.values())
        {
            UIIcon button = this.tabButtons.get(category);

            if (button != null && button.getParent() != null)
            {
                present.add(category);
            }
        }

        present.sort(Comparator.comparingInt((c) -> this.tabButtons.get(c).area.y));

        if (index >= 0 && index < present.size())
        {
            this.setCategory(present.get(index));
        }
    }

    public ReplayCategory getCategory()
    {
        return this.category;
    }

    public void pickPlayerCategory()
    {
        if (this.category != ReplayCategory.PLAYER)
        {
            this.setCategory(ReplayCategory.PLAYER);
        }
    }

    public void setFilm(Film film)
    {
        this.savePoseTabState(this.replay);
        this.expandedPoseTabsByReplay.clear();
        this.film = film;
        this.filmPanel.getController().orbit.reset();

        if (film != null)
        {
            List<Replay> replays = film.replays.getList();
            int index = film.getId().equals(lastFilm) ? lastReplay : 0;

            if (!CollectionUtils.inRange(replays, index))
            {
                index = 0;
            }

            this.replaysList.replays.refreshReplayList();
            this.setReplay(replays.isEmpty() ? null : replays.get(index), true, OrbitReaction.SWITCH);
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public void setReplay(Replay replay)
    {
        this.setReplay(replay, true, OrbitReaction.SWITCH);
    }

    public void setReplay(Replay replay, boolean select, OrbitReaction orbit)
    {
        /* Guard against re-entry: scrollToReplay() below picks the replay in the list,
         * which fires the list's selection callback and calls setReplay() again. The
         * outermost call owns the orbit reaction, so the nested call is redundant and
         * must not override it (otherwise undo would teleport via the SWITCH callback). */
        if (this.settingReplay)
        {
            return;
        }

        this.settingReplay = true;

        try
        {
            this.savePoseTabState(this.replay);

            this.replay = replay;

            if (orbit == OrbitReaction.RESET)
            {
                this.filmPanel.getController().orbit.reset();
            }
            else if (orbit == OrbitReaction.SWITCH && replay != null && BBSSettings.editorOrbitTeleportOnSwitch.get())
            {
                this.filmPanel.getController().orbit.teleportPivotToReplay();
            }

            this.replayProperties.setReplay(replay);
            this.filmPanel.actionEditor.setClips(replay == null ? null : replay.actions);
            this.updateChannelsList();

            if (select && replay != null)
            {
                this.replaysList.replays.scrollToReplay(replay);
            }
        }
        finally
        {
            this.settingReplay = false;
        }
    }

    public void moveReplay(double x, double y, double z)
    {
        if (this.replay != null)
        {
            int cursor = this.filmPanel.getCursor();

            this.replay.keyframes.x.insert(cursor, x);
            this.replay.keyframes.y.insert(cursor, y);
            this.replay.keyframes.z.insert(cursor, z);
        }
    }

    public void updateChannelsList()
    {
        UIKeyframes lastEditor = this.keyframeEditor != null ? this.keyframeEditor.view : null;

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.removeFromParent();
            this.keyframeEditor = null;
        }

        if (this.replay == null)
        {
            return;
        }

        this.updateIKTab();
        this.updatePhysicsTab();

        List<UIKeyframeSheet> sheets = new ArrayList<>();
        Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs = new HashMap<>();
        Map<UIKeyframeSheet, Integer> poseTabDepths = new HashMap<>();

        this.collectCuratedSheets(sheets);
        this.collectFormPropertySheets(sheets, poseTabs, poseTabDepths);
        this.collectIKSheets(sheets);
        this.collectPhysicsSheets(sheets);

        this.keys.clear();
        this.keyToColor.clear();
        this.keyToLabel.clear();

        for (UIKeyframeSheet sheet : sheets)
        {
            String filterKey = getSheetFilterKey(sheet);

            this.keys.add(filterKey);
            this.keyToColor.put(filterKey, sheet.color);
            this.keyToLabel.put(filterKey, sheet.title.get());
        }

        Set<String> disabled = BBSSettings.disabledSheets.get();

        sheets.removeIf((v) ->
        {
            if (!this.allMode && categoryOf(v) != this.category)
            {
                return true;
            }

            String filterKey = getSheetFilterKey(v);

            for (String s : disabled)
            {
                if (filterKey.equals(s) || v.id.equals(s) || v.id.endsWith("/" + s))
                {
                    return true;
                }
            }

            Form owner = getSheetForm(v);

            if (owner != null)
            {
                Set<String> ownerDisabled = owner.disabledTracks.get();

                return ownerDisabled.contains(Form.DISABLED_ALL) || ownerDisabled.contains(filterKey);
            }

            return false;
        });

        /* Tabs only filter the gathered sheets, so drop pose-tab entries whose pose sheet the active tab filtered out. */
        Set<UIKeyframeSheet> kept = new LinkedHashSet<>(sheets);
        poseTabs.entrySet().removeIf((entry) ->
        {
            entry.getValue().removeIf((child) -> !kept.contains(child));

            return !kept.contains(entry.getKey()) || entry.getValue().isEmpty();
        });
        poseTabDepths.keySet().retainAll(kept);

        Form lastForm = null;

        for (UIKeyframeSheet sheet : sheets)
        {
            Form form = getSheetForm(sheet);

            if (!Objects.equals(lastForm, form))
            {
                sheet.separator = true;
            }

            lastForm = form;
        }

        if (!sheets.isEmpty())
        {
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.filmPanel.cameraEditor, consumer).absolute())
                .target(this.filmPanel.editArea)
                .editPanelTopOffset(this.filmPanel::getEditPanelTopOffsetPx);
            this.keyframeEditor.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
            this.keyframeEditor.setUndoId("replay_keyframe_editor");

            this.layoutBottomToggles();

            /* Reset */
            if (lastEditor != null)
            {
                this.keyframeEditor.view.copyViewport(lastEditor);
            }

            this.keyframeEditor.view.rulerRenderer((context) -> renderRuler(context, this.keyframeEditor.view, this.filmPanel.cameraEditor, this.film.camera, 0));
            this.keyframeEditor.view.duration(() -> this.film.camera.calculateDuration());
            this.keyframeEditor.view.context(menu ->
            {
                int mouseY = this.getContext().mouseY;
                UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(mouseY);

                if (this.replay.form.get() instanceof ModelForm modelForm)
                {
                    if (sheet != null && sheet.channel.getFactory() == KeyframeFactories.POSE && sheet.id.equals("pose"))
                    {
                        menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
                        {
                            ModelInstance model = ModelFormRenderer.getModel(modelForm);

                            if (model != null)
                            {
                                UIOverlay.addOverlay(
                                    this.getContext(),
                                    new UIAnimationToPoseOverlayPanel(
                                        (animationKey, onlyKeyframes, length, step) ->
                                        {
                                            int current = this.filmPanel.getCursor();
                                            IEntity entity = this.filmPanel.getController().getCurrentEntity();

                                            UIReplaysEditorUtils.animationToPoseKeyframes(this.keyframeEditor, sheet, modelForm, entity, current, animationKey, onlyKeyframes, length, step);
                                        },
                                    modelForm, sheet), 200, 197
                                );
                            }
                        });
                    }

                    List<String> controllers = ModelIKRuntime.getControllers(ModelFormRenderer.getModel(modelForm));
                    if (!controllers.isEmpty())
                    {
                        menu.action(Icons.CLOSE, UIKeys.FILM_REPLAY_CONTEXT_CLEAR_IK, () ->
                        {
                            UIReplaysEditorUtils.clearIKTracks(this.replay, modelForm);
                            this.updateChannelsList();
                        });
                    }
                }

                boolean isPoseTrack = sheet != null
                    && sheet.channel.getFactory() == KeyframeFactories.POSE
                    && (sheet.id.equals("pose")
                    || sheet.id.endsWith(FormUtils.PATH_SEPARATOR + "pose"))
                    && !sheet.id.contains("pose_overlay");

                Form sheetForm = sheet != null && sheet.property != null ? FormUtils.getForm(sheet.property) : null;
                boolean limbTracksOn = sheetForm instanceof PoseForm poseForm && poseForm.getBoneTracks().get();

                if (isPoseTrack && sheet.selection.hasAny() && limbTracksOn)
                {
                    menu.action(Icons.LIMB, UIKeys.FILM_REPLAY_CONTEXT_POSES_TO_LIMBS, () ->
                    {
                        UIReplaysEditorUtils.posesToLimbTracks(this.replay, sheet);

                        sheet.selection.removeSelected();
                        this.updateChannelsList();
                    });
                }

                if (this.keyframeEditor.view.getGraph() instanceof UIKeyframeDopeSheet)
                {
                    menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                    {
                        Set<String> disabledSet = BBSSettings.disabledSheets.get();
                        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(
                                disabledSet,
                                this.keys,
                                this.keyToColor,
                                this.keyToLabel
                        );

                        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

                        panel.onClose(e ->
                        {
                            BBSSettings.disabledSheets.set(disabledSet);
                            this.updateChannelsList();
                        });
                    });
                }
            });

            for (UIKeyframeSheet sheet : sheets)
            {
                this.keyframeEditor.view.addSheet(sheet);
            }

            Set<String> expandedPoseIds = this.expandedPoseTabsByReplay.getOrDefault(
                this.replay == null ? "" : this.replay.getId(),
                Collections.emptySet()
            );
            this.keyframeEditor.view.getDopeSheet().configurePoseTabs(poseTabs, poseTabDepths, expandedPoseIds);

            this.add(this.keyframeEditor);
            /* Category bar + actions toggle on top so they overlay the track names column. */
            this.bringBarToFront();
            this.updateTimelineModeVisibility();
        }

        this.resize();

        if (this.keyframeEditor != null && lastEditor == null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    private void collectCuratedSheets(List<UIKeyframeSheet> sheets)
    {
        for (String key : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue value = this.replay.keyframes.get(key);
            KeyframeChannel channel = (KeyframeChannel) value;

            sheets.add(new UIKeyframeSheet(getColor(key), false, channel, null).icon(ICONS.get(key)));
        }
    }

    private void collectFormPropertySheets(List<UIKeyframeSheet> sheets, Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs, Map<UIKeyframeSheet, Integer> poseTabDepths)
    {
        Form lastForm = null;
        List<UIKeyframeSheet> formSheets = new ArrayList<>();

        for (String key : FormUtils.collectPropertyPaths(this.replay.form.get()))
        {
            KeyframeChannel property = this.replay.properties.getOrCreate(this.replay.form.get(), key);

            if (property != null)
            {
                BaseValueBasic formProperty = FormUtils.getProperty(this.replay.form.get(), key);
                Form form = formProperty.getParent() instanceof Form ? (Form) formProperty.getParent() : null;

                if (form != lastForm)
                {
                    if (lastForm != null)
                    {
                        this.flushForm(sheets, formSheets, lastForm, poseTabs, poseTabDepths);
                    }

                    lastForm = form;
                }

                UIKeyframeSheet sheet = new UIKeyframeSheet(getColor(key), false, property, formProperty);

                formSheets.add(sheet.icon(getIcon(key)));
            }
        }

        if (lastForm != null)
        {
            this.flushForm(sheets, formSheets, lastForm, poseTabs, poseTabDepths);
        }
    }

    /** IK tracks live in their own category; they are not form properties, so collect them by walking the form tree. */
    private void collectIKSheets(List<UIKeyframeSheet> sheets)
    {
        this.collectIKSheets(sheets, this.replay.form.get());
    }

    private void collectIKSheets(List<UIKeyframeSheet> sheets, Form form)
    {
        if (form == null)
        {
            return;
        }

        if (form instanceof ModelForm modelForm)
        {
            UIReplaysEditorUtils.addIKControlSheet(modelForm, this.replay.properties, sheets);
            UIReplaysEditorUtils.addIKTargetSheets(modelForm, this.replay.properties, sheets);
            UIReplaysEditorUtils.addPoleTargetSheets(modelForm, this.replay.properties, sheets);
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            this.collectIKSheets(sheets, part.getForm());
        }
    }

    /** Physics tracks live in their own category; like IK they are not form properties, so collect them by walking the form tree. */
    private void collectPhysicsSheets(List<UIKeyframeSheet> sheets)
    {
        this.collectPhysicsSheets(sheets, this.replay.form.get());
    }

    private void collectPhysicsSheets(List<UIKeyframeSheet> sheets, Form form)
    {
        if (form == null)
        {
            return;
        }

        if (form instanceof ModelForm modelForm)
        {
            UIReplaysEditorUtils.addPhysicsControlSheet(modelForm, this.replay.properties, sheets);
            UIReplaysEditorUtils.addWindControlSheet(modelForm, this.replay.properties, sheets);
            UIReplaysEditorUtils.addPhysicsTargetSheets(modelForm, this.replay.properties, sheets);
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            this.collectPhysicsSheets(sheets, part.getForm());
        }
    }

    /** Show the IK tab only when the record actually has IK; bounce an active IK category back to Model when it does not. */
    private void updateIKTab()
    {
        UIIcon button = this.tabButtons.get(ReplayCategory.IK);

        if (button == null)
        {
            return;
        }

        boolean hasIK = this.formHasIK(this.replay.form.get());
        boolean present = button.getParent() != null;

        if (hasIK && !present)
        {
            this.iconBar.add(button);
            this.iconBar.resize();
        }
        else if (!hasIK && present)
        {
            button.removeFromParent();
            this.iconBar.resize();
        }

        if (!hasIK && this.category == ReplayCategory.IK)
        {
            this.category = ReplayCategory.MODEL;
        }
    }

    private boolean formHasIK(Form form)
    {
        if (form == null)
        {
            return false;
        }

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model != null)
            {
                model.form = modelForm;

                if (!ModelIKRuntime.getControllers(model).isEmpty())
                {
                    return true;
                }
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            if (this.formHasIK(part.getForm()))
            {
                return true;
            }
        }

        return false;
    }

    /** Show the Physics tab only when the record actually has physics chains; bounce an active Physics category back to Model when it does not. */
    private void updatePhysicsTab()
    {
        UIIcon button = this.tabButtons.get(ReplayCategory.PHYSICS);

        if (button == null)
        {
            return;
        }

        boolean hasPhysics = this.formHasPhysics(this.replay.form.get());
        boolean present = button.getParent() != null;

        if (hasPhysics && !present)
        {
            this.iconBar.add(button);
            this.iconBar.resize();
        }
        else if (!hasPhysics && present)
        {
            button.removeFromParent();
            this.iconBar.resize();
        }

        if (!hasPhysics && this.category == ReplayCategory.PHYSICS)
        {
            this.category = ReplayCategory.MODEL;
        }
    }

    private boolean formHasPhysics(Form form)
    {
        if (form == null)
        {
            return false;
        }

        if (form instanceof ModelForm modelForm && modelForm.physics.get() instanceof MapType map)
        {
            ModelPhysicsConfig config = ModelPhysicsIO.fromData(map);

            if (config != null && config.bones() != null && !config.bones().isEmpty())
            {
                return true;
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            if (this.formHasPhysics(part.getForm()))
            {
                return true;
            }
        }

        return false;
    }

    private void flushForm(List<UIKeyframeSheet> sheets, List<UIKeyframeSheet> formSheets, Form form, Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs, Map<UIKeyframeSheet, Integer> poseTabDepths)
    {
        String path = FormUtils.getPath(form);
        String poseId = path.isEmpty() ? "pose" : path + FormUtils.PATH_SEPARATOR + "pose";
        UIKeyframeSheet poseSheet = null;

        for (UIKeyframeSheet sheet : formSheets)
        {
            if (poseId.equals(sheet.id) && sheet.channel.getFactory() == KeyframeFactories.POSE)
            {
                poseSheet = sheet;
                break;
            }
        }

        List<UIKeyframeSheet> orderedFormSheets = new ArrayList<>(formSheets);
        formSheets.clear();

        if (form instanceof ModelForm modelForm)
        {
            List<UIKeyframeSheet> materialSheets = new ArrayList<>();
            UIReplaysEditorUtils.addMaterialTextureSheets(modelForm, this.replay.properties, materialSheets);
            orderedFormSheets.addAll(materialSheets);
        }

        if (form instanceof PoseForm)
        {
            List<UIKeyframeSheet> boneSheets = new ArrayList<>();
            Map<String, Integer> depthBySheetId = new HashMap<>();
            UIReplaysEditorUtils.addBoneTrackSheets(form, this.replay.properties, boneSheets, depthBySheetId);

            for (UIKeyframeSheet boneSheet : boneSheets)
            {
                Integer depth = depthBySheetId.get(boneSheet.id);
                poseTabDepths.put(boneSheet, depth == null ? 0 : depth);
            }

            if (poseSheet != null && !boneSheets.isEmpty())
            {
                poseTabs.put(poseSheet, boneSheets);

                int poseIndex = orderedFormSheets.indexOf(poseSheet);

                if (poseIndex >= 0)
                {
                    orderedFormSheets.addAll(poseIndex + 1, boneSheets);
                }
                else
                {
                    orderedFormSheets.addAll(boneSheets);
                }
            }
            else
            {
                orderedFormSheets.addAll(boneSheets);
            }
        }

        sheets.addAll(orderedFormSheets);
    }

    private void savePoseTabState(Replay replay)
    {
        if (replay == null || this.keyframeEditor == null)
        {
            return;
        }

        this.expandedPoseTabsByReplay.put(replay.getId(), this.keyframeEditor.view.getDopeSheet().getExpandedPoseTabIds());
    }

    /**
     * Re-applies keyframe parameters panel position (e.g. after layout lock
     * toggle).
     */
    public void refreshEditPanelOffset()
    {
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.refreshEditPanelOffset();
        }

        if (this.replaysList != null)
        {
            this.replaysList.refreshEditPanelOffset();
        }

        if (this.replayProperties != null)
        {
            this.replayProperties.refreshEditPanelOffset();
        }
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.updateTimelineModeVisibility();
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;
        this.updateTimelineModeVisibility();
    }

    /** The action-clips timeline shares this editor's timeline area; the actions toggle switches to it. */
    public void attachActionTimeline(UIClipsPanel actionTimeline)
    {
        this.actionTimeline = actionTimeline;
        actionTimeline.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
        this.add(actionTimeline);
        this.bringBarToFront();
        this.updateTimelineModeVisibility();
    }

    public boolean isActionsMode()
    {
        return this.actionsMode;
    }

    private void toggleActionsMode()
    {
        this.setActionsMode(!this.actionsMode);
    }

    public void setActionsMode(boolean actionsMode)
    {
        if (this.actionsMode == actionsMode)
        {
            return;
        }

        this.actionsMode = actionsMode;
        this.updateTimelineModeVisibility();
    }

    /**
     * Show either the keyframe timeline or the action-clips timeline in the same area;
     * their parameters share editArea, so only the active mode's panel is shown.
     */
    private void updateTimelineModeVisibility()
    {
        boolean keyframes = !this.actionsMode;

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.setTimelineVisible(this.timelineVisible && keyframes);
            this.keyframeEditor.setPropertiesVisible(this.propertiesVisible && keyframes);
        }

        if (this.actionTimeline != null)
        {
            this.actionTimeline.setVisible((this.timelineVisible || this.propertiesVisible) && this.actionsMode);
            this.actionTimeline.setTimelineVisible(this.timelineVisible && this.actionsMode);
            this.actionTimeline.setPropertiesVisible(this.propertiesVisible && this.actionsMode);
        }
    }

    /** Keep the category bar and the actions toggle above the timelines. */
    private void bringBarToFront()
    {
        if (this.iconBar.getParent() != null)
        {
            this.iconBar.removeFromParent();
        }

        if (this.allToggle.getParent() != null)
        {
            this.allToggle.removeFromParent();
        }

        if (this.actionsToggle.getParent() != null)
        {
            this.actionsToggle.removeFromParent();
        }

        this.add(this.iconBar, this.allToggle, this.actionsToggle);
    }

    /**
     * Pin the actions toggle to the right edge of the track-names column. The iconBar
     * shrink-wraps to its category icons, so anchor to the editor by label width instead.
     */
    private void layoutBottomToggles()
    {
        this.allToggle.relative(this).x(0).y(1F, -40).wh(CATEGORY_BAR_WIDTH, 20);
        this.actionsToggle.relative(this).x(0).y(1F, -20).wh(CATEGORY_BAR_WIDTH, 20);
    }

    public void pickForm(Form form, String bone)
    {
        this.pickFormBone(form, bone, false);
    }

    /**
     * Bone pick from the 3D viewport with the Shift / Ctrl offer gestures. The gizmo
     * sphere's deferred pick goes through here so clicking a bone on the sphere behaves
     * like a direct bone click — Shift opens the hierarchy menu, Ctrl multi-selects —
     * instead of only doing a plain select.
     */
    public void pickFormWithOffers(UIContext context, Form form, String bone)
    {
        UIReplaysEditorUtils.pickFormWithOffers(context, new Pair<>(form, bone), this::pickFormBone);
    }

    /**
     * Picking an editable bone in the viewport is a pose edit, but the pose/bone tracks
     * only exist in the {@link ReplayCategory#POSE} category. So when another category
     * is open, jump to Pose first (and out of actions mode) before delegating to the
     * shared pick logic — otherwise the click finds no pose sheet in the current graph
     * and silently does nothing, forcing a manual tab switch.
     */
    private void pickFormBone(Form form, String bone, boolean insert)
    {
        if (form instanceof PoseForm && bone != null && !bone.isEmpty())
        {
            if (this.allMode)
            {
                this.setActionsMode(false);
            }
            else if (this.category != ReplayCategory.POSE || this.actionsMode)
            {
                this.setCategory(ReplayCategory.POSE);
            }
        }

        UIReplaysEditorUtils.pickForm(this.keyframeEditor, this.filmPanel, form, bone, insert);
    }

    public boolean clickViewport(UIContext context, Area area)
    {
        if (this.filmPanel.isFlying() && area.isInside(context))
        {
            if (context.mouseButton == 0 && this.filmPanel.getController().orbit.enabled)
            {
                this.filmPanel.getController().orbit.start(context);

                return true;
            }
            if (context.mouseButton == 2)
            {
                this.filmPanel.dashboard.orbit.start(2, context.mouseX, context.mouseY);

                return true;
            }
        }

        if (this.filmPanel.isFlying())
        {
            return false;
        }

        if (area.isInside(context) && context.mouseButton == 2 && this.filmPanel.getController().orbit.enabled)
        {
            this.filmPanel.getController().orbit.start(context);

            return true;
        }

        StencilFormFramebuffer stencil = this.filmPanel.getController().getStencil();

        /* In orbit mode left-drag rotates the camera anywhere, even over a form.
         * The form selection is deferred to release, so a click still selects but a
         * drag orbits instead of being swallowed by the form under the cursor. */
        if (area.isInside(context) && context.mouseButton == 0 && this.filmPanel.getController().orbit.enabled)
        {
            this.pendingPick = stencil.hasPicked() ? stencil.getPicked() : null;
            this.filmPanel.getController().orbit.start(context);

            return true;
        }

        if (stencil.hasPicked())
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null && (context.mouseButton < 2 || (context.mouseButton == 2 && Window.isCtrlPressed())))
            {
                if (!this.isVisible())
                {
                    this.filmPanel.showPanel(this);
                }

                if (UIReplaysEditorUtils.pickFormWithOffers(context, pair, this::pickFormBone))
                {
                    return true;
                }
            }
        }
        else if (context.mouseButton == 1 && this.isVisible())
        {
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.filmPanel.getCamera();

            BlockHitResult blockHitResult = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(camera.position),
                RayTracing.fromVector3f(
                    CameraUtils.getMouseDirection(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h)
                ),
                256F
            );

            if (blockHitResult.getType() != HitResult.Type.MISS)
            {
                Vector3d vec = new Vector3d(blockHitResult.getPos().x, blockHitResult.getPos().y, blockHitResult.getPos().z);

                if (Window.isShiftPressed())
                {
                    vec = new Vector3d(Math.floor(vec.x) + 0.5D, Math.round(vec.y), Math.floor(vec.z) + 0.5D);
                }

                final Vector3d finalVec = vec;

                context.replaceContextMenu(menu ->
                {
                    float pitch = 0F;
                    float yaw = MathUtils.toDeg(camera.rotation.y);

                    menu.action(Icons.ADD, UIKeys.FILM_REPLAY_CONTEXT_ADD, () -> this.replaysList.replays.addReplay(finalVec, pitch, yaw));
                    menu.action(Icons.POINTER, UIKeys.FILM_REPLAY_CONTEXT_MOVE_HERE, () -> this.moveReplay(finalVec.x, finalVec.y, finalVec.z));
                });

                return true;
            }
        }

        return false;
    }

    public void releaseViewport(UIContext context, boolean dragged)
    {
        Pair<Form, String> pending = this.pendingPick;

        this.pendingPick = null;

        if (pending == null || dragged || context.mouseButton != 0)
        {
            return;
        }

        if (!this.isVisible())
        {
            this.filmPanel.showPanel(this);
        }

        UIReplaysEditorUtils.pickFormWithOffers(context, pending, this::pickFormBone);
    }

    public void close()
    {
        if (this.film != null)
        {
            lastFilm = this.film.getId();
            Replay r = this.getReplay();

            lastReplay = r == null ? 0 : this.film.replays.getList().indexOf(r);
        }
    }

    public void teleport()
    {
        if (this.filmPanel.getData() == null)
        {
            return;
        }

        Replay replay = this.getReplay();

        if (replay != null)
        {
            int tick = this.filmPanel.getCursor();
            double x = replay.keyframes.x.interpolate(tick);
            double y = replay.keyframes.y.interpolate(tick);
            double z = replay.keyframes.z.interpolate(tick);
            float yaw = replay.keyframes.yaw.interpolate(tick).floatValue();
            float headYaw = replay.keyframes.headYaw.interpolate(tick).floatValue();
            float bodyYaw = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
            float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            PlayerUtils.teleport(x, y, z, headYaw, pitch);
            player.setYaw(yaw);
            player.setHeadYaw(headYaw);
            player.setBodyYaw(bodyYaw);
            player.setPitch(pitch);
        }
    }

    @Override
    public void render(UIContext context)
    {
        /* Hide category bar + actions toggle while the "edit track" overlay is open */
        boolean notEditing = this.keyframeEditor == null || !this.keyframeEditor.view.isEditing();

        this.iconBar.setVisible(this.timelineVisible && notEditing);
        this.actionsToggle.setVisible(this.timelineVisible && notEditing);

        UIReplaysEditorUtils.configureFilmHotkeyDrag(this.filmPanel, context);

        super.render(context);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.layoutBottomToggles();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));
        List<Integer> currentIndices = this.replaysList.replays.getCurrentIndices();

        this.setReplay(CollectionUtils.getSafe(this.film.replays.getList(), data.getInt("replay")), true, OrbitReaction.KEEP);

        currentIndices.clear();
        currentIndices.addAll(selection);
        this.replaysList.replays.update();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        int index = this.film.replays.getList().indexOf(this.getReplay());

        data.putInt("replay", index);
        data.put("selection", DataStorageUtils.intListToData(this.replaysList.replays.getCurrentIndices()));
    }

    /**
     * How the orbit camera should react when the selected replay is set.
     */
    public enum OrbitReaction
    {
        /** Reset the orbit camera to its default position. */
        RESET,
        /** Treat it as a user switching replays — teleport the pivot onto the replay if the setting allows. */
        SWITCH,
        /** Leave the orbit camera untouched (used when restoring selection during undo/redo). */
        KEEP
    }
}
