package mchorse.bbs_mod.utils.keyframes.factories;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.GameRegistries;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;

import java.util.Optional;

public class ItemStackKeyframeFactory implements IKeyframeFactory<ItemStack>
{
    /* An encode that fails used to end here as a silent empty map — which is how an enchanted
     * item could be recorded, written down as air and noticed by nobody. Say it out loud, but
     * only once in a while: this runs per keyframe, and a recording writes one every tick. */
    private static int errorLog;

    @Override
    public ItemStack fromData(BaseType data)
    {
        DataResult<Pair<ItemStack, NbtElement>> decode = ItemStack.CODEC.decode(GameRegistries.nbtOps(), DataStorageUtils.toNbt(data));
        Optional<Pair<ItemStack, NbtElement>> result = decode.result();

        return result.map(Pair::getFirst).orElse(ItemStack.EMPTY);
    }

    @Override
    public BaseType toData(ItemStack value)
    {
        DataResult<NbtElement> encoded = ItemStack.CODEC.encodeStart(GameRegistries.nbtOps(), value);
        Optional<NbtElement> result = encoded.result();

        if (result.isEmpty() && !value.isEmpty() && errorLog++ % 200 == 0)
        {
            System.out.println("[BBS] couldn't write down item stack " + value + ": "
                + encoded.error().map(DataResult.Error::message).orElse("unknown error"));
        }

        return result.map(DataStorageUtils::fromNbt).orElse(new MapType());
    }

    @Override
    public ItemStack createEmpty()
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean compare(Object a, Object b)
    {
        if (a instanceof ItemStack itemA && b instanceof ItemStack itemB)
        {
            return ItemStack.areEqual(itemA, itemB);
        }

        return false;
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public ItemStack copy(ItemStack value)
    {
        return value.copy();
    }

    @Override
    public ItemStack interpolate(ItemStack preA, ItemStack a, ItemStack b, ItemStack postB, IInterp interpolation, float x)
    {
        return a;
    }
}