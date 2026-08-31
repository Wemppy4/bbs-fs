package mchorse.bbs_mod.utils;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.BlockPos;

/**
 * Writes a region of the world out as a structure NBT file, the same one the {@code bbs:structure}
 * form later reads back out of {@code world/generated}.
 *
 * <p>Both ways in end up here: the {@code /bbs structures save} command, which is handed typed
 * coordinates, and the structure wand, which sends the corners it picked in the world. Neither
 * goes through the vanilla structure block, whose 48-block-per-axis cap this deliberately
 * sidesteps.</p>
 */
public class StructureSaver
{
    /**
     * @param name structure id ({@code namespace:path}, plain names land in {@code minecraft:})
     * @param from either corner of the region, inclusive
     * @param to the opposite corner, inclusive
     * @return whether the file was written
     */
    public static boolean save(ServerWorld world, String name, BlockPos from, BlockPos to)
    {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        Identifier id;
        StructureTemplate template;

        try
        {
            id = new Identifier(name);
            template = manager.getTemplateOrBlank(id);
        }
        catch (InvalidIdentifierException e)
        {
            return false;
        }

        BlockPos min = new BlockPos(
            Math.min(from.getX(), to.getX()),
            Math.min(from.getY(), to.getY()),
            Math.min(from.getZ(), to.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(from.getX(), to.getX()),
            Math.max(from.getY(), to.getY()),
            Math.max(from.getZ(), to.getZ())
        );

        template.saveFromWorld(world, min, max.subtract(min).add(1, 1, 1), true, Blocks.STRUCTURE_VOID);

        try
        {
            return manager.saveTemplate(id);
        }
        catch (InvalidIdentifierException e)
        {
            return false;
        }
    }
}
