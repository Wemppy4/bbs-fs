package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
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
            id = Identifier.of(name);
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

    /**
     * Empty a region, for the film cut that turns a build into a form: the blocks have to leave the
     * world once the structure stands in their place, or the shot has both.
     *
     * <p>This is not breaking. Blocks are replaced with air without notifying neighbours, so the
     * water at the edge does not pour in, the gravel above does not fall, and no redstone, door or
     * piston runs while the region empties. Containers are emptied first, so a cleared chest does
     * not carpet the floor with its contents. Top down, because a column cleared from below leaves
     * whatever is above it unsupported for a tick.</p>
     *
     * @return how many blocks were removed
     */
    public static int clear(ServerWorld world, BlockPos from, BlockPos to)
    {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        int cleared = 0;

        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        for (int y = maxY; y >= minY; y--)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    pos.set(x, y, z);

                    if (world.getBlockState(pos).isAir())
                    {
                        continue;
                    }

                    BlockEntity blockEntity = world.getBlockEntity(pos);

                    if (blockEntity instanceof Inventory inventory)
                    {
                        inventory.clear();
                    }

                    world.setBlockState(pos, air, flags);
                    cleared += 1;
                }
            }
        }

        /* Damage Control captures every setBlockState on the server while a film holds a snapshot,
         * and puts them all back when the editor closes. That is right for what a film does to the
         * world and wrong for this: a cut is an authoring edit, and restoring it would stand the
         * build back up inside the form made from it. Dropped after the clear, so the region's
         * earlier captures go with it — the cut is the last word on what is there. */
        ActionManager actions = BBSMod.getActions();

        if (actions != null)
        {
            actions.forgetBlocks(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }

        return cleared;
    }
}
