package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.cells.CellAction;
import mchorse.bbs_mod.ui.utils.cells.CellActionBar;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
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
public class UITextureBrowser extends UIElement
{
    public static final int BAR_HEIGHT = 20;
    private static final int SEARCH_CAP = 400;
    private static final int MIN_SIDE = 100;
    private static final int MAX_SIDE = 400;

    /* Side panel widths, dragged by the user and kept for the session like the form editor's tree */
    private static int leftWidth = 140;
    private static int infoWidth = 150;

    public final UITexturePicker picker;

    public UIElement bar;
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

    public final TextureSelection selection = new TextureSelection();
    public final TextureDrag drag = new TextureDrag();

    private Link path = new Link("", "");
    private final List<TextureEntry> entries = new ArrayList<>();
    private String query = "";
    private boolean searchEverywhere;

    /** A move that Ctrl+Z reverses: where a file was, where it went. */
    private record Move(Link from, Link to)
    {}

    /* Moves done and undone, most recent last */
    private final Deque<Move> undos = new ArrayDeque<>();
    private final Deque<Move> redos = new ArrayDeque<>();

    private int seenVersion = -1;
    private TextureEntry contextEntry;

    /* A folder pressed but not yet released: a release without a drag enters it */
    private Link pendingFolder;

    private CellAction hoveredAction;
    private int hoveredActionX;
    private int hoveredActionY;

    /* Type-to-pick, the way the old list did it */
    private final Timer lastTyped = new Timer(1000);
    private String typed = "";

    public UITextureBrowser(UITexturePicker picker)
    {
        this.picker = picker;

        this.bar = new UIElement();
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

        UIDraggable leftHandle = new UIDraggable((context) ->
        {
            leftWidth = MathUtils.clamp(context.mouseX - this.area.x, MIN_SIDE, Math.min(MAX_SIDE, this.area.w - infoWidth - MIN_SIDE));
            this.layout();
        });
        UIDraggable infoHandle = new UIDraggable((context) ->
        {
            infoWidth = MathUtils.clamp(this.area.ex() - context.mouseX, MIN_SIDE, Math.min(MAX_SIDE, this.area.w - leftWidth - MIN_SIDE));
            this.layout();
        });

        leftHandle.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        infoHandle.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        leftHandle.relative(this.left).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        infoHandle.relative(this.info).x(0).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.bar.relative(this).xy(0, 0).w(1F).h(BAR_HEIGHT).row(0).height(BAR_HEIGHT);
        this.addToBar(this.back, this.treeToggle, this.multiToggle, this.search, this.everywhere, this.sort, this.newTexture, picker.close);
        this.add(this.bar, this.crumbs, this.text, this.left, this.grid, this.info, picker.editor, leftHandle, infoHandle);
        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.layout();
        this.setMultiskin(false);
        this.grid.context(this::buildContextMenu);
        this.markContainer();

        this.navigate(new Link("", ""));
    }

    /** Put controls on the top strip; the row leaves self-sized children alone, so their height is set here. */
    public void addToBar(UIElement... elements)
    {
        for (UIElement element : elements)
        {
            element.h(BAR_HEIGHT);
        }

        this.bar.add(elements);
    }

