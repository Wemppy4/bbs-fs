package mchorse.bbs_mod.blocks;

import net.minecraft.block.SoundType;
import net.minecraft.util.SoundEvent;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.IStringSerializable;

/**
 * Sound material of a model block. Lives in the block STATE (not the block
 * entity) because {@link net.minecraft.block.AbstractBlock#getSoundGroup}
 * receives only a state — there is no position to reach the block entity from.
 */
public enum ModelBlockSound implements IStringSerializable
{
    STONE("stone", SoundType.STONE),
    WOOD("wood", SoundType.WOOD),
    METAL("metal", SoundType.METAL),
    GLASS("glass", SoundType.GLASS),
    WOOL("wool", SoundType.CLOTH),
    GRASS("grass", SoundType.PLANT),
    NONE("none", new SoundType(0F, 1F, SoundEvents.BLOCK_STONE_BREAK, SoundEvents.BLOCK_STONE_STEP, SoundEvents.BLOCK_STONE_PLACE, SoundEvents.BLOCK_STONE_HIT, SoundEvents.BLOCK_STONE_FALL));

    public final String id;
    public final SoundType group;

    ModelBlockSound(String id, SoundType group)
    {
        this.id = id;
        this.group = group;
    }

    public static ModelBlockSound byId(String id)
    {
        for (ModelBlockSound sound : values())
        {
            if (sound.id.equals(id))
            {
                return sound;
            }
        }

        return STONE;
    }

    @Override
    public String getName()
    {
        return this.id;
    }
}
