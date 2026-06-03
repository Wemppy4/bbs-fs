package mchorse.bbs_mod.data.types;

import mchorse.bbs_mod.data.DataStorageContext;
import mchorse.bbs_mod.math.Operation;

import java.io.IOException;

public abstract class BaseType
{
    public static final byte TYPE_MAP = 0;
    public static final byte TYPE_LIST = 1;
    public static final byte TYPE_STRING = 2;
    public static final byte TYPE_BYTE = 3;
    public static final byte TYPE_SHORT = 4;
    public static final byte TYPE_INT = 5;
    public static final byte TYPE_FLOAT = 6;
    public static final byte TYPE_LONG = 7;
    public static final byte TYPE_DOUBLE = 8;
    public static final byte TYPE_BYTE_ARRAY = 9;
    public static final byte TYPE_SHORT_ARRAY = 10;
    public static final byte TYPE_INT_ARRAY = 11;

    public static BaseType fromData(DataStorageContext context) throws IOException
    {
        byte type = context.in.readByte();
        BaseType output = null;

        if (type == TYPE_MAP) output = new MapType();
        else if (type == TYPE_LIST) output = new ListType();
        else if (type == TYPE_STRING) output = new StringType();
        else if (type == TYPE_BYTE) output = new ByteType();
        else if (type == TYPE_SHORT) output = new ShortType();
        else if (type == TYPE_INT) output = new IntType();
        else if (type == TYPE_FLOAT) output = new FloatType();
        else if (type == TYPE_LONG) output = new LongType();
        else if (type == TYPE_DOUBLE) output = new DoubleType();
        else if (type == TYPE_BYTE_ARRAY) output = new ByteArrayType();
        else if (type == TYPE_SHORT_ARRAY) output = new ShortArrayType();
        else if (type == TYPE_INT_ARRAY) output = new IntArrayType();

        if (output != null)
        {
            output.read(context);

            return output;
        }

        throw new IllegalStateException("Data type " + type + " doesn't exist!");
    }

    public static void toData(DataStorageContext context, BaseType type) throws IOException
    {
        context.out.writeByte(type.getTypeId());
        type.write(context);
    }

    public static boolean isMap(BaseType data)
    {
        return is(data, TYPE_MAP);
    }

    public static boolean isList(BaseType data)
    {
        return is(data, TYPE_LIST);
    }

    public static boolean isString(BaseType data)
    {
        return is(data, TYPE_STRING);
    }

    public static boolean isNumeric(BaseType data)
    {
        return data instanceof NumericType;
    }

    public static boolean isPrimitive(BaseType data)
    {
        return isString(data) || isNumeric(data);
    }

    public static boolean is(BaseType data, byte type)
    {
        return data != null && data.getTypeId() == type;
    }

    /**
     * Lenient structural comparison of two {@link BaseType} trees.
     *
     * <p>Unlike the default {@link Object#equals(Object)} on the data types, this
     * treats <em>all</em> numbers as {@code double}: a {@link FloatType}
     * and a {@link DoubleType} (or int, long, etc.) holding the
     * same numeric value compare equal. That matters because data round-tripped
     * through JSON comes back as doubles, while the in-memory structure stores
     * floats — so a plain {@code equals()} reports them as different even though
     * they represent the same value.</p>
     *
     * <p>Maps are compared by matching keys with recursively-equal values, lists
     * element-wise, and everything else (strings, arrays) falls back to the
     * type's own {@code equals()}.</p>
     */
    public static boolean equals(BaseType a, BaseType b)
    {
        if (a == b)
        {
            return true;
        }

        if (a == null || b == null)
        {
            return false;
        }

        if (a.isNumeric() && b.isNumeric())
        {
            return Operation.equals(a.asNumeric().doubleValue(), b.asNumeric().doubleValue());
        }

        if (a.isMap() && b.isMap())
        {
            MapType mapA = a.asMap();
            MapType mapB = b.asMap();

            if (mapA.size() != mapB.size())
            {
                return false;
            }

            for (String key : mapA.keys())
            {
                if (!mapB.has(key) || !equals(mapA.get(key), mapB.get(key)))
                {
                    return false;
                }
            }

            return true;
        }

        if (a.isList() && b.isList())
        {
            ListType listA = a.asList();
            ListType listB = b.asList();

            if (listA.size() != listB.size())
            {
                return false;
            }

            for (int i = 0; i < listA.size(); i++)
            {
                if (!equals(listA.get(i), listB.get(i)))
                {
                    return false;
                }
            }

            return true;
        }

        return a.equals(b);
    }

    public void traverseKeys(DataStorageContext context)
    {}

    public boolean isMap()
    {
        return this instanceof MapType;
    }

    public boolean isList()
    {
        return this instanceof ListType;
    }

    public boolean isString()
    {
        return this instanceof StringType;
    }

    public boolean isNumeric()
    {
        return this instanceof NumericType;
    }

    public MapType asMap()
    {
        return (MapType) this;
    }

    public ListType asList()
    {
        return (ListType) this;
    }

    public String asString()
    {
        return ((StringType) this).value;
    }

    public NumericType asNumeric()
    {
        return (NumericType) this;
    }

    public abstract byte getTypeId();

    public abstract BaseType copy();

    public abstract void read(DataStorageContext context) throws IOException;

    public abstract void write(DataStorageContext context) throws IOException;
}