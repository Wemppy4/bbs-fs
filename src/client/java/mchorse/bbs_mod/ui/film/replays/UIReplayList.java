package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * This GUI is responsible for drawing replays available in the director thing
 */
public class UIReplayList extends UIList<ReplayListEntry>
{
    private static String LAST_OFFSET = "0";
    private static final ProcessReplaysState PROCESS_STATE = new ProcessReplaysState();

    public UIFilmPanel panel;
    public UIReplaysOverlayPanel overlay;

    /** Category names whose replay rows are hidden (headers stay visible). */
    private final Set<String> collapsedCategories = new HashSet<>();

    /** Set while building the context menu when the cursor is on a category folder row. */
    private String contextFolderCategoryName;

    private static class ProcessReplaysState
    {
        public String expression = "v";
        public List<String> properties = new ArrayList<>(Arrays.asList("x"));
        public boolean advanced;

        public NormalOperation operation = NormalOperation.RANDOM;
        public double randomMin = -1;
        public double randomMax = 1;
        public double lineOffset = 1;
        public double size = 3;
        public double shift = 1;
    }

    public UIReplayList(Consumer<List<Replay>> callback, UIReplaysOverlayPanel overlay, UIFilmPanel panel)
    {
        super((entries) -> callback.accept(replaysFromEntries(entries)));

        this.overlay = overlay;
        this.panel = panel;

        this.multi().sorting();
        this.context((menu) ->
        {
            Film film = this.panel.getData();

            menu.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CONTEXT_ADD, this::addReplay);

            if (film != null)
            {
                menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_ADD_CATEGORY, this::openAddCategoryOverlay);
            }

            if (film != null && this.contextFolderCategoryName != null)
            {
                String cat = this.contextFolderCategoryName;

                menu.action(Icons.TRASH, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE_CATEGORY, () -> this.removeReplayCategory(cat));
            }

            if (this.hasReplaySelection())
            {
                menu.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY, this::copyReplay);
            }

            MapType copyReplay = Window.getClipboardMap("_CopyReplay");

