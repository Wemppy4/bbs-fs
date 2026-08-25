package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.DragGesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One drag of textures (or folders) in progress: what's carried and the folder it would land
 * in. The grid and the folder tree report the target while they paint; the browser resolves
 * the drop on release.
 */
public class TextureDrag extends DragGesture
{
    private final List<Link> links = new ArrayList<>();
    private Link target;

    public void press(List<Link> links, int x, int y)
    {
        this.reset();
        this.press(x, y);

        this.links.addAll(links);
    }

    @Override
    public void reset()
    {
        super.reset();

        this.links.clear();
        this.target = null;
    }

    public boolean isDragging(Link link)
    {
        return this.isActive() && this.links.contains(link);
    }

    public List<Link> getLinks()
    {
        return Collections.unmodifiableList(this.links);
    }

    /** The folder the drop would go into; null when the cursor isn't over one. */
    public Link getTarget()
    {
        return this.target;
    }

    public void setTarget(Link folder)
    {
        this.target = folder;
    }

    public void clearTarget()
    {
        this.target = null;
    }

    public boolean isTarget(Link folder)
    {
        return this.isActive() && folder != null && folder.equals(this.target);
    }

    /**
     * Whether dropping into {@code folder} would do anything: it's on disk and, for a move,
     * isn't where the files already are. A copy is fine into their own folder — that's how a
     * duplicate is made by hand.
     */
    public boolean accepts(Link folder, boolean copy)
    {
        if (folder == null || !TextureFiles.isFolder(folder))
        {
            return false;
        }

        for (Link link : this.links)
        {
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
}
