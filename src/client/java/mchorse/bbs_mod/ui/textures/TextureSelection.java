package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The entries a user has picked in a texture browser to act on together — drag them into a
 * folder, delete them. Separate from the browser's <em>current</em> texture, the one the
 * picker hands back; there is always at most one of those.
 */
public class TextureSelection
{
    private final List<Link> links = new ArrayList<>();
    private Link anchor;

    public boolean contains(Link link)
    {
        return this.links.contains(link);
    }

    public boolean isEmpty()
    {
        return this.links.isEmpty();
    }

    public boolean isGroup()
    {
        return this.links.size() > 1;
    }

    public int size()
    {
        return this.links.size();
    }

    public List<Link> getLinks()
    {
        return Collections.unmodifiableList(this.links);
    }

    public void clear()
    {
        this.links.clear();
        this.anchor = null;
    }

    public void set(Link link)
    {
        this.clear();
        this.add(link);
    }

    public void add(Link link)
    {
        if (link != null && !this.links.contains(link))
        {
            this.links.add(link);
        }

        this.anchor = link;
    }

    public void toggle(Link link)
    {
        if (!this.links.remove(link))
        {
            this.add(link);
        }
        else if (link.equals(this.anchor))
        {
            this.anchor = this.links.isEmpty() ? null : this.links.get(this.links.size() - 1);
        }
    }

    /** Pick everything between the anchor and {@code link} in {@code order}, the way Shift-click does. */
    public void range(Link link, List<TextureEntry> order)
    {
        int from = this.indexOf(order, this.anchor);
        int to = this.indexOf(order, link);

        if (from == -1 || to == -1)
        {
            this.add(link);

            return;
        }

        for (int i = Math.min(from, to); i <= Math.max(from, to); i++)
        {
            Link l = order.get(i).link();

            if (!this.links.contains(l))
            {
                this.links.add(l);
            }
        }
    }

    /** Forget what is no longer listed. */
    public void retain(List<TextureEntry> entries)
    {
        this.links.removeIf((link) -> this.indexOf(entries, link) == -1);

        if (this.anchor != null && !this.links.contains(this.anchor))
        {
            this.anchor = null;
        }
    }

    private int indexOf(List<TextureEntry> entries, Link link)
    {
        if (link == null)
        {
            return -1;
        }

        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i).link().equals(link))
            {
                return i;
            }
        }

        return -1;
    }
}
