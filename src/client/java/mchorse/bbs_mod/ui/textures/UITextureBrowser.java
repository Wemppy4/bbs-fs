package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.items.ItemDrag;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIStrip;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.cells.CellState;
import mchorse.bbs_mod.ui.utils.cells.DragGhost;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * The browsing half of a {@link UITexturePicker}: a strip of search and actions along the
 * top, the path as breadcrumbs under it, a side panel down the left (the folder tree, or the
 * multiskin's list of skins), the grid of the folder in the middle and the chosen texture's
 * facts on the right.
 *
 * <p>It owns everything that spans those parts — the folder on show, the listing, the
 * search, the multi-selection, a drag, the file operations — and leaves to the picker what a
 * choice means (its callback, the multiskin, the editor tabs). The grid and the tree paint and
 * hit-test their own rows and call back here with what was pressed.</p>
 */
public class UITextureBrowser extends UIElement implements IFolderTreeHost
{
    public static final int BAR_HEIGHT = 20;

    /** The status line along the bottom: what's in the folder, what's picked, what's on the clipboard. */
    public static final int STATUS_HEIGHT = 16;
    private static final int SEARCH_CAP = 400;
    private static final int MIN_SIDE = 100;
    private static final int MAX_SIDE = 400;

    /**
     * The four sets of quick actions a texture cell can show — the shared ones with the pin
     * button in front, kept whole rather than built per cell per frame.
     */
    private static final CellAction[] PIN_MODIFIABLE = CellAction.with(CellAction.PIN, CellAction.of(true));
    private static final CellAction[] UNPIN_MODIFIABLE = CellAction.with(CellAction.UNPIN, CellAction.of(true));
    private static final CellAction[] PIN_READ_ONLY = CellAction.with(CellAction.PIN, CellAction.of(false));
    private static final CellAction[] UNPIN_READ_ONLY = CellAction.with(CellAction.UNPIN, CellAction.of(false));

    /* Side panel widths, dragged by the user; each is capped by what the other leaves of the row */
    private final UISplitter leftHandle = UISplitter.pixels("texture_browser.left", 140, MIN_SIDE, MAX_SIDE);
    private final UISplitter infoHandle = UISplitter.pixels("texture_browser.info", 150, MIN_SIDE, MAX_SIDE);

    public final UITexturePicker picker;

    public UIStrip bar;
    public UIIcon back;
    public UIIcon treeToggle;
    public UIIcon multiToggle;
    public UITextbox search;
    public UIIcon sort;
    public UIIcon everywhere;
    public UIIcon newTexture;

    /** The path typed by hand — shown in place of the breadcrumbs while editing. */
    public UITextbox text;
    public UIBreadcrumbs crumbs;

    /** The side panel: the folder tree, or the picker's multiskin list while a multiskin is edited. */
    public UIElement left;
    public UIFolderTree tree;
    public UITextureGrid grid;
    public UITextureInfoPanel info;


    /* Files taken by Ctrl+C / Ctrl+X, put down by Ctrl+V; shown on the status line until then */
    private final List<Link> clipboard = new ArrayList<>();
    private boolean cut;
    public UIIcon clearClipboard;

    private Link path = new Link("", "");
    private final List<TextureEntry> entries = new ArrayList<>();
    private String query = "";
    private boolean searchEverywhere;

    /**
     * A file operation Ctrl+Z reverses. Only what the browser itself produced is reversible:
     * a move goes back, a copy is deleted (the original never moved). Deletions and renames
     * of the user's own files stay out — undoing those would mean guessing.
     */
    private interface Change
    {
        /** Whether the file is still where this change left it, and got put back. */
        public boolean undo(UITextureBrowser browser);

        public boolean redo(UITextureBrowser browser);
    }

    private static class MoveChange implements Change
    {
        private final Link from;
        private Link to;

        public MoveChange(Link from, Link to)
        {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean undo(UITextureBrowser browser)
        {
            Link back = TextureFiles.move(this.to, TextureEntry.folderLink(this.from.parent()));

            if (back != null && this.to.equals(browser.getCurrent()))
            {
                browser.picker.selectCurrent(back);
            }

            return back != null;
        }

        @Override
        public boolean redo(UITextureBrowser browser)
        {
            Link moved = TextureFiles.move(this.from, TextureEntry.folderLink(this.to.parent()));

            if (moved != null)
            {
                this.to = moved;
            }

            return moved != null;
        }
    }

    private static class CopyChange implements Change
    {
        private final Link source;
        private Link created;

        public CopyChange(Link source, Link created)
        {
            this.source = source;
            this.created = created;
        }

        @Override
        public boolean undo(UITextureBrowser browser)
        {
            if (this.created.equals(browser.getCurrent()))
            {
                browser.picker.selectCurrent(this.source);
            }

            return TextureFiles.delete(this.created);
        }

        @Override
        public boolean redo(UITextureBrowser browser)
        {
            Link again = TextureFiles.copyInto(this.source, TextureEntry.folderLink(this.created.parent()));

            if (again != null)
            {
                this.created = again;
            }

            return again != null;
        }
    }

    /** Several textures made into one animated texture: undone by deleting it, redone by making it again. */
    private static class CombineChange implements Change
    {
        private final List<Link> frames;
        private final Link folder;
        private final int frametime;
        private Link created;

        public CombineChange(List<Link> frames, Link folder, int frametime, Link created)
        {
            this.frames = frames;
            this.folder = folder;
            this.frametime = frametime;
            this.created = created;
        }

        @Override
        public boolean undo(UITextureBrowser browser)
        {
            if (this.created.equals(browser.getCurrent()))
            {
                browser.picker.selectCurrent(null);
            }

            return TextureFiles.delete(this.created);
        }

        @Override
        public boolean redo(UITextureBrowser browser)
        {
            Link again = TextureFiles.combine(this.frames, this.folder, StringUtils.fileName(this.created.path), this.frametime);

            if (again != null)
            {
                this.created = again;
            }

            return again != null;
        }
    }

    /* Changes done and undone, most recent last */
    private final Deque<Change> undos = new ArrayDeque<>();
    private final Deque<Change> redos = new ArrayDeque<>();

    private int seenVersion = -1;
    private TextureEntry contextEntry;

    private CellAction hoveredAction;
    private int hoveredActionX;
    private int hoveredActionY;

    /* Type-to-pick, the way the old list did it */
    private final Timer lastTyped = new Timer(1000);
    private String typed = "";

    public UITextureBrowser(UITexturePicker picker)
    {
        this.picker = picker;

        this.bar = new UIStrip(BAR_HEIGHT);
        this.back = new UIIcon(Icons.ARROW_LEFT, (b) -> this.up());
        this.back.tooltip(UIKeys.TEXTURES_BROWSER_BACK, Direction.BOTTOM);
        this.treeToggle = new UIIcon(Icons.TREE, (b) ->
        {
            if (picker.multiLink != null)
            {
                picker.toggleMulti();
            }
        });
        this.treeToggle.tooltip(UIKeys.TEXTURES_BROWSER_TREE, Direction.BOTTOM);
        this.treeToggle.highlight(() -> picker.multiLink == null, Direction.BOTTOM);
        this.multiToggle = new UIIcon(Icons.GALLERY, (b) ->
        {
            if (picker.multiLink == null)
            {
                picker.toggleMulti();
            }
        });
        this.multiToggle.tooltip(UIKeys.TEXTURE_MULTISKIN, Direction.BOTTOM);
        this.multiToggle.highlight(() -> picker.multiLink != null, Direction.BOTTOM);
        this.search = new UITextbox(100, this::onSearch).placeholder(UIKeys.TEXTURES_BROWSER_SEARCH);
        this.sort = new UIIcon(Icons.LIST, (b) -> this.openSortMenu());
        this.sort.tooltip(UIKeys.TEXTURES_BROWSER_SORT, Direction.BOTTOM);
        this.sort.highlight(() -> this.getSort() != TextureSort.NAME, Direction.BOTTOM);
        this.everywhere = new UIIcon(Icons.GLOBE, (b) ->
        {
            this.searchEverywhere = !this.searchEverywhere;
            this.refresh();
        });
        this.everywhere.tooltip(UIKeys.TEXTURES_BROWSER_EVERYWHERE, Direction.BOTTOM);
        this.everywhere.highlight(() -> this.searchEverywhere, Direction.BOTTOM);
        this.newTexture = new UIIcon(Icons.MATERIAL, (b) -> this.promptNewTexture());
        this.newTexture.tooltip(UIKeys.TEXTURES_BROWSER_NEW_TEXTURE, Direction.BOTTOM);

        this.text = new UITextbox(1000, this::onPathTyped);
        this.text.delayedInput();
        this.text.setVisible(false);

        this.clearClipboard = new UIIcon(Icons.CLOSE, (b) -> this.setClipboard(Collections.emptyList(), false));
        this.clearClipboard.tooltip(UIKeys.TEXTURES_BROWSER_CLIPBOARD_CLEAR, Direction.TOP);
        this.clearClipboard.setVisible(false);

        this.crumbs = new UIBreadcrumbs(this);
        this.left = new UIElement();
        this.tree = new UIFolderTree(this);
        this.grid = new UITextureGrid(this);
        this.info = new UITextureInfoPanel(this);

        /* The multiskin column is the picker's; it only lives in the side panel here */
        picker.multiList.relative(this.left).xy(0, 0).w(1F).h(1F, -20);
        picker.buttons.relative(this.left).y(1F, -20).w(1F).h(20);
        this.tree.relative(this.left).xy(0, 0).w(1F).h(1F);
        this.left.add(this.tree, picker.multiList, picker.buttons);

        this.leftHandle.measure(this).onChange(this::layout)
            .range(MIN_SIDE, () -> (float) Math.min(MAX_SIDE, this.area.w - this.infoHandle.getPixels() - MIN_SIDE));
        this.infoHandle.measure(this).fromEnd().onChange(this::layout)
            .range(MIN_SIDE, () -> (float) Math.min(MAX_SIDE, this.area.w - this.leftHandle.getPixels() - MIN_SIDE));

        this.leftHandle.relative(this.left).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.infoHandle.relative(this.info).x(0).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.bar.relative(this).xy(0, 0).w(1F).h(BAR_HEIGHT);
        this.bar.add(this.back, this.treeToggle, this.multiToggle, this.search, this.everywhere, this.sort, this.newTexture, picker.close);
        /* The grid goes before the tree: it clears the drag's target as its frame begins, and
         * the tree reports a folder of its own while painting after it */
        this.add(this.bar, this.crumbs, this.text, this.grid, this.left, this.info, picker.editor, this.leftHandle, this.infoHandle, this.clearClipboard);
        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.layout();
        this.setMultiskin(false);
        this.grid.context(this::buildContextMenu);
        this.markContainer();

        this.navigate(new Link("", ""));
    }

    /** Place the parts for the current side panel widths: the info column reaches up beside the breadcrumbs. */
    private void layout()
    {
        int top = BAR_HEIGHT * 2;
        int leftWidth = this.leftHandle.getPixels();
        int infoWidth = this.infoHandle.getPixels();

        this.crumbs.relative(this).xy(0, BAR_HEIGHT).w(1F, -infoWidth).h(BAR_HEIGHT);
        this.text.relative(this).xy(0, BAR_HEIGHT).w(1F, -infoWidth).h(BAR_HEIGHT);
        this.info.relative(this).x(1F, -infoWidth).y(BAR_HEIGHT).w(infoWidth).h(1F, -BAR_HEIGHT - STATUS_HEIGHT);
        this.left.relative(this).xy(0, top).w(leftWidth).h(1F, -top - STATUS_HEIGHT);
        this.grid.relative(this).xy(leftWidth, top).w(1F, -leftWidth - infoWidth).h(1F, -top - STATUS_HEIGHT);
        this.picker.editor.relative(this).xy(leftWidth, BAR_HEIGHT).w(1F, -leftWidth).h(1F, -BAR_HEIGHT - STATUS_HEIGHT);
        this.clearClipboard.relative(this).x(1F, -STATUS_HEIGHT).y(1F, -STATUS_HEIGHT).wh(STATUS_HEIGHT, STATUS_HEIGHT);

        if (this.area.w > 0)
        {
            this.resize();
        }
    }

    /** Which the side panel shows: the multiskin's skins while one is edited, the folder tree otherwise. */
    public void setMultiskin(boolean multiskin)
    {
        this.tree.setVisible(!multiskin);
        this.picker.multiList.setVisible(multiskin);
        this.picker.buttons.setVisible(multiskin);
    }

    /** The multiskin editor takes the place of the grid, the breadcrumbs and the info column. */
    public void setEditing(boolean editing)
    {
        this.crumbs.setVisible(!editing);
        this.text.setVisible(false);
        this.grid.setVisible(!editing);
        this.info.setVisible(!editing);
    }

    /* Listing */

    public Link getPath()
    {
        return this.path;
    }

    public List<TextureEntry> getEntries()
    {
        return this.entries;
    }

    public boolean isSearching()
    {
        return !this.query.isEmpty();
    }

    public boolean isCurrentFolder(Link folder)
    {
        return !this.isSearching() && TextureEntry.folderLink(folder).equals(this.path);
    }

    @Override
    public boolean isCurrentTexture(Link link)
    {
        return link.equals(this.getCurrent());
    }

    /** A pinned texture was clicked in the tree: it becomes the chosen one, folder and all. */
    @Override
    public void openPinned(Link link)
    {
        this.picker.selectCurrent(link);
        this.setCurrent(link, true);
    }

    public Link getCurrent()
    {
        return this.picker.current;
    }

    @Override
    public ItemDrag<TextureEntry> getDrag()
    {
        return this.grid.drag;
    }

    public TextureSort getSort()
    {
        return TextureSort.byId(BBSSettings.textureSort.get());
    }

    /** The entry the keyboard acts on: the current texture, or the first entry. */
    public TextureEntry getCurrentEntry()
    {
        int index = this.indexOf(this.getCurrent());

        return index != -1 ? this.entries.get(index) : this.entries.isEmpty() ? null : this.entries.get(0);
    }

    private int indexOf(Link link)
    {
        if (link == null)
        {
            return -1;
        }

        for (int i = 0; i < this.entries.size(); i++)
        {
            if (this.entries.get(i).link().equals(link))
            {
                return i;
            }
        }

        return -1;
    }

    public void navigate(Link folder)
    {
        this.path = folder == null ? new Link("", "") : TextureEntry.folderLink(folder);
        this.grid.selection.clear();
        this.hidePathEditor();
        this.refresh();
        this.crumbs.setPath(this.path);
        this.tree.reveal(this.path);
        this.grid.scroll.setScroll(0);
        this.info.set(this.path.source.isEmpty() ? null : this.path);
    }

    /** One step up the breadcrumbs — the ".." of the old file list. */
    private void up()
    {
        if (this.path.source.isEmpty())
        {
            return;
        }

        this.navigate(this.path.path.isEmpty() ? new Link("", "") : this.path.parent());
    }

    /* Undo of moves */

    private void record(Change change)
    {
        this.undos.addLast(change);
        this.redos.clear();
    }

    /** A copy just made (or null when it failed) goes on the undo stack; tells whether it was made. */
    private boolean copied(Link source, Link created)
    {
        if (created != null)
        {
            this.record(new CopyChange(source, created));
        }

        return created != null;
    }

    /** Say what just happened at the bottom of the screen — "Copied: 3" — unless nothing did. */
    private void notify(IKey what, int count)
    {
        if (count > 0 && this.getContext() != null)
        {
            this.getContext().notifyInfo(what.format(String.valueOf(count)));
        }
    }

    private void undo()
    {
        Change change = this.undos.pollLast();

        if (change != null && change.undo(this))
        {
            this.redos.addLast(change);
            this.refresh();
            this.tree.refresh();
        }
    }

    private void redo()
    {
        Change change = this.redos.pollLast();

        if (change != null && change.redo(this))
        {
            this.undos.addLast(change);
            this.refresh();
            this.tree.refresh();
        }
    }

    /** Relist the folder (or rerun the search) from the disk as it is now. */
    public void refresh()
    {
        this.entries.clear();
        this.entries.addAll(this.isSearching() ? this.searchEntries() : this.folderEntries());
        this.grid.selection.retain((entry) -> this.indexOf(entry.link()) != -1);
        this.grid.relayout();
        this.newTexture.setEnabled(TextureFiles.isFolder(this.path));
        this.seenVersion = BBSResources.getAssetsVersion();
    }

    private List<TextureEntry> folderEntries()
    {
        List<TextureEntry> list = new ArrayList<>();

        if (this.path.source.isEmpty())
        {
            for (String source : BBSMod.getProvider().getSourceKeys())
            {
                list.add(TextureEntry.source(source));
            }
        }
        else
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(this.path, false))
            {
                if (link.path.endsWith("/") || link.path.endsWith(".png"))
                {
                    list.add(TextureEntry.of(link));
                }
            }
        }

        list.sort(this.getSort()::compare);

        return list;
    }

    private List<TextureEntry> searchEntries()
    {
        List<TextureEntry> list = new ArrayList<>();
        List<Link> roots = new ArrayList<>();
        String needle = this.query.toLowerCase();

        if (this.searchEverywhere || this.path.source.isEmpty())
        {
            for (String source : BBSMod.getProvider().getSourceKeys())
            {
                roots.add(new Link(source, ""));
            }
        }
        else
        {
            roots.add(this.path);
        }

        for (Link root : roots)
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(root, true))
            {
                if (!link.path.endsWith(".png") || link.path.contains("textures/banners/"))
                {
                    continue;
                }

                if (StringUtils.fileName(link.path).toLowerCase().contains(needle))
                {
                    TextureEntry hit = TextureEntry.hit(link, root);

                    list.add(roots.size() > 1 ? hit.withCaption(link.toString()) : hit);

                    if (list.size() >= SEARCH_CAP)
                    {
                        list.sort(this.getSort()::compare);

                        return list;
                    }
                }
            }
        }

        list.sort(this.getSort()::compare);

        return list;
    }

    private void onSearch(String query)
    {
        this.query = query == null ? "" : query.trim();
        this.grid.selection.clear();
        this.refresh();
        this.grid.scroll.setScroll(0);
    }

    private void openSortMenu()
    {
        UIChoiceMenu.of(TextureSort.values())
            .current(this.getSort())
            .icon((sort) -> sort.icon)
            .label((sort) -> sort.label)
            .open(this.getContext(), (sort) ->
            {
                BBSSettings.textureSort.set(sort.id);
                this.refresh();
            });
    }

    /* Path by hand */

    public void editPath()
    {
        Link current = this.getCurrent();

        this.text.setText(current != null ? current.toString() : this.path.toString());
        this.text.setVisible(true);
        this.crumbs.setVisible(false);
        this.getContext().focus(this.text);
    }

    private void hidePathEditor()
    {
        this.text.setVisible(false);
        this.crumbs.setVisible(this.grid.isVisible());
    }

    private void onPathTyped(String typed)
    {
        String value = typed == null ? "" : typed.trim();

        if (value.isEmpty())
        {
            this.hidePathEditor();

            return;
        }

        Link link = LinkUtils.create(value);

        if (link == null)
        {
            return;
        }

        if (TextureFiles.isFolder(link) || link.path.isEmpty())
        {
            /* Entering a folder while the path is still being typed keeps the field open */
            this.navigate(link);
            this.text.setVisible(true);
            this.crumbs.setVisible(false);
        }
        else
        {
            this.picker.selectCurrent(link);
            this.setCurrent(link, true);
            this.hidePathEditor();
        }
    }

    /* Selection */

    /** Show a texture as the chosen one: enter its folder when needed, and bring it into view. */
    public void setCurrent(Link link, boolean scroll)
    {
        /* A multiskin ("multi:…") isn't a file anywhere: nothing to enter, nothing to show */
        if (link != null && !BBSMod.getProvider().getSourceKeys().contains(link.source))
        {
            return;
        }

        if (link != null && !this.isSearching())
        {
            Link parent = TextureEntry.folderLink(link.parent());

            if (!parent.equals(this.path))
            {
                this.navigate(parent);
            }
        }

        this.info.set(link == null ? (this.path.source.isEmpty() ? null : this.path) : link);

        if (scroll)
        {
            this.grid.scrollTo(this.indexOf(link));
        }
    }

    /** The picked entries as links — what the file operations act on. */
    private List<Link> pickedLinks()
    {
        List<Link> links = new ArrayList<>();

        for (TextureEntry entry : this.grid.selection.getItems())
        {
            links.add(entry.link());
        }

        return links;
    }

    /**
     * The picked textures in the order they are shown — the frames a combined animation is made
     * of. Folders are left out: only pictures become frames.
     */
    private List<Link> pickedFrames()
    {
        List<Link> links = new ArrayList<>();

        for (TextureEntry entry : this.entries)
        {
            if (!entry.folder() && this.grid.selection.contains(entry))
            {
                links.add(entry.link());
            }
        }

        return links;
    }

    /** How many of the picked entries are textures; asked every frame, so it walks the pick rather than the listing. */
    private int pickedFrameCount()
    {
        int count = 0;

        for (TextureEntry entry : this.grid.selection.getItems())
        {
            if (!entry.folder())
            {
                count += 1;
            }
        }

        return count;
    }

    private boolean isPicked(Link link)
    {
        return this.grid.selection.contains(TextureEntry.of(link));
    }

    /** Whether a link is one of several picked — the state in which an action on it acts on the whole pick. */
    private boolean isGrouped(Link link)
    {
        return this.grid.selection.isGroup() && this.isPicked(link);
    }

    /* Clipboard */

    /** What Ctrl+C / Ctrl+X take: the pick, or the current texture when nothing is picked. */
    private List<Link> subjects()
    {
        if (!this.grid.selection.isEmpty())
        {
            return this.pickedLinks();
        }

        Link current = this.getCurrent();

        return current == null || current.path.endsWith("/") ? Collections.emptyList() : Collections.singletonList(current);
    }

    /** The subjects that live on disk — the ones a rename, a move or a deletion can touch. */
    private List<Link> modifiableSubjects()
    {
        return this.modifiable(this.subjects());
    }

    private List<Link> modifiable(List<Link> subjects)
    {
        List<Link> modifiable = new ArrayList<>();

        for (Link link : subjects)
        {
            if (TextureFiles.canModify(link))
            {
                modifiable.add(link);
            }
        }

        if (modifiable.isEmpty() && !subjects.isEmpty())
        {
            this.getContext().notifyError(UIKeys.TEXTURES_BROWSER_READ_ONLY);
        }

        return modifiable;
    }

    /** Whether the drag in progress copies rather than moves: Ctrl is held, or the files can't be moved anyway. */
    private boolean isCopyDrag()
    {
        if (this.grid.drag.isCopy())
        {
            return true;
        }

        for (TextureEntry entry : this.grid.drag.getItems())
        {
            if (TextureFiles.canModify(entry.link()))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether dropping what's carried into {@code folder} would do anything: it's on disk and,
     * for a move, isn't where the files already are. A copy is fine into their own folder —
     * that's how a duplicate is made by hand.
     */
    private boolean accepts(Link folder, boolean copy)
    {
        if (folder == null || !TextureFiles.isFolder(folder))
        {
            return false;
        }

        for (TextureEntry entry : this.grid.drag.getItems())
        {
            Link link = entry.link();

            if (link.equals(folder) || TextureEntry.folderLink(link).equals(TextureEntry.folderLink(folder)))
            {
                continue;
            }

            if (copy || !TextureEntry.folderLink(link.parent()).equals(TextureEntry.folderLink(folder)))
            {
                return true;
            }
        }

        return false;
    }

    private void copyToClipboard(boolean cut)
    {
        List<Link> subjects = this.subjects();

        if (subjects.isEmpty())
        {
            return;
        }

        this.setClipboard(subjects, cut);
    }

    private void setClipboard(List<Link> links, boolean cut)
    {
        this.clipboard.clear();
        this.clipboard.addAll(links);
        this.cut = cut && !links.isEmpty();
        this.clearClipboard.setVisible(!links.isEmpty());
    }

    /** Put the clipboard down in the folder on show: copies, or moves after a cut (once). */
    private boolean paste()
    {
        if (this.clipboard.isEmpty() || !TextureFiles.isFolder(this.path) || this.isSearching())
        {
            return false;
        }

        Link current = this.getCurrent();
        int pasted = 0;

        for (Link link : this.clipboard)
        {
            /* A cut of something read-only can only ever be a copy */
            if (this.cut && TextureFiles.canModify(link))
            {
                Link moved = TextureFiles.move(link, this.path);

                if (moved != null)
                {
                    this.record(new MoveChange(link, moved));
                    pasted += 1;

                    if (link.equals(current))
                    {
                        this.picker.selectCurrent(moved);
                    }
                }
            }
            else if (this.copied(link, TextureFiles.copyInto(link, this.path)))
            {
                pasted += 1;
            }
        }

        if (this.cut)
        {
            this.setClipboard(Collections.emptyList(), false);
        }

        this.notify(UIKeys.TEXTURES_BROWSER_NOTIFY_PASTED, pasted);
        this.refresh();
        this.tree.refresh();

        return true;
    }

    private void promptImport()
    {
        File into = TextureFiles.file(this.path);

        if (into == null || !into.isDirectory())
        {
            return;
        }

        UIFileDialogs.pickFile(UIKeys.TEXTURES_BROWSER_IMPORT_TITLE, into, new String[] {"*.png"}, UIKeys.TEXTURES_BROWSER_IMPORT_FILTER, (file) ->
        {
            if (file == null || !file.isFile())
            {
                return;
            }

            try
            {
                java.nio.file.Files.copy(file.toPath(), new File(into, file.getName()).toPath());
                BBSResources.markAssetsChanged();
            }
            catch (java.io.IOException e)
            {
                e.printStackTrace();
            }
        });
    }

    public void setContextEntry(TextureEntry entry)
    {
        this.contextEntry = entry;
    }

    public void setHoveredAction(CellAction action, int x, int y)
    {
        this.hoveredAction = action;
        this.hoveredActionX = x;
        this.hoveredActionY = y;
    }

    public CellAction[] getActions(TextureEntry entry)
    {
        if (entry.folder())
        {
            return CellAction.none();
        }

        boolean pinned = TexturePins.isPinned(entry.link());

        if (TextureFiles.canModify(entry.link()))
        {
            return pinned ? UNPIN_MODIFIABLE : PIN_MODIFIABLE;
        }

        return pinned ? UNPIN_READ_ONLY : PIN_READ_ONLY;
    }

    public void runAction(TextureEntry entry, CellAction action)
    {
        switch (action)
        {
            case EDIT -> this.openInEditor(entry.link());
            case DUPLICATE -> this.duplicate(this.group(entry.link()));
            case REMOVE -> this.confirmDelete(this.group(entry.link()));
            /* The button says what it does to the cell it sits on, so it acts on that one
             * alone — a group goes through the context menu, where the label counts them */
            case PIN, UNPIN -> this.togglePins(Collections.singletonList(entry.link()));
        }
    }

    /** Make a {@code _copy} of each beside the original — those that live on disk. */
    private void duplicate(List<Link> links)
    {
        int made = 0;

        for (Link link : links)
        {
            if (this.copied(link, TextureFiles.duplicate(link)))
            {
                made += 1;
            }
        }

        this.notify(UIKeys.TEXTURES_BROWSER_NOTIFY_DUPLICATED, made);
        this.refresh();
    }

    /** The links an action on {@code link} touches: the whole pick when it's one of several picked. */
    private List<Link> group(Link link)
    {
        return this.isGrouped(link) ? this.pickedLinks() : Collections.singletonList(link);
    }

    /** Delete went down over the pick: the picked files that live on disk go, after asking. */
    public void deleteEntries(List<TextureEntry> entries)
    {
        List<Link> links = new ArrayList<>();

        for (TextureEntry entry : entries)
        {
            links.add(entry.link());
        }

        this.confirmDelete(this.modifiable(links));
    }

    /** Put the links in or take them out of the pins, and let the tree show what changed. */
    private void togglePins(List<Link> links)
    {
        TexturePins.toggle(links);
        this.tree.refresh();
    }

    public void openInEditor(Link link)
    {
        if (link != null && !link.path.endsWith("/") && !link.path.isEmpty())
        {
            this.picker.openTexture(link);
        }
    }

    public void openFolder()
    {
        File target = TextureFiles.file(this.path);

        if (target != null && target.isDirectory())
        {
            UIUtils.openFolder(target);
        }
    }

    /* Drop */

    /** The carried entries were let go over a folder — in the grid or in the tree. */
    public void drop(Link target, List<TextureEntry> entries)
    {
        boolean copy = this.isCopyDrag();

        if (!this.accepts(target, copy))
        {
            return;
        }

        Link current = this.getCurrent();
        int copies = 0;
        int moves = 0;

        for (TextureEntry entry : entries)
        {
            Link link = entry.link();

            if (copy || !TextureFiles.canModify(link))
            {
                if (this.copied(link, TextureFiles.copyInto(link, target)))
                {
                    copies += 1;
                }

                continue;
            }

            Link moved = TextureFiles.move(link, target);

            if (moved == null)
            {
                continue;
            }

            this.record(new MoveChange(link, moved));
            moves += 1;

            if (link.equals(current))
            {
                this.picker.selectCurrent(moved);
            }
        }

        this.notify(UIKeys.TEXTURES_BROWSER_NOTIFY_COPIED, copies);
        this.notify(UIKeys.TEXTURES_BROWSER_NOTIFY_MOVED, moves);
        this.grid.selection.clear();
        this.refresh();
        this.tree.refresh();
    }

    private void promptNewTexture()
    {
        if (!TextureFiles.isFolder(this.path))
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UINewTextureOverlayPanel((name, width, height) ->
        {
            Link created = TextureFiles.create(this.path, name, width, height);

            if (created != null)
            {
                this.refresh();
                this.picker.selectCurrent(created);
                this.picker.openTexture(created);
            }
        }));
    }

    /* Combining several textures into one animated texture */

    /**
     * Where a combined animation is written: the folder on show, or — when that isn't one on
     * disk, as while searching from the root — the folder the first frame lives in.
     */
    private Link combineFolder(List<Link> frames)
    {
        if (TextureFiles.isFolder(this.path))
        {
            return this.path;
        }

        Link parent = frames.isEmpty() ? null : TextureEntry.folderLink(frames.get(0).parent());

        return TextureFiles.isFolder(parent) ? parent : null;
    }

    /**
     * What the animation is called by default: what the frames' names have in common, without
     * whatever separated the numbering ("fire_1", "fire_2" → "fire"), and free in the folder.
     * When they have nothing in common, the first frame's name stands in.
     */
    private static String defaultAnimationName(List<Link> frames, Link folder)
    {
        String first = StringUtils.removeExtension(StringUtils.fileName(frames.get(0).path));
        String prefix = first;

        for (Link frame : frames)
        {
            String name = StringUtils.removeExtension(StringUtils.fileName(frame.path));
            int i = 0;

            while (i < prefix.length() && i < name.length() && prefix.charAt(i) == name.charAt(i))
            {
                i += 1;
            }

            prefix = prefix.substring(0, i);
        }

        while (!prefix.isEmpty() && !Character.isLetterOrDigit(prefix.charAt(prefix.length() - 1)))
        {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        return TextureFiles.freeName(folder, prefix.isEmpty() ? first : prefix);
    }

    /** Ask what the animation the picked textures are about to become is called, then make it. */
    private void promptCombine(List<Link> frames)
    {
        if (frames.size() < TextureFiles.MIN_COMBINE_FRAMES)
        {
            return;
        }

        Link folder = this.combineFolder(frames);

        /* Both the folder on show and the frames' own are inside the mod: there is nothing to write to */
        if (folder == null)
        {
            this.getContext().notifyError(UIKeys.TEXTURES_BROWSER_COMBINE_NO_FOLDER);

            return;
        }

        IKey message = UIKeys.TEXTURES_BROWSER_COMBINE_DESCRIPTION.format(
            String.valueOf(frames.size()),
            StringUtils.fileName(frames.get(0).path),
            StringUtils.fileName(frames.get(frames.size() - 1).path)
        );

        UIOverlay.addOverlay(this.getContext(), new UICombineTexturesOverlayPanel(message, defaultAnimationName(frames, folder), (name, frametime) ->
        {
            this.combine(frames, folder, name, frametime);
        }));
    }

    /** Stack the frames into the folder, then show what came out of it: picked, listed and open in the editor. */
    private void combine(List<Link> frames, Link folder, String name, int frametime)
    {
        Link created = TextureFiles.combine(frames, folder, name, frametime);

        if (created == null)
        {
            this.getContext().notifyError(UIKeys.TEXTURES_BROWSER_COMBINE_FAILED);

            return;
        }

        this.record(new CombineChange(frames, folder, frametime, created));
        this.notify(UIKeys.TEXTURES_BROWSER_NOTIFY_COMBINED, frames.size());
        this.grid.selection.clear();
        this.refresh();
        this.tree.refresh();
        this.picker.selectCurrent(created);
        this.picker.openTexture(created);
    }

    /* File operations */

    private void promptNewFolder()
    {
        if (!TextureFiles.isFolder(this.path))
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.TEXTURES_BROWSER_NEW_FOLDER_TITLE, UIKeys.TEXTURES_BROWSER_NEW_FOLDER_DESCRIPTION, (name) ->
        {
            if (TextureFiles.newFolder(this.path, name) != null)
            {
                this.refresh();
                this.tree.refresh();
            }
        });

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void promptRename(Link link)
    {
        String name = StringUtils.fileName(link.path).replace("/", "");
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.TEXTURES_BROWSER_RENAME_TITLE, UIKeys.TEXTURES_BROWSER_RENAME_DESCRIPTION, (newName) ->
        {
            Link renamed = TextureFiles.rename(link, newName);

            if (renamed != null)
            {
                if (link.equals(this.getCurrent()))
                {
                    this.picker.selectCurrent(renamed);
                }

                this.refresh();
                this.tree.refresh();
            }
        });

        panel.text.filename();
        panel.text.setText(name);
        panel.text.textbox.selectFilename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void confirmDelete(List<Link> links)
    {
        if (links.isEmpty())
        {
            return;
        }

        String what = links.size() == 1 ? StringUtils.fileName(links.get(0).path) : String.valueOf(links.size());
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.TEXTURES_BROWSER_DELETE_TITLE, UIKeys.TEXTURES_BROWSER_DELETE_DESCRIPTION.format(what), (confirm) ->
        {
            if (!confirm)
            {
                return;
            }

            Link current = this.getCurrent();

            for (Link link : links)
            {
                if (TextureFiles.delete(link) && link.equals(current))
                {
                    this.picker.selectCurrent(null);
                }
            }

            this.grid.selection.clear();
            this.refresh();
            this.tree.refresh();
        });

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    /**
     * The menu comes in two halves: what the click landed on, then where the browser stands.
     * Inside each half the order is always the same — open it, mark it, make another of it,
     * rename it, hand it to the system, and destroy it last, well away from the rest.
     */
    private void buildContextMenu(ContextMenuManager menu)
    {
        this.buildEntryActions(menu, this.contextEntry);
        this.buildFolderActions(menu, this.contextEntry);
    }

    /** What can be done to the entry pressed; nothing at all when the press was on empty space. */
    private void buildEntryActions(ContextMenuManager menu, TextureEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        Link link = entry.link();
        boolean texture = !entry.folder();
        boolean group = this.isGrouped(link);
        boolean modifiable = TextureFiles.canModify(link);

        if (texture)
        {
            menu.action(Icons.EDIT, UIKeys.GENERAL_EDIT, () -> this.openInEditor(link));
        }

        if (TexturePins.canPin(link))
        {
            List<Link> links = this.group(link);
            boolean pinned = TexturePins.arePinned(links);
            IKey label = group
                ? (pinned ? UIKeys.TEXTURES_BROWSER_UNPIN_SELECTED : UIKeys.TEXTURES_BROWSER_PIN_SELECTED).format(String.valueOf(links.size()))
                : (pinned ? UIKeys.TEXTURES_BROWSER_UNPIN : UIKeys.TEXTURES_BROWSER_PIN);

            menu.action(Icons.BOOKMARK, label, () -> this.togglePins(links));
        }

        if (modifiable && !group)
        {
            if (texture)
            {
                menu.action(Icons.DUPE, UIKeys.FORMS_CATEGORIES_CONTEXT_DUPLICATE_FORM, () -> this.duplicate(Collections.singletonList(link)));
            }

            menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.promptRename(link));
        }

        if (texture)
        {
            menu.action(Icons.COPY, UIKeys.TEXTURES_COPY, () -> Window.setClipboard(link.toString()));

            File file = TextureFiles.file(link);
            List<Link> frames = group ? this.pickedFrames() : Collections.emptyList();

            /* Several pictures picked: they become the frames of one animated texture */
            if (frames.size() >= TextureFiles.MIN_COMBINE_FRAMES)
            {
                menu.action(Icons.FILM, UIKeys.TEXTURES_BROWSER_COMBINE.format(String.valueOf(frames.size())), () -> this.promptCombine(frames));
            }
            /* A texture on disk that isn't animated yet: into the editor with the animation on */
            else if (file != null && file.isFile() && !TextureAnimation.file(file).isFile())
            {
                menu.action(Icons.FILM, UIKeys.TEXTURES_MAKE_ANIMATED, () -> this.picker.openTextureAnimated(link));
            }
        }

        if (modifiable)
        {
            List<Link> links = this.group(link);

            menu.icon(MenuVerb.REMOVE, () -> this.confirmDelete(links)).label(group ? UIKeys.TEXTURES_BROWSER_DELETE_SELECTED.format(String.valueOf(links.size())) : UIKeys.GENERAL_REMOVE);
        }
    }

    /** What can be done where the browser stands, pressed on something or not. */
    private void buildFolderActions(ContextMenuManager menu, TextureEntry entry)
    {
        if (TextureFiles.isFolder(this.path))
        {
            /* Pressed on a cell, the pin entry above is about that cell — this one would only
             * ask which of the two is meant */
            if (entry == null && TexturePins.canPin(this.path))
            {
                menu.action(Icons.BOOKMARK, TexturePins.isPinned(this.path) ? UIKeys.TEXTURES_BROWSER_UNPIN : UIKeys.TEXTURES_BROWSER_PIN_FOLDER, () -> this.togglePins(Collections.singletonList(this.path)));
            }

            if (!this.clipboard.isEmpty())
            {
                menu.action(Icons.PASTE, UIKeys.GENERAL_PASTE, this::paste);
            }

            menu.icon(MenuVerb.ADD, this::promptNewTexture).label(UIKeys.TEXTURES_BROWSER_NEW_TEXTURE);

            menu.action(Icons.ADD, UIKeys.TEXTURES_BROWSER_NEW_FOLDER, this::promptNewFolder);
            menu.action(Icons.UPLOAD, UIKeys.TEXTURES_BROWSER_IMPORT, this::promptImport);
            menu.action(Icons.FOLDER, UIKeys.TEXTURE_OPEN_FOLDER, this::openFolder);
        }

        if (Link.isAssets(this.path))
        {
            menu.action(Icons.DOWNLOAD, UIKeys.TEXTURES_DOWNLOAD, () -> this.picker.download(""));
        }
    }

    /* Keyboard */

    public boolean handleKey(UIContext context)
    {
        if (context.isPressed(Keys.COPY))
        {
            this.copyToClipboard(false);

            return !this.clipboard.isEmpty();
        }
        else if (context.isPressed(Keys.CUT))
        {
            this.copyToClipboard(true);

            return !this.clipboard.isEmpty();
        }
        else if (context.isPressed(Keys.PASTE))
        {
            /* With nothing taken, Ctrl+V falls through to the picker, which downloads a URL from the clipboard */
            return this.paste();
        }
        else if (context.isPressed(Keys.DELETE))
        {
            this.confirmDelete(this.modifiableSubjects());

            return true;
        }
        else if (context.isPressed(GLFW.GLFW_KEY_A) && Window.isCtrlPressed())
        {
            this.grid.selectAll();

            return true;
        }
        else if (context.isPressed(GLFW.GLFW_KEY_D) && Window.isCtrlPressed())
        {
            this.duplicate(this.subjects());

            return true;
        }
        else if (context.isPressed(GLFW.GLFW_KEY_F2))
        {
            List<Link> subjects = this.modifiableSubjects();

            if (subjects.size() == 1)
            {
                this.promptRename(subjects.get(0));
            }

            return true;
        }
        else if (context.isPressed(GLFW.GLFW_KEY_ENTER))
        {
            TextureEntry entry = this.getCurrentEntry();

            if (entry != null)
            {
                if (entry.folder())
                {
                    this.navigate(entry.link());
                }
                else
                {
                    this.picker.selectCurrent(entry.link());
                }
            }

            this.typed = "";

            return true;
        }

        int perRow = this.grid.getLayout().getPerRow();

        if (context.isHeld(GLFW.GLFW_KEY_UP))
        {
            return this.moveCurrent(-perRow, Window.isShiftPressed());
        }
        else if (context.isHeld(GLFW.GLFW_KEY_DOWN))
        {
            return this.moveCurrent(perRow, Window.isShiftPressed());
        }
        else if (context.isHeld(GLFW.GLFW_KEY_LEFT))
        {
            return this.moveCurrent(-1, false);
        }
        else if (context.isHeld(GLFW.GLFW_KEY_RIGHT))
        {
            return this.moveCurrent(1, false);
        }
        else if (context.isPressed(GLFW.GLFW_KEY_BACKSPACE))
        {
            this.up();

            return true;
        }

        return false;
    }

    private boolean moveCurrent(int delta, boolean end)
    {
        if (this.entries.isEmpty())
        {
            return false;
        }

        int index = this.indexOf(this.getCurrent());
        int length = this.entries.size();

        index = index == -1 ? (delta > 0 ? 0 : length - 1) : index + delta;

        if (end)
        {
            index = delta > 0 ? length - 1 : 0;
        }

        index = ((index % length) + length) % length;

        this.pick(this.entries.get(index));
        this.typed = "";

        return true;
    }

    private void pick(TextureEntry entry)
    {
        this.grid.selection.set(entry, null);
        this.show(entry);
        this.grid.scrollTo(this.indexOf(entry.link()));
    }

    /** The keyboard stands on an entry: a texture becomes the current one, a folder shows its facts. */
    public void show(TextureEntry entry)
    {
        if (entry.folder())
        {
            this.info.set(entry.link());
        }
        else
        {
            this.picker.selectCurrent(entry.link());
        }
    }

    public boolean pickByTyping(char inputChar)
    {
        if (this.lastTyped.checkReset())
        {
            this.typed = "";
        }

        this.typed += Character.toString(inputChar);
        this.lastTyped.mark();

        for (TextureEntry entry : this.entries)
        {
            if (entry.name().startsWith(this.typed))
            {
                this.pick(entry);

                return true;
            }
        }

        return true;
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        if (this.seenVersion != BBSResources.getAssetsVersion())
        {
            this.refresh();
            this.tree.refresh();
        }

        /* The typed path gives way to the breadcrumbs as soon as it loses focus (Escape, a click elsewhere) */
        if (this.text.isVisible() && !this.text.isFocused())
        {
            this.hidePathEditor();
        }

        this.hoveredAction = null;
        this.back.setEnabled(!this.path.source.isEmpty());

        int strip = BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + BAR_HEIGHT, strip);

        if (this.crumbs.isVisible() || this.text.isVisible())
        {
            context.batcher.box(this.crumbs.area.x, this.crumbs.area.y, this.crumbs.area.ex(), this.crumbs.area.ey(), strip);
        }

        context.batcher.box(this.left.area.x, this.left.area.y, this.left.area.ex(), this.left.area.ey(), strip);
        this.renderStatus(context, strip);

        super.render(context);

        if (!this.lastTyped.check() && this.lastTyped.enabled)
        {
            int x = this.grid.area.x + 6;
            int y = this.grid.area.y + 6;

            context.batcher.textCard(this.typed, x + 2, y + 2, Colors.WHITE, Colors.A50 | BBSSettings.primaryColor.get(), 2);
        }

        if (this.hoveredAction != null && !this.grid.drag.isActive())
        {
            CellActionBar.renderLabel(context, this.hoveredAction, this.hoveredActionX, this.hoveredActionY);
        }

        if (this.grid.drag.isActive())
        {
            this.renderGhost(context);
        }
    }

    /**
     * The line along the bottom. Left: how many textures the folder holds and how many are
     * picked. Right: what's on the clipboard — it stays there until pasted or cleared, so the
     * user always knows something is waiting to be put down.
     */
    private void renderStatus(UIContext context, int strip)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int y = this.area.ey() - STATUS_HEIGHT;
        int textY = y + (STATUS_HEIGHT - font.getHeight()) / 2 + 1;
        int x = this.area.x + 6;

        batcher.box(this.area.x, y, this.area.ex(), this.area.ey(), strip);

        int files = 0;

        for (TextureEntry entry : this.entries)
        {
            if (!entry.folder())
            {
                files += 1;
            }
        }

        String count = UIKeys.TEXTURES_BROWSER_STATUS_FILES.format(String.valueOf(files)).get();

        batcher.text(count, x, textY, Colors.GRAY);
        x += font.getWidth(count) + 12;

        if (!this.grid.selection.isEmpty())
        {
            batcher.text(UIKeys.TEXTURES_BROWSER_STATUS_SELECTED.format(String.valueOf(this.grid.selection.size())).get(), x, textY, Colors.LIGHTER_GRAY);
        }

        if (!this.clipboard.isEmpty())
        {
            IKey key = this.cut ? UIKeys.TEXTURES_BROWSER_STATUS_CUT : UIKeys.TEXTURES_BROWSER_STATUS_COPIED;
            String label = key.format(String.valueOf(this.clipboard.size())).get();

            batcher.textShadow(label, this.area.ex() - STATUS_HEIGHT - 6 - font.getWidth(label), textY, Colors.WHITE);
        }
    }

    /**
     * What's being carried, beside the cursor: a stack of the textures and their count. Drawn
     * here rather than by the grid, so it rides over the folder tree and the info column too.
     */
    private void renderGhost(UIContext context)
    {
        boolean copy = this.isCopyDrag();
        boolean landing = this.accepts(this.grid.drag.getTarget() instanceof Link folder ? folder : null, copy);
        int size = Math.min(this.grid.getCellSize(), UITextureGrid.GHOST_SIZE);
        TextureEntry front = this.grid.drag.getItems().get(0);
        CellState plain = new CellState();

        this.grid.drag.renderGhost(context, size, size, landing, (ctx, x, y, w, h) ->
        {
            TextureCellRenderer.render(ctx, front, x, y, w, h, plain, CellAction.none());
        });

        if (copy)
        {
            DragGhost.label(context, UIKeys.TEXTURES_BROWSER_COPYING.get(), context.mouseX, context.mouseY, size, landing);
        }
    }
}
