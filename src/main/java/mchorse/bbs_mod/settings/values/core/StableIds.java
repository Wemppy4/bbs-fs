package mchorse.bbs_mod.settings.values.core;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Identity of a list element that survives insertion, removal and reordering — what replaces the
 * "id = position in the list" scheme of {@link ValueList#sync()}. Everything that used to address a
 * replay or a body part by its index (property track keys, anchors, camera-clip selectors) addresses
 * it by this id instead, so reordering the list no longer silently retargets the data.
 *
 * <p>The id lives <em>in the saved data</em> (a {@code "id"} key inside the element's map), not in
 * runtime state. That is not an implementation detail: the client and the server load the same film
 * file independently, and both must arrive at the same ids for cross-references (action actors,
 * sync-by-path) to keep meaning the same thing. A random id generated at load time would differ
 * per side, so legacy conversion uses {@link #fromLegacyIndex} until those ids are saved.
 *
 * <p>The format is eight lowercase hex chars with at least one letter. Eight hex chars keep track
 * keys readable; the mandatory letter guarantees an id never parses as an integer, so it can never
 * be mistaken for a legacy index by anything that still distinguishes the two (converters, track
 * name shortening).
 */
public final class StableIds
{
    /** Key an element's stable id is stored under inside its own map. */
    public static final String KEY = "id";

    private static final int LENGTH = 8;

    private StableIds()
    {}

    public static String generate()
    {
        while (true)
        {
            String id = String.format("%08x", ThreadLocalRandom.current().nextInt());

            /* All-digit ids (2.3% of draws) are rerolled so no id ever looks like an index. */
            if (hasLetter(id))
            {
                return id;
            }
        }
    }

    public static boolean isStableId(String id)
    {
        if (id == null || id.length() != LENGTH || !hasLetter(id))
        {
            return false;
        }

        for (int i = 0; i < id.length(); i++)
        {
            char c = id.charAt(i);

            if ((c < '0' || c > '9') && (c < 'a' || c > 'f'))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Repeatable identity for an unsaved legacy list. Independent readers must agree even before
     * the user saves the converted document. Ids are scoped to their owning list; once saved they
     * are preserved, and newly inserted elements still use {@link #generate()}.
     */
    public static String fromLegacyIndex(int index, Collection<String> taken)
    {
        for (int attempt = 0; ; attempt++)
        {
            String seed = "bbs:legacy-list:" + index + ":" + attempt;
            String id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().substring(0, LENGTH);

            if (isStableId(id) && !taken.contains(id))
            {
                return id;
            }
        }
    }

    private static boolean hasLetter(String id)
    {
        for (int i = 0; i < id.length(); i++)
        {
            char c = id.charAt(i);

            if (c >= 'a' && c <= 'f')
            {
                return true;
            }
        }

        return false;
    }
}
