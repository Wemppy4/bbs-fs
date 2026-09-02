package mchorse.bbs_mod.blocks;

import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.StringIdentifiable;

/**
 * Sound material of a model block. Lives in the block STATE (not the block
 * entity) because {@link net.minecraft.block.AbstractBlock#getSoundGroup}
 * receives only a state — there is no position to reach the block entity from.
 */
public enum ModelBlockSound implements StringIdentifiable
{
    STONE("stone", BlockSoundGroup.STONE),
    WOOD("wood", BlockSoundGroup.WOOD),
    METAL("metal", BlockSoundGroup.METAL),
    GLASS("glass", BlockSoundGroup.GLASS),
    WOOL("wool", BlockSoundGroup.WOOL),
    GRASS("grass", BlockSoundGroup.GRASS),
    NONE("none", BlockSoundGroup.INTENTIONALLY_EMPTY);

    public final String id;
    public final BlockSoundGroup group;

    ModelBlockSound(String id, BlockSoundGroup group)
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
    public String asString()
    {
        return this.id;
    }
}
