package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;

/**
 * One thing a texture browser shows in its grid: a folder (a source at the root, a directory
 * below it) or a texture file.
 *
 * @param caption what the cell's strip says — the name, or the path under the searched root
 *                for a search hit, since a name alone doesn't say where a hit lives
 */
public record TextureEntry(Link link, String name, boolean folder, String caption)
{
    public static TextureEntry of(Link link)
    {
        boolean folder = link.path.endsWith("/");
        String name = StringUtils.fileName(link.path).replace("/", "");

        return new TextureEntry(link, name, folder, name);
    }

    /** A root entry: one of the sources (assets, http…), shown as a folder. */
    public static TextureEntry source(String source)
    {
        return new TextureEntry(new Link(source, ""), source, true, source);
    }

    public static TextureEntry hit(Link link, Link root)
    {
        String name = StringUtils.fileName(link.path);
        String caption = link.path.startsWith(root.path) ? link.path.substring(root.path.length()) : link.toString();

        return new TextureEntry(link, name, false, caption);
    }

    public TextureEntry withCaption(String caption)
    {
        return new TextureEntry(this.link, this.name, this.folder, caption);
    }

    /** Folders are addressed with a trailing slash, the way the provider lists them. */
    public static Link folderLink(Link link)
    {
        if (link.path.isEmpty() || link.path.endsWith("/"))
        {
            return link;
        }

        return new Link(link.source, link.path + "/");
    }
}