    /** Place the parts for the current side panel widths: the info column reaches up beside the breadcrumbs. */
    private void layout()
    {
        int top = BAR_HEIGHT * 2;

        this.crumbs.relative(this).xy(0, BAR_HEIGHT).w(1F, -infoWidth).h(BAR_HEIGHT);
        this.text.relative(this).xy(0, BAR_HEIGHT).w(1F, -infoWidth).h(BAR_HEIGHT);
        this.info.relative(this).x(1F, -infoWidth).y(BAR_HEIGHT).w(infoWidth).h(1F, -BAR_HEIGHT);
        this.left.relative(this).xy(0, top).w(leftWidth).h(1F, -top);
        this.grid.relative(this).xy(leftWidth, top).w(1F, -leftWidth - infoWidth).h(1F, -top);
        this.picker.editor.relative(this).xy(leftWidth, BAR_HEIGHT).w(1F, -leftWidth).h(1F, -BAR_HEIGHT);

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

    public Link getCurrent()
    {
        return this.picker.current;
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
        this.selection.clear();
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

    private void undo()
    {
        Move move = this.undos.pollLast();

        if (move != null && this.reverse(move) != null)
        {
            this.redos.addLast(move);
        }
    }

    private void redo()
    {
        Move move = this.redos.pollLast();

        if (move != null && this.reverse(new Move(move.to(), move.from())) != null)
        {
            this.undos.addLast(move);
        }
    }

    /** Put a moved file back where it was; null when the file isn't there any more. */
    private Link reverse(Move move)
    {
        Link back = TextureFiles.move(move.to(), TextureEntry.folderLink(move.from().parent()));

        if (back != null)
        {
            if (move.to().equals(this.getCurrent()))
            {
                this.picker.selectCurrent(back);
            }

            this.refresh();
            this.tree.refresh();
        }

        return back;
    }

    /** Relist the folder (or rerun the search) from the disk as it is now. */
    public void refresh()
    {
        this.entries.clear();
        this.entries.addAll(this.isSearching() ? this.searchEntries() : this.folderEntries());
        this.selection.retain(this.entries);
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
        this.selection.clear();
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

    public void pressEntry(TextureEntry entry, UIContext context)
    {
        Link link = entry.link();

        if (Window.isCtrlPressed())
        {
            this.selection.toggle(link);
        }
        else if (Window.isShiftPressed())
        {
            this.selection.range(link, this.entries);
        }
        else
        {
            if (!this.selection.contains(link) || !this.selection.isGroup())
            {
                this.selection.set(link);
            }

            if (entry.folder())
            {
                /* Entered on release, so a press can also begin dragging the folder */
                this.pendingFolder = link;
            }
            else
            {
                this.picker.onFileClicked(link);
            }
        }

        List<Link> payload = this.selection.contains(link) ? new ArrayList<>(this.selection.getLinks()) : Collections.singletonList(link);

        this.drag.press(payload, context.mouseX, context.mouseY);
    }

    public void clickEmpty()
    {
        this.selection.clear();
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

        return CellAction.of(TextureFiles.canModify(entry.link()));
    }

    public void runAction(TextureEntry entry, CellAction action)
    {
        switch (action)
        {
            case EDIT -> this.openInEditor(entry.link());
            case DUPLICATE ->
            {
                for (Link link : this.group(entry.link()))
                {
                    TextureFiles.duplicate(link);
                }

                this.refresh();
            }
            case REMOVE -> this.confirmDelete(this.group(entry.link()));
        }
    }

    /** The links an action on {@code link} touches: the whole pick when it's one of several picked. */
    private List<Link> group(Link link)
    {
        return this.selection.isGroup() && this.selection.contains(link) ? new ArrayList<>(this.selection.getLinks()) : Collections.singletonList(link);
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

    /* Release and drop */

    public void release()
    {
        if (this.drag.isActive())
        {
            this.drop();
        }
        else if (this.pendingFolder != null)
        {
            this.navigate(this.pendingFolder);
        }

        this.pendingFolder = null;
        this.drag.reset();
    }

    private void drop()
    {
        Link target = this.drag.getTarget();

        if (!this.drag.accepts(target))
        {
            return;
        }

        Link current = this.getCurrent();
        boolean copy = Window.isCtrlPressed();

        for (Link link : this.drag.getLinks())
        {
            if (copy)
            {
                TextureFiles.copyInto(link, target);

                continue;
            }

            Link moved = TextureFiles.move(link, target);

            if (moved == null)
            {
                continue;
            }

            this.undos.addLast(new Move(link, moved));
            this.redos.clear();

            if (link.equals(current))
            {
                this.picker.selectCurrent(moved);
            }
        }

        this.selection.clear();
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

            this.selection.clear();
            this.refresh();
            this.tree.refresh();
        });

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void buildContextMenu(ContextMenuManager menu)
    {
        TextureEntry entry = this.contextEntry;
        Link link = entry == null ? null : entry.link();
        boolean group = link != null && this.selection.isGroup() && this.selection.contains(link);
        boolean modifiable = link != null && TextureFiles.canModify(link);

        if (entry != null && !entry.folder())
        {
            menu.action(Icons.EDIT, UIKeys.GENERAL_EDIT, () -> this.openInEditor(link));
            menu.action(Icons.COPY, UIKeys.TEXTURES_COPY, () -> Window.setClipboard(link.toString()));

            File file = TextureFiles.file(link);

            if (file != null && file.isFile())
            {
                menu.action(Icons.ADD, UIKeys.TEXTURES_CREATE_MCMETA, () ->
                {
                    MapType data = DataToString.mapFromString("{\"animation\":{\"frametime\":2}}");

                    DataToString.writeSilently(new File(file.getAbsolutePath() + ".mcmeta"), data, true);
                });
            }
        }

        if (modifiable && !group)
        {
            menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.promptRename(link));

            if (!entry.folder())
            {
                menu.action(Icons.DUPE, UIKeys.FORMS_CATEGORIES_CONTEXT_DUPLICATE_FORM, () ->
                {
                    TextureFiles.duplicate(link);
                    this.refresh();
                });
            }
        }

        if (modifiable)
        {
            List<Link> links = this.group(link);

            menu.action(Icons.REMOVE, group ? UIKeys.TEXTURES_BROWSER_DELETE_SELECTED.format(String.valueOf(links.size())) : UIKeys.GENERAL_REMOVE, Colors.RED, () -> this.confirmDelete(links));
        }

        if (TextureFiles.isFolder(this.path))
        {
            menu.action(Icons.ADD, UIKeys.TEXTURES_BROWSER_NEW_FOLDER, this::promptNewFolder);
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
        if (context.isPressed(GLFW.GLFW_KEY_ENTER))
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
        this.selection.set(entry.link());

        if (entry.folder())
        {
            this.info.set(entry.link());
        }
        else
        {
            this.picker.selectCurrent(entry.link());
        }

        this.grid.scrollTo(this.indexOf(entry.link()));
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

        if ((this.drag.isPressed() || this.pendingFolder != null) && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            this.release();
        }

        this.drag.update(context.mouseX, context.mouseY);
        this.drag.clearTarget();
        this.hoveredAction = null;
        this.back.setEnabled(!this.path.source.isEmpty());

        int strip = BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + BAR_HEIGHT, strip);

        if (this.crumbs.isVisible() || this.text.isVisible())
        {
            context.batcher.box(this.crumbs.area.x, this.crumbs.area.y, this.crumbs.area.ex(), this.crumbs.area.ey(), strip);
        }

        context.batcher.box(this.left.area.x, this.left.area.y, this.left.area.ex(), this.left.area.ey(), strip);

        super.render(context);

        if (!this.lastTyped.check() && this.lastTyped.enabled)
        {
            int x = this.grid.area.x + 6;
            int y = this.grid.area.y + 6;

            context.batcher.textCard(this.typed, x + 2, y + 2, Colors.WHITE, Colors.A50 | BBSSettings.primaryColor.get(), 2);
        }

        if (this.hoveredAction != null && !this.drag.isActive())
        {
            CellActionBar.renderLabel(context, this.hoveredAction, this.hoveredActionX, this.hoveredActionY);
        }

        if (this.drag.isActive())
        {
            this.renderGhost(context);
        }
    }

    /** What's being carried, beside the cursor: a stack of the textures and their count. */
    private void renderGhost(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int primary = BBSSettings.primaryColor.get();
        List<Link> links = this.drag.getLinks();
        boolean landing = this.drag.accepts(this.drag.getTarget());
        int size = Math.min(this.grid.getCellSize(), 48);
        int x = context.mouseX + 10;
        int y = context.mouseY + 10;
        int stack = Math.min(3, links.size());

        for (int i = stack - 1; i >= 0; i--)
        {
            int ox = x + i * 4;
            int oy = y + i * 4;

            batcher.box(ox, oy, ox + size, oy + size, BBSSettings.color(BBSSettings.raisedSurface(), landing ? Colors.A100 : Colors.A50));
            batcher.outline(ox, oy, ox + size, oy + size, landing ? Colors.A100 | primary : BBSSettings.dividerColor(), 1);

            if (i == 0)
            {
                TextureEntry entry = TextureEntry.of(links.get(0));

                TextureCellRenderer.render(context, entry, ox, oy, size, size, new TextureCellRenderer.State(), CellAction.none());
            }
        }

        if (links.size() > 1)
        {
            batcher.textCard(String.valueOf(links.size()), x + size - 4, y - 4, Colors.WHITE, Colors.A100 | primary, 3);
        }

        if (landing && Window.isCtrlPressed())
        {
            batcher.textCard(UIKeys.TEXTURES_BROWSER_COPYING.get(), x, y + size + 8, Colors.WHITE, Colors.A100 | primary, 3);
        }
    }
}
