package mchorse.bbs_mod.utils.factory;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public interface IFactory <T, D>
{
    public Link getType(T object);

    public T create(Link type);

    /**
     * A stand-in for a type this factory doesn't know, or null to let the failure through.
     *
     * <p>Whoever implements this is promising that the stand-in writes the data back out
     * unchanged — see {@link IUnknownType} for why that matters.</p>
     */
    public default T createUnknown(Link type, MapType data)
    {
        return null;
    }

    public default MapType toData(T object)
    {
        MapType data = new MapType();

        if (object instanceof IDataSerializable)
        {
            BaseType baseData = ((IDataSerializable) object).toData();

            if (baseData.isMap())
            {
                data = baseData.asMap();
            }

            this.appendId(object, data);
        }

        return data;
    }

    public default void appendId(T object, MapType data)
    {
        data.putString(this.getTypeKey(), this.getType(object).toString());
    }

    public default T fromData(MapType data)
    {
        if (data == null)
        {
            return null;
        }

        Link type = Link.create(data.getString(this.getTypeKey()));

        if (type.path.isEmpty())
        {
            return null;
        }

        T object;

        try
        {
            object = this.create(type);
        }
        catch (IllegalStateException e)
        {
            object = this.createUnknown(type, data);

            if (object == null)
            {
                throw e;
            }
        }

        if (object instanceof IDataSerializable)
        {
            ((IDataSerializable) object).fromData(data);
        }

        return object;
    }

    public default String getTypeKey()
    {
        return "type";
    }

    public D getData(T object);

    public D getData(Link type);

    public Collection<Link> getKeys();

    public default Collection<String> getStringKeys()
    {
        Set<String> keys = new HashSet<>();

        for (Link link : this.getKeys())
        {
            keys.add(link.toString());
        }

        return keys;
    }
}
