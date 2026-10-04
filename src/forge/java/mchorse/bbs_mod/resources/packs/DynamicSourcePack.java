package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** The global library remains primary; world-only assets are read and edited in place. */
public class DynamicSourcePack implements ISourcePack
{
    private final ISourcePack main;
    private volatile ISourcePack secondary;
    public DynamicSourcePack(ISourcePack main) { this.main = main; }
    public void setSecondary(ISourcePack secondary) { this.secondary = secondary; }
    public ISourcePack getSourcePack() { return this.main; }
    public ISourcePack getSecondary() { return this.secondary; }
    public String getPrefix() { return this.main.getPrefix(); }

    public boolean hasAsset(Link link)
    {
        ISourcePack local = this.secondary;
        return this.main.hasAsset(link) || local != null && local.hasAsset(link);
    }

    public InputStream getAsset(Link link) throws IOException
    {
        ISourcePack local = this.secondary;
        return this.main.hasAsset(link) || local == null ? this.main.getAsset(link) : local.getAsset(link);
    }

    public File getFile(Link link)
    {
        File shared = this.main.getFile(link);
        if (shared != null && shared.exists()) return shared;
        ISourcePack local = this.secondary;
        File file = local == null ? null : local.getFile(link);
        return file != null && file.exists() ? file : shared;
    }

    public Link getLink(File file)
    {
        Link link = this.main.getLink(file);
        ISourcePack local = this.secondary;
        return link != null || local == null ? link : local.getLink(file);
    }

    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        this.main.getLinksFromPath(links, link, recursive);
        ISourcePack local = this.secondary;
        if (local != null) local.getLinksFromPath(links, link, recursive);
    }
}
