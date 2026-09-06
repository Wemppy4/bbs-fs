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
import mchorse.bbs_mod.forms.structure.StructureManager;
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
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEntityList;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.categories.Category;
import mchorse.bbs_mod.utils.categories.CategoryPath;
import mchorse.bbs_mod.utils.categories.CategoryTree;
import mchorse.bbs_mod.utils.MathUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    /** How far one folder of nesting shifts a row. */
    private static final int INDENT = 10;

    /** The box a row's form is drawn in, centred on the row however tall the row is. */
    private static final int PREVIEW = 40;

    /** Set while building the context menu when the cursor is on a folder row. */
    private String contextFolderPath;

    /** The folder a press went down on, waiting to see whether the press turns into a drag. */
    private String pressedFolder;

    public UIReplayList(Consumer<List<Replay>> callback, Consumer<Form> formConsumer, UIFilmPanel panel)
    {
        /* Rows are rebuilt wrappers over stable data, so "the same row" is the same replay (or
         * the same category name) — that is what lets a pick survive every list rebuild. */
        super((entries) -> callback.accept(replaysFromEntries(entries)), (a, b) ->
            a.kind == b.kind && (a.isReplay() ? a.replay == b.replay : a.folderPath.equals(b.folderPath)));

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

            /* Asked for on a folder row, a new category is made inside that folder: nesting
             * without anyone having to know that a path is spelled with a slash. */
            String folder = this.contextFolderPath == null ? "" : this.contextFolderPath;

            if (film != null)
            {
                menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_ADD_CATEGORY, () -> this.openAddCategoryOverlay(folder));
            }

            if (film != null && this.contextFolderPath != null)
            {
                boolean enabled = this.isCategoryEnabled(folder);

                menu.action(Icons.EDIT, UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_CATEGORY, () -> this.openCategoryEditMenu(folder));
                menu.action(enabled ? Icons.INVISIBLE : Icons.VISIBLE, enabled ? UIKeys.SCENE_REPLAYS_CONTEXT_DISABLE_CATEGORY : UIKeys.SCENE_REPLAYS_CONTEXT_ENABLE_CATEGORY, () -> this.setCategoryEnabled(folder, !enabled));
                menu.action(Icons.TRASH, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE_CATEGORY, () -> this.removeReplayCategory(folder));
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

                if (film != null)
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

    /**
     * A press on nothing drops the pick here, the way it does in the grids: the list fills the
     * panel, so the space under the last replay is the one place to click to mean "none of them".
     */
    @Override
    protected boolean clearsOnEmpty()
    {
        return true;
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
        this.contextFolderPath = null;

        int idx = this.getIndexAtCursor(context);

        if (this.exists(idx))
        {
            ReplayListEntry e = this.list.get(idx);

            if (e.isFolder() && !e.folderPath.isEmpty())
            {
                this.contextFolderPath = e.folderPath;
            }
        }

        try
        {
            return super.createContextMenu(context);
        }
        finally
        {
            this.contextFolderPath = null;
        }
    }

    /**
     * Remove a folder. Everything that was in it — replays and folders alike — moves up into the
     * folder that held it, because a folder is a place to keep replays and removing the place must
     * not take the replays with it.
     */
    private void removeReplayCategory(String path)
    {
        Film film = this.panel.getData();

        if (film == null || path.isEmpty())
        {
            return;
        }

        String parent = CategoryPath.parent(path);

        for (Replay replay : film.replays.getList())
        {
            String category = Replay.normalizeCategory(replay.category.get());

            if (CategoryPath.isInside(category, path))
            {
                replay.category.set(CategoryPath.reparent(category, path, parent));
            }
        }

        film.replayCategories.removeRecord(path);
        film.replayCategories.renameSubtree(path, parent);

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

        this.expandTo(Replay.normalizeCategory(replay.category.get()));
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

        List<Replay> all = film.replays.getList();
        List<ReplayListEntry> entries = new ArrayList<>();

        this.addFolderRows(entries, film, all, CategoryTree.byParent(this.collectCategoryPaths(film)), "", 0, 0, 0);

        /* Carry the pick over the rebuild: fresh rows that mean the same replay or folder
         * (the constructor's sameness) replace their stale twins; rows that vanished - a
         * deleted replay, a closed folder's replays - drop out. This is the ONE place
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
     * The rows of what lies in one folder: the folders it holds first, opened ones bringing their
     * own contents with them, then its replays in the order the film keeps them. The root is a
     * folder like any other here, which is why replays of no category end up last.
     */
    private void addFolderRows(List<ReplayListEntry> entries, Film film, List<Replay> all, Map<String, List<String>> children, String parent, int depth, int lines, int color)
    {
        List<String> folders = children.getOrDefault(parent, Collections.emptyList());
        List<Replay> replays = this.replaysIn(all, parent);

        for (int i = 0; i < folders.size(); i++)
        {
            String path = folders.get(i);
            boolean last = i == folders.size() - 1 && replays.isEmpty();
            Category category = film.replayCategories.getByPath(path);
            /* A folder with no colour of its own wears the one it sits in, so a painted folder
             * marks everything under it rather than only its own replays. */
            int stripe = category == null || category.color.get() == 0 ? color : category.color.get();

            entries.add(ReplayListEntry.folder(path, depth, lines, last, this.countReplays(path), stripe));

            if (film.replayCategories.isExpanded(path))
            {
                this.addFolderRows(entries, film, all, children, path, depth + 1, childGuideLines(lines, depth, last), stripe);
            }
        }

        for (int i = 0; i < replays.size(); i++)
        {
            entries.add(ReplayListEntry.replay(replays.get(i), depth, lines, i == replays.size() - 1, color));
        }
    }

    /** The replays that sit directly in a folder, the root included. */
    private List<Replay> replaysIn(List<Replay> all, String path)
    {
        List<Replay> replays = new ArrayList<>();

        for (Replay replay : all)
        {
            if (path.equals(Replay.normalizeCategory(replay.category.get())))
            {
                replays.add(replay);
            }
        }

        return replays;
    }

    /** Every folder of the film: the recorded ones in their order, then those only a replay is in. */
    private List<String> collectCategoryPaths(Film film)
    {
        List<String> used = new ArrayList<>();

        for (Replay replay : film.replays.getList())
        {
            used.add(replay.category.get());
        }

        return CategoryTree.paths(film.replayCategories, used);
    }

    /** Open a folder and every folder on the way to it, so that a row inside it is there to be seen. */
    private void expandTo(String path)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        for (String walk = path; !walk.isEmpty(); walk = CategoryPath.parent(walk))
        {
            film.replayCategories.setExpanded(walk, true);
        }
    }

    /** Open a folder and everything inside it. */
    private void expandSubtree(String path)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        this.expandTo(path);

        for (String other : this.collectCategoryPaths(film))
        {
            if (CategoryPath.isInside(other, path))
            {
                film.replayCategories.setExpanded(other, true);
            }
        }
    }

    /**
     * A press on a folder row picks everything in it, however deep. The folder is opened first: a
     * pick lives on the rows, so replays hidden in a closed folder could not be part of one, and a
     * selection nobody can see is worse than a folder that opens itself.
     */
    private void selectCategory(String path)
    {
        this.expandSubtree(path);
        this.refreshReplayList();

        List<ReplayListEntry> picked = new ArrayList<>();

        for (ReplayListEntry entry : this.list)
        {
            if (entry.isReplay() && CategoryPath.isInside(Replay.normalizeCategory(entry.replay.category.get()), path))
            {
                picked.add(entry);
            }
        }

        this.selection.setAll(picked);
    }

    /** How many replays a folder holds, the ones in the folders inside it included. */
    private int countReplays(String path)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return 0;
        }

        int count = 0;

        for (Replay replay : film.replays.getList())
        {
            if (CategoryPath.isInside(Replay.normalizeCategory(replay.category.get()), path))
            {
                count += 1;
            }
        }

        return count;
    }

    /** A folder reads as on while any replay in it is. */
    private boolean isCategoryEnabled(String path)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return false;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay.enabled.get() && CategoryPath.isInside(Replay.normalizeCategory(replay.category.get()), path))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Turn a whole folder on or off. The switch is the replays' own {@link Replay#enabled} and not
     * a flag on the folder: everything that draws or plays a replay already asks the replay, and
     * none of it has to learn to ask the folder too.
     */
    private void setCategoryEnabled(String path, boolean enabled)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        for (Replay replay : film.replays.getList())
        {
            if (CategoryPath.isInside(Replay.normalizeCategory(replay.category.get()), path))
            {
                replay.enabled.set(enabled);
            }
        }

        this.updateFilmEditor();
    }

    /**
     * Update {@link Replay#category}, record the folder and open the way to it; does not refresh
     * the list (for use before index-based ops).
     */
    private void assignReplayCategoryValue(Replay replay, String rawCategory)
    {
        String path = Replay.normalizeCategory(rawCategory);
        Film film = this.panel.getData();

        replay.category.set(path);

        if (!path.isEmpty() && film != null)
        {
            film.replayCategories.ensure(path);
            this.expandTo(path);
        }
    }

    /** Ask for a name and make the folder - inside {@code parent}, when the ask came from a folder. */
    private void openAddCategoryOverlay(String parent)
    {
        this.openCategoryNameOverlay(UIKeys.SCENE_REPLAYS_ADD_CATEGORY_TITLE, UIKeys.SCENE_REPLAYS_ADD_CATEGORY_DESCRIPTION, "", (name) ->
        {
            Film film = this.panel.getData();
            String path = CategoryPath.join(parent, name);

            if (film == null || path.isEmpty())
            {
                return;
            }

            film.replayCategories.ensure(path);
            this.expandTo(path);
            this.refreshReplayList();
            this.updateFilmEditor();
        });
    }

    /** What a folder has to say for itself — its name and its colour — as a popup at the cursor. */
    private void openCategoryEditMenu(String path)
    {
        Film film = this.panel.getData();
        UIContext context = this.getContext();

        if (film == null || context == null)
        {
            return;
        }

        Category category = film.replayCategories.getByPath(path);

        context.replaceContextMenu(new UIReplayCategoryContextMenu(this, path, category == null ? 0 : category.color.get()));
    }

    /**
     * Rename the folder and paint it, and answer with where the folder ended up — the popup edits
     * it further, and after a rename it lives somewhere else. A name already taken in the same
     * place would pour two folders into one, so it is left alone; the colour is still applied,
     * which is the half that can be.
     */
    String applyCategoryEdit(String path, String name, int color)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return path;
        }

        String renamed = CategoryPath.join(CategoryPath.parent(path), name);

        if (renamed.isEmpty() || this.collectCategoryPaths(film).contains(renamed))
        {
            renamed = path;
        }

        if (!renamed.equals(path))
        {
            for (Replay replay : film.replays.getList())
            {
                String category = Replay.normalizeCategory(replay.category.get());

                if (CategoryPath.isInside(category, path))
                {
                    replay.category.set(CategoryPath.reparent(category, path, renamed));
                }
            }

            film.replayCategories.renameSubtree(path, renamed);
        }

        film.replayCategories.ensure(renamed).color.set(color);
        this.expandTo(renamed);
        this.refreshReplayList();
        this.updateFilmEditor();

        return renamed;
    }

    /** The one overlay that both making and renaming a folder ask a name with. */
    private void openCategoryNameOverlay(IKey title, IKey description, String initial, Consumer<String> consumer)
    {
        UITextbox box = new UITextbox(1000, (text) -> {});

        box.setText(initial);
        box.placeholder(UIKeys.SCENE_REPLAYS_ADD_CATEGORY_PLACEHOLDER);

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(title, description, (ok) ->
        {
            if (ok)
            {
                consumer.accept(box.getText());
            }
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
            add.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CATEGORY_NEW, () -> this.openCategoryNameOverlay(
                UIKeys.SCENE_REPLAYS_ADD_CATEGORY_TITLE,
                UIKeys.SCENE_REPLAYS_ADD_CATEGORY_DESCRIPTION,
                "",
                (name) -> this.applyReplayCategory(selected, name)));

            for (String path : this.collectCategoryPaths(film))
            {
                add.action(Icons.FOLDER, IKey.raw(path), () -> this.applyReplayCategory(selected, path));
            }
        });
    }

    private void applyReplayCategory(List<Replay> selected, String rawCategory)
    {
        for (Replay replay : selected)
        {
            this.assignReplayCategoryValue(replay, rawCategory);
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
    protected int indent(ReplayListEntry element)
    {
        return element.depth * INDENT;
    }

    @Override
    protected int indentStep()
    {
        return INDENT;
    }

    /** Folders are the branches of this tree; a replay is a leaf and wears no arrow. */
    @Override
    protected Boolean branch(ReplayListEntry element)
    {
        Film film = this.panel.getData();

        if (!element.isFolder() || film == null)
        {
            return null;
        }

        return film.replayCategories.isExpanded(element.folderPath);
    }

    @Override
    protected void toggle(ReplayListEntry element)
    {
        Film film = this.panel.getData();

        if (!element.isFolder() || film == null)
        {
            return;
        }

        film.replayCategories.setExpanded(element.folderPath, !film.replayCategories.isExpanded(element.folderPath));
        this.refreshReplayList();
    }

    /**
     * A press on a folder row, which the base list hands here through {@link #pressItem}. The arrow
     * folds the branch at once; Ctrl picks everything the folder holds; a plain press only arms the
     * drag, and the fold waits for the release — otherwise carrying a folder off would fold it on
     * the way.
     */
    private boolean pressFolder(ReplayListEntry entry, int index, UIContext context)
    {
        this.pressedFolder = null;

        if (this.hitsArrow(entry, this.contentX(context)))
        {
            this.toggle(entry);
        }
        else if (Window.isCtrlPressed())
        {
            this.selectCategory(entry.folderPath);
        }
        else
        {
            this.pressedFolder = entry.folderPath;

            this.startDragging(entry, context);
        }

        this.cursor = index;

        this.fireSelectionCallback();
        this.update();

        return true;
    }

    /**
     * The fold a plain press on a folder promised, now that the press turned out not to be a drag
     * — the same bargain the form palette's category headers make with theirs.
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        String pressed = this.pressedFolder;

        this.pressedFolder = null;

        if (pressed != null && !this.drag.isActive() && this.area.isInside(context))
        {
            int index = this.indexAt(this.contentX(context), this.contentY(context));
            List<ReplayListEntry> visible = this.visible();

            if (index >= 0 && index < visible.size())
            {
                ReplayListEntry entry = visible.get(index);

                if (entry.isFolder() && entry.folderPath.equals(pressed))
                {
                    this.toggle(entry);
                    this.update();
                }
            }
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        ReplayListEntry entry = this.visible().get(index);

        return entry.isFolder() ? this.pressFolder(entry, index, context) : super.pressItem(index, context);
    }

    /** Arm the drag this row would carry, if it carries anything. */
    private void startDragging(ReplayListEntry entry, UIContext context)
    {
        List<ReplayListEntry> payload = this.dragPayload(entry);

        if (payload != null && !payload.isEmpty())
        {
            this.drag.start(payload, context.mouseX, context.mouseY);
        }
    }

    /**
     * Replays travel as a whole pick; a folder travels alone, because a handful of folders dropped
     * at once says nothing about what should happen to the rows between them.
     */
    @Override
    protected List<ReplayListEntry> dragPayload(ReplayListEntry item)
    {
        if (item.isFolder())
        {
            return this.sorting && !this.isFiltering() && !item.folderPath.isEmpty()
                ? Collections.singletonList(item)
                : null;
        }

        return super.dragPayload(item);
    }

    /** A folder row takes replays dropped into it; between the rows, the caret decides. */
    @Override
    protected boolean acceptsDrop(ReplayListEntry element)
    {
        if (!element.isFolder())
        {
            return false;
        }

        List<ReplayListEntry> dragged = this.drag.getItems();

        /* A folder cannot swallow itself or anything it already holds, so it must not light up
         * as though it could. */
        return dragged.isEmpty()
            || !dragged.get(0).isFolder()
            || !CategoryPath.isInside(element.folderPath, dragged.get(0).folderPath);
    }

    /** The caret runs from where a replay row's name starts, so a drop into a folder reads as one. */
    @Override
    protected int dropInset(ReplayListEntry element)
    {
        return ROW_PADDING + this.indent(element);
    }

    /** Dropped onto a folder row: the replays are filed under it, or the folder moves inside it. */
    @Override
    protected void onDrop(Object target, List<ReplayListEntry> items)
    {
        ReplayListEntry dragged = items.isEmpty() ? null : items.get(0);

        if (!(target instanceof ReplayListEntry folder) || !folder.isFolder() || dragged == null)
        {
            return;
        }

        if (dragged.isFolder())
        {
            this.moveCategory(dragged.folderPath, folder.folderPath, "");
        }
        else
        {
            this.applyReplayCategory(this.draggedReplays(items), folder.folderPath);
        }
    }

    /**
     * Dropped between rows: the replays take the folder of that slot and land in it one after
     * another, or the dragged folder takes that place among its new siblings.
     */
    @Override
    protected void reorder(List<ReplayListEntry> items, int insertion)
    {
        ReplayListEntry dragged = items.isEmpty() ? null : items.get(0);

        if (dragged == null)
        {
            return;
        }

        if (dragged.isFolder())
        {
            this.moveCategoryToSlot(dragged.folderPath, insertion);

            return;
        }

        List<Replay> moving = this.draggedReplays(items);
        int slot = insertion;

        for (Replay replay : moving)
        {
            this.moveReplayToSlot(replay, slot);

            int row = this.rowOfReplay(replay);

            slot = row == -1 ? slot : row + 1;
        }

        this.selectReplays(moving);
    }

    /** The dragged rows' replays, in the order they were shown — the order they have to land in. */
    private List<Replay> draggedReplays(List<ReplayListEntry> items)
    {
        return replaysFromEntries(this.inViewOrder(items));
    }

    /** Row of a replay in the list as it stands now, or -1. */
    private int rowOfReplay(Replay replay)
    {
        for (int i = 0; i < this.list.size(); i++)
        {
            ReplayListEntry entry = this.list.get(i);

            if (entry.isReplay() && entry.replay == replay)
            {
                return i;
            }
        }

        return -1;
    }

    /** Leave the given replays picked, which is what a group that has just been moved should be. */
    private void selectReplays(List<Replay> replays)
    {
        List<ReplayListEntry> picked = new ArrayList<>();

        for (ReplayListEntry entry : this.list)
        {
            /* By identity: two replays with the same fields are equal by content, and a duplicate
             * would drag its twin into the pick. */
            if (entry.isReplay() && CollectionUtils.getIndex(replays, entry.replay) != -1)
            {
                picked.add(entry);
            }
        }

        this.selection.setAll(picked);
        this.fireSelectionCallback();
    }

    /**
     * Where a folder dropped between rows lands: in the folder that holds the row under the caret,
     * before that row's folder. Replays are shown after the folders of the folder they are in, so
     * a caret among them means "last folder in there", and past the last row means the root's end.
     */
    private void moveCategoryToSlot(String path, int insertion)
    {
        String parent = "";
        String before = "";

        if (insertion >= 0 && insertion < this.list.size())
        {
            ReplayListEntry at = this.list.get(insertion);

            if (at.isFolder())
            {
                if (at.folderPath.equals(path))
                {
                    return;
                }

                parent = CategoryPath.parent(at.folderPath);
                before = at.folderPath;
            }
            else
            {
                parent = Replay.normalizeCategory(at.replay.category.get());
            }
        }

        this.moveCategory(path, parent, before);
    }

    /**
     * Move a folder into another one, and before one of its folders when the caret named it. Two
     * moves do nothing: a folder into itself (there would be no tree left), and one into a place
     * where its name is taken, which would silently pour two folders into one.
     */
    private void moveCategory(String path, String parent, String before)
    {
        Film film = this.panel.getData();

        if (film == null || path.isEmpty() || CategoryPath.isInside(parent, path))
        {
            return;
        }

        String moved = CategoryPath.join(parent, CategoryPath.name(path));

        if (!moved.equals(path))
        {
            if (this.collectCategoryPaths(film).contains(moved))
            {
                return;
            }

            for (Replay replay : film.replays.getList())
            {
                String category = Replay.normalizeCategory(replay.category.get());

                if (CategoryPath.isInside(category, path))
                {
                    replay.category.set(CategoryPath.reparent(category, path, moved));
                }
            }

            film.replayCategories.renameSubtree(path, moved);
        }

        film.replayCategories.moveBefore(moved, before.equals(path) ? "" : before);
        this.expandTo(moved);
        this.refreshReplayList();
        this.updateFilmEditor();
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
            : above.folderPath;
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

            /* The folder travels with the replay: a path means the same thing in any film, and the
             * folder is simply made here when this one has never heard of it. */
            BaseValue.edit(replay, (r) -> r.fromData(replayType));
            this.assignReplayCategoryValue(replay, replay.category.get());

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
     * form standing exactly where the blocks did. Destructive and without an undo, so it asks first
     * — and the message names the structure the blocks live on as, since that file is all that is
     * left of them.
     */
    private void cutSelectionIntoReplay()
    {
        Film film = this.panel.getData();

        if (film == null || !StructureSelection.isReady())
        {
            return;
        }

        String path = StructureCut.nextPath(film.getId());
        String id = StructureManager.assetId(path);
        BlockPos min = StructureSelection.getMin();
        BlockPos max = StructureSelection.getMax();
        Vec3i size = StructureSelection.getSize();
        IKey message = UIKeys.STRUCTURE_CUT_CONFIRM.format(String.valueOf(StructureSelection.getVolume()), id);

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(UIKeys.STRUCTURE_CUT_TITLE, message, (confirmed) ->
        {
            if (confirmed)
            {
                StructureCut.request(path, min, max, (ok) ->
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
            return element.folderName();
        }

        int w = this.area.w - 20 - this.indent(element);

        return context.batcher.getFont().limitToWidth(element.replay.getName(), w);
    }

    /**
     * The folder's colour. The list lays it down under the pick and the hover — belonging is a
     * quieter thing than which row the cursor or the pick is on — and tints the hover with it.
     */
    @Override
    protected int rowColor(ReplayListEntry element)
    {
        return element.color;
    }

    @Override
    protected void renderElementPart(UIContext context, ReplayListEntry element, int i, int x, int y, boolean hover, boolean selected)
    {
        int rowHeight = this.scroll.scrollItemSize;
        int textY = y + (rowHeight - context.batcher.getFont().getHeight()) / 2;

        if (element.isFolder())
        {
            int iconX = x + this.rowContentX(element) + ARROW_SLOT;
            int textX = x + iconRowTextX(this.rowContentX(element));

            this.renderTreeGuides(context, x, y, element.depth, element.lines, element.last, iconX);
            this.renderArrow(context, element, x, y);
            context.batcher.icon(Icons.FOLDER, iconX, y + (rowHeight - 16) / 2);
            context.batcher.textShadow(this.elementToString(context, i, element), textX, textY, RowStyle.textColor(hover || selected));

            /* How much is in there, which a closed folder cannot say any other way. */
            String count = String.valueOf(element.count);

            context.batcher.textShadow(count, this.area.ex() - ROW_PADDING - context.batcher.getFont().getWidth(count), textY, Colors.GRAY);

            return;
        }

        Replay replay = element.replay;

        this.renderTreeGuides(context, x, y, element.depth, element.lines, element.last, x + this.rowContentX(element));

        if (replay.enabled.get())
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
        }
        else
        {
            context.batcher.textShadow(this.elementToString(context, i, element), x + this.rowContentX(element), textY, RowStyle.textColor(hover || selected, Colors.GRAY));
        }

        Form form = replay.form.get();

        if (form != null)
        {
            int formX = this.area.x + this.area.w - 30;
            int my = y + rowHeight / 2;
            int formY = my - PREVIEW / 2;

            if (BBSSettings.listModelPreview.get())
            {
                context.batcher.clip(formX, y, PREVIEW, rowHeight, context);

                FormUtilsClient.renderUI(form, context, formX, formY, formX + PREVIEW, formY + PREVIEW);

                context.batcher.unclip(context);
            }

            if (replay.fp.get())
            {
                context.batcher.outlinedIcon(Icons.ARROW_UP, formX, my, 0.5F, 0.5F);
            }
        }
    }
}
