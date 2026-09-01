package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.factory.IUnknownType;

/**
 * Stands in for a clip whose type this build doesn't know. See {@link IUnknownType}.
 *
 * <p>The common clip properties are read, so the clip keeps its place, layer and length on the
 * timeline instead of piling up at tick zero on top of everything else. What it does is another
 * matter — it does nothing — and on save it writes back exactly what it was read from.</p>
 */
public class UnknownClip extends Clip implements IUnknownType
{
    private final Link type;

    private MapType raw = new MapType();

    public UnknownClip(Link type)
    {
        super();

        this.type = type;
    }

    @Override
    public Link getUnknownType()
    {
        return this.type;
    }

    @Override
    protected Clip create()
    {
        return new UnknownClip(this.type);
    }

    @Override
    public Clip copy()
    {
        UnknownClip clip = new UnknownClip(this.type);

        clip.fromData(this.raw.copy());

        return clip;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            this.raw = (MapType) map.copy();
        }

        super.fromData(data);
    }

    @Override
    public BaseType toData()
    {
        return this.raw.copy();
    }
}
