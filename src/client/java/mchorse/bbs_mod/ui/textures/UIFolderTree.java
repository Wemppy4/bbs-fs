package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The folders of every source as a tree down the side of a texture browser, for jumping
 * between {@code models/…/skins} of different models without climbing up and down. Branches
 * are listed only when unfolded, so a huge assets folder costs nothing until opened.
 *
 * <p>A row is a folder: the arrow unfolds it, the name goes there. While textures are
 * dragged, the row under the cursor is where they'd drop.</p>
 */
public class UIFolderTree extends UIList<UIFolderTree.Node>
{
    public static final int ROW = 16;
    public static final int INDENT = 10;

    public record Node(Link link, int depth, boolean branch, boolean expanded)
    {}

    private final UITextureBrowser browser;
    private final Set<Link> expanded = new HashSet<>();

    /** Whether a folder has folders inside — asked once per listing, not per frame. */
    private final Map<Link, Boolean> branches = new HashMap<>();

    public UIFolderTree(UITextureBrowser browser)
    {
        super(null);

        this.browser = browser;
        this.scroll.scrollItemSize = ROW;
        this.scroll.scrollSpeed = ROW * 2;
        this.cancelScrollEdge();
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
            Link folder = TextureEntry.folderLink(path);

            while (!folder.path.isEmpty())
            {
                folder = TextureEntry.folderLink(folder.parent());
                this.expanded.add(folder);
            }

            this.expanded.add(new Link(path.source, ""));
        }

        this.rebuild();
    }

    private void rebuild()
    {
        this.clear();

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
        boolean open = branch && this.expanded.contains(folder);

        this.add(new Node(folder, depth, branch, open));

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

    private void toggle(Node node)
    {
        if (!this.expanded.remove(node.link()))
        {
            this.expanded.add(node.link());
        }

        this.rebuild();
    }

    private String nameOf(Node node)
    {
        return node.link().path.isEmpty() ? node.link().source : StringUtils.fileName(node.link().path).replace("/", "");
    }

    @Override
    protected boolean sortElements()
    {
        return false;
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        int index = this.scroll.getIndex(context.mouseX, context.mouseY);

        if (!this.exists(index))
        {
            return false;
        }

        Node node = this.getList().get(index);
        int arrowX = this.area.x + 4 + node.depth() * INDENT;

        if (context.mouseButton == 0)
        {
            if (node.branch() && context.mouseX < arrowX + 12)
            {
                this.toggle(node);
            }
            else
            {
                this.browser.navigate(node.link());

                if (node.branch() && !node.expanded())
                {
                    this.expanded.add(node.link());
                    this.rebuild();
                }
            }

            return true;
        }

        return false;
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
        TextureDrag drag = this.browser.drag;
        boolean current = this.browser.isCurrentFolder(node.link());
        boolean target = false;

        if (hover && drag.isActive() && !drag.isDragging(node.link()))
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
    }

    @Override
    protected void renderElementPart(UIContext context, Node node, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int ix = x + 4 + node.depth() * INDENT;
        int my = y + ROW / 2;

        if (node.branch())
        {
            UISection.renderArrow(context, ix + 6, my, node.expanded());
        }

        context.batcher.icon(Icons.FOLDER, hover ? Colors.LIGHTEST_GRAY : Colors.WHITE, ix + 12, my - 8);

        boolean readOnly = TextureFiles.isReadOnly(node.link());
        int right = this.area.ex() - 4 - (readOnly ? 18 : 0);
        String name = font.limitToWidth(this.nameOf(node), right - (ix + 32));

        context.batcher.textShadow(name, ix + 32, y + (ROW - font.getHeight()) / 2 + 1, hover ? Colors.LIGHTEST_GRAY : Colors.WHITE);

        if (readOnly)
        {
            context.batcher.icon(Icons.GEAR, Colors.LIGHTER_GRAY, this.area.ex() - 20, my - 8);
        }
    }
}
