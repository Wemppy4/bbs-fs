package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code armor_slots} block of a model's config: a fixed slot per {@link ArmorType} keyed by the
 * type's lowercase name, matching the on-disk map. Inactive (boneless) slots stay out of the file.
 */
public class ArmorSlotsValue extends ValueGroup
{
    public ArmorSlotsValue(String id)
    {
        super(id);

        for (ArmorType type : ArmorType.values())
        {
            this.add(new ArmorSlotValue(type.name().toLowerCase()));
        }
    }

    public ArmorSlotValue slot(ArmorType type)
    {
        BaseValue value = this.get(type.name().toLowerCase());

        return value instanceof ArmorSlotValue slot ? slot : null;
    }

    public boolean hasActive()
    {
        for (ArmorType type : ArmorType.values())
        {
            ArmorSlotValue slot = this.slot(type);

            if (slot != null && slot.isActive())
            {
                return true;
            }
        }

        return false;
    }

    public Map<ArmorType, ArmorSlot> toMap()
    {
        Map<ArmorType, ArmorSlot> map = new HashMap<>();

        for (ArmorType type : ArmorType.values())
        {
            ArmorSlotValue slot = this.slot(type);

            if (slot != null && slot.isActive())
            {
                map.put(type, slot.toSlot());
            }
        }

        return map;
    }

    @Override
    protected boolean canPersist(BaseValue value)
    {
        return value instanceof ArmorSlotValue slot && slot.isActive();
    }
}
