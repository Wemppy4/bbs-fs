package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.forms.structure.StructureCut;
import mchorse.bbs_mod.forms.structure.StructureSelection;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEntityList;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.presets.PresetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
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
    /** What the time-offset dialog remembers between openings. */
    private static String LAST_OFFSET = "0";

    public UIFilmPanel panel;
    private final Consumer<Form> formConsumer;

    private final UICopyPasteController presetController;

    /** Category names whose replay rows are hidden (headers stay visible). */
    private final Set<String> collapsedCategories = new HashSet<>();

    /** Set while building the context menu when the cursor is on a category folder row. */
    private String contextFolderCategoryName;

    public UIReplayList(Consumer<List<Replay>> callback, Consumer<Form> formConsumer, UIFilmPanel panel)
    {
        /* Rows are rebuilt wrappers over stable data, so "the same row" is the same replay (or
         * the same category name) — that is what lets a pick survive every list rebuild. */
        super((entries) -> callback.accept(replaysFromEntries(entries)), (a, b) ->
            a.kind == b.kind && (a.isReplay() ? a.replay == b.replay : a.folderName.equals(b.folderName)));

        this.formConsumer = formConsumer;
        this.panel = panel;

        this.presetController = new UICopyPasteController(PresetManager.REPLAYS, "_CopyReplay")
            .supplier(() -> this.hasReplaySelection() ? this.replaysToData() : null)
            .consumer((data, mouseX, mouseY) -> this.pasteReplay(data))
            .canCopy(this::hasReplaySelection)
            .canPaste(() -> this.panel != null && this.panel.getData() != null)
            .labels(UIKeys.SCENE_REPLAYS_CONTEXT_COPY, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE);

        this.multi().sorting();
        this.emptyState(UIKeys.SCENE_REPLAYS_EMPTY, BBSSettings::deepSurface);
        this.context((menu) ->
        {
            Film film = this.panel.getData();
            UIContext context = this.getContext();

            this.presetController.install(menu, context, context.mouseX, context.mouseY);

            menu.icon(MenuVerb.ADD, this::addReplay).label(UIKeys.SCENE_REPLAYS_CONTEXT_ADD);
            menu.icon(MenuVerb.REMOVE, this::removeReplay).label(UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE).enabled(this.hasReplaySelection());

            if (film != null)
            {
                menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_ADD_CATEGORY, this::openAddCategoryOverlay);
            }

            if (film != null && this.contextFolderCategoryName != null)
            {
                String cat = this.contextFolderCategoryName;

                menu.action(Icons.TRASH, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE_CATEGORY, () -> this.removeReplayCategory(cat));
            }

            if (film != null && StructureSelection.isReady())
            {
                menu.action(Icons.BLOCK, UIKeys.STRUCTURE_CUT_TITLE, this::cutSelectionIntoReplay);
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
            }
        });

        this.keys().register(Keys.DELETE, this::removeReplay)
            .inside()
            .label(UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE)
            .active(this::hasReplaySelection)
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.COPY, this::copyReplay)
            .inside()
            .label(UIKeys.SCENE_REPLAYS_CONTEXT_COPY)
            .active(this::hasReplaySelection)
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.PASTE, () ->
        {
            MapType data = Window.getClipboardMap("_CopyReplay");
            if (data != null)
            {
                this.pasteReplay(data);
            }
        }).inside()
            .label(UIKeys.SCENE_REPLAYS_CONTEXT_PASTE)
            .active(() -> this.panel != null && this.panel.getData() != null)
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_DUPE, this::dupeReplay)
            .inside()
            .label(UIKeys.SCENE_REPLAYS_CONTEXT_DUPE)
            .active(this::hasReplaySelection)
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.REPLAYS_SELECT_ALL, this::selectAllReplays)
            .inside()
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.keys().register(Keys.FORMS_EDIT, () ->
        {
            Replay r = this.getSelectedReplayFirst();
            if (r != null)
            {
                this.openFormEditor(r.form, true, null);
            }
        }).inside()
            .category(UIKeys.FILM_REPLAY_TITLE);
    }

    /** Ctrl+A from the base list lands here too: only replay rows are selectable, never folders. */
    @Override
    public void selectAll()
    {
        this.selectAllReplays();
    }

    private void selectAllReplays()
    {
        if (!this.multi)
        {
            return;
        }

        List<ReplayListEntry> replays = new ArrayList<>();

        for (ReplayListEntry e : this.list)
        {
            if (e.isReplay())
            {
                replays.add(e);
            }
        }

        this.selection.setAll(replays);
        this.fireSelectionCallback();
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

    /** The picked replays, in the order they were picked. */
    public List<Replay> getSelectedReplays()
    {
        return replaysFromEntries(this.selection.getItems());
    }

    public Replay getSelectedReplayFirst()
    {
        List<Replay> replays = this.getSelectedReplays();

        return replays.isEmpty() ? null : replays.get(0);
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

        for (ReplayListEntry e : this.list)
        {
            if (e.isReplay() && this.selection.contains(e))
            {
                out.add(e.replay);
            }
        }

        return out;
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

        /* Carry the pick over the rebuild: fresh rows that mean the same replay or category
         * (the constructor's sameness) replace their stale twins; rows that vanished — a
         * deleted replay, a collapsed category's replays — drop out. This is the ONE place
         * selection survival lives, for every rebuild caller alike. */
        List<ReplayListEntry> keep = new ArrayList<>();

        for (ReplayListEntry picked : this.selection.getItems())
        {
            int index = this.selection.indexOf(entries, picked);

            if (index != -1)
            {
                keep.add(entries.get(index));
            }
        }

        this.setList(entries);
        this.selection.setAll(keep);
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
        this.fireSelectionCallback();
        this.updateFilmEditor();
    }

    /** Tell the host what is picked now (the rebuild itself keeps the pick, but the host's
     *  panels follow the callback). */
    private void fireSelectionCallback()
    {
        if (this.callback != null && !this.selection.isEmpty())
        {
            this.callback.accept(this.getCurrent());
        }
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

                    this.refreshReplayList();
                    this.fireSelectionCallback();
                    this.update();

                    return true;
                }

                this.applySelectionOnClick(index);

                if (this.sorting && entry.isReplay() && this.current.size() == 1)
                {
                    this.startDrag(index, context);
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

    /** A category header takes replays dropped into it; between the rows, the caret decides. */
    @Override
    protected boolean acceptsDrop(ReplayListEntry element)
    {
        return element.isFolder();
    }

    /** The caret runs from where a replay row's name starts, so a drop into a category reads as one. */
    @Override
    protected int dropInset(ReplayListEntry element)
    {
        return ROW_PADDING + element.indent;
    }

    /** Dropped onto a category header: the replay is filed under it, keeping its place in the film. */
    @Override
    protected void onDrop(Object target, List<ReplayListEntry> items)
    {
        ReplayListEntry dragged = items.isEmpty() ? null : items.get(0);

        if (target instanceof ReplayListEntry folder && folder.isFolder() && dragged != null && dragged.isReplay())
        {
            this.applyReplayCategory(List.of(dragged.replay), folder.folderName);
        }
    }

    /** Dropped between rows: the replay takes the category of that slot and lands in it. */
    @Override
    protected void reorder(List<ReplayListEntry> items, int insertion)
    {
        ReplayListEntry dragged = items.isEmpty() ? null : items.get(0);

        if (dragged != null && dragged.isReplay())
        {
            this.moveReplayToSlot(dragged.replay, insertion);
        }
    }

    /**
     * Which category a slot between rows belongs to: the group the rows around it are in, and
     * the root below the last row - the replays of no category are listed last, so the bottom
     * of the list is the one place that always means "out of every category".
     */
    private String slotCategory(int insertion)
    {
        if (insertion >= this.list.size())
        {
            return "";
        }

        ReplayListEntry at = this.list.get(insertion);

        if (at.isReplay())
        {
            return Replay.normalizeCategory(at.replay.category.get());
        }

        /* The slot sits right above a header, so it belongs to whatever ends above it */
        ReplayListEntry above = insertion > 0 ? this.list.get(insertion - 1) : null;

        if (above == null)
        {
            return "";
        }

        return above.isReplay()
            ? Replay.normalizeCategory(above.replay.category.get())
            : Replay.normalizeCategory(above.folderName);
    }

    /**
     * Move a replay to a slot of the list. The film keeps one flat list of replays and the
     * categories are only a way of showing it, so the place in that flat list has to be the one
     * that looks like the slot: right after the last replay of the same category above the
     * caret, or right before the first one below it.
     */
    private void moveReplayToSlot(Replay moved, int insertion)
    {
        Film data = this.panel.getData();

        if (data == null)
        {
            return;
        }

        String category = this.slotCategory(insertion);
        Replay above = this.neighbourInCategory(insertion - 1, -1, category, moved);
        Replay below = this.neighbourInCategory(insertion, 1, category, moved);

        Replays replays = data.replays;

        data.preNotify(IValueListener.FLAG_UNMERGEABLE);

        this.assignReplayCategoryValue(moved, category);
        replays.remove(moved);

        List<Replay> all = replays.getList();
        int target = all.size();

        if (above != null)
        {
            target = all.indexOf(above) + 1;
        }
        else if (below != null)
        {
            target = all.indexOf(below);
        }

        /* Anchors and camera selectors hold the replay's stable id, so moving it about is
         * nothing but moving it about. */
        replays.add(MathUtils.clamp(target, 0, all.size()), moved);

        data.postNotify(IValueListener.FLAG_UNMERGEABLE);

        this.refreshReplayList();
        this.updateFilmEditor();

        for (int i = 0; i < this.list.size(); i++)
        {
            ReplayListEntry e = this.list.get(i);

            if (e.isReplay() && e.replay == moved)
            {
                this.pick(i);

                break;
            }
        }
    }

    /** Walking from {@code from} in {@code step}s, the first replay row of {@code category}. */
    private Replay neighbourInCategory(int from, int step, String category, Replay skip)
    {
        for (int i = from; i >= 0 && i < this.list.size(); i += step)
        {
            ReplayListEntry entry = this.list.get(i);

            if (entry.isReplay() && entry.replay != skip
                && Replay.normalizeCategory(entry.replay.category.get()).equals(category))
            {
                return entry.replay;
            }
        }

        return null;
    }

    private void pasteToReplays(MapType data)
    {
        List<Replay> selectedReplays = this.getSelectedReplays();

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

                    /* A track this replay's form has no room for — pasting player keyframes onto a
                     * replay whose form lost that body part, say. */
                    if (channel == null)
                    {
                        continue;
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

        UIOverlay.addOverlay(this.getContext(), new UIProcessReplaysPanel(this.panel, this.getSelectedReplaysInViewOrder()), 320, 320);
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

                /* i/o are ordered by the film's own replay list, not by visible rows — a selected
                 * replay in a collapsed folder has no row and used to silently drop out. The
                 * index is taken by identity - see the same note in collectVisibleReplays. */
                List<Replay> all = film.replays.getList();

                for (Replay replay : selected)
                {
                    int index = CollectionUtils.getIndex(all, replay);

                    if (index >= 0)
                    {
                        min = Math.min(min, index);
                    }
                }

                if (min == Integer.MAX_VALUE)
                {
                    return;
                }

                for (Replay replay : selected)
                {
                    int index = CollectionUtils.getIndex(all, replay);

                    if (index < 0)
                    {
                        continue;
                    }

                    builder.variables.get("i").set(index);
                    builder.variables.get("o").set(index - min);

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
        Window.setClipboard(this.replaysToData(), "_CopyReplay");
    }

    /**
     * Serialize the selected replays into the shared {@code {"replays": [...]}} format
     * used by both clipboard copy/paste and presets.
     */
    private MapType replaysToData()
    {
        MapType replays = new MapType();
        ListType replayList = new ListType();

        replays.put("replays", replayList);

        for (Replay replay : this.getSelectedReplays())
        {
            replayList.add(replay.toData());
        }

        return replays;
    }

    /**
     * Open the presets overlay for replays (save the current selection, or load a preset into the film).
     */
    public void openReplayPresets()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            this.presetController.openPresets(context, context.mouseX, context.mouseY);
        }
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
            this.showNewReplay(last);
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
            else if (this.formConsumer != null)
            {
                this.formConsumer.accept(f);
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
        Replay replay = ReplayFactory.fromCamera(this.panel.getData(), duration);

        this.showNewReplay(replay);
        this.openFormEditor(replay.form, false, null);
    }

    private void fromModelBlock()
    {
        /* The same list the model block panel shows, so a block is picked here by the
         * face it wears there instead of by a line of coordinates. */
        UIModelBlockEntityList list = new UIModelBlockEntityList(null);
        UISearchList<ModelBlockEntity> search = new UISearchList<>(list);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_DESCRIPTION, (b) ->
        {
            ModelBlockEntity modelBlock = b ? list.getCurrentFirst() : null;

            if (modelBlock != null)
            {
                this.fromModelBlock(modelBlock);
            }
        });

        list.setBlocks(BBSRendering.capturedModelBlocks);
        list.background();

        search.label(UIKeys.GENERAL_SEARCH);
        search.relative(panel.confirm).y(-5).w(1F).h(UIModelBlockEntityList.ROW * 7 + 20).anchor(0F, 1F);

        panel.confirm.w(1F, -10);
        panel.content.add(search);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void fromModelBlock(ModelBlockEntity modelBlock)
    {
        this.showNewReplay(ReplayFactory.fromModelBlock(this.panel.getData(), modelBlock));
    }

    /**
     * The wand's region as a replay: saved as a structure, cleared out of the world, and added as a
     * form standing exactly where the blocks did. Destructive, so it asks first — and the message
     * names the command that puts the build back, because Minecraft has no undo for this.
     */
    private void cutSelectionIntoReplay()
    {
        Film film = this.panel.getData();

        if (film == null || !StructureSelection.isReady())
        {
            return;
        }

        String id = StructureCut.nextId(film.getId());
        BlockPos min = StructureSelection.getMin();
        BlockPos max = StructureSelection.getMax();
        Vec3i size = StructureSelection.getSize();
        IKey message = UIKeys.STRUCTURE_CUT_CONFIRM.format(String.valueOf(StructureSelection.getVolume()), id);

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(UIKeys.STRUCTURE_CUT_TITLE, message, (confirmed) ->
        {
            if (confirmed)
            {
                StructureCut.request(id, min, max, (ok) ->
                {
                    if (ok)
                    {
                        this.addStructureReplay(id, min, size);
                    }
                });
            }
        }), 300, 140);
    }

    /** The cut region's form, dropped in at the very spot it was cut from. */
    private void addStructureReplay(String id, BlockPos min, Vec3i size)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        this.showNewReplay(ReplayFactory.fromStructure(film, id, min, size));
    }

    public void addReplay(Vector3d position, float pitch, float yaw)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        Replay replay = ReplayFactory.atPosition(film, position, pitch, yaw);

        this.showNewReplay(replay);
        this.openFormEditor(replay.form, false, null);
    }

    /** The tail every way of adding a replay shares: rebuild the rows, focus the newcomer. */
    private void showNewReplay(Replay replay)
    {
        this.refreshReplayList();
        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.scrollToReplay(replay);
        this.updateFilmEditor();
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
            this.showNewReplay(last);
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
            int formY = y - 10;

            if (BBSSettings.listModelPreview.get())
            {
                context.batcher.clip(formX, y, 40, 20, context);

                FormUtilsClient.renderUI(form, context, formX, formY, formX + 40, formY + 40);

                context.batcher.unclip(context);
            }

            if (replay.fp.get())
            {
                context.batcher.outlinedIcon(Icons.ARROW_UP, formX, formY + 20, 0.5F, 0.5F);
            }
        }
    }
}
