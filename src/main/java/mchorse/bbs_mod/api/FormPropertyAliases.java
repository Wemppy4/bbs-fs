package mchorse.bbs_mod.api;

import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import java.util.HashMap;
import java.util.Map;

/** Read aliases for migrating addon form values AND their animation tracks to namespaced ids.
 * Register on both sides during addon initialization, before loading documents. Writes use only
 * the canonical id. A canonical value/track wins when both spellings occur in the same document.
 * A document must be loaded and saved with the addon installed once to migrate legacy data. */
public final class FormPropertyAliases
{
    private static final Map<String, String> ALIASES = new HashMap<>();

    public static void register(String legacy, String canonical)
    {
        if (legacy == null || legacy.codePoints().allMatch(Character::isWhitespace) || canonical == null || canonical.indexOf(':') <= 0
            || canonical.endsWith(":") || legacy.contains("/") || canonical.contains("/")
            || legacy.equals(canonical) || ALIASES.containsKey(canonical) || ALIASES.containsValue(legacy))
        {
            throw new IllegalArgumentException("Expected a direct alias to a namespaced form property");
        }
        String previous = ALIASES.putIfAbsent(legacy, canonical);
        if (previous != null && !previous.equals(canonical)) throw new IllegalArgumentException("Conflicting property alias: " + legacy);
    }

    public static String resolve(String property)
    {
        return ALIASES.getOrDefault(property, property);
    }

    public static TrackId resolve(TrackId track)
    {
        if (track == null || track.kind() != TrackKind.PROPERTY) return track;
        String subject = resolve(track.subject());
        return subject.equals(track.subject()) ? track : TrackId.property(track.formPath(), subject);
    }

    private FormPropertyAliases() {}
}
