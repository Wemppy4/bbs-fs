package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/** The BBS item channel backed by 1.12.2's native ItemStack NBT. */
public class ItemStackKeyframeFactory implements IKeyframeFactory<ItemStack>
{
    @Override public ItemStack fromData(BaseType data)
    {
        NBTBase tag = DataStorageUtils.toNbt(data);
        return tag instanceof NBTTagCompound ? new ItemStack((NBTTagCompound) tag) : ItemStack.EMPTY;
    }
    @Override public BaseType toData(ItemStack value) { return DataStorageUtils.fromNbt(value.writeToNBT(new NBTTagCompound())); }
    @Override public ItemStack createEmpty() { return ItemStack.EMPTY; }
    @Override public boolean compare(Object a, Object b) { return a instanceof ItemStack && b instanceof ItemStack && ItemStack.areItemStacksEqual((ItemStack) a, (ItemStack) b); }
    @Override public boolean isStepped() { return true; }
    @Override public ItemStack copy(ItemStack value) { return value.copy(); }
    @Override public ItemStack interpolate(ItemStack before, ItemStack a, ItemStack b, ItemStack after, IInterp interpolation, float x) { return a; }
}
