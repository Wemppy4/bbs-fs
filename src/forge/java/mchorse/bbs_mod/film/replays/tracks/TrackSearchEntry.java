package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A search result keeps an address, never a timeline row that a rebuild can invalidate. */
@com.github.bsideup.jabel.Desugar
public record TrackSearchEntry(String key, String path, boolean owned, String title,
    String original, String location, int color, Icon icon, boolean hidden)
{
    @com.github.bsideup.jabel.Desugar
    private record Match(TrackSearchEntry entry, int rank) {}

    public String identity()
    {
        return (this.owned ? "form:" : "replay:") + this.key;
    }

    public static String normalize(String text)
    {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim().replaceAll("\\s+", " ");
    }

    private int rank(String query)
    {
        int direct = this.rank(query, false);
        if (direct >= 0) return direct;
        int remapped = this.rank(query, true);
        return remapped < 0 ? -1 : remapped + 4;
    }

    private static String searchable(String text, boolean remap)
    {
        return normalize(remap ? KeyCodes.cyrillicToQwerty(text.toLowerCase(Locale.ROOT)) : text);
    }

    private int rank(String query, boolean remap)
    {
        query = searchable(query, remap);
        String name = searchable(this.title, remap);
        String source = searchable(this.original, remap);
        String all = name + " " + source + " " + searchable(this.location, remap) + " " + searchable(this.key, remap);

        for (String word : query.split(" "))
        {
            if (!all.contains(word)) return -1;
        }

        if (name.equals(query) || source.equals(query)) return 0;
        if (name.startsWith(query) || source.startsWith(query)) return 1;
        if (name.contains(query) || source.contains(query)) return 2;
        return 3;
    }

    public static List<TrackSearchEntry> find(List<TrackSearchEntry> entries, String text,
        String currentPath, List<String> recent)
    {
        String query = normalize(text);

        if (query.isEmpty())
        {
            return entries.stream().sorted(Comparator
                .comparingInt((TrackSearchEntry entry) ->
                {
                    int index = recent.indexOf(entry.identity());
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparingInt(entry -> entry.owned && entry.path.equals(currentPath) ? 0 : 1)).collect(java.util.stream.Collectors.toList());
        }

        return entries.stream().map(entry -> new Match(entry, entry.rank(text.trim())))
            .filter(match -> match.rank >= 0)
            .sorted(Comparator.comparingInt(Match::rank)
                .thenComparingInt(match -> match.entry.owned && match.entry.path.equals(currentPath) ? 0 : 1))
            .map(Match::entry).collect(java.util.stream.Collectors.toList());
    }
}
