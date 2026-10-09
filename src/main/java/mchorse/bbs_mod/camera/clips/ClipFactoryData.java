package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.clips.converters.IClipConverter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

public class ClipFactoryData
{
    public final Icon icon;
    public final int color;
    public final Map<Link, IClipConverter<? extends Clip, ? extends Clip>> converters = new HashMap<>();

    /** The column of the palette this clip type is listed in, see {@link ClipCategories}. */
    public Link category;

    public ClipFactoryData(Icon icon, int color)
    {
        this.icon = icon;
        this.color = color & Colors.RGB;
    }

    public ClipFactoryData in(Link category)
    {
        this.category = category;

        return this;
    }

    public ClipFactoryData withConverter(Link to, IClipConverter<? extends Clip, ? extends Clip> converter)
    {
        this.converters.put(to, converter);

        return this;
    }
}