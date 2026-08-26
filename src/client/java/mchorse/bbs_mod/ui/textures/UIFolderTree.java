package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.items.ItemDrag;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The folders of every source as a tree down the side of a texture browser, for jumping
 * between {@code models/…/skins} of different models without climbing up and down. Branches
 * are listed only when unfolded, so a huge assets folder costs nothing until opened.
 *
 * <p>A row is a folder: the arrow unfolds it, the name goes there. While textures are
 * dragged, the row under the cursor is where they'd drop.</p>
 *
 * <p>Above the sources sit the {@link TexturePins pins} — the folders and textures the user
 * keeps at hand — as flat rows, marked with a bookmark and cut off from the tree by a
 * divider. They're the same list everywhere a tree is shown.</p>
 */
public class UIFolderTree extends UIList<UIFolderTree.Node>
{
    public static final int ROW = 16;
    public static final int INDENT = 10;

    /** What a row is: a folder of the tree, one of the pins above it, or the pins' own title. */
    public enum Kind
    {
        FOLDER, PIN, HEADER
    }

    public record Node(Link link, int depth, boolean branch, boolean expanded, Kind kind)
    {
        public static Node folder(Link link, int depth, boolean branch, boolean expanded)
        {
            return new Node(link, depth, branch, expanded, Kind.FOLDER);
        }

        public static Node pin(Link link)
        {
            return new Node(link, 0, false, false, Kind.PIN);
        }

        /** The row that says what the rows under it are; it points at nothing. */
        public static Node pinsHeader()
        {
            return new Node(new Link("", ""), 0, false, false, Kind.HEADER);
        }

        public boolean pin()
        {
            return this.kind == Kind.PIN;
        }

        public boolean header()
        {
            return this.kind == Kind.HEADER;
        }

        /** A source root and anything with a trailing slash; only a pin can be a texture. */
        public boolean folder()
        {
            return this.link.path.isEmpty() || this.link.path.endsWith("/");
        }
    }

    private final IFolderTreeHost browser;

    /* Which folders are unfolded; it outlives every relisting of the tree */
    private final FoldState<Link> folds = new FoldState<>();

    /** Whether a folder has folders inside — asked once per listing, not per frame. */
    private final Map<Link, Boolean> branches = new HashMap<>();

    /**
     * How many rows the pins take, their title included: where the divider goes, and where
     * the tree itself starts. Zero when nothing is pinned — no title, no divider.
     */
    private int pinRows;

    /* The row right clicked on, whose pin the context menu acts upon */
    private Node contextNode;

    public UIFolderTree(IFolderTreeHost browser)
    {
        super(null);

        this.browser = browser;
        this.scroll.scrollItemSize = ROW;
        this.scroll.scrollSpeed = ROW * 2;
        this.cancelScrollEdge();
        this.context(this::buildContextMenu);
    }

    /** Forget what's cached about the disk and lay the tree out again. */
    public void refresh()
    {
        this.branches.clear();
        this.rebuild();
    }

    /** Unfold every folder above {@code path} so it shows, then lay out. */
    public void reveal(Link path)
    {
        if (path != null && !path.source.isEmpty())
        {
            List<Link> above = new ArrayList<>();
            Link folder = TextureEntry.folderLink(path);

            while (!folder.path.isEmpty())
            {
                folder = TextureEntry.folderLink(folder.parent());
                above.add(folder);
            }

            above.add(new Link(path.source, ""));
            this.folds.expandAll(above);
        }

        this.rebuild();
    }

    private void rebuild()
    {
        this.clear();

        List<Link> pinned = TexturePins.getPins();

        if (!pinned.isEmpty())
        {
            this.add(Node.pinsHeader());

            for (Link pin : pinned)
            {
                this.add(Node.pin(pin));
            }
        }

        this.pinRows = pinned.isEmpty() ? 0 : pinned.size() + 1;

        List<String> sources = new ArrayList<>(BBSMod.getProvider().getSourceKeys());

        sources.sort((a, b) -> NaturalOrderComparator.compare(true, a, b));

        for (String source : sources)
        {
            this.addBranch(new Link(source, ""), 0);
        }

        this.update();
    }

