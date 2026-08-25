package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One drag of textures (or folders) in progress: what's carried and the folder it would land
 * in. A press only arms it; it goes active once the cursor has travelled a few pixels, so a
 * click never turns into an accidental move. The grid and the folder tree report the target
 * while they paint; the browser resolves the drop on release.
 */
public class TextureDrag
{
    public static final int THRESHOLD = 4;

    private final List<Link> links = new ArrayList<>();
    private boolean pressed;
    private boolean active;
    private int startX;
    private int startY;
    private Link target;

    public void press(List<Link> links, int x, int y)
    {
        this.reset();

        this.links.addAll(links);
        this.pressed = true;
        this.startX = x;
        this.startY = y;
    }

    public void reset()
    {
        this.links.clear();
        this.pressed = false;
        this.active = false;
        this.target = null;
    }

    public boolean isPressed()
    {
        return this.pressed;
    }

    public boolean isActive()
    {
        return this.active;
    }

    public boolean update(int x, int y)
    {
        if (this.pressed && !this.active)
        {
            this.active = Math.abs(x - this.startX) >= THRESHOLD || Math.abs(y - this.startY) >= THRESHOLD;
        }

        return this.active;
    }

    public boolean isDragging(Link link)
    {
        return this.active && this.links.contains(link);
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
        return this.active && folder != null && folder.equals(this.target);
    }

    /** Whether dropping into {@code folder} would do anything: it's on disk and isn't the source itself. */
    public boolean accepts(Link folder)
    {
        if (folder == null || !TextureFiles.isFolder(folder))
        {
            return false;
        }

        for (Link link : this.links)
        {
            if (!TextureEntry.folderLink(link.parent()).equals(TextureEntry.folderLink(folder)) && !link.equals(folder))
            {
                return true;
            }
        }

        return false;
    }
}
