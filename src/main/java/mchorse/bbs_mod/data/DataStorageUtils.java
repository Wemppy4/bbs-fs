package mchorse.bbs_mod.data;

import mchorse.bbs_mod.data.storage.DataStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.LongType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.ShortType;
import mchorse.bbs_mod.data.types.StringType;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import org.joml.Matrix3f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DataStorageUtils
{
    private static final byte[] EMPTY = new byte[0];

    /* PacketByteBuf */

    public static byte[] writeToBytes(BaseType type)
    {
        if (type == null)
        {
            return EMPTY;
        }

        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            DataStorage.writeToStream(stream, type);

            return stream.toByteArray();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return EMPTY;
    }

    public static BaseType readFromBytes(byte[] bytes)
    {
        if (bytes == null)
        {
            return null;
        }

        try
        {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

            return DataStorage.readFromStream(stream);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static void writeToPacket(PacketByteBuf packet, BaseType type)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            DataStorage.writeToStream(stream, type);

            packet.writeByteArray(stream.toByteArray());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static BaseType readFromPacket(PacketByteBuf packet)
    {
        try
        {
            ByteArrayInputStream stream = new ByteArrayInputStream(packet.readByteArray());

            return DataStorage.readFromStream(stream);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /* NBT */

    public static NbtElement toNbt(BaseType type)
    {
        if (type instanceof ByteType byteType)
        {
            return NbtByte.of(byteType.value);
        }
        else if (type instanceof DoubleType doubleType)
        {
            return NbtDouble.of(doubleType.value);
        }
        else if (type instanceof FloatType floatType)
        {
            return NbtFloat.of(floatType.value);
        }
        else if (type instanceof IntType intType)
        {
            return NbtInt.of(intType.value);
        }
        else if (type instanceof LongType longType)
        {
            return NbtLong.of(longType.value);
        }
        else if (type instanceof ShortType shortType)
        {
            return NbtShort.of(shortType.value);
        }
        else if (type instanceof StringType stringType)
        {
            return NbtString.of(stringType.value);
        }
        else if (type instanceof ListType listType)
        {
            NbtList list = new NbtList();

            for (BaseType baseType : listType)
            {
                list.add(toNbt(baseType));
            }

            return list;
        }
        else if (type instanceof MapType mapType)
        {
            NbtCompound compound = new NbtCompound();

            for (String key : mapType.keys())
            {
                compound.put(key, toNbt(mapType.get(key)));
            }

            return compound;
        }

        // TODO: ArrayType

        return null;
    }

    public static BaseType fromNbt(NbtElement element)
    {
        if (element instanceof NbtByte nbtByte)
        {
            return new ByteType(nbtByte.byteValue());
        }
        else if (element instanceof NbtDouble nbtDouble)
        {
            return new DoubleType(nbtDouble.doubleValue());
        }
        else if (element instanceof NbtFloat nbtFloat)
        {
            return new FloatType(nbtFloat.floatValue());
        }
        else if (element instanceof NbtInt nbtInt)
        {
            return new IntType(nbtInt.intValue());
        }
        else if (element instanceof NbtLong nbtLong)
        {
            return new LongType(nbtLong.longValue());
        }
        else if (element instanceof NbtShort nbtShort)
        {
            return new ShortType(nbtShort.shortValue());
        }
        else if (element instanceof NbtString nbtString)
        {
            return new StringType(nbtString.value());
        }
        else if (element instanceof NbtList nbtList)
        {
            ListType list = new ListType();

            for (NbtElement nbtElement : nbtList)
            {
                list.add(fromNbt(nbtElement));
            }

            return list;
        }
        else if (element instanceof NbtCompound nbtCompound)
        {
            MapType map = new MapType();

            for (String key : nbtCompound.getKeys())
            {
                map.put(key, fromNbt(nbtCompound.get(key)));
            }

            return map;
        }

        // TODO: ArrayType

        return null;
    }

    public static void writeToNbtCompound(NbtCompound compound, String key, BaseType data)
    {
        compound.put(key, DataStorageUtils.toNbt(data));
    }

    public static BaseType readFromNbtCompound(NbtCompound compound, String key)
    {
        BaseType baseType = DataStorageUtils.fromNbt(compound.get(key));

        if (baseType != null)
        {
            return baseType;
        }

        return null;
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