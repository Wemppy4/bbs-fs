package mchorse.bbs_mod.data;
import mchorse.bbs_mod.data.types.*;
import org.joml.*;
import net.minecraft.nbt.*;
import java.util.*;
public class DataStorageUtils {
    public static NBTBase toNbt(BaseType type)
    {
        if (type instanceof ByteType byteType)
        {
            return new NBTTagByte(byteType.value);
        }
        else if (type instanceof DoubleType doubleType)
        {
            return new NBTTagDouble(doubleType.value);
        }
        else if (type instanceof FloatType floatType)
        {
            return new NBTTagFloat(floatType.value);
        }
        else if (type instanceof IntType intType)
        {
            return new NBTTagInt(intType.value);
        }
        else if (type instanceof LongType longType)
        {
            return new NBTTagLong(longType.value);
        }
        else if (type instanceof ShortType shortType)
        {
            return new NBTTagShort(shortType.value);
        }
        else if (type instanceof StringType stringType)
        {
            return new NBTTagString(stringType.value);
        }
        else if (type instanceof ByteArrayType byteArrayType)
        {
            return new NBTTagByteArray(byteArrayType.value);
        }
        else if (type instanceof IntArrayType intArrayType)
        {
            return new NBTTagIntArray(intArrayType.value);
        }
        else if (type instanceof LongArrayType longArrayType)
        {
            return new NBTTagLongArray(longArrayType.value);
        }
        else if (type instanceof ShortArrayType shortArrayType)
        {
            /* NBT has no short array, so widen to an int array (lossless). */
            int[] ints = new int[shortArrayType.value.length];

            for (int i = 0; i < ints.length; i++)
            {
                ints[i] = shortArrayType.value[i];
            }

            return new NBTTagIntArray(ints);
        }
        else if (type instanceof ListType listType)
        {
            NBTTagList list = new NBTTagList();

            for (BaseType baseType : listType)
            {
                NBTBase converted = toNbt(baseType);

                if (converted != null)
                {
                    list.appendTag(converted);
                }
            }

            return list;
        }
        else if (type instanceof MapType mapType)
        {
            NBTTagCompound compound = new NBTTagCompound();

            for (String key : mapType.keys())
            {
                NBTBase converted = toNbt(mapType.get(key));

                if (converted != null)
                {
                    compound.setTag(key, converted);
                }
            }

            return compound;
        }

        return null;
    }

    public static BaseType fromNbt(NBTBase element)
    {
        if (element instanceof NBTTagByte nbtByte)
        {
            return new ByteType(nbtByte.getByte());
        }
        else if (element instanceof NBTTagDouble nbtDouble)
        {
            return new DoubleType(nbtDouble.getDouble());
        }
        else if (element instanceof NBTTagFloat nbtFloat)
        {
            return new FloatType(nbtFloat.getFloat());
        }
        else if (element instanceof NBTTagInt nbtInt)
        {
            return new IntType(nbtInt.getInt());
        }
        else if (element instanceof NBTTagLong nbtLong)
        {
            return new LongType(nbtLong.getLong());
        }
        else if (element instanceof NBTTagShort nbtShort)
        {
            return new ShortType(nbtShort.getShort());
        }
        else if (element instanceof NBTTagString nbtString)
        {
            return new StringType(nbtString.getString());
        }
        else if (element instanceof NBTTagByteArray nbtByteArray)
        {
            return new ByteArrayType(nbtByteArray.getByteArray());
        }
        else if (element instanceof NBTTagIntArray nbtIntArray)
        {
            return new IntArrayType(nbtIntArray.getIntArray());
        }
        else if (element instanceof NBTTagLongArray nbtLongArray)
        {
            return new LongArrayType(longArray(nbtLongArray));
        }
        else if (element instanceof NBTTagList nbtList)
        {
            ListType list = new ListType();

            for (int i = 0; i < nbtList.tagCount(); i++)
            {
                BaseType converted = fromNbt(nbtList.get(i));

                if (converted != null)
                {
                    list.add(converted);
                }
            }

            return list;
        }
        else if (element instanceof NBTTagCompound nbtCompound)
        {
            MapType map = new MapType();

            for (String key : nbtCompound.getKeySet())
            {
                BaseType converted = fromNbt(nbtCompound.getTag(key));

                if (converted != null)
                {
                    map.put(key, converted);
                }
            }

            return map;
        }

        return null;
    }

    public static void writeToNBTTagCompound(NBTTagCompound compound, String key, BaseType data)
    {
        compound.setTag(key, DataStorageUtils.toNbt(data));
    }

    public static BaseType readFromNBTTagCompound(NBTTagCompound compound, String key)
    {
        BaseType baseType = DataStorageUtils.fromNbt(compound.getTag(key));

        if (baseType != null)
        {
            return baseType;
        }

        return null;
    }

