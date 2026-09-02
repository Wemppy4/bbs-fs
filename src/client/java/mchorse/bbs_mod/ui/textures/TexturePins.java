package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the user keeps at hand in a texture browser: the folders and textures pinned to the
 * top of the {@link UIFolderTree folder tree}, so the few places worked in every day are one
 * click away instead of several folders down.
 *
 * <p>The list is the same everywhere a tree is shown (the browser, the save dialog) and
 * outlives the session — it lives in the settings, in the order the user put things in.</p>
 *
 * <p>Links are stored the way the browser hands them over: a folder with its trailing slash,
 * a texture without one. Nothing here touches the disk, so a pin whose folder went missing
 * stays pinned — the tree shows it greyed out and the user decides what to do with it.</p>
 */
public class TexturePins
{
    /**
     * Whether something can be pinned at all: a source's own root is already at the top of
     * the tree, and the list of sources isn't a place.
     */
    public static boolean canPin(Link link)
    {
        return link != null && !link.source.isEmpty() && !link.path.isEmpty();
    }

    public static List<Link> getPins()
    {
        return Collections.unmodifiableList(BBSSettings.texturePins.get());
    }

    public static boolean isPinned(Link link)
    {
        return link != null && BBSSettings.texturePins.get().contains(link);
    }

    /** Pin what isn't pinned yet; the newest goes to the bottom of the list. */
    public static void pin(Link link)
    {
        if (!canPin(link) || isPinned(link))
        {
            return;
        }

        List<Link> pins = new ArrayList<>(BBSSettings.texturePins.get());

        pins.add(link);
        BBSSettings.texturePins.set(pins);
    }

    public static void unpin(Link link)
    {
        List<Link> pins = new ArrayList<>(BBSSettings.texturePins.get());

        if (pins.remove(link))
        {
            BBSSettings.texturePins.set(pins);
        }
    }

    public static void toggle(Link link)
    {
        if (isPinned(link))
        {
            unpin(link);
        }
        else
        {
            pin(link);
        }
    }

    /**
     * Pin every one of them, or — when they all are pinned already — unpin them, so one menu
     * entry works the same for a group as it does for a single file.
     */
    public static void toggle(List<Link> links)
    {
        boolean unpin = arePinned(links);

        for (Link link : links)
        {
            if (unpin)
            {
                unpin(link);
            }
            else
            {
                pin(link);
            }
        }
    }

    /** Whether every one of them is pinned — what tells the menu entry which way it goes. */
    public static boolean arePinned(List<Link> links)
    {
        if (links.isEmpty())
        {
            return false;
        }

        for (Link link : links)
        {
            if (!isPinned(link))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Follow a rename, a move or a deletion ({@code to} null), so a pin never quietly goes
     * stale. A folder takes everything pinned under it along: renaming {@code skins} keeps
     * the pin on {@code skins/steve.png} pointing at the same picture.
     *
     * <p>Called from {@link TextureFiles}, where every such operation goes through, rather
     * than from the browser — undoing a move has to move the pins back just the same.</p>
     */
    public static void follow(Link from, Link to)
    {
        if (from == null)
        {
            return;
        }

        boolean folder = from.path.endsWith("/");
        Link target = to == null || !folder ? to : TextureEntry.folderLink(to);
        List<Link> pins = new ArrayList<>(BBSSettings.texturePins.get());
        boolean changed = false;

        for (int i = pins.size() - 1; i >= 0; i--)
        {
            Link pin = pins.get(i);
            boolean under = folder && pin.source.equals(from.source) && pin.path.startsWith(from.path);

            if (!pin.equals(from) && !under)
            {
                continue;
            }

            if (target == null)
            {
                pins.remove(i);
            }
            else
            {
                pins.set(i, under ? new Link(target.source, target.path + pin.path.substring(from.path.length())) : target);
            }

            changed = true;
        }

        if (changed)
        {
            BBSSettings.texturePins.set(pins);
        }
    }

    /** Shift a pin one place up or down the list — the user's own order. */
    public static void shift(Link link, int delta)
    {
        List<Link> pins = new ArrayList<>(BBSSettings.texturePins.get());
        int index = pins.indexOf(link);
        int to = index + delta;

        if (index == -1 || to < 0 || to >= pins.size())
        {
            return;
        }

        pins.add(to, pins.remove(index));
        BBSSettings.texturePins.set(pins);
    }
}
