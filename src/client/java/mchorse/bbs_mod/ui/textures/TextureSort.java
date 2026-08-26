package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;

import java.io.File;
import java.util.Comparator;

/**
 * How a texture browser orders the textures of a folder. Folders always come first, by name;
 * the sort is about the files after them. Kept in the settings, one for every browser.
 */
public enum TextureSort
{
    NAME("name", Icons.FONT, UIKeys.TEXTURES_BROWSER_SORT_NAME, (a, b) -> NaturalOrderComparator.compare(true, a.caption(), b.caption())),
    DATE("date", Icons.TIME, UIKeys.TEXTURES_BROWSER_SORT_DATE, (a, b) -> Long.compare(modified(b), modified(a))),
    SIZE("size", Icons.SCALE, UIKeys.TEXTURES_BROWSER_SORT_SIZE, (a, b) -> Long.compare(length(b), length(a)));

    public final String id;
    public final Icon icon;
    public final IKey label;

    private final Comparator<TextureEntry> comparator;

    TextureSort(String id, Icon icon, IKey label, Comparator<TextureEntry> comparator)
    {
        this.id = id;
        this.icon = icon;
        this.label = label;
        this.comparator = comparator;
    }

    public static TextureSort byId(String id)
    {
        for (TextureSort sort : values())
        {
            if (sort.id.equals(id))
            {
                return sort;
            }
        }

        return NAME;
    }

    public int compare(TextureEntry a, TextureEntry b)
    {
        if (a.folder() != b.folder())
        {
            return a.folder() ? -1 : 1;
        }

        if (a.folder())
        {
            return NaturalOrderComparator.compare(true, a.caption(), b.caption());
        }

        int result = this.comparator.compare(a, b);

        return result != 0 ? result : NaturalOrderComparator.compare(true, a.caption(), b.caption());
    }

    private static long modified(TextureEntry entry)
    {
        File file = TextureFiles.file(entry.link());

        return file == null ? 0 : file.lastModified();
    }

    private static long length(TextureEntry entry)
    {
        File file = TextureFiles.file(entry.link());

        return file == null ? 0 : file.length();
    }
}
