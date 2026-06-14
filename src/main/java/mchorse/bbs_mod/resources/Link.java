package mchorse.bbs_mod.resources;

import mchorse.bbs_mod.utils.StringUtils;

import java.util.Objects;

public class Link
{
    public static final String ASSETS = "assets";
    /** Synthetic source for 1x1 solid-color textures; the path is the ARGB color in hex. */
    public static final String COLOR = "color";
    public static final String SOURCE_SEPARATOR = ":";

    public final String source;
    public final String path;

    protected Integer hash;

    public static boolean isAssets(Link link)
    {
        return link.source.equals(ASSETS);
    }

    public static Link assets(String path)
    {
        return new Link(ASSETS, path);
    }

    public static Link bbs(String path)
    {
        return new Link("bbs", path);
    }

    public static Link create(String full)
    {
        int index = full.indexOf(SOURCE_SEPARATOR);

        if (index < 0)
        {
            return new Link(ASSETS, full);
        }

        return new Link(full.substring(0, index), full.substring(index + 1));
    }

    public Link(String source, String path)
    {
        this.source = source;
        this.path = path;
    }

    public Link combine(String path)
    {
        return new Link(this.source, StringUtils.combinePaths(this.path, path));
    }

    public Link parent()
    {
        String parent = StringUtils.parentPath(this.path);

        return parent.isEmpty()
            ? new Link(this.source, "")
            : new Link(this.source, parent);
    }

    @Override
    public int hashCode()
    {
        if (this.hash == null)
        {
            this.hash = this.toString().hashCode();
        }

        return this.hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Link)
        {
            Link link = (Link) obj;

            return Objects.equals(this.source, link.source) && Objects.equals(this.path, link.path);
        }

        return super.equals(obj);
    }

    @Override
    public String toString()
    {
        return this.source + SOURCE_SEPARATOR + this.path;
    }
}