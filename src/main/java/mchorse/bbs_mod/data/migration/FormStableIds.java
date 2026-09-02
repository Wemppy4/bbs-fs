package mchorse.bbs_mod.data.migration;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.utils.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The form-fragment half of the stable-id migration: gives every body part of a <em>raw</em> form
 * a {@link StableIds stable id} and rewrites the form's own animation-state track keys from legacy
 * part indices to those ids.
 *
 * <p>It is separate from the film migration because a form is not a film's private property — the
 * same fragment lives in model blocks, morphs, items and presets, none of which pass the document
 * version gate. Those containers call {@link #ensure(MapType)} from the form factory instead, where
 * "a part without an id" is itself the legacy marker. The operation is idempotent: parts that
 * already carry stable ids and keys that already start with them are left untouched.
 */
public class FormStableIds
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Ensure ids and rewrite state track keys, recursively through nested body parts.
     *
     * @return the mapping from every legacy index path (relative to this form, e.g. {@code "0/2"})
     *         to the corresponding stable-id path — what the caller needs to rewrite track keys it
     *         keeps <em>outside</em> the form (a replay's property tracks).
     */
    public static Map<String, String> ensure(MapType form)
    {
        Map<String, String> mapping = new LinkedHashMap<>();

        if (form == null)
        {
            return mapping;
        }

        ListType parts = getParts(form);

        /* Ids that survived from an earlier run (or a partially converted source) are taken, not
         * regenerated — regenerating would orphan every track key already written against them. */
        List<String> taken = new ArrayList<>();

        for (BaseType type : parts)
        {
            if (type.isMap() && StableIds.isStableId(type.asMap().getString(StableIds.KEY)))
            {
                taken.add(type.asMap().getString(StableIds.KEY));
            }
        }

        for (int i = 0; i < parts.size(); i++)
        {
            if (!parts.get(i).isMap())
            {
                continue;
            }

            MapType part = parts.get(i).asMap();
            String id = part.getString(StableIds.KEY);

            if (!StableIds.isStableId(id))
            {
                do
                {
                    id = StableIds.generate();
                }
                while (taken.contains(id));

                taken.add(id);
                part.putString(StableIds.KEY, id);
            }

            mapping.put(String.valueOf(i), id);

            if (part.has("form"))
            {
                Map<String, String> nested = ensure(part.getMap("form"));

                for (Map.Entry<String, String> entry : nested.entrySet())
                {
                    mapping.put(i + "/" + entry.getKey(), id + "/" + entry.getValue());
                }
            }
        }

        if (form.has("states"))
        {
            for (BaseType stateType : form.getList("states"))
            {
                if (stateType.isMap() && stateType.asMap().has("properties"))
                {
                    rewriteFormPaths(stateType.asMap().getMap("properties"), mapping);
                }
            }
        }

        return mapping;
    }

    /**
     * The raw form's body part list: {@code parts}, or the pre-1.x {@code bodyParts.parts} nest
     * (the same legacy shape {@code Form.fromData} unwraps — but this runs before it).
     */
    private static ListType getParts(MapType form)
    {
        if (form.has("parts"))
        {
            return form.getList("parts");
        }

        if (form.has("bodyParts") && form.getMap("bodyParts").has("parts"))
        {
            return form.getMap("bodyParts").getList("parts");
        }

        return new ListType();
    }

    /** Key the track list is stored under since the track refactor; its absence means the older map shape. */
    public static final String TRACKS = "tracks";

    /**
     * The form path every track in a properties map is authored against, rewritten from body part
     * indices to stable ids.
     *
     * <p>Two shapes are in the wild and neither carries a format version, so both are handled: the
     * track list written since the track refactor, where the path is an entry's {@code "form"}
     * field, and the older map whose very key started with the index path.</p>
     */
    public static void rewriteFormPaths(MapType properties, Map<String, String> mapping)
    {
        if (!isTrackList(properties))
        {
            rewriteTrackKeys(properties, mapping);

            return;
        }

        for (BaseType entryType : properties.getList(TRACKS))
        {
            if (!entryType.isMap())
            {
                continue;
            }

            MapType entry = entryType.asMap();
            String form = entry.getString("form");

            if (form != null && !form.isEmpty())
            {
                entry.putString("form", rewriteTrackKey(form, mapping));
            }
        }
    }

    public static boolean isTrackList(MapType properties)
    {
        return properties.has(TRACKS) && properties.get(TRACKS).isList();
    }

    /**
     * Rewrite every track key's leading index segments ({@code "0/2/pose"}) into stable-id
     * segments. A key whose index path no longer resolves is an orphan already and is left as it
     * is — a track with a missing target sits idle rather than disappearing.
     */
    public static void rewriteTrackKeys(MapType tracks, Map<String, String> mapping)
    {
        Map<String, String> renames = new LinkedHashMap<>();

        for (String key : tracks.keys())
        {
            String rewritten = rewriteTrackKey(key, mapping);

            if (!rewritten.equals(key))
            {
                renames.put(key, rewritten);
            }
        }

        for (Map.Entry<String, String> entry : renames.entrySet())
        {
            BaseType value = tracks.get(entry.getKey());

            tracks.remove(entry.getKey());
            tracks.put(entry.getValue(), value);
        }
    }

    /** Rewrite one key's leading index segments; also used for anchor attachment paths. */
    public static String rewriteTrackKey(String key, Map<String, String> mapping)
    {
        String[] segments = key.split("/");
        int indices = 0;

        while (indices < segments.length && StringUtils.isInteger(segments[indices]))
        {
            indices += 1;
        }

        if (indices == 0)
        {
            return key;
        }

        String indexPath = String.join("/", java.util.Arrays.copyOfRange(segments, 0, indices));
        String idPath = mapping.get(indexPath);

        if (idPath == null)
        {
            LOGGER.warn("Track key \"" + key + "\" points at body part [" + indexPath + "] which does not exist; leaving the orphan as is");

            return key;
        }

        String tail = String.join("/", java.util.Arrays.copyOfRange(segments, indices, segments.length));

        return tail.isEmpty() ? idPath : idPath + "/" + tail;
    }
}
