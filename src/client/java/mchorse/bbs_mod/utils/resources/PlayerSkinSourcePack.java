package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * The <code>player:</code> source: <code>player:Notch</code> is that player's skin. See
 * {@link PlayerSkins} for where the picture comes from — this only puts it into the asset
 * provider, so every place that takes a texture link takes a nickname too.
 *
 * <p>Nothing here is a file the user owns, so the pack has no folders and hands back no
 * {@link File}: skins can't be renamed, moved or deleted from the texture browser, only
 * fetched again.</p>
 */
public class PlayerSkinSourcePack implements ISourcePack
{
    @Override
    public String getPrefix()
    {
        return PlayerSkins.SOURCE;
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return PlayerSkins.nickname(link) != null;
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        return PlayerSkins.open(link);
    }

    @Override
    public File getFile(Link link)
    {
        return null;
    }

    @Override
    public Link getLink(File file)
    {
        return null;
    }

    @Override
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        /* The source has no folders: everything fetched before sits in its root. The
         * extension is what makes the texture browser show them as pictures. */
        if (!link.path.isEmpty())
        {
            return;
        }

        for (String nickname : PlayerSkins.getNicknames())
        {
            links.add(new Link(this.getPrefix(), nickname + ".png"));
        }
    }
}