    private void addBranch(Link folder, int depth)
    {
        boolean branch = this.isBranch(folder);
        boolean open = branch && this.folds.isExpanded(folder);

        this.add(Node.folder(folder, depth, branch, open));

        if (!open)
        {
            return;
        }

        List<Link> children = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(folder, false))
        {
            if (link.path.endsWith("/"))
            {
                children.add(link);
            }
        }

        children.sort((a, b) -> NaturalOrderComparator.compare(true, a.path, b.path));

        for (Link child : children)
        {
            this.addBranch(child, depth + 1);
        }
    }

    private boolean isBranch(Link folder)
    {
        return this.branches.computeIfAbsent(folder, (f) ->
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(f, false))
            {
                if (link.path.endsWith("/"))
                {
                    return true;
                }
            }

            return false;
        });
    }

    /* Tree convention: only folders of the tree fold; pins and the title are flat rows */

    @Override
    protected int indent(Node node)
    {
        return node.depth() * INDENT;
    }

    @Override
    protected Boolean branch(Node node)
    {
        return node.branch() ? node.expanded() : null;
    }

    @Override
    protected void toggle(Node node)
    {
        this.folds.toggle(node.link());
        this.rebuild();
    }

    /** A pin was clicked: its folder is entered, its texture handed to whoever shows the tree. */
    private void openPin(Node node)
    {
        if (node.folder())
        {
            this.browser.navigate(node.link());
        }
        else
        {
            this.browser.openPinned(node.link());
        }
    }

    private String nameOf(Node node)
    {
        return node.link().path.isEmpty() ? node.link().source : StringUtils.fileName(node.link().path).replace("/", "");
    }

    /**
     * Whether a pin points at something that is gone — shown greyed out, so a pin left over
     * from a deleted folder says so instead of quietly going nowhere. Only what lives on disk
     * can be told: a source inside the mod's jar hands out no files, and is never called stale.
     */
    private boolean isMissing(Node node)
    {
        File file = TextureFiles.file(node.link());

        return file != null && !file.exists();
    }

    @Override
    protected boolean sortElements()
    {
        return false;
    }

    /* Input */

    private void buildContextMenu(ContextMenuManager menu)
    {
        Node node = this.contextNode;

        if (node == null || !TexturePins.canPin(node.link()))
        {
            return;
        }

        Link link = node.link();

        if (node.pin())
        {
            menu.action(Icons.MOVE_UP, UIKeys.TEXTURES_BROWSER_PIN_UP, () -> this.shiftPin(link, -1));
            menu.action(Icons.MOVE_DOWN, UIKeys.TEXTURES_BROWSER_PIN_DOWN, () -> this.shiftPin(link, 1));
            menu.action(Icons.BOOKMARK, UIKeys.TEXTURES_BROWSER_UNPIN, () -> this.togglePin(link));
        }
        else
        {
            menu.action(Icons.BOOKMARK, TexturePins.isPinned(link) ? UIKeys.TEXTURES_BROWSER_UNPIN : UIKeys.TEXTURES_BROWSER_PIN, () -> this.togglePin(link));
        }
    }

    private void togglePin(Link link)
    {
        TexturePins.toggle(link);
        this.rebuild();
    }

    private void shiftPin(Link link, int delta)
    {
        TexturePins.shift(link, delta);
        this.rebuild();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 1 && this.area.isInside(context))
        {
            int index = this.scroll.getIndex(context.mouseX, context.mouseY);

            this.contextNode = this.exists(index) ? this.getList().get(index) : null;

            return false;
        }

        return super.subMouseClicked(context);
    }

    /** A row is entered, not picked: the browser goes there, and the tree keeps no pick of its own. */
    @Override
    protected boolean pressItem(int index, UIContext context)
    {
        Node node = this.getList().get(index);

        if (node.header())
        {
            /* Nothing to enter, but the press is still ours — it mustn't start anything */
            return true;
        }

        if (node.pin())
        {
            this.openPin(node);

            return true;
        }

        if (this.pressArrow(index, context))
        {
            return true;
        }

        this.cursor = index;
        this.browser.navigate(node.link());

        if (node.branch() && !node.expanded())
        {
            this.folds.set(node.link(), true);
            this.rebuild();
        }

        return true;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.browser.release();

        return false;
    }

    /* Rendering */

    @Override
    public void renderListElement(UIContext context, Node node, int i, int x, int y, boolean hover, boolean selected)
    {
        if (node.header())
        {
            this.renderHeader(context, x, y);

            return;
        }

        ItemDrag<TextureEntry> drag = this.browser.getDrag();
        boolean current = node.folder() ? this.browser.isCurrentFolder(node.link()) : this.browser.isCurrentTexture(node.link());
        boolean target = false;

        /* A folder can't receive itself; the carried entries are matched by link since the tree has no entries */
        if (hover && node.folder() && drag != null && drag.isActive() && drag.getItems().stream().noneMatch((entry) -> entry.link().equals(node.link())))
        {
            drag.setTarget(node.link());
            target = true;
        }

        if (current || target)
        {
            context.batcher.box(x, y, x + this.area.w, y + ROW, Colors.A25 | BBSSettings.primaryColor.get());
        }
        else if (hover)
        {
            context.batcher.box(x, y, x + this.area.w, y + ROW, Colors.A12 | 0xffffff);
        }

        this.renderElementPart(context, node, i, x, y, hover, selected);

        /* The pins are their own group: a divider says where the tree itself begins */
        if (node.pin() && i == this.pinRows - 1)
        {
            context.batcher.box(x, y + ROW - 1, x + this.area.w, y + ROW, BBSSettings.dividerColor());
        }
    }

    @Override
    protected void renderElementPart(UIContext context, Node node, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int ix = x + this.rowContentX(node);
        int my = y + ROW / 2;
        boolean missing = node.pin() && this.isMissing(node);
        int color = missing ? Colors.GRAY : (hover ? Colors.LIGHTEST_GRAY : Colors.WHITE);

        this.renderArrow(context, node, x, y);

        if (node.folder())
        {
            context.batcher.icon(Icons.FOLDER, color, ix + 12, my - 8);
        }
        else if (missing)
        {
            /* Nothing to show, and nothing to ask the texture manager for either */
            context.batcher.icon(Icons.IMAGE, color, ix + 12, my - 8);
        }
        else
        {
            this.renderThumbnail(context, node.link(), ix + 12, my - 8, color);
        }

        /* A folder of the mod's own says it can't be changed; what a pin is, its title says */
        boolean readOnly = !node.pin() && TextureFiles.isReadOnly(node.link());
        int right = this.area.ex() - 4 - (readOnly ? 18 : 0);
        String name = font.limitToWidth(this.nameOf(node), right - (ix + 32));

        context.batcher.textShadow(name, ix + 32, y + (ROW - font.getHeight()) / 2 + 1, color);

        if (readOnly)
        {
            context.batcher.icon(Icons.GEAR, Colors.LIGHTER_GRAY, this.area.ex() - 20, my - 8);
        }
    }

    /**
     * The pins' title: a bookmark and a word, in the same full white as everything else down
     * this side — greyed out, it read as disabled rather than as a label. It carries no band
     * of its own: the side panel is already chrome, and a second tone there would muddy it.
     */
    private void renderHeader(UIContext context, int x, int y)
    {
        FontRenderer font = context.batcher.getFont();
        int my = y + ROW / 2;

        context.batcher.icon(Icons.BOOKMARK, Colors.WHITE, x + 4, my - 8);

        String title = font.limitToWidth(UIKeys.TEXTURES_BROWSER_PINNED.get(), this.area.ex() - 4 - (x + 22));

        context.batcher.textShadow(title, x + 22, y + (ROW - font.getHeight()) / 2 + 1, Colors.WHITE);
    }

    /** A pinned texture shows itself, fitted into the icon's place, so it's told apart at a glance. */
    private void renderThumbnail(UIContext context, Link link, int x, int y, int color)
    {
        Texture texture = BBSModClient.getTextures().getTexture(link);

        if (texture == null || texture == BBSModClient.getTextures().getError())
        {
            context.batcher.icon(Icons.IMAGE, color, x, y);

            return;
        }

        int tw = Math.max(1, texture.width);
        int th = Math.max(1, texture.height);
        float scale = Math.min(16F / tw, 16F / th);
        int w = Math.max(1, Math.round(tw * scale));
        int h = Math.max(1, Math.round(th * scale));

        context.batcher.iconArea(Icons.CHECKBOARD, x + (16 - w) / 2, y + (16 - h) / 2, w, h);
        context.batcher.fullTexturedBox(texture, x + (16 - w) / 2, y + (16 - h) / 2, w, h);
    }
}
