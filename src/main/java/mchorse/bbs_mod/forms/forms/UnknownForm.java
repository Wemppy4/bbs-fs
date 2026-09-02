package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.factory.IUnknownType;

/**
 * Stands in for a form whose type this build doesn't know. See {@link IUnknownType}.
 *
 * <p>The common form properties are still read, so the stand-in sits where the original sat and
 * a body part holding it keeps its shape. Nothing about it is authored, though: every edit would
 * be dropped by {@link #toData()} anyway, so its values stay off the timeline and it has neither
 * a renderer nor an editor panel — both look their factories up by class and are content to find
 * nothing.</p>
 */
public class UnknownForm extends Form implements IUnknownType
{
    private final Link type;

    private MapType raw = new MapType();

    public UnknownForm(Link type)
    {
        super();

        this.type = type;

        for (BaseValue value : this.getAll())
        {
            value.invisible();
        }
    }

    @Override
    public Link getUnknownType()
    {
        return this.type;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            /* Before super, which rewrites the map in place while migrating older forms. */
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