            if (copyReplay != null)
            {
                menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE, () -> this.pasteReplay(copyReplay));
            }

            if (film != null)
            {
                int duration = film.camera.calculateDuration();

                if (duration > 0)
                {
                    menu.action(Icons.PLAY, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_CAMERA, () -> this.fromCamera(duration));
                }
            }

            menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, this::fromModelBlock);

            if (this.hasReplaySelection())
            {
                boolean shift = Window.isShiftPressed();
                MapType data = Window.getClipboardMap("_CopyKeyframes");

                if (film != null && this.hasReplayCategoryNames())
                {
                    menu.action(Icons.SHIFT_TO, UIKeys.SCENE_REPLAYS_CONTEXT_MOVE_TO_CATEGORY, this::openMoveToCategoryContextMenu);
                }

                menu.action(Icons.ALL_DIRECTIONS, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS, this::processReplays);
                menu.action(Icons.TIME, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME, this::offsetTimeReplays);

                if (this.getSelectedReplays().size() > 1)
                {
                    menu.action(Icons.MATERIAL, UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_TEXTURES, this::openRandomTexturesOverlay);
                }

                if (data != null)
                {
                    menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES, () -> this.pasteToReplays(data));
                }

                menu.action(Icons.DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, () ->
                {
                    if (Window.isShiftPressed() || shift)
                    {
                        this.dupeReplay();
                    }
                    else
                    {
                        UINumberOverlayPanel numberPanel = new UINumberOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE_DESCRIPTION, (n) ->
                        {
                            for (int i = 0; i < n; i++)
                            {
                                this.dupeReplay();
                            }
                        });

                        numberPanel.value.limit(1).integer();
                        numberPanel.value.setValue(1D);

                        UIOverlay.addOverlay(this.getContext(), numberPanel);
                    }
                });
                menu.action(Icons.REMOVE, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE, this::removeReplay);
            }
        });
    }

    @Override
    public UIContextMenu createContextMenu(UIContext context)
    {
        this.contextFolderCategoryName = null;

        int idx = this.getIndexAtCursor(context);

        if (this.exists(idx))
        {
            ReplayListEntry e = this.list.get(idx);

            if (e.isFolder())
            {
                String cat = Replay.normalizeCategory(e.folderName);

                if (!cat.isEmpty())
                {
                    this.contextFolderCategoryName = cat;
                }
            }
        }

        try
        {
            return super.createContextMenu(context);
        }
        finally
        {
            this.contextFolderCategoryName = null;
        }
    }

    /**
     * Remove a category from the film and move all replays in it to root.
     */
    private void removeReplayCategory(String normalizedName)
    {
        Film film = this.panel.getData();

        if (film == null || normalizedName.isEmpty())
        {
            return;
        }

        Set<String> names = new HashSet<>(film.replayCategoryNames.get());

        names.remove(normalizedName);
        film.replayCategoryNames.set(names);

        for (Replay r : film.replays.getList())
        {
            if (normalizedName.equals(Replay.normalizeCategory(r.category.get())))
            {
                r.category.set("");
            }
        }

        this.collapsedCategories.remove(normalizedName);
        this.refreshReplayList();
        this.updateFilmEditor();
    }

    private static List<Replay> replaysFromEntries(List<ReplayListEntry> entries)
    {
        List<Replay> out = new ArrayList<>();

        for (ReplayListEntry e : entries)
        {
            if (e.isReplay())
            {
                out.add(e.replay);
            }
        }

        return out;
    }

    /**
     * Ensure the replay row is visible and selected (expands its category if needed).
     */
    public void scrollToReplay(Replay replay)
    {
        if (replay == null)
        {
            return;
        }

        String cat = Replay.normalizeCategory(replay.category.get());

        if (!cat.isEmpty())
        {
            this.collapsedCategories.remove(cat);
        }

        this.refreshReplayList();

        for (int i = 0; i < this.list.size(); i++)
        {
            ReplayListEntry e = this.list.get(i);

            if (e.isReplay() && e.replay == replay)
            {
                this.pick(i);
                this.scroll.setScroll(i * this.scroll.scrollItemSize);

                return;
            }
        }
    }

    private void restoreReplaySelection(List<Replay> replays)
    {
        this.current.clear();

        for (Replay r : replays)
        {
            for (int i = 0; i < this.list.size(); i++)
            {
                ReplayListEntry e = this.list.get(i);

                if (e.isReplay() && e.replay == r)
                {
                    this.addIndex(i);

                    break;
                }
            }
        }

        if (this.callback != null && !this.current.isEmpty())
        {
            this.callback.accept(this.getCurrent());
        }
    }

    public List<Replay> getSelectedReplays()
    {
        List<Replay> out = new ArrayList<>();

        for (int i : this.current)
        {
            if (this.exists(i))
            {
                ReplayListEntry e = this.list.get(i);

                if (e.isReplay())
                {
                    out.add(e.replay);
                }
            }
        }

        return out;
    }

    public Replay getSelectedReplayFirst()
    {
        for (int i : this.current)
        {
            if (this.exists(i))
            {
                ReplayListEntry e = this.list.get(i);

                if (e.isReplay())
                {
                    return e.replay;
                }
            }
        }

        return null;
    }

    public boolean hasReplaySelection()
    {
        return this.getSelectedReplayFirst() != null;
    }

    /**
     * Selected replays in current visible list order.
     */
    private List<Replay> getSelectedReplaysInViewOrder()
    {
        List<Replay> out = new ArrayList<>();

        for (int i = 0; i < this.list.size(); i++)
        {
            if (!this.current.contains(i))
            {
                continue;
            }

            ReplayListEntry e = this.list.get(i);

            if (e.isReplay())
            {
                out.add(e.replay);
            }
        }

        return out;
    }

    /**
     * Replay index in currently visible replay rows (folder rows ignored).
     */
    private int getVisibleReplayIndex(Replay replay)
    {
        int index = 0;

        for (ReplayListEntry e : this.list)
        {
            if (!e.isReplay())
            {
                continue;
            }

            if (e.replay == replay)
            {
                return index;
            }

            index += 1;
        }

        return -1;
    }

    /**
     * Global index of the first selected replay in {@link Film#replays}, or {@code -1}.
     */
    public int getGlobalReplayIndex()
    {
        Replay r = this.getSelectedReplayFirst();
        Film film = this.panel.getData();

        if (r == null || film == null)
        {
            return -1;
        }

        return film.replays.getList().indexOf(r);
    }

    public void refreshReplayList()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            this.clear();

            return;
        }

        TreeSet<String> categories = this.collectCategoryNames(film);

        this.collapsedCategories.removeIf((name) -> !categories.contains(name));

        List<Replay> all = film.replays.getList();
        List<ReplayListEntry> entries = new ArrayList<>();
        int indent = 12;

        for (String c : categories)
        {
            entries.add(ReplayListEntry.folder(c));

            if (!this.collapsedCategories.contains(c))
            {
                for (Replay r : all)
                {
                    if (c.equals(Replay.normalizeCategory(r.category.get())))
                    {
                        entries.add(ReplayListEntry.replay(r, indent));
                    }
                }
            }
        }

        for (Replay r : all)
        {
            if (Replay.normalizeCategory(r.category.get()).isEmpty())
            {
                entries.add(ReplayListEntry.replay(r));
            }
        }

        this.setList(entries);
    }

    /**
     * All category folder names: explicit empty folders plus names used by replays.
     */
    private TreeSet<String> collectCategoryNames(Film film)
    {
        TreeSet<String> categories = new TreeSet<>((a, b) -> NaturalOrderComparator.compare(true, a, b));

        for (String s : film.replayCategoryNames.get())
        {
            String c = Replay.normalizeCategory(s);

            if (!c.isEmpty())
            {
                categories.add(c);
            }
        }

        for (Replay r : film.replays.getList())
        {
            String c = Replay.normalizeCategory(r.category.get());

            if (!c.isEmpty())
            {
                categories.add(c);
            }
        }

        return categories;
    }

    private boolean hasReplayCategoryNames()
    {
        Film film = this.panel.getData();

        return film != null && !this.collectCategoryNames(film).isEmpty();
    }

    /**
     * Update {@link Replay#category} and uncollapse the folder; does not refresh the list (for use before index-based ops).
     */
    private void assignReplayCategoryValue(Replay replay, String rawCategory)
    {
        String cat = Replay.normalizeCategory(rawCategory);

        replay.category.set(cat);

        if (!cat.isEmpty())
        {
            this.collapsedCategories.remove(cat);
        }
    }

    private void openAddCategoryOverlay()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        UITextbox box = new UITextbox(1000, (s) -> {});
        box.setText("");
        box.placeholder(UIKeys.SCENE_REPLAYS_ADD_CATEGORY_PLACEHOLDER);

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_ADD_CATEGORY_TITLE, UIKeys.SCENE_REPLAYS_ADD_CATEGORY_DESCRIPTION, (ok) ->
        {
            if (!ok)
            {
                return;
            }

            String cat = Replay.normalizeCategory(box.getText());

            if (cat.isEmpty())
            {
                return;
            }

            Set<String> names = new HashSet<>(film.replayCategoryNames.get());

            names.add(cat);
            film.replayCategoryNames.set(names);
            this.collapsedCategories.remove(cat);
            this.refreshReplayList();
            this.updateFilmEditor();
        });

        box.relative(panel.confirm).y(-1F, -5).w(1F).h(20);
        panel.confirm.w(1F, -10);
        panel.content.add(box);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    /**
     * Second context menu: pick target category (replaces main replay context menu).
     */
    private void openMoveToCategoryContextMenu()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        List<Replay> selected = new ArrayList<>(this.getSelectedReplays());

        if (selected.isEmpty())
        {
            return;
        }

        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        context.replaceContextMenu((add) ->
        {
            add.action(Icons.ARROW_DOWN, UIKeys.SCENE_REPLAYS_CATEGORY_NONE, () -> this.applyReplayCategory(selected, ""));

            for (String c : this.collectCategoryNames(film))
            {
                final String cat = c;

                add.action(Icons.FOLDER, IKey.raw(cat), () -> this.applyReplayCategory(selected, cat));
            }
        });
    }

    private void applyReplayCategory(List<Replay> selected, String rawCategory)
    {
        String cat = Replay.normalizeCategory(rawCategory);

        for (Replay r : selected)
        {
            r.category.set(cat);
        }

        if (!cat.isEmpty())
        {
            this.collapsedCategories.remove(cat);
        }

        this.refreshReplayList();
        this.restoreReplaySelection(selected);
        this.updateFilmEditor();
    }

    @Override
    public boolean isSelected()
    {
        return this.hasReplaySelection();
    }

    @Override
    protected boolean sortElements()
    {
        return false;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.isFiltering())
        {
            return super.subMouseClicked(context);
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = this.scroll.getIndex(context.mouseX, context.mouseY);

            if (this.exists(index))
            {
                ReplayListEntry entry = this.list.get(index);

                if (entry.isFolder())
                {
                    String name = Replay.normalizeCategory(entry.folderName);

                    if (this.collapsedCategories.contains(name))
                    {
                        this.collapsedCategories.remove(name);
                    }
                    else
                    {
                        this.collapsedCategories.add(name);
                    }

                    List<Replay> keep = new ArrayList<>(this.getSelectedReplays());
                    this.refreshReplayList();
                    this.restoreReplaySelection(keep);
                    this.update();

                    return true;
                }

                this.applySelectionOnClick(index);

                if (this.sorting && entry.isReplay() && this.current.size() == 1)
                {
                    this.dragging = index;
                    this.dragTime = System.currentTimeMillis();
                }

                if (this.callback != null)
                {
                    this.callback.accept(this.getCurrent());

                    return true;
                }
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.sorting && !this.isFiltering())
        {
            if (this.isDragging())
            {
                int index = this.scroll.getIndex(context.mouseX, context.mouseY);

                /* Past the last row (empty padding below short lists): move to root — no root replay row to drop on. */
                if (index == -2)
                {
                    ReplayListEntry dragged = this.list.get(this.dragging);

                    if (dragged.isReplay())
                    {
                        this.applyReplayCategory(List.of(dragged.replay), "");
                    }
                }
                else if (index != this.dragging && this.exists(index))
                {
                    ReplayListEntry a = this.list.get(this.dragging);
                    ReplayListEntry b = this.list.get(index);

                    if (a.isReplay() && b.isFolder())
                    {
                        this.dropReplaysOntoCategory(index);
                    }
                    else if (a.isReplay() && b.isReplay())
                    {
                        this.handleSwap(this.dragging, index);
                    }
                }
            }

            this.dragging = -1;
        }

        this.scroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    /** Drag a replay row onto a category header to assign that category. */
    private void dropReplaysOntoCategory(int folderIndex)
    {
        ReplayListEntry folderEntry = this.list.get(folderIndex);

        if (!folderEntry.isFolder())
        {
            return;
        }

        ReplayListEntry draggedEntry = this.list.get(this.dragging);

        if (!draggedEntry.isReplay())
        {
            return;
        }

        this.applyReplayCategory(List.of(draggedEntry.replay), folderEntry.folderName);
    }

    @Override
    protected void handleSwap(int from, int to)
    {
        Film data = this.panel.getData();
        Replays replays = data.replays;
        List<Replay> all = replays.getList();

        ReplayListEntry ef = this.list.get(from);
        ReplayListEntry et = this.list.get(to);

        if (!ef.isReplay() || !et.isReplay())
        {
            return;
        }

        this.assignReplayCategoryValue(ef.replay, et.replay.category.get());

        int globalFrom = all.indexOf(ef.replay);
        int globalTo = all.indexOf(et.replay);

        if (globalFrom < 0 || globalTo < 0)
        {
            return;
        }

        Replay value = all.get(globalFrom);

        data.preNotify(IValueListener.FLAG_UNMERGEABLE);

        replays.remove(value);
        replays.add(globalTo, value);
        replays.sync();

        for (Replay replay : replays.getList())
        {
            if (replay.properties.get("anchor") instanceof KeyframeChannel<?> channel && channel.getFactory() == KeyframeFactories.ANCHOR)
            {
                KeyframeChannel<Anchor> keyframeChannel = (KeyframeChannel<Anchor>) channel;

                for (Keyframe<Anchor> keyframe : keyframeChannel.getKeyframes())
                {
                    keyframe.getValue().replay = MathUtils.remapIndex(keyframe.getValue().replay, globalFrom, globalTo);
                }
            }
        }

        for (Clip clip : data.camera.get())
        {
            if (clip instanceof EntityClip entityClip)
            {
                entityClip.selector.set(MathUtils.remapIndex(entityClip.selector.get(), globalFrom, globalTo));
            }
        }

        data.postNotify(IValueListener.FLAG_UNMERGEABLE);

        this.refreshReplayList();
        this.updateFilmEditor();

        for (int i = 0; i < this.list.size(); i++)
        {
            ReplayListEntry e = this.list.get(i);

            if (e.isReplay() && e.replay == value)
            {
                this.pick(i);

                break;
            }
        }
    }

    private void pasteToReplays(MapType data)
    {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        List<Replay> selectedReplays = replayEditor.replays.replays.getSelectedReplays();

        if (data == null)
        {
            return;
        }

        Map<String, UIKeyframes.PastedKeyframes> parsedKeyframes = UIKeyframes.parseKeyframes(data);

        if (parsedKeyframes.isEmpty())
        {
            return;
        }

        UINumberOverlayPanel offsetPanel = new UINumberOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_DESCRIPTION, (n) ->
        {
            int tick = this.panel.getCursor();

            for (Replay replay : selectedReplays)
            {
                int randomOffset = (int) (n.intValue() * Math.random());

                for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : parsedKeyframes.entrySet())
                {
                    String id = entry.getKey();
                    UIKeyframes.PastedKeyframes pastedKeyframes = entry.getValue();
                    KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(id);

                    if (channel == null || channel.getFactory() != pastedKeyframes.factory)
                    {
                        channel = replay.properties.getOrCreate(replay.form.get(), id);
                    }

                    float min = Integer.MAX_VALUE;

                    for (Keyframe kf : pastedKeyframes.keyframes)
                    {
                        min = Math.min(kf.getTick(), min);
                    }

                    for (Keyframe kf : pastedKeyframes.keyframes)
                    {
                        float finalTick = tick + (kf.getTick() - min) + randomOffset;
                        int idx = channel.insert(finalTick, kf.getValue());
                        Keyframe inserted = channel.get(idx);

                        inserted.copy(kf);
                        inserted.setTick(finalTick);
                    }

                    channel.sort();
                }
            }
        });

        UIOverlay.addOverlay(this.getContext(), offsetPanel);
    }

    private void openRandomTexturesOverlay()
    {
        List<Replay> selected = new ArrayList<>(this.getSelectedReplays());

        if (selected.size() < 2)
        {
            return;
        }

        UIFolderOverlayPanel panel = new UIFolderOverlayPanel(UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_TITLE, UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_DESCRIPTION, (folder) ->
        {
            this.applyRandomTextures(folder, selected, this.getContext());
        }).confirmLabel(UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_APPLY);

        UIOverlay.addOverlay(this.getContext(), panel, 320, 0.8F);
    }

    private void applyRandomTextures(Link folder, List<Replay> replays, UIContext context)
    {
        if (folder == null || folder.source.isEmpty())
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_ERROR);

            return;
        }

        List<Link> textures = this.collectTextures(folder);

        if (textures.isEmpty())
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_ERROR);

            return;
        }

        int applied = 0;
        Random random = new Random();

        for (Replay replay : replays)
        {
            Form form = replay.form.get();

            if (form == null)
            {
                continue;
            }

            Form copy = FormUtils.copy(form);
            BaseValue property = FormUtils.getProperty(copy, "texture");

            if (property instanceof ValueLink valueLink)
            {
                valueLink.set(textures.get(random.nextInt(textures.size())));
                replay.form.set(copy);
                applied += 1;
            }
        }

        if (applied == 0)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_TEXTURES_ERROR);

            return;
        }

        this.updateFilmEditor();
    }

    private List<Link> collectTextures(Link folder)
    {
        List<Link> textures = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(folder, false))
        {
            if (!link.path.endsWith("/") && link.path.endsWith(".png"))
            {
                textures.add(link);
            }
        }

        return textures;
    }

    private void processReplays()
    {
        Replay first = this.getSelectedReplayFirst();

        if (first == null)
        {
            return;
        }

        UIProcessReplaysPanel panel = new UIProcessReplaysPanel(first);

        UIOverlay.addOverlay(this.getContext(), panel, 320, 320);
    }

    private static List<String> collectProcessChannelIds(Replay replay)
    {
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> added = new HashSet<>();

        for (String id : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue baseValue = replay.keyframes.get(id);

            if (baseValue instanceof KeyframeChannel<?> channel && KeyframeFactories.isNumeric(channel.getFactory()))
            {
                out.add(id);
                added.add(id);
            }
        }

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (!KeyframeFactories.isNumeric(channel.getFactory()) || added.contains(channel.getId()))
            {
                continue;
            }

            out.add(channel.getId());
        }

        return out;
    }

    private class UIProcessReplaysPanel extends UIConfirmOverlayPanel
    {
        private final UIStringList properties = new UIStringList(null)
        {
            @Override
            protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
            {
                int h = this.scroll.scrollItemSize;
                int color = UIReplaysEditor.getColor(element);
                Icon icon = UIReplaysEditor.getIcon(element);

                context.batcher.box(x, y, x + 2, y + h, Colors.A100 | color);
                context.batcher.gradientHBox(x + 2, y, x + 24, y + h, Colors.A25 | color, color);
                context.batcher.icon(icon, x + 2, y + h / 2F, 0F, 0.5F);
                context.batcher.textShadow(this.elementToString(context, i, element), x + 24, y + (h - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
            }
        };

        private final UIPanelBase<UIElement> modes = new UIPanelBase<>(Direction.TOP);
        private final UINormalProcessView normal = new UINormalProcessView();
        private final UIAdvancedProcessView advanced = new UIAdvancedProcessView();

        public UIProcessReplaysPanel(Replay first)
        {
            super(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_TITLE, IKey.EMPTY, null);

            this.message.setVisible(false);

            this.properties.scroll.scrollItemSize = 16;

            for (String id : collectProcessChannelIds(first))
            {
                this.properties.add(id);
            }

            this.properties.background().multi();
            this.properties.update();

            if (!PROCESS_STATE.properties.isEmpty())
            {
                this.properties.setCurrentScroll(PROCESS_STATE.properties.get(0));
            }

            for (String property : PROCESS_STATE.properties)
            {
                this.properties.addIndex(this.properties.getList().indexOf(property));
            }

            this.modes.registerPanel(this.normal, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_NORMAL, Icons.SHAPES);
            this.modes.registerPanel(this.advanced, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_ADVANCED, Icons.CODE);
            this.modes.setPanel(PROCESS_STATE.advanced ? this.advanced : this.normal);

            UIElement body = new UIElement();
            body.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -40);

            this.modes.relative(body).x(0).y(0).w(1F, -126).h(1F);

            this.properties.relative(body).x(1F, -120).y(20).w(120).h(1F, -20);

            this.confirm.w(1F, -10);
            this.content.add(body);
            body.add(this.modes, this.properties);
        }

        @Override
        public void confirm()
        {
            if (this.apply())
            {
                super.confirm();
            }
        }

        private boolean apply()
        {
            UIContext context = this.getContext();

            if (context == null)
            {
                context = UIReplayList.this.getContext();
            }

            if (context == null)
            {
                return false;
            }

            List<String> selectedProperties = new ArrayList<>(this.properties.getCurrent());

            if (selectedProperties.isEmpty())
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_CHANNELS);

                return false;
            }

            PROCESS_STATE.properties = new ArrayList<>(selectedProperties);

            List<VisibleReplay> visible = this.collectVisibleReplays();

            if (visible.isEmpty())
            {
                return false;
            }

            boolean isAdvanced = this.modes.view == this.advanced;

            PROCESS_STATE.advanced = isAdvanced;

            if (isAdvanced)
            {
                if (!this.applyAdvanced(context, visible, selectedProperties))
                {
                    return false;
                }
            }
            else
            {
                if (!this.applyNormal(context, visible, selectedProperties))
                {
                    return false;
                }
            }

            UIReplayList.this.updateFilmEditor();

            return true;
        }

        private static class VisibleReplay
        {
            public final Replay replay;
            public final int i;
            public final int o;

            public VisibleReplay(Replay replay, int i, int o)
            {
                this.replay = replay;
                this.i = i;
                this.o = o;
            }
        }

        private List<VisibleReplay> collectVisibleReplays()
        {
            List<Replay> selected = UIReplayList.this.getSelectedReplaysInViewOrder();
            List<Replay> visible = new ArrayList<>();
            int min = Integer.MAX_VALUE;

            for (Replay replay : selected)
            {
                int visibleI = UIReplayList.this.getVisibleReplayIndex(replay);

                if (visibleI < 0)
                {
                    continue;
                }

                min = Math.min(min, visibleI);
                visible.add(replay);
            }

            if (min == Integer.MAX_VALUE || visible.isEmpty())
            {
                return new ArrayList<>();
            }

            List<VisibleReplay> out = new ArrayList<>();

            for (Replay replay : visible)
            {
                int visibleI = UIReplayList.this.getVisibleReplayIndex(replay);
                out.add(new VisibleReplay(replay, visibleI, visibleI - min));
            }

            return out;
        }

        private boolean applyAdvanced(UIContext context, List<VisibleReplay> selected, List<String> selectedProperties)
        {
            MathBuilder builder = new MathBuilder();

            builder.register("i");
            builder.register("o");
            builder.register("v");
            builder.register("ki");

            String expressionText = this.advanced.expression.getText();

            IExpression parse;

            try
            {
                parse = builder.parse(expressionText);
            }
            catch (Exception e)
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_INVALID_EXPRESSION);

                return false;
            }

            PROCESS_STATE.expression = expressionText;

            for (VisibleReplay replay : selected)
            {
                builder.variables.get("i").set(replay.i);
                builder.variables.get("o").set(replay.o);

                for (String s : selectedProperties)
                {
                    KeyframeChannel channel = (KeyframeChannel) replay.replay.keyframes.get(s);

                    if (channel == null)
                    {
                        continue;
                    }

                    List keyframes = channel.getKeyframes();

                    for (int i = 0; i < keyframes.size(); i++)
                    {
                        Keyframe kf = (Keyframe) keyframes.get(i);

                        builder.variables.get("v").set(kf.getFactory().getY(kf.getValue()));
                        builder.variables.get("ki").set(i);

                        kf.setValue(kf.getFactory().yToValue(parse.doubleValue()), true);
                    }
                }
            }
            
            return true;
        }

        private boolean applyNormal(UIContext context, List<VisibleReplay> selected, List<String> selectedProperties)
        {
            NormalOperation operation = null;
            mchorse.bbs_mod.ui.utils.Label<NormalOperation> operationLabel = this.normal.operations.getCurrentFirst();

            if (operationLabel != null)
            {
                operation = operationLabel.value;
            }

            if (operation == null)
            {
                operation = NormalOperation.RANDOM;
            }

            PROCESS_STATE.operation = operation;

            if (operation == NormalOperation.RANDOM)
            {
                PROCESS_STATE.randomMin = this.normal.randomMin.getValue();
                PROCESS_STATE.randomMax = this.normal.randomMax.getValue();
            }
            else if (operation == NormalOperation.LINE)
            {
                PROCESS_STATE.lineOffset = this.normal.lineOffset.getValue();
            }
            else if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE || operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE)
            {
                PROCESS_STATE.size = this.normal.size.getValue();
            }
            else if (operation == NormalOperation.SHIFT)
            {
                PROCESS_STATE.shift = this.normal.shift.getValue();
            }

            if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE || operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE)
            {
                if (selectedProperties.size() < 2)
                {
                    context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_TWO_CHANNELS);

                    return false;
                }

                String aId = selectedProperties.get(0);
                String bId = selectedProperties.get(1);
                String cId = selectedProperties.size() >= 3 ? selectedProperties.get(2) : null;

                int count = selected.size();

                for (VisibleReplay replay : selected)
                {
                    int o = replay.o;
                    double a = 0;
                    double b = 0;
                    double c = 0;

                    if (operation == NormalOperation.SQUARE)
                    {
                        double step;
                        boolean cube = cId != null;

                        if (cube)
                        {
                            int side = (int) Math.ceil(Math.cbrt(count));
                            double half = (side - 1) / 2D;
                            step = side > 1 ? PROCESS_STATE.size / (side - 1) : 0D;
                            int gx = o % side;
                            int gy = (o / side) % side;
                            int gz = o / (side * side);

                            a = (gx - half) * step;
                            b = (gy - half) * step;
                            c = (gz - half) * step;
                        }
                        else
                        {
                            int side = (int) Math.ceil(Math.sqrt(count));
                            double half = (side - 1) / 2D;
                            step = side > 1 ? PROCESS_STATE.size / (side - 1) : 0D;
                            int col = o % side;
                            int row = o / side;

                            a = (col - half) * step;
                            b = (row - half) * step;
                        }
                    }
                    else if (operation == NormalOperation.SQUARE_OUTLINE)
                    {
                        int side = Math.max(2, (int) Math.ceil(count / 4D) + 1);
                        int edge = side - 1;
                        double half = edge / 2D;
                        double step = edge > 0 ? PROCESS_STATE.size / edge : 0D;
                        int pos = edge == 0 ? 0 : (o % (edge * 4));
                        int x;
                        int y;

                        if (pos < edge)
                        {
                            x = pos;
                            y = 0;
                        }
                        else if (pos < edge * 2)
                        {
                            x = edge;
                            y = pos - edge;
                        }
                        else if (pos < edge * 3)
                        {
                            x = edge - (pos - edge * 2);
                            y = edge;
                        }
                        else
                        {
                            x = 0;
                            y = edge - (pos - edge * 3);
                        }

                        a = (x - half) * step;
                        b = (y - half) * step;
                    }
                    else if (operation == NormalOperation.CIRCLE)
                    {
                        double radius = PROCESS_STATE.size / 2D;
                        if (cId != null)
                        {
                            double t = (o + 0.5D) / count;
                            double theta = Math.PI * (3D - Math.sqrt(5D)) * (o + 0.5D);
                            double yv = 1D - 2D * t;
                            double s = Math.cbrt(t);
                            double rxy = Math.sqrt(1D - yv * yv);

                            double x = Math.cos(theta) * rxy;
                            double z = Math.sin(theta) * rxy;
                            double y = yv;

                            a = x * radius * s;
                            b = z * radius * s;
                            c = y * radius * s;
                        }
                        else
                        {
                            double goldenAngle = Math.PI * (3D - Math.sqrt(5D));
                            double t = (o + 0.5D) / count;
                            double r = Math.sqrt(t) * radius;
                            double angle = o * goldenAngle;

                            a = Math.cos(angle) * r;
                            b = Math.sin(angle) * r;
                        }
                    }
                    else if (operation == NormalOperation.CIRCLE_OUTLINE)
                    {
                        double radius = PROCESS_STATE.size / 2D;
                        if (cId != null)
                        {
                            double t = (o + 0.5D) / count;
                            double theta = Math.PI * (3D - Math.sqrt(5D)) * (o + 0.5D);
                            double yv = 1D - 2D * t;
                            double rxy = Math.sqrt(1D - yv * yv);

                            double x = Math.cos(theta) * rxy;
                            double z = Math.sin(theta) * rxy;
                            double y = yv;

                            a = x * radius;
                            b = z * radius;
                            c = y * radius;
                        }
                        else
                        {
                            double angle = (count == 1 ? 0D : (o / (double) count) * Math.PI * 2D);

                            a = Math.cos(angle) * radius;
                            b = Math.sin(angle) * radius;
                        }
                    }

                    this.applyDelta(replay.replay, aId, a);
                    this.applyDelta(replay.replay, bId, b);

                    if ((operation == NormalOperation.SQUARE || operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE) && cId != null)
                    {
                        this.applyDelta(replay.replay, cId, c);
                    }
                }

                return true;
            }

            if (operation == NormalOperation.LINE)
            {
                double offset = PROCESS_STATE.lineOffset;

                for (VisibleReplay replay : selected)
                {
                    double delta = replay.o * offset;

                    for (String s : selectedProperties)
                    {
                        this.applyDelta(replay.replay, s, delta);
                    }
                }

                return true;
            }

            if (operation == NormalOperation.SHIFT)
            {
                double delta = PROCESS_STATE.shift;

                for (VisibleReplay replay : selected)
                {
                    for (String s : selectedProperties)
                    {
                        this.applyDelta(replay.replay, s, delta);
                    }
                }

                return true;
            }

            if (operation == NormalOperation.RANDOM)
            {
                double minValue = PROCESS_STATE.randomMin;
                double maxValue = PROCESS_STATE.randomMax;
                double minRand = Math.min(minValue, maxValue);
                double maxRand = Math.max(minValue, maxValue);
                long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();

                for (VisibleReplay replay : selected)
                {
                    long replaySeed = mix64(seed + (long) replay.o * 0x9e3779b97f4a7c15L);

                    for (int ci = 0; ci < selectedProperties.size(); ci++)
                    {
                        String s = selectedProperties.get(ci);
                        long channelSeed = mix64(replaySeed + (long) ci * 0xbf58476d1ce4e5b9L);
                        java.util.SplittableRandom random = new java.util.SplittableRandom(channelSeed);
                        double delta = minRand + random.nextDouble() * (maxRand - minRand);

                        this.applyDelta(replay.replay, s, delta);
                    }
                }
            }

            return true;
        }

        private static long mix64(long z)
        {
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        private void applyDelta(Replay replay, String id, double delta)
        {
            KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(id);

            if (channel == null || !KeyframeFactories.isNumeric(channel.getFactory()))
            {
                return;
            }

            List keyframes = channel.getKeyframes();

            for (int i = 0; i < keyframes.size(); i++)
            {
                Keyframe kf = (Keyframe) keyframes.get(i);
                double v = kf.getFactory().getY(kf.getValue());

                kf.setValue(kf.getFactory().yToValue(v + delta), true);
            }
        }

        private class UINormalProcessView extends UIElement
        {
            private final UILabelList<NormalOperation> operations;

            private final UITrackpad randomMin;
            private final UITrackpad randomMax;
            private final UITrackpad lineOffset;
            private final UITrackpad size;
            private final UITrackpad shift;

            private final UIElement params = new UIElement();
            private final UIText hint = new UIText(IKey.EMPTY).padding(0, 0).lineHeight(10);

            public UINormalProcessView()
            {
                super();

                this.operations = new UILabelList<>((l) -> this.updateOperation())
                {
                    @Override
                    protected void renderElementPart(UIContext context, mchorse.bbs_mod.ui.utils.Label<NormalOperation> element, int i, int x, int y, boolean hover, boolean selected)
                    {
                        int h = this.scroll.scrollItemSize;
                        Icon icon = element.value.icon;

                        context.batcher.icon(icon, x + 3, y + (h - 16) / 2F);
                        context.batcher.textShadow(element.title.get(), x + 22, y + (h - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
                    }
                };
                this.operations.background();
                this.operations.scroll.scrollItemSize = UIConstants.CONTROL_HEIGHT;

                for (NormalOperation operation : NormalOperation.values())
                {
                    this.operations.add(operation.title, operation);
                }

                this.randomMin = new UITrackpad();
                this.randomMin.limit(-10000, 10000, false);
                this.randomMin.setValue(PROCESS_STATE.randomMin);

                this.randomMax = new UITrackpad();
                this.randomMax.limit(-10000, 10000, false);
                this.randomMax.setValue(PROCESS_STATE.randomMax);

                this.lineOffset = new UITrackpad();
                this.lineOffset.limit(-10000, 10000, false);
                this.lineOffset.setValue(PROCESS_STATE.lineOffset);

                this.size = new UITrackpad();
                this.size.limit(0, 10000, false);
                this.size.setValue(PROCESS_STATE.size);

                this.shift = new UITrackpad();
                this.shift.limit(-10000, 10000, false);
                this.shift.setValue(PROCESS_STATE.shift);

                int opsHeight = UIConstants.CONTROL_HEIGHT * NormalOperation.values().length;

                this.operations.relative(this).xy(0, 0).w(1F).h(opsHeight);
                this.params.relative(this.operations).y(1F, UIConstants.MARGIN * 2).w(1F).h(UIConstants.CONTROL_HEIGHT);
                this.hint.relative(this.params).y(1F, UIConstants.MARGIN).w(1F);

                this.add(this.operations, this.params, this.hint);

                this.operations.setCurrentValue(PROCESS_STATE.operation);
                this.updateOperation();
            }

            private void updateOperation()
            {
                NormalOperation operation = null;
                mchorse.bbs_mod.ui.utils.Label<NormalOperation> operationLabel = this.operations.getCurrentFirst();

                if (operationLabel != null)
                {
                    operation = operationLabel.value;
                }

                this.params.removeAll();

                if (operation == NormalOperation.RANDOM)
                {
                    UILabel minLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MIN, 36);
                    UILabel maxLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MAX, 36);
                    UIElement row = UI.row(minLabel, this.randomMin, maxLabel, this.randomMax);
                    row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                    this.params.add(row);
                    this.hint.text(operation.hint);
                }
                else if (operation == NormalOperation.LINE)
                {
                    UILabel offsetLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_OFFSET, 56);
                    UIElement row = UI.row(offsetLabel, this.lineOffset);
                    row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                    this.params.add(row);
                    this.hint.text(operation.hint);
                }
                else if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE)
                {
                    UILabel sizeLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, 56);
                    UIElement row1 = UI.row(sizeLabel, this.size);
                    row1.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                    this.params.add(row1);
                    this.hint.text(operation.hint);
                }
                else if (operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE)
                {
                    UILabel sizeLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, 56);
                    UIElement row = UI.row(sizeLabel, this.size);
                    row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                    this.params.add(row);
                    this.hint.text(operation.hint);
                }
                else if (operation == NormalOperation.SHIFT)
                {
                    UILabel shiftLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SHIFT, 56);
                    UIElement row = UI.row(shiftLabel, this.shift);
                    row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                    this.params.add(row);
                    this.hint.text(operation.hint);
                }
                else
                {
                    this.hint.text(IKey.EMPTY);
                }

                this.resize();
            }

            private UILabel paramLabel(IKey key, int width)
            {
                UILabel label = UI.label(key, UIConstants.CONTROL_HEIGHT);
                label.w(width);

                return label.labelAnchor(0F, 0.5F);
            }

        }

        private class UIAdvancedProcessView extends UIElement
        {
            private final UITextbox expression = new UITextbox((t) -> PROCESS_STATE.expression = t);
            private final UIText description = new UIText(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_DESCRIPTION).padding(0, 0);

            public UIAdvancedProcessView()
            {
                super();

                this.expression.setText(PROCESS_STATE.expression);
                this.expression.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_EXPRESSION_TOOLTIP);
                this.expression.relative(this).xy(0, 0).w(1F).h(20);
                this.description.relative(this.expression).y(1F, 6).w(1F);

                this.add(this.expression, this.description);
            }
        }
    }

    private enum NormalOperation
    {
        RANDOM(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_RANDOM, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_RANDOM, Icons.SIX_STAR),
        LINE(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_LINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_LINE, Icons.LINE),
        SQUARE(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.SQUARE),
        SQUARE_OUTLINE(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.OUTLINE),
        CIRCLE(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.CIRCLE),
        CIRCLE_OUTLINE(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.OUTLINE_SPHERE),
        SHIFT(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SHIFT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHIFT, Icons.SHIFT_TO);

        public final IKey title;
        public final IKey hint;
        public final Icon icon;

        NormalOperation(IKey title, IKey hint, Icon icon)
        {
            this.title = title;
            this.hint = hint;
            this.icon = icon;
        }
    }

    private void offsetTimeReplays()
    {
        Replay first = this.getSelectedReplayFirst();

        if (first == null)
        {
            return;
        }

        UITextbox tick = new UITextbox((t) -> LAST_OFFSET = t);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION, (b) ->
        {
            if (b)
            {
                MathBuilder builder = new MathBuilder();
                int min = Integer.MAX_VALUE;

                builder.register("i");
                builder.register("o");

                IExpression parse = null;

                try
                {
                    parse = builder.parse(tick.getText());
                }
                catch (Exception e)
                {}

                Film film = this.panel.getData();
                List<Replay> selected = this.getSelectedReplaysInViewOrder();

                for (Replay replay : selected)
                {
                    int visibleI = this.getVisibleReplayIndex(replay);

                    if (visibleI < 0)
                    {
                        continue;
                    }

                    min = Math.min(min, visibleI);
                }

                if (min == Integer.MAX_VALUE)
                {
                    return;
                }

                for (Replay replay : selected)
                {
                    int visibleI = this.getVisibleReplayIndex(replay);

                    if (visibleI < 0)
                    {
                        continue;
                    }

                    builder.variables.get("i").set(visibleI);
                    builder.variables.get("o").set(visibleI - min);

                    float tickv = parse == null ? 0F : (float) parse.doubleValue();

                    BaseValue.edit(replay, (r) -> r.shift(tickv));
                }
            }
        });

        tick.setText(LAST_OFFSET);
        tick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_EXPRESSION_TOOLTIP);
        tick.relative(panel.confirm).y(-1F, -5).w(1F).h(20);

        panel.confirm.w(1F, -10);
        panel.content.add(tick);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public void copyReplay()
    {
        MapType replays = new MapType();
        ListType replayList = new ListType();

        replays.put("replays", replayList);

        for (Replay replay : this.getSelectedReplays())
        {
            replayList.add(replay.toData());
        }

        Window.setClipboard(replays, "_CopyReplay");
    }

    public void pasteReplay(MapType data)
    {
        Film film = this.panel.getData();
        ListType replays = data.getList("replays");
        Replay last = null;

        for (BaseType replayType : replays)
        {
            Replay replay = film.replays.addReplay();

            BaseValue.edit(replay, (r) -> r.fromData(replayType));
            replay.category.set("");

            last = replay;
        }

        if (last != null)
        {
            this.refreshReplayList();
            this.update();
            this.panel.replayEditor.setReplay(last);
            this.scrollToReplay(last);
            this.updateFilmEditor();
        }
    }

    public void openFormEditor(ValueForm form, boolean editing, Consumer<Form> consumer)
    {
        UIElement target = this.panel;

        if (this.getRoot() != null)
        {
            target = this.getParentContainer();
        }

        UIFormPalette palette = UIFormPalette.open(target, editing, form.get(), (f) ->
        {
            for (Replay replay : this.getSelectedReplays())
            {
                replay.form.set(FormUtils.copy(f));
            }

            this.updateFilmEditor();

            if (consumer != null)
            {
                consumer.accept(f);
            }
            else
            {
                this.overlay.pickEdit.setForm(f);
            }
        });

        palette.updatable();
    }

    public void addReplay()
    {
        World world = MinecraftClient.getInstance().world;
        Camera camera = this.panel.getCamera();

        BlockHitResult blockHitResult = RayTracing.rayTrace(world, camera, 64F);
        Vec3d p = blockHitResult.getPos();
        Vector3d position = new Vector3d(p.x, p.y, p.z);

        if (blockHitResult.getType() == HitResult.Type.MISS)
        {
            position.set(camera.getLookDirection()).mul(5F).add(camera.position);
        }

        this.addReplay(position, camera.rotation.x, camera.rotation.y + MathUtils.PI);
    }

    private void fromCamera(int duration)
    {
        Position position = new Position();
        Clips camera = this.panel.getData().camera;
        CameraClipContext context = new CameraClipContext();

        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.category.set("");

        context.clips = camera;

        for (int i = 0; i < duration; i++)
        {
            context.clipData.clear();
            context.setup(i, 0F);

            for (Clip clip : context.clips.getClips(i))
            {
                context.apply(clip, position);
            }

            context.currentLayer = 0;

            float yaw = position.angle.yaw - 180;

            replay.keyframes.x.insert(i, position.point.x);
            replay.keyframes.y.insert(i, position.point.y);
            replay.keyframes.z.insert(i, position.point.z);
            replay.keyframes.yaw.insert(i, (double) yaw);
            replay.keyframes.headYaw.insert(i, (double) yaw);
            replay.keyframes.bodyYaw.insert(i, (double) yaw);
            replay.keyframes.pitch.insert(i, (double) position.angle.pitch);
        }

        this.refreshReplayList();
        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.scrollToReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void fromModelBlock()
    {
        ArrayList<ModelBlockEntity> modelBlocks = new ArrayList<>(BBSRendering.capturedModelBlocks);
        UISearchList<String> search = new UISearchList<>(new UIStringList(null));
        UIList<String> list = search.list;
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_DESCRIPTION, (b) ->
        {
            if (b)
            {
                int index = list.getIndex();
                ModelBlockEntity modelBlock = CollectionUtils.getSafe(modelBlocks, index);

                if (modelBlock != null)
                {
                    this.fromModelBlock(modelBlock);
                }
            }
        });

        modelBlocks.sort(Comparator.comparing(ModelBlockEntity::getName));

        for (ModelBlockEntity modelBlock : modelBlocks)
        {
            list.add(modelBlock.getName());
        }

        list.background();
        search.relative(panel.confirm).y(-5).w(1F).h(16 * 9 + 20).anchor(0F, 1F);

        panel.confirm.w(1F, -10);
        panel.content.add(search);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void fromModelBlock(ModelBlockEntity modelBlock)
    {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.category.set("");

        BlockPos blockPos = modelBlock.getPos();
        ModelProperties properties = modelBlock.getProperties();
        Transform transform = properties.getTransform().copy();
        double x = blockPos.getX() + transform.translate.x + 0.5D;
        double y = blockPos.getY() + transform.translate.y;
        double z = blockPos.getZ() + transform.translate.z + 0.5D;

        transform.translate.set(0, 0, 0);

        replay.shadow.set(properties.isShadow());
        replay.form.set(FormUtils.copy(properties.getForm()));
        replay.keyframes.x.insert(0, x);
        replay.keyframes.y.insert(0, y);
        replay.keyframes.z.insert(0, z);

        if (!transform.isDefault())
        {
            if (
                transform.rotate.x == 0 && transform.rotate.z == 0 &&
                transform.rotate2.x == 0 && transform.rotate2.y == 0 && transform.rotate2.z == 0 &&
                transform.scale.x == 1 && transform.scale.y == 1 && transform.scale.z == 1
            ) {
                double yaw = -Math.toDegrees(transform.rotate.y);

                replay.keyframes.yaw.insert(0, yaw);
                replay.keyframes.headYaw.insert(0, yaw);
                replay.keyframes.bodyYaw.insert(0, yaw);
            }
            else
            {
                AnchorForm form = new AnchorForm();
                BodyPart part = new BodyPart("");

                part.setForm(replay.form.get());
                form.transform.set(transform);
                form.parts.addBodyPart(part);

                replay.form.set(form);
            }
        }

        this.refreshReplayList();
        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.scrollToReplay(replay);
        this.updateFilmEditor();
    }

    public void addReplay(Vector3d position, float pitch, float yaw)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        Replay replay = film.replays.addReplay();

        replay.category.set("");

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        replay.keyframes.pitch.insert(0, (double) pitch);
        replay.keyframes.yaw.insert(0, (double) yaw);
        replay.keyframes.headYaw.insert(0, (double) yaw);
        replay.keyframes.bodyYaw.insert(0, (double) yaw);

        this.refreshReplayList();
        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.scrollToReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void updateFilmEditor()
    {
        this.panel.getController().createEntities();
        this.panel.replayEditor.updateChannelsList();
    }

    public void dupeReplay()
    {
        if (!this.hasReplaySelection())
        {
            return;
        }

        Replay last = null;

        for (Replay replay : this.getSelectedReplays())
        {
            Film film = this.panel.getData();
            Replay newReplay = film.replays.addReplay();

            newReplay.copy(replay);

            last = newReplay;
        }

        if (last != null)
        {
            this.refreshReplayList();
            this.update();
            this.panel.replayEditor.setReplay(last);
            this.scrollToReplay(last);
            this.updateFilmEditor();
        }
    }

    public void removeReplay()
    {
        if (!this.hasReplaySelection())
        {
            return;
        }

        Film film = this.panel.getData();
        List<Replay> removing = new ArrayList<>(this.getSelectedReplays());
        Replay focus = removing.get(0);
        int globalFocus = film.replays.getList().indexOf(focus);

        for (Replay replay : removing)
        {
            film.replays.remove(replay);
        }

        List<Replay> remaining = film.replays.getList();

        this.refreshReplayList();
        this.update();

        if (remaining.isEmpty())
        {
            this.panel.replayEditor.setReplay(null);
        }
        else
        {
            int idx = MathUtils.clamp(globalFocus, 0, remaining.size() - 1);
            Replay next = remaining.get(idx);

            this.panel.replayEditor.setReplay(next);
            this.scrollToReplay(next);
        }

        this.updateFilmEditor();
    }

    @Override
    protected String elementToString(UIContext context, int i, ReplayListEntry element)
    {
        if (element.isFolder())
        {
            return element.folderName;
        }

        int w = this.area.w - 20 - element.indent;

        return context.batcher.getFont().limitToWidth(element.replay.getName(), w);
    }

    @Override
    protected void renderElementPart(UIContext context, ReplayListEntry element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (element.isFolder())
        {
            boolean collapsed = this.collapsedCategories.contains(Replay.normalizeCategory(element.folderName));

            context.batcher.icon(collapsed ? Icons.ARROW_RIGHT : Icons.ARROW_DOWN, x, y);

            super.renderElementPart(context, element, i, x + 12, y, hover, selected);

            return;
        }

        x += element.indent;

        Replay replay = element.replay;

        if (replay.enabled.get())
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
        }
        else
        {
            context.batcher.textShadow(this.elementToString(context, i, element), x + 4, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.mulRGB(Colors.HIGHLIGHT, 0.75F) : Colors.GRAY);
        }

        Form form = replay.form.get();

        if (form != null)
        {
            int formX = this.area.x + this.area.w - 30;

            context.batcher.clip(formX, y, 40, 20, context);

            int formY = y - 10;

            FormUtilsClient.renderUI(form, context, formX, formY, formX + 40, formY + 40);

            context.batcher.unclip(context);

            if (replay.fp.get())
            {
                context.batcher.outlinedIcon(Icons.ARROW_UP, formX, formY + 20, 0.5F, 0.5F);
            }
        }
    }
}
