package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the user set on a form category that isn't the category's own content: whether it's
 * expanded and how its forms are ordered. Keyed by category id and kept in one file, so the
 * asset categories (models, particles, mobs), which have no file of their own, remember it too.
 *
 * <p>The file used to hold just {@code id: visible}; that shape still reads.</p>
 */
public class CategoryPreferences
{
    private final List<ValueBoolean> visibility = new ArrayList<>();
    private final Map<String, FormSort> sorts = new HashMap<>();

    public ValueBoolean visible(String id)
    {
        return this.visible(id, true);
    }

    public ValueBoolean visible(String id, boolean defaultValue)
    {
        for (ValueBoolean visibility : this.visibility)
        {
            if (visibility.getId().equals(id))
            {
                return visibility;
            }
        }

        ValueBoolean value = new ValueBoolean(id, defaultValue);

        value.postCallback((v, f) ->
        {
            if (f != 1) this.write();
        });
        this.visibility.add(value);

        return value;
    }

    public FormSort sort(String id)
    {
        return this.sorts.getOrDefault(id, FormSort.MANUAL);
    }

    public void setSort(String id, FormSort sort)
    {
        if (sort == FormSort.MANUAL)
        {
            this.sorts.remove(id);
        }
        else
        {
            this.sorts.put(id, sort);
        }

        this.write();
    }

    public void remove(String id)
    {
        this.visibility.removeIf(visibility -> visibility.getId().equals(id));
        this.sorts.remove(id);

        this.write();
    }

    public void read()
    {
        try
        {
            BaseType data = DataToString.read(BBSMod.getSettingsPath("categories.json"));

            if (data instanceof MapType map)
            {
                for (String key : map.keys())
                {
                    BaseType entry = map.get(key);

                    if (entry.isMap())
                    {
                        MapType prefs = entry.asMap();

                        this.visible(key, prefs.getBool("visible", true)).set(prefs.getBool("visible", true), 1);

                        FormSort sort = FormSort.byId(prefs.getString("sort"));

                        if (sort != FormSort.MANUAL)
                        {
                            this.sorts.put(key, sort);
                        }
                    }
                    else
                    {
                        this.visible(key, map.getBool(key)).set(map.getBool(key), 1);
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void write()
    {
        MapType type = new MapType();

        for (ValueBoolean value : this.visibility)
        {
            MapType prefs = new MapType();

            prefs.putBool("visible", value.get());

            FormSort sort = this.sorts.get(value.getId());

            if (sort != null)
            {
                prefs.putString("sort", sort.id);
            }

            type.put(value.getId(), prefs);
        }

        DataToString.writeSilently(BBSMod.getSettingsPath("categories.json"), type, true);
    }
}
