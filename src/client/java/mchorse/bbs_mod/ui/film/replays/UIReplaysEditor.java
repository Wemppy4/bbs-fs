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
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIStackedIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.IBoneSelectionHost;
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
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UIReplaysEditor extends UIElement implements IBoneSelectionHost
{
    private final BoneSelection boneSelection = new BoneSelection();

    private static String lastFilm = "";
    private static int lastReplay;

    public UIReplaysListPanel replaysList;
    public UIReplayPropertiesPanel replayProperties;

    private static final int CATEGORY_BAR_WIDTH = 20;

    public UIElement iconBar;
    public Map<ReplayCategory, UIIcon> tabButtons = new HashMap<>();
    private ReplayCategory category = ReplayCategory.REPLAY;

    /* Keyframes */
    public UIKeyframeEditor keyframeEditor;

    /* Action clips share the timeline area; the toggle below the categories switches to them. */
    private UIClipsPanel actionTimeline;
    private UIIcon actionsToggle;
    private boolean actionsMode;
    /* «All tracks» view: shows every category's tracks at once, bypassing the category filter. */
    private UIIcon allToggle;
    private UIIcon collapseAll;
    private UIIcon expandAll;
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
    /**
     * Which rows the user left unfolded, per replay. Every rebuild of the timeline throws the dope
     * sheet away — switching category, toggling "all tracks", changing the track filter — so this set
     * is handed to each new sheet, which folds in it directly rather than keeping a copy.
     */
    private final Map<String, Set<String>> expandedTracksByReplay = new HashMap<>();

    public enum ReplayCategory
    {
        REPLAY(Icons.PLAYER, L10n.lang("bbs.ui.film.replays.category.replay"), L10n.lang("bbs.ui.film.replays.category.replay.tooltip")),
        FORM(Icons.BLOCK, L10n.lang("bbs.ui.film.replays.category.form"), L10n.lang("bbs.ui.film.replays.category.form.tooltip")),
        POSE(Icons.POSE, L10n.lang("bbs.ui.film.replays.category.pose"), L10n.lang("bbs.ui.film.replays.category.pose.tooltip")),
        IK(Icons.IK, L10n.lang("bbs.ui.film.replays.category.ik"), L10n.lang("bbs.ui.film.replays.category.ik.tooltip")),
        PHYSICS(Icons.PHYSICS, L10n.lang("bbs.ui.film.replays.category.physics"), L10n.lang("bbs.ui.film.replays.category.physics.tooltip"));

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


    public static Icon getIcon(String key)
    {
        return TrackStyle.icon(key);
    }

    public static int getColor(String key)
    {
        return TrackStyle.color(key);
    }

    /** The key a sheet is identified by in track filters (global and per-form) and in name/colour overrides. */
    public static String getSheetFilterKey(UIKeyframeSheet sheet)
    {
        return sheet.getFilterKey();
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
        return categoryOf(sheet.id, sheet.property != null || sheet.form != null);
    }

    /**
     * @param owned whether the track belongs to a form at all — a replay's own curated channels
     *              (position, hotbar, sticks) do not, and they are the Replay tab
     */
    public static ReplayCategory categoryOf(TrackId track, boolean owned)
    {
        return categoryOf(track == null ? null : track.toKey(), owned);
    }

    private static ReplayCategory categoryOf(String id, boolean owned)
    {
        TrackKind kind = TrackId.kindOf(id);

        if (kind != null)
        {
            switch (kind)
            {
                case IK_CONTROLS, IK_TARGET, POLE_TARGET:
                    return ReplayCategory.IK;
                case PHYSICS_CONTROLS, PHYSICS_TARGET, WIND_CONTROLS:
                    return ReplayCategory.PHYSICS;
                case BONE, BONE_CONSTRAINT:
                    return ReplayCategory.POSE;
                case MATERIAL_TEXTURE, MATERIAL_PROP:
                    return ReplayCategory.FORM;
                default:
                    break;
            }
        }

        /* What is left is a plain property track — a curated replay channel (which belongs to no form)
         * or one of the form's own properties. */
        if (!owned)
        {
            return ReplayCategory.REPLAY;
        }


        return FormUtils.isPoseProperty(StringUtils.fileName(id)) ? ReplayCategory.POSE : ReplayCategory.FORM;
    }

    public static void renderRuler(UIContext context, UIKeyframes keyframes, UIClipsPanel clipsPanel, Clips camera, int clipOffset)
    {
        Area area = keyframes.graphArea;

        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);

        if (rulerBottom <= area.y)
        {
            return;
        }

        context.batcher.clipBox(area.x, area.y, area.ex(), rulerBottom, context);

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
        }));

        /* «All tracks» heads the bar: it is not one of the categories but what you see instead of
         * them, so it sits above the rule that separates the two. */
        this.allToggle = new UIIcon(Icons.LIST, b -> this.setAllTracks());
        this.allToggle.tooltip(UIKeys.FILM_REPLAY_ALL_TRACKS, Direction.RIGHT);
        this.allToggle.highlight(() -> !this.actionsMode && this.allMode, Direction.LEFT);

        this.iconBar.add(this.allToggle);
        this.iconBar.add(this.buildCategorySeparator());

        for (ReplayCategory category : ReplayCategory.values())
        {
            UIIcon button = new UIIcon(category.icon, b -> this.setCategory(category));

            button.tooltip(category.tooltip, Direction.RIGHT);
            button.highlight(() -> !this.actionsMode && !this.allMode && this.category == category, Direction.LEFT);
            this.iconBar.add(button);
            this.tabButtons.put(category, button);
        }

        /* Folding and the actions timeline, pinned to the bottom of the bar. */
        /* Arrows meeting in the middle: bring the sections together. */
        this.collapseAll = new UIStackedIcon(Icons.MOVE_DOWN, Icons.MOVE_UP, b -> this.setAllFolded(false));
        this.collapseAll.tooltip(UIKeys.FILM_REPLAY_COLLAPSE_ALL, Direction.RIGHT);

        /* And pushing apart: open them up. */
        this.expandAll = new UIStackedIcon(Icons.MOVE_UP, Icons.MOVE_DOWN, b -> this.setAllFolded(true));
        this.expandAll.tooltip(UIKeys.FILM_REPLAY_EXPAND_ALL, Direction.RIGHT);

        this.actionsToggle = new UIIcon(Icons.ACTION, b -> this.toggleActionsMode());
        this.actionsToggle.tooltip(UIKeys.FILM_REPLAY_ACTIONS_TIMELINE, Direction.RIGHT);
        this.actionsToggle.highlight(() -> this.actionsMode, Direction.LEFT);
        this.layoutBottomToggles();

        /* Everything at once is the view to open on: a category is a way to narrow down, and
         * narrowing before the animator has seen what there is hides tracks they came for. */
        this.setAllTracks();

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

        this.add(this.iconBar, this.collapseAll, this.expandAll, this.actionsToggle);
        this.markContainer();
    }

    /** A rule between "all tracks" and the categories: they are different questions, not siblings. */
    private UIElement buildCategorySeparator()
    {
        UIElement separator = new UIElement();

        separator.h(7);
        separator.add(new UIRenderable((context) ->
        {
            Area area = separator.area;
            int y = area.my();

            context.batcher.box(area.x + 4, y, area.ex() - 4, y + 1, BBSSettings.dividerColor());
        }));

        return separator;
    }

    /** Fold or unfold every section and bone of the timeline at once. */
    private void setAllFolded(boolean unfold)
    {
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.getDopeSheet().setAllFolded(unfold);
        }
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
     * present when the replay has IK / physics, so a fixed key-to-category mapping would point past the gap; the
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

    public void pickReplayCategory()
    {
        if (this.category != ReplayCategory.REPLAY)
        {
            this.setCategory(ReplayCategory.REPLAY);
        }
    }

    public void setFilm(Film film)
    {
        this.expandedTracksByReplay.clear();
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

        List<TrackDescriptor> catalog = TrackCatalog.ordered(TrackCatalog.of(this.replay.form.get(), this.replay.properties));

        this.updateTab(ReplayCategory.IK, catalog);
        this.updateTab(ReplayCategory.PHYSICS, catalog);

        List<UIKeyframeSheet> sheets = new ArrayList<>();

        this.collectCuratedSheets(sheets);
        UIReplaysEditorUtils.buildSheets(catalog, sheets);

        this.keys.clear();

        for (UIKeyframeSheet sheet : sheets)
        {
            /* Headers name body parts, not tracks — the filter menu has nothing to offer for them. */
            if (!sheet.header)
            {
                this.keys.add(getSheetFilterKey(sheet));
            }
        }

        Set<String> disabled = BBSSettings.disabledSheets.get();

        /* A body part's row belongs to no category — it says whose the tracks under it are, whatever
         * they animate. It leaves with its last child instead (see dropEmptyHeaders). */
        sheets.removeIf((v) -> !v.header && !this.allMode && categoryOf(v) != this.category);

        /* The tab isn't empty by itself - so if the filter empties it, the timeline has to stay (see below). */
        boolean hadTracks = !sheets.isEmpty();

        sheets.removeIf((v) ->
        {
            if (v.header)
            {
                return false;
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

        /*
         * Filtering every track off used to drop the timeline itself, and the track filter lives in its
         * context menu - so «disable all» locked the user out of the only way back. Keep the (empty)
         * timeline whenever the tab had tracks before the filter ran; the dope sheet says why it's blank.
         */
        boolean filteredOutEverything = hadTracks && sheets.isEmpty();

        UIReplaysEditorUtils.dropEmptyHeaders(sheets);
        UIReplaysEditorUtils.detachMissingParents(sheets);

        if (!sheets.isEmpty() || filteredOutEverything)
        {
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.filmPanel.cameraEditor, consumer).absolute())
                .target(this.filmPanel.editArea);
            this.keyframeEditor.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
            this.keyframeEditor.setUndoId("replay_keyframe_editor");
            this.keyframeEditor.view.getDopeSheet().setEmptyState(UIKeys.KEYFRAMES_EMPTY_FILTERED, UIKeys.KEYFRAMES_EMPTY_FILTERED_HINT);

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
                if (this.replay.form.get() instanceof ModelForm modelForm)
                {
                    int mouseY = this.getContext().mouseY;
                    UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(mouseY);

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

                    boolean isPoseTrack = sheet != null
                        && sheet.channel.getFactory() == KeyframeFactories.POSE
                        && (sheet.id.equals("pose")
                        || sheet.id.endsWith(FormUtils.PATH_SEPARATOR + "pose"))
                        && !sheet.id.contains("pose_overlay");

                    Form sheetForm = sheet != null && sheet.property != null ? FormUtils.getForm(sheet.property) : null;
                    boolean limbTracksOn = sheetForm instanceof ModelForm m && m.boneTracks.get();

                    if (isPoseTrack && sheet.selection.hasAny() && limbTracksOn)
                    {
                        ModelForm poseModelForm = sheetForm instanceof ModelForm m ? m : modelForm;
                        menu.action(Icons.LIMB, UIKeys.FILM_REPLAY_CONTEXT_POSES_TO_LIMBS, () ->
                        {
                            UIReplaysEditorUtils.posesToLimbTracks(this.replay, sheet, poseModelForm);

                            sheet.selection.removeSelected();
                            this.updateChannelsList();
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

                if (this.keyframeEditor.view.getGraph() instanceof UIKeyframeDopeSheet)
                {
                    menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                    {
                        Set<String> disabledSet = BBSSettings.disabledSheets.get();
                        Map<String, Integer> keyToColor = new HashMap<>();
                        for (UIKeyframeSheet sheet : this.keyframeEditor.view.getGraph().getSheets())
                        {
                            keyToColor.put(getSheetFilterKey(sheet), sheet.color);
                        }
                        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(
                                disabledSet,
                                this.keys,
                                keyToColor
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

            this.keyframeEditor.view.getDopeSheet().setExpanded(this.getExpandedTracks());

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

            sheets.add(new UIKeyframeSheet(getColor(key), channel, null).icon(getIcon(key)));
        }
    }

    /**
     * Show a category's tab only while the replay actually has tracks of that kind, and bounce the
     * active category back to Model when it does not. Asked of the catalog, so "does this replay have
     * IK" is the same question as "which tracks land in the IK tab" — it used to be a separate walk
     * of the form tree per category, with its own idea of the answer.
     */
    private void updateTab(ReplayCategory category, List<TrackDescriptor> catalog)
    {
        UIIcon button = this.tabButtons.get(category);

        if (button == null)
        {
            return;
        }

        boolean has = false;

        for (TrackDescriptor track : catalog)
        {
            if (categoryOf(track.id(), track.owner() != null) == category)
            {
                has = true;

                break;
            }
        }

        boolean present = button.getParent() != null;

        if (has != present)
        {
            if (has)
            {
                this.iconBar.add(button);
            }
            else
            {
                button.removeFromParent();
            }

            this.iconBar.resize();
        }

        if (!has && this.category == category)
        {
            this.category = ReplayCategory.FORM;
        }
    }

    /**
     * Rows the user has unfolded in this replay's timeline. Handed to the dope sheet as-is, so folding
     * a row there lands here directly — there is nothing to read back out when the timeline is rebuilt,
     * which is what the old save-and-restore step existed for (and it had to know which tracks the
     * current category could even answer for).
     */
    public Set<String> getExpandedTracks()
    {
        return this.expandedTracksByReplay.computeIfAbsent(this.replay == null ? "" : this.replay.getId(), (k) -> new HashSet<>());
    }

    /** Pose tracks unfolded right now — what {@code insertFrame} keys by, per limb or as a whole pose. */
    public Set<String> getExpandedPoseTabIds()
    {
        return this.getExpandedTracks();
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

        for (UIIcon pinned : new UIIcon[] {this.collapseAll, this.expandAll, this.actionsToggle})
        {
            if (pinned.getParent() != null)
            {
                pinned.removeFromParent();
            }
        }

        this.add(this.iconBar, this.collapseAll, this.expandAll, this.actionsToggle);
    }

    /**
     * Pin the actions toggle to the right edge of the track-names column. The iconBar
     * shrink-wraps to its category icons, so anchor to the editor by label width instead.
     */
    private void layoutBottomToggles()
    {
        this.collapseAll.relative(this).x(0).y(1F, -60).wh(CATEGORY_BAR_WIDTH, 20);
        this.expandAll.relative(this).x(0).y(1F, -40).wh(CATEGORY_BAR_WIDTH, 20);
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
     * Picking a model bone in the viewport is a pose edit, but the pose/bone tracks
     * only exist in the {@link ReplayCategory#POSE} category. So when another category
     * is open, jump to Pose first (and out of actions mode) before delegating to the
     * shared pick logic — otherwise the click finds no pose sheet in the current graph
     * and silently does nothing, forcing a manual tab switch.
     */

    @Override
    public BoneSelection getBoneSelection()
    {
        return this.boneSelection;
    }

    private void pickFormBone(Form form, String bone, boolean insert)
    {
        if (form instanceof ModelForm && bone != null && !bone.isEmpty())
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

            Vector3f rayOffset = new Vector3f();
            Vector3f rayDirection = CameraUtils.getMouseRay(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

            BlockHitResult blockHitResult = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
                RayTracing.fromVector3f(rayDirection),
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

        PlayerUtils.teleportToReplay(this.getReplay(), this.filmPanel.getCursor());
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
