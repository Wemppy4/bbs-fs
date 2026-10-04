package mchorse.bbs_mod.cubic.model;

import net.minecraft.inventory.EntityEquipmentSlot;

public enum ArmorType
{
    HELMET(EntityEquipmentSlot.HEAD), CHEST(EntityEquipmentSlot.CHEST), LEGGINGS(EntityEquipmentSlot.LEGS), LEFT_ARM(EntityEquipmentSlot.CHEST), RIGHT_ARM(EntityEquipmentSlot.CHEST), LEFT_LEG(EntityEquipmentSlot.LEGS), RIGHT_LEG(EntityEquipmentSlot.LEGS), LEFT_BOOT(EntityEquipmentSlot.FEET), RIGHT_BOOT(EntityEquipmentSlot.FEET);

    public final EntityEquipmentSlot slot;

    ArmorType(EntityEquipmentSlot slot)
    {
        this.slot = slot;
    }
}