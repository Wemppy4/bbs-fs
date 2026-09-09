package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;

import java.io.File;

/**
 * Writes a region of the world out as a structure NBT file, the one the {@code bbs:structure} form
 * later reads back.
 *
 * <p>Every way in — the {@code /bbs structures save} command, the structure wand, the film's cut —
 * writes into the same place: BBS's {@code structures} assets folder. Nothing BBS saves goes into
 * the world's own {@code generated} folder any more; structures written there by vanilla structure
 * blocks are still read, but they are the world's, not ours. None of this goes through the vanilla
 * structure block either, whose 48-block-per-axis cap is deliberately sidestepped.</p>
 */
public class StructureSaver
{
    /**
     * The folder BBS's own structures live in, under the assets folder. Declared here, on the
     * common side, because the writer is here and the reader ({@code StructureManager}) is on
     * the client.
     */
    public static final String ASSETS_FOLDER = "structures";

    /**
     * Write the region into BBS's {@code structures} folder, where it is addressed as
     * {@code assets:path} and is there in every world.
     *
     * @param path file path under the structures folder, without the extension
     * @return whether the file was written
     */
    public static boolean save(ServerWorld world, String path, BlockPos from, BlockPos to)
    {
        File folder = BBSMod.getAssetsPath(ASSETS_FOLDER);
        File file = new File(folder, path + ".nbt");

        /* No writing outside the folder through a path full of ".." */
        if (!file.toPath().normalize().startsWith(folder.toPath().normalize()))
        {
            return false;
        }

        try
        {
            StructureTemplate template = new StructureTemplate();
            NbtCompound nbt = new NbtCompound();

            template.saveFromWorld(world, min(from, to), size(from, to), true, Blocks.STRUCTURE_VOID);
            template.writeNbt(nbt);

            /* Stamped the way the vanilla manager stamps its own, so a file written here is the
             * same file a structure block would have written and reads back everywhere. */
            NbtHelper.putDataVersion(nbt);

            file.getParentFile().mkdirs();
            NbtIo.writeCompressed(nbt, file);

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return false;
        }
    }

    private static BlockPos min(BlockPos from, BlockPos to)
    {
        return new BlockPos(
            Math.min(from.getX(), to.getX()),
            Math.min(from.getY(), to.getY()),
            Math.min(from.getZ(), to.getZ())
        );
    }

    private static BlockPos size(BlockPos from, BlockPos to)
    {
        BlockPos min = min(from, to);
        BlockPos max = new BlockPos(
            Math.max(from.getX(), to.getX()),
            Math.max(from.getY(), to.getY()),
            Math.max(from.getZ(), to.getZ())
        );

        return max.subtract(min).add(1, 1, 1);
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
