package mchorse.bbs_mod.utils.categories;

/**
 * A folder path — {@code "Crowd/Guards"} — and the arithmetic every list of folders needs to do
 * with one: what a folder is called, what holds it, what lies inside it, and what a whole subtree
 * becomes when the folder at its head is renamed or moved.
 *
 * <p>The path itself is the address, rather than an id: a thing filed away carries its path with
 * it, so copying it into another film, another palette or another file lands it in the same folder
 * there, made on the spot if that place has never heard of it. An id would point at a record only
 * its own home knows.</p>
 */
public class CategoryPath
{
    public static final String SEPARATOR = "/";

    /** How deep folders may nest; a path deeper than this is cut down to it. */
    public static final int MAX_DEPTH = 8;

    /**
     * A user-supplied path made canonical: segments trimmed, empty ones dropped, depth capped.
     * The empty string is the root, which is no folder at all.
     */
    public static String normalize(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int depth = 0;

        for (String segment : raw.split(SEPARATOR))
        {
            String trimmed = segment.trim();

            if (trimmed.isEmpty())
            {
                continue;
            }

            if (depth >= MAX_DEPTH)
            {
                break;
            }

            if (depth > 0)
            {
                builder.append(SEPARATOR);
            }

            builder.append(trimmed);

            depth += 1;
        }

        return builder.toString();
    }

    /** The folder's own name — the last segment of its path. */
    public static String name(String path)
    {
        int index = path.lastIndexOf(SEPARATOR);

        return index < 0 ? path : path.substring(index + 1);
    }

    /** The folder this one sits in; empty for a top-level folder. */
    public static String parent(String path)
    {
        int index = path.lastIndexOf(SEPARATOR);

        return index < 0 ? "" : path.substring(0, index);
    }

    /** A path from a folder and a name inside it. */
    public static String join(String parent, String name)
    {
        return normalize(parent.isEmpty() ? name : parent + SEPARATOR + name);
    }

    /** How many folders deep a path sits; 0 for the root. */
    public static int depth(String path)
    {
        if (path.isEmpty())
        {
            return 0;
        }

        int depth = 1;

        for (int i = 0; i < path.length(); i++)
        {
            if (path.charAt(i) == '/')
            {
                depth += 1;
            }
        }

        return depth;
    }

    /**
     * Whether a path is the given folder itself or lies somewhere inside it. The root
     * ({@code ""}) holds everything, so it answers for every path but itself.
     */
    public static boolean isInside(String path, String ancestor)
    {
        if (ancestor.isEmpty())
        {
            return !path.isEmpty();
        }

        return path.equals(ancestor) || path.startsWith(ancestor + SEPARATOR);
    }

    /**
     * The same path with the head {@code from} swapped for {@code to} — what renaming or moving a
     * folder does to everything inside it. Paths outside {@code from} are returned untouched.
     */
    public static String reparent(String path, String from, String to)
    {
        if (from.isEmpty() || !isInside(path, from))
        {
            return path;
        }

        return normalize(to + path.substring(from.length()));
    }
}
