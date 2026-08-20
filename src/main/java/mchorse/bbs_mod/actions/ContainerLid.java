package mchorse.bbs_mod.actions;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Raises and lowers a container's lid for a film (LUCKYWAY).
 *
 * <p>An actor can't open a real container: {@link SuperFakePlayer} turns every
 * screen down, because the film draws its own. Vanilla's lid, though, hangs off
 * the viewer count behind that screen, so turning the screen down also cost the
 * chest its animation and its sound.</p>
 *
 * <p>So the lid is driven straight instead: this sends the very block event
 * vanilla's viewer count would have sent (ChestBlockEntity#onViewerCountUpdate),
 * which is what every client turns into a moving lid. The container's real
 * viewer count is left alone, and that's the point - opening it for real also
 * schedules a recount five ticks later that counts the players standing within
 * five blocks of the chest, finds no actor there (an actor is never in the
 * world's entity list) and slams the lid shut mid-swing.</p>
 */
public class ContainerLid
{
    /** Vanilla's block event type for "this many players are looking inside". */
    private static final int VIEWER_COUNT = 1;

    /** Whether the block at this position has a lid worth animating at all. */
    public static boolean isLidded(World world, BlockPos pos)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        return entity instanceof ChestBlockEntity || entity instanceof EnderChestBlockEntity;
    }

    public static void setOpen(World world, BlockPos pos, boolean open)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof ChestBlockEntity)
        {
            BlockState state = world.getBlockState(pos);

            setChestHalfOpen(world, pos, state, open);

            /* Both halves of a double chest keep their own lid, and vanilla
             * moves both of them - a half open chest reads as a broken one */
            if (chestType(state) != ChestType.SINGLE)
            {
                BlockPos other = pos.offset(ChestBlock.getFacing(state));
                BlockState otherState = world.getBlockState(other);

                if (otherState.isOf(state.getBlock()))
                {
                    setChestHalfOpen(world, other, otherState, open);
                }
            }
        }
        else if (entity instanceof EnderChestBlockEntity)
        {
            BlockState state = world.getBlockState(pos);

            world.addSyncedBlockEvent(pos, state.getBlock(), VIEWER_COUNT, open ? 1 : 0);
            playSound(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, open ? SoundEvents.BLOCK_ENDER_CHEST_OPEN : SoundEvents.BLOCK_ENDER_CHEST_CLOSE);
        }
    }

    private static void setChestHalfOpen(World world, BlockPos pos, BlockState state, boolean open)
    {
        world.addSyncedBlockEvent(pos, state.getBlock(), VIEWER_COUNT, open ? 1 : 0);

        playChestSound(world, pos, state, open ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_CHEST_CLOSE);
    }

    /**
     * ChestBlockEntity#playSound, which isn't ours to call: the left half stays
     * quiet so a double chest is heard once, from the middle of the two.
     */
    private static void playChestSound(World world, BlockPos pos, BlockState state, SoundEvent sound)
    {
        ChestType type = chestType(state);

        if (type == ChestType.LEFT)
        {
            return;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;

        if (type == ChestType.RIGHT)
        {
            Direction direction = ChestBlock.getFacing(state);

            x += direction.getOffsetX() * 0.5D;
            z += direction.getOffsetZ() * 0.5D;
        }

        playSound(world, x, y, z, sound);
    }

    private static void playSound(World world, double x, double y, double z, SoundEvent sound)
    {
        world.playSound(null, x, y, z, sound, SoundCategory.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /** A chest block entity doesn't have to sit in a vanilla chest block. */
    private static ChestType chestType(BlockState state)
    {
        return state.contains(ChestBlock.CHEST_TYPE) ? state.get(ChestBlock.CHEST_TYPE) : ChestType.SINGLE;
    }
}
