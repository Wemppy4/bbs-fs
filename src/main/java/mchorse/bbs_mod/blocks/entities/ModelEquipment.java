package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Equipment worn by a model block: six vanilla slots poured into the block's
 * stub entity every tick, from which the existing armor/held item renderers
 * (both cubic models and mob forms) already know how to draw them.
 */
public class ModelEquipment implements IMapSerializable
{
    private final Map<EquipmentSlot, ItemStack> stacks = new EnumMap<>(EquipmentSlot.class);

    public ItemStack get(EquipmentSlot slot)
    {
        return this.stacks.getOrDefault(slot, ItemStack.EMPTY);
    }

    public void set(EquipmentSlot slot, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            this.stacks.remove(slot);
        }
        else
        {
            this.stacks.put(slot, stack);
        }
    }

    public void apply(IEntity entity)
    {
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            entity.setEquipmentStack(slot, this.get(slot));
        }
    }

    @Override
    public void fromData(MapType data)
    {
        this.stacks.clear();

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            if (data.has(slot.getName()))
            {
                this.set(slot, KeyframeFactories.ITEM_STACK.fromData(data.get(slot.getName())));
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack stack = this.get(slot);

            if (!stack.isEmpty())
            {
                data.put(slot.getName(), KeyframeFactories.ITEM_STACK.toData(stack));
            }
        }
    }
}
