package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * User overrides for timeline tracks: a custom name and/or a custom colour, stored per track key.
 *
 * The key is the same one the track filters use ({@code UIKeyframeSheet#getFilterKey()}), so a track
 * is identified by what kind of track it is rather than by which film it sits in. That is what makes
 * the override global: renaming {@code extra1_x} once names it in every film, every replay and every
 * keyframe editor that shows that track.
 *
 * An absent field means "keep the default": an empty name falls back to the track's built-in title,
 * a missing colour to its built-in colour. Both empty means the entry is dropped entirely.
 */
public class ValueTrackStyles extends BaseValueBasic<Map<String, ValueTrackStyles.Style>>
{
    public ValueTrackStyles(String id)
    {
        super(id, new LinkedHashMap<>());
    }

    public Style getStyle(String key)
    {
        return key == null ? null : this.value.get(key);
    }

    public boolean has(String key)
    {
        return this.getStyle(key) != null;
    }

    /** Custom name for the track, or {@code fallback} when it wasn't renamed. */
    public String name(String key, String fallback)
    {
        Style style = this.getStyle(key);

        return style != null && !style.name.isEmpty() ? style.name : fallback;
    }

    /** Custom colour for the track, or {@code fallback} when it wasn't recoloured. */
    public int color(String key, int fallback)
    {
        Style style = this.getStyle(key);

        return style != null && style.color != null ? style.color : fallback;
    }

    public void setName(String key, String name)
    {
        this.edit(key, (style) -> style.name = name == null ? "" : name.trim());
    }

    public void setColor(String key, Integer color)
    {
        this.edit(key, (style) -> style.color = color == null ? null : color & Colors.RGB);
    }

    public void reset(String key)
    {
        if (this.value.containsKey(key))
        {
            this.preNotify();
            this.value.remove(key);
            this.postNotify();
        }
    }

    private void edit(String key, Consumer<Style> editor)
    {
        if (key == null || key.isEmpty())
        {
            return;
        }

        Style style = this.value.get(key);

        this.preNotify();

        if (style == null)
        {
            style = new Style();
        }

        editor.accept(style);

        /* An entry that overrides nothing is just noise in the config */
        if (style.isEmpty())
        {
            this.value.remove(key);
        }
        else
        {
            this.value.put(key, style);
        }

        this.postNotify();
    }

    @Override
    public BaseType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, Style> entry : this.value.entrySet())
        {
            Style style = entry.getValue();
            MapType data = new MapType();

            if (!style.name.isEmpty())
            {
                data.putString("name", style.name);
            }

            if (style.color != null)
            {
                data.putInt("color", style.color);
            }

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

            MapType styleData = entry.getValue().asMap();
            Style style = new Style();

            style.name = styleData.getString("name", "");

            if (styleData.has("color"))
            {
                style.color = styleData.getInt("color") & Colors.RGB;
            }

            if (!style.isEmpty())
            {
                this.value.put(entry.getKey(), style);
            }
        }
    }

    public static class Style
    {
        public String name = "";
        public Integer color;

        public boolean isEmpty()
        {
            return this.name.isEmpty() && this.color == null;
        }
    }
}
