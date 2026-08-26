package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * What the user opened last in each of the data panels — films, particle effects, models —
 * newest first, so an empty tab can offer the way back into yesterday's work.
 *
 * <p>Keyed by content type id. An entry is the document's id and when it was last opened;
 * touching an id again moves it to the front. Nothing here looks at the disk: a rename or a
 * removal is mirrored by the panel that did it, the same way it mirrors its tabs, and whatever
 * went missing behind the panel's back is simply left out when the list is shown.</p>
 */
public class ValueRecentData extends BaseValue
{
    public static class Entry
    {
        public final String id;
        public final long time;

        public Entry(String id, long time)
        {
            this.id = id;
            this.time = time;
        }
    }

    private final Map<String, List<Entry>> recent = new LinkedHashMap<>();

    /** Entries kept per content type; the oldest fall off the end. */
    private int limit = 20;

    public ValueRecentData(String id)
    {
        super(id);
    }

    public ValueRecentData limit(int limit)
    {
        this.limit = limit;

        return this;
    }

    public List<Entry> get(String type)
    {
        List<Entry> entries = this.recent.get(type);

        return entries == null ? Collections.emptyList() : Collections.unmodifiableList(entries);
    }

    /** The document was just opened: to the front, stamped with now. */
    public void touch(String type, String id)
    {
        if (type == null || id == null)
        {
            return;
        }

        List<Entry> entries = this.recent.computeIfAbsent(type, (key) -> new ArrayList<>());

        this.preNotify();
        entries.removeIf((entry) -> entry.id.equals(id));
        entries.add(0, new Entry(id, System.currentTimeMillis()));

        while (this.limit > 0 && entries.size() > this.limit)
        {
            entries.remove(entries.size() - 1);
        }

        this.postNotify();
    }

    public void forget(String type, String id)
    {
        this.rewrite(type, (entryId) -> entryId.equals(id) ? null : entryId);
    }

    public void forgetFolder(String type, String path)
    {
        String prefix = path.endsWith("/") ? path : path + "/";

        this.rewrite(type, (id) -> id.startsWith(prefix) ? null : id);
    }

    public void rename(String type, String from, String to)
    {
        this.rewrite(type, (id) -> id.equals(from) ? to : id);
    }

    public void renameFolder(String type, String fromPath, String name)
    {
        String oldPrefix = fromPath + "/";
        int slash = fromPath.lastIndexOf('/');
        String parent = slash >= 0 ? fromPath.substring(0, slash + 1) : "";
        String newPrefix = parent + name + "/";

        this.rewrite(type, (id) -> id.startsWith(oldPrefix) ? newPrefix + id.substring(oldPrefix.length()) : id);
    }

    /** Map every id of a type; null drops the entry. The time of a renamed entry stays. */
    private void rewrite(String type, UnaryOperator<String> mapper)
    {
        List<Entry> entries = this.recent.get(type);

        if (entries == null)
        {
            return;
        }

        List<Entry> mapped = new ArrayList<>(entries.size());
        boolean changed = false;

        for (Entry entry : entries)
        {
            String id = mapper.apply(entry.id);

            if (id == null)
            {
                changed = true;
            }
            else if (!id.equals(entry.id))
            {
                mapped.add(new Entry(id, entry.time));
                changed = true;
            }
            else
            {
                mapped.add(entry);
            }
        }

        if (changed)
        {
            this.preNotify();
            entries.clear();
            entries.addAll(mapped);
            this.postNotify();
        }
    }

    @Override
    public BaseType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, List<Entry>> type : this.recent.entrySet())
        {
            ListType list = new ListType();

            for (Entry entry : type.getValue())
            {
                MapType data = new MapType();

                data.putString("id", entry.id);
                data.putLong("time", entry.time);
                list.add(data);
            }

            map.put(type.getKey(), list);
        }

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.recent.clear();

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        for (String type : map.keys())
        {
            BaseType value = map.get(type);

            if (!value.isList())
            {
                continue;
            }

            List<Entry> entries = new ArrayList<>();

            for (BaseType element : value.asList())
            {
                if (!element.isMap())
                {
                    continue;
                }

                MapType entry = element.asMap();
                String id = entry.getString("id", "");

                if (!id.isEmpty())
                {
                    entries.add(new Entry(id, entry.getLong("time", 0L)));
                }
            }

            this.recent.put(type, entries);
        }
    }
}