    private static long[] longArray(NBTTagLongArray tag)
    {
        // 1.12.2 has no public long-array accessor. Locate the only long[] field,
        // which is stable under MCP/SRG obfuscation, and return an independent copy.
        for (java.lang.reflect.Field field : NBTTagLongArray.class.getDeclaredFields())
        {
            if (field.getType() == long[].class)
            {
                try { field.setAccessible(true); return ((long[]) field.get(tag)).clone(); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read NBT long array", e); }
            }
        }
        throw new IllegalStateException("Minecraft NBT long-array field is missing");
    }

    /* Vector2i */

    public static ListType vector2iToData(Vector2i vector)
    {
        ListType list = new ListType();

        list.addInt(vector.x);
        list.addInt(vector.y);

        return list;
    }

    public static Vector2i vector2iFromData(ListType element)
    {
        return vector2iFromData(element, new Vector2i());
    }

    public static Vector2i vector2iFromData(ListType element, Vector2i defaultValue)
    {
        if (element != null && element.size() >= 2)
        {
            return new Vector2i(element.getInt(0), element.getInt(1));
        }

        return defaultValue;
    }

    /* Vector3f */

    public static ListType vector3fToData(Vector3f vector)
    {
        ListType list = new ListType();

        list.addFloat(vector.x);
        list.addFloat(vector.y);
        list.addFloat(vector.z);

        return list;
    }

    public static Vector3f vector3fFromData(ListType element)
    {
        return vector3fFromData(element, new Vector3f());
    }

    public static Vector3f vector3fFromData(ListType element, Vector3f defaultValue)
    {
        if (element != null && element.size() >= 3)
        {
            return new Vector3f(element.getFloat(0), element.getFloat(1), element.getFloat(2));
        }

        return defaultValue;
    }

    /* Quaternionf (stored x, y, z, w) */

    public static ListType quaternionfToData(Quaternionf quaternion)
    {
        ListType list = new ListType();

        list.addFloat(quaternion.x);
        list.addFloat(quaternion.y);
        list.addFloat(quaternion.z);
        list.addFloat(quaternion.w);

        return list;
    }

    public static Quaternionf quaternionfFromData(ListType element)
    {
        return quaternionfFromData(element, new Quaternionf());
    }

    public static Quaternionf quaternionfFromData(ListType element, Quaternionf defaultValue)
    {
        if (element != null && element.size() >= 4)
        {
            return new Quaternionf(element.getFloat(0), element.getFloat(1), element.getFloat(2), element.getFloat(3));
        }

        return defaultValue;
    }

    /* Vector3d */

    public static ListType vector3dToData(Vector3d vector)
    {
        ListType list = new ListType();

        list.addDouble(vector.x);
        list.addDouble(vector.y);
        list.addDouble(vector.z);

        return list;
    }

    public static Vector3d vector3dFromData(ListType element)
    {
        return vector3dFromData(element, new Vector3d());
    }

    public static Vector3d vector3dFromData(ListType element, Vector3d defaultValue)
    {
        if (element != null && element.size() >= 3)
        {
            return new Vector3d(element.getDouble(0), element.getDouble(1), element.getDouble(2));
        }

        return defaultValue;
    }

    /* Vector4f */

    public static ListType vector4fToData(Vector4f vector)
    {
        ListType list = new ListType();

        list.addFloat(vector.x);
        list.addFloat(vector.y);
        list.addFloat(vector.z);
        list.addFloat(vector.w);

        return list;
    }

    public static Vector4f vector4fFromData(ListType element)
    {
        return vector4fFromData(element, new Vector4f());
    }

    public static Vector4f vector4fFromData(ListType element, Vector4f defaultValue)
    {
        if (element != null && element.size() >= 4)
        {
            return new Vector4f(element.getFloat(0), element.getFloat(1), element.getFloat(2), element.getFloat(3));
        }

        return defaultValue;
    }

    /* Matrix3f */

    public static ListType matrix3fToData(Matrix3f matrix)
    {
        ListType list = new ListType();

        list.addFloat(matrix.m00);
        list.addFloat(matrix.m01);
        list.addFloat(matrix.m02);
        list.addFloat(matrix.m10);
        list.addFloat(matrix.m11);
        list.addFloat(matrix.m12);
        list.addFloat(matrix.m20);
        list.addFloat(matrix.m21);
        list.addFloat(matrix.m22);

        return list;
    }

    public static Matrix3f matrix3fFromData(ListType element)
    {
        return matrix3fFromData(element, new Matrix3f());
    }

    public static Matrix3f matrix3fFromData(ListType element, Matrix3f defaultValue)
    {
        if (element != null && element.size() >= 9)
        {
            return new Matrix3f(
                element.getFloat(0), element.getFloat(1), element.getFloat(2),
                element.getFloat(3), element.getFloat(4), element.getFloat(5),
                element.getFloat(6), element.getFloat(7), element.getFloat(8)
            );
        }

        return defaultValue;
    }

    /* List<String> */

    public static ListType stringListToData(Collection<String> strings)
    {
        ListType list = new ListType();

        for (String string : strings)
        {
            list.addString(string);
        }

        return list;
    }

    public static List<String> stringListFromData(BaseType type)
    {
        ArrayList<String> strings = new ArrayList<>();

        if (type.isList())
        {
            for (BaseType baseType : type.asList())
            {
                if (baseType.isString())
                {
                    strings.add(baseType.asString());
                }
            }
        }

        return strings;
    }

    public static ListType intListToData(Collection<Integer> ints)
    {
        ListType list = new ListType();

        for (Integer i : ints)
        {
            list.addInt(i);
        }

        return list;
    }

    public static List<Integer> intListFromData(BaseType type)
    {
        ArrayList<Integer> ints = new ArrayList<>();

        if (type.isList())
        {
            for (BaseType baseType : type.asList())
            {
                if (baseType.isNumeric())
                {
                    ints.add(baseType.asNumeric().intValue());
                }
            }
        }

        return ints;
    }
}