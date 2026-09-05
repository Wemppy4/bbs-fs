package mchorse.bbs_mod.resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class AssetProvider
{
    private Map<String, List<ISourcePack>> sourcePacks = new HashMap<>();

    public void registerFirst(ISourcePack pack)
    {
        this.sourcePacks.computeIfAbsent(pack.getPrefix(), (k) -> new ArrayList<>()).add(0, pack);
    }

    public void register(ISourcePack pack)
    {
        this.sourcePacks.computeIfAbsent(pack.getPrefix(), (k) -> new ArrayList<>()).add(pack);
    }

    public Collection<String> getSourceKeys()
    {
        return this.sourcePacks.keySet();
    }

    private List<ISourcePack> getPacks(String source)
    {
        List<ISourcePack> sourcePacks = this.sourcePacks.get(source);

        return sourcePacks == null ? Collections.emptyList() : sourcePacks;
    }

    public InputStream getAsset(Link link) throws IOException
    {
        List<ISourcePack> packs = this.getPacks(link.source);

        for (ISourcePack pack : packs)
        {
            if (pack.hasAsset(link))
            {
                return pack.getAsset(link);
            }
        }

        throw new FileNotFoundException("Asset " + link + " couldn't be found!");
    }

    public File getFile(Link link)
    {
        if (link == null)
        {
            return null;
        }

        List<ISourcePack> packs = this.getPacks(link.source);

        for (ISourcePack pack : packs)
        {
            File file = pack.getFile(link);

            if (file != null)
            {
                return file;
            }
        }

        return null;
    }

    public Link getLink(File file)
    {
        for (List<ISourcePack> sourcePacks : this.sourcePacks.values())
        {
            for (ISourcePack sourcePack : sourcePacks)
            {
                Link link = sourcePack.getLink(file);

                if (link != null)
                {
                    return link;
                }
            }
        }

        return null;
    }

    public Collection<Link> getLinksFromPath(Link link)
    {
        return this.getLinksFromPath(link, true);
    }

    public Collection<Link> getLinksFromPath(Link link, boolean recursive)
    {
        /* Sorted, not hashed: loaders pick "the first .obj/.geo.json/.jem" out of this, and with a
         * HashSet which file that was came down to hash order - a folder holding player.jem and
         * player_cape.jem loaded whichever one landed first, and the same folder could answer
         * differently on another machine. */
        Set<Link> links = new TreeSet<>(Comparator.comparing(Link::toString));
        List<ISourcePack> packs = this.getPacks(link.source);

        for (ISourcePack pack : packs)
        {
            pack.getLinksFromPath(links, link, recursive);
        }

        return links;
    }
}