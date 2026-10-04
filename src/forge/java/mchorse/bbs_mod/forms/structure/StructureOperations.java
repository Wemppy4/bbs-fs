package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.structure.template.Template;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Common-side native structure saving/cutting. Call on the server thread after checking panel permission. */
public final class StructureOperations
{
    public static final String ASSETS_FOLDER = "structures";

    /** The compressed template is durable before any block or inventory is touched. */
    public static boolean cut(WorldServer world, String name, BlockPos from, BlockPos to)
    {
        if (world == null || world.getWorldInfo().getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES)
        {
            return false;
        }
        if (!save(world, name, from, to)) return false;
        try
        {
            clear(world, from, to);
            return true;
        }
        catch (RuntimeException error)
        {
            BBSMod.LOGGER.error("Structure {} was saved, but clearing its region failed; the saved NBT is retained", name, error);
            return false;
        }
    }

    public static boolean save(WorldServer world, String name, BlockPos from, BlockPos to)
    {
        if (world == null || name == null || name.trim().isEmpty() || from == null || to == null) return false;
        if (!world.isValid(from) || !world.isValid(to)) return false;
        Path temporary = null;
        try
        {
            File folder = BBSMod.getAssetsPath(ASSETS_FOLDER).getCanonicalFile();
            File file = new File(folder, name + ".nbt").getCanonicalFile();
            Path root = folder.toPath(), target = file.toPath();
            if (target.equals(root) || !target.startsWith(root)) return false;

            Template template = new Template();
            BlockPos min = min(from, to), max = max(from, to);
            template.takeBlocksFromWorld(world, min, max.subtract(min).add(1, 1, 1), true, Blocks.STRUCTURE_VOID);
            /* Native Template writes DataVersion 1343 and the full tile/entity NBT palette. */
            NBTTagCompound nbt = template.writeToNBT(new NBTTagCompound());
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".bbs-structure-", ".nbt.tmp");
            try (FileOutputStream output = new FileOutputStream(temporary.toFile()))
            {
                /* writeCompressed closes its supplied stream; sync through a separate handle below. */
                CompressedStreamTools.writeCompressed(nbt, output);
            }
            try (java.io.RandomAccessFile durable = new java.io.RandomAccessFile(temporary.toFile(), "rw"))
            {
                durable.getFD().sync();
            }
            try
            {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException unsupported)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            return true;
        }
        catch (Exception error)
        {
            BBSMod.LOGGER.error("Cannot save structure {}", name, error);
            return false;
        }
        finally
        {
            if (temporary != null)
            {
                try { Files.deleteIfExists(temporary); }
                catch (java.io.IOException error) { BBSMod.LOGGER.warn("Cannot remove incomplete structure {}", temporary, error); }
            }
        }
    }

    /** Clear top-down without ordinary neighbor/observer updates or container drops. */
    public static int clear(WorldServer world, BlockPos from, BlockPos to)
    {
        if (world == null || from == null || to == null || !world.isValid(from) || !world.isValid(to))
        {
            throw new IllegalArgumentException("Invalid structure region");
        }
        BlockPos min = min(from, to), max = max(from, to);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        IBlockState air = Blocks.AIR.getDefaultState();
        boolean tileDrops = world.getGameRules().getBoolean("doTileDrops");
        int cleared = 0;
        /* 1.12 has no SKIP_DROPS state flag. Native breakBlock implementations (e.g. shulker boxes)
         * can emit their own item, so the existing gamerule is scoped to this synchronous operation. */
        world.getGameRules().setOrCreateGameRule("doTileDrops", "false");
        try
        {
            for (int y = max.getY(); y >= min.getY(); y--)
            {
                for (int x = min.getX(); x <= max.getX(); x++)
                {
                    for (int z = min.getZ(); z <= max.getZ(); z++)
                    {
                        pos.setPos(x, y, z);
                        IBlockState state = world.getBlockState(pos);
                        if (state.getBlock().isAir(state, world, pos)) continue;
                        TileEntity tile = world.getTileEntity(pos);
                        if (tile instanceof IInventory) ((IInventory) tile).clear();
                        /* 2 = client update, 16 = suppress observers; no flag 1 neighbor physics. */
                        if (!world.setBlockState(pos, air, 2 | 16))
                        {
                            throw new IllegalStateException("Cannot clear structure block at " + pos);
                        }
                        cleared++;
                    }
                }
            }
        }
        finally
        {
            world.getGameRules().setOrCreateGameRule("doTileDrops", Boolean.toString(tileDrops));
            /* An authoring cut must not be restored by a film's Damage Control snapshot. */
            ActionManager actions = BBSMod.getActions();
            if (actions != null) actions.forgetBlocks(min, max);
        }
        return cleared;
    }

    private static BlockPos min(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }
    private static BlockPos max(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }
    private StructureOperations() {}
}
