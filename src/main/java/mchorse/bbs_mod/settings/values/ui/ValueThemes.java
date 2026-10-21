package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Themes the user saved by name, in the order they were saved. A theme is
 * everything the interface is coloured from, see {@link Theme}.
 */
public class ValueThemes extends BaseValueBasic<Map<String, ValueThemes.Theme>>
{
    public ValueThemes(String id)
    {
        super(id, new LinkedHashMap<>());
    }

    /** Saving under a taken name replaces that theme in its place. */
    public void put(String name, Theme theme)
    {
        this.preNotify();
        this.value.put(name, theme);
        this.postNotify();
    }

    public void remove(String name)
    {
        if (this.value.containsKey(name))
        {
            this.preNotify();
            this.value.remove(name);
            this.postNotify();
        }
    }

    @Override
    public BaseType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, Theme> entry : this.value.entrySet())
        {
            Theme theme = entry.getValue();
            MapType data = new MapType();

            data.putInt("primary", theme.primary());
            data.putInt("secondary", theme.secondary());
            data.putInt("text", theme.text());
            data.putFloat("contrast", theme.contrast());
            map.put(entry.getKey(), data);
        }

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (!data.isMap())
        {
            return;
        }

        for (Map.Entry<String, BaseType> entry : data.asMap())
        {
            if (!entry.getValue().isMap())
            {
                continue;
            }

            MapType theme = entry.getValue().asMap();

            this.value.put(entry.getKey(), new Theme(
                theme.getInt("primary") & Colors.RGB,
                theme.getInt("secondary") & Colors.RGB,
                theme.getInt("text", Colors.RGB) & Colors.RGB,
                theme.getFloat("contrast", 1F)
            ));
        }
    }

    @Override
    protected Map<String, Theme> copyValue(Map<String, Theme> value)
    {
        return value == null ? null : new LinkedHashMap<>(value);
    }

    /**
     * The accent, the background the surfaces are stepped from, the colour of
     * text and icons, and how far apart the surface steps sit.
     */
    public record Theme(int primary, int secondary, int text, float contrast)
    {}
}
