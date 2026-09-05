package mchorse.bbs_mod.utils.categories;

import mchorse.bbs_mod.utils.NaturalOrderComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shape of a folder tree, worked out from the two things that know about it: the folders the
 * data records, and the paths its items happen to mention.
 *
 * <p>Neither half is authoritative on its own. Records carry the order and the folders nobody has
 * filed anything into yet; the items carry folders brought in from elsewhere, which have no record
 * here and would otherwise not be shown at all. This puts the two together, and leaves drawing the
 * rows to whoever is drawing rows.</p>
 */
public class CategoryTree
{
    /**
     * Every folder there is, ancestors always before their children: the recorded ones in the order
     * they are recorded, then the ones only an item mentions, by name. A path nobody recorded is a
     * folder all the same; it just has no say in the order until someone moves it.
     *
     * @param used the folder paths the items are in; the root ({@code ""}) is ignored
     */
    public static List<String> paths(Categories categories, Collection<String> used)
    {
        Set<String> ordered = new LinkedHashSet<>();

        for (String path : categories.getPaths())
        {
            addWithAncestors(ordered, path);
        }

        Set<String> mentioned = new TreeSet<>((a, b) -> NaturalOrderComparator.compare(true, a, b));

        for (String path : used)
        {
            String normalized = CategoryPath.normalize(path);

            if (!normalized.isEmpty() && !ordered.contains(normalized))
            {
                mentioned.add(normalized);
            }
        }

        for (String path : mentioned)
        {
            addWithAncestors(ordered, path);
        }

        return new ArrayList<>(ordered);
    }

    /** The same paths seen as "what lies directly in each folder", keeping the order they came in. */
    public static Map<String, List<String>> byParent(List<String> paths)
    {
        Map<String, List<String>> children = new HashMap<>();

        for (String path : paths)
        {
            children.computeIfAbsent(CategoryPath.parent(path), (key) -> new ArrayList<>()).add(path);
        }

        return children;
    }

    private static void addWithAncestors(Set<String> ordered, String path)
    {
        if (path.isEmpty() || ordered.contains(path))
        {
            return;
        }

        addWithAncestors(ordered, CategoryPath.parent(path));
        ordered.add(path);
    }
}
