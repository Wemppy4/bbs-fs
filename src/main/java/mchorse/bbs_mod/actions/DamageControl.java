package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A snapshot of everything a film changed in one world, so it can be put back when the film
 * stops.
 *
 * <p>The snapshot is shared by everyone filming in that world: whoever needs the world kept
 * intact {@link #acquire(Object)}s it and {@link #release(Object)}s it when they're done, and
 * the world is restored once the last holder lets go. Holding by identity rather than by a
 * counter is what makes every exit path correct on its own - a holder that disappears without
 * saying so (a player who disconnected mid-take) can still be dropped by name.
 */
public class DamageControl
{
    /* Insertion ordered, so blocks are put back in the order they were first changed, and keyed
     * by position, so re-touching a block during a long take is a lookup rather than a scan of
     * everything captured so far - an explosion or a /fill used to cost time quadratic in the
     * size of the snapshot. */
    private Map<BlockPos, BlockCapture> blocks = new LinkedHashMap<>();
    private List<Entity> entities = new ArrayList<>();

    /* Identity, not equality: two ActionPlayers of the same film are two holders. */
    private Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
    private int manualHolds;

    private ServerWorld world;

    public boolean enable;

    public DamageControl(ServerWorld world)
    {
        this.world = world;
        this.enable = BBSSettings.damageControl.get();
    }

    /**
     * Take a hold on this snapshot. A null owner is a hold taken by hand (the /bbs dc start
     * command), which has no object to be identified by and is counted instead.
     */
    public void acquire(Object owner)
    {
        if (owner == null)
        {
            this.manualHolds += 1;
        }
        else
        {
            this.owners.add(owner);
        }
    }

    /**
     * Let go of a hold, and say whether that was the last one. Releasing a hold that isn't
     * held is not an error: playback can be stopped more than once, and the second stop should
     * find nothing left to do rather than restore the world twice.
     */
    public boolean release(Object owner)
    {
        if (owner == null)
        {
            if (this.manualHolds > 0)
            {
                this.manualHolds -= 1;
            }
        }
        else
        {
            this.owners.remove(owner);
        }

        return this.owners.isEmpty() && this.manualHolds <= 0;
    }

    public void addBlock(BlockPos pos, BlockState state, NbtCompound blockEntity)
    {
        if (!this.enable || this.blocks.containsKey(pos))
        {
            return;
        }

        /* The position handed over by the world is reused between calls, so both the key and
         * the capture get a copy of their own. */
        BlockPos key = new BlockPos(pos);

        this.blocks.put(key, new BlockCapture(key, state, blockEntity));
    }

    public void addEntity(Entity entity)
    {
        if (!this.enable)
        {
            return;
        }

        this.entities.add(entity);
    }

    /**
     * Put the world back the way it was found.
     *
     * <p>The caller is expected to have dropped this snapshot before calling: restoring a block
     * is itself a block change, and a snapshot still reachable from the manager would be
     * written to while it's being walked.
     *
     * <p>One entry that cannot be restored - a block entity whose type no longer exists, a
     * position outside the world - must not cost the rest of them, so every entry is put back
     * on its own and the snapshot is emptied whatever happens.
     */
    public void restore()
    {
        try
        {
            for (BlockCapture block : new ArrayList<>(this.blocks.values()))
            {
                this.restoreBlock(block);
            }

            for (Entity entity : new ArrayList<>(this.entities))
            {
                try
                {
                    if (!entity.isRemoved())
                    {
                        entity.remove(Entity.RemovalReason.DISCARDED);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
        finally
        {
            this.blocks.clear();
            this.entities.clear();
        }
    }

    private void restoreBlock(BlockCapture block)
    {
        try
        {
            this.world.setBlockState(block.pos, block.lastState, 2);

            if (block.blockEntity != null)
            {
                BlockEntity blockEntity = BlockEntity.createFromNbt(block.pos, block.lastState, block.blockEntity, this.world.getRegistryManager());

                /* Null when the block entity's type is gone - a mod removed since the take was
                 * captured. The block itself is already back, which is the most that can be
                 * done for it. */
                if (blockEntity != null)
                {
                    this.world.addBlockEntity(blockEntity);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static class BlockCapture
    {
        public BlockPos pos;
        public BlockState lastState;
        public NbtCompound blockEntity;

        public BlockCapture(BlockPos pos, BlockState lastState, NbtCompound blockEntity)
        {
            this.pos = pos;
            this.lastState = lastState;
            this.blockEntity = blockEntity;
        }
    }
}
