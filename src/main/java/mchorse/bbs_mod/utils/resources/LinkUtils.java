package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;

public class LinkUtils
{
    public static Link create(String path)
    {
        return path.isEmpty() ? null : Link.create(path);
    }

    /**
     * A synthetic link that resolves to a 1x1 texture of the given color, used as the
     * default texture for flat-color materials. The color is ARGB-hex encoded into the
     * link's path; {@link mchorse.bbs_mod.resources.Link#COLOR} marks the source and the
     * texture manager generates the pixels on demand.
     */
    public static Link color(float r, float g, float b)
    {
        int argb = new Color().set(r, g, b, 1F).getARGBColor();

        return new Link(Link.COLOR, Integer.toHexString(argb));
    }

    public static Link create(String domain, String path)
    {
        return new Link(domain, path);
    }

    public static Link create(BaseType data)
    {
        Link location = MultiLink.from(data);

        if (location != null)
        {
            return location;
        }

        if (BaseType.isString(data))
        {
            return create(data.asString());
        }

        return null;
    }

    public static BaseType toData(Link link)
    {
        if (link instanceof IWritableLink)
        {
            return ((IWritableLink) link).toData();
        }
        else if (link != null)
        {
            return new StringType(link.toString());
        }

        return null;
    }

    public static Link copy(Link link)
    {
        if (link instanceof IWritableLink)
        {
            return ((IWritableLink) link).copy();
        }
        else if (link != null)
        {
            return create(link.toString());
        }

        return null;
    }
}