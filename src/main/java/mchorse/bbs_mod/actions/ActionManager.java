package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ActionManager
{
    private List<ActionPlayer> players = new ArrayList<>();
    private Map<ServerPlayerEntity, ActionRecorder> recorders = new HashMap<>();
    private Map<ServerWorld, DamageControl> dc = new HashMap<>();

    /**
     * Stopping, not just forgetting: playback borrows the first person player's equipment and
     * only gives it back on stop, so dropping the players on the floor here would leave them
     * dressed as the film - their own items gone with the server they left. Damage control is
     * held the same way, and stopping is what makes the world it was keeping get put back
     * rather than saved broken.
     */
    public void reset()
    {
        for (ActionPlayer player : this.players)
        {
            player.stop();
        }

        for (ActionRecorder recorder : this.recorders.values())
        {
            this.stopDamage(recorder.getWorld(), recorder);
        }

        this.players.clear();
        this.recorders.clear();

        /* Whatever is left is held by hand (/bbs dc start). The server is going away, so this
         * is the last chance to put those worlds back - forgetting them here is what used to
         * save the damage into the world for good. */
        List<DamageControl> remaining = new ArrayList<>(this.dc.values());

        this.dc.clear();

        for (DamageControl control : remaining)
        {
            control.restore();
        }
    }

    /** Give a leaving player their equipment back and drop any playback that was dressing them. */
    public void stopFor(ServerPlayerEntity player)
    {
        this.players.removeIf((next) ->
        {
            if (next.isPlayedBy(player))
            {
                next.stop();

                return true;
            }

            return false;
        });

        ActionRecorder recorder = this.recorders.remove(player);

        if (recorder != null)
        {
            this.stopDamage(recorder.getWorld(), recorder);
        }
    }

    public void tick()
    {
        this.players.removeIf((player) ->
        {
            boolean tick = player.tick();

            if (tick)
            {
                player.stop();
            }

            return tick;
        });

        for (Map.Entry<ServerPlayerEntity, ActionRecorder> entry : this.recorders.entrySet())
        {
            entry.getValue().tick(entry.getKey());
        }
    }

    /* Actions playback */

    public void syncData(String filmId, DataPath key, BaseType data)
    {
        for (ActionPlayer player : this.players)
        {
            if (player.film.getId().equals(filmId))
            {
                player.syncData(key, data);
            }
        }
    }

    public ActionPlayer getPlayer(String filmId)
    {
        for (ActionPlayer player : this.players)
        {
            if (player.film.getId().equals(filmId))
            {
                return player;
            }
        }

        return null;
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick)
    {
        return this.play(serverPlayer, world, film, tick, 0, -1, PlayerType.NORMAL);
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, PlayerType type)
    {
        return this.play(serverPlayer, world, film, tick, 0, -1, type);
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        if (film != null)
        {
            /* One playback per film, and the one asked for last wins. Playing a film for several
             * players at once (/bbs film @a play) called this once per player, and each call put
             * its own full cast into the world - the scene was acted two, three, five times over,
             * on top of itself, while stopping and seeking only ever reached the first. */
            this.stop(film.getId());

            ActionPlayer player = new ActionPlayer(serverPlayer, world, film, tick, countdown, exception, type);

            this.players.add(player);

            /* The playback itself holds damage control, and lets go in ActionPlayer#stop - so
             * every way a playback can end, including ones added later, puts the world back
             * without having to remember to say so here. */
            this.trackDamage(world, player);

            return player;
        }

        return null;
    }

    public void stop(String filmId)
    {
        Iterator<ActionPlayer> it = this.players.iterator();

        while (it.hasNext())
        {
            ActionPlayer next = it.next();

            if (next.film.getId().equals(filmId))
            {
                next.stop();
                it.remove();
            }
        }
    }

    /* Actions recording */

    public void startRecording(Film film, ServerPlayerEntity entity, int tick, int countdown, int replayId)
    {
        ActionRecorder recorder = new ActionRecorder(film, entity, tick, countdown);

        this.play(entity, entity.getServerWorld(), film, tick, countdown, replayId, PlayerType.RECORDING);

        /* The recording outlives the playback that drives it - the film can reach its end while
         * the take is still going - so the recorder holds damage control in its own right. */
        this.trackDamage(recorder.getWorld(), recorder);

        this.recorders.put(entity, recorder);
    }

    public void addAction(ServerPlayerEntity entity, Supplier<ActionClip> supplier)
    {
        ActionRecorder recorder = this.recorders.get(entity);

        if (recorder != null && supplier != null)
        {
            ActionClip actionClip = supplier.get();

            if (actionClip != null)
            {
                recorder.add(actionClip);
            }
        }
    }

    public ActionRecorder stopRecording(ServerPlayerEntity entity)
    {
        ActionRecorder remove = this.recorders.remove(entity);

        if (remove == null)
        {
            return null;
        }

        this.stop(remove.getFilm().getId());
        this.stopDamage(remove.getWorld(), remove);

        return remove;
    }

    /* Damage control */

    /** Whether anything is being kept intact right now - the cheap check the block hook needs. */
    public boolean isTracking()
    {
        return !this.dc.isEmpty();
    }

    /** Take a hold on the world's snapshot by hand, for /bbs dc start. */
    public void trackDamage(ServerWorld world)
    {
        this.trackDamage(world, null);
    }

    public void trackDamage(ServerWorld world, Object owner)
    {
        this.dc.computeIfAbsent(world, DamageControl::new).acquire(owner);
    }

    /** Let go of a hold taken by hand, for /bbs dc stop. */
    public void stopDamage(ServerWorld world)
    {
        this.stopDamage(world, null);
    }

    public void stopDamage(ServerWorld world, Object owner)
    {
        DamageControl damageControl = this.dc.get(world);

        if (damageControl != null && damageControl.release(owner))
        {
            /* Dropped before restoring, not after: putting a block back is itself a block
             * change, and a door or a bed puts its other half back too - a snapshot still
             * reachable from here would be written to while it's being walked. */
            this.dc.remove(world);

            damageControl.restore();
        }
    }

    public void resetDamage(ServerWorld world)
    {
        DamageControl dc = this.dc.remove(world);

        if (dc != null)
        {
            dc.restore();
        }
    }

    public void changedBlock(BlockPos pos, BlockState state, NbtCompound blockEntity)
    {
        for (DamageControl control : this.dc.values())
        {
            control.addBlock(pos, state, blockEntity);
        }
    }

    /**
     * Take a region out of every snapshot. Block changes go into all of them (see
     * {@link #changedBlock}), so a region only stays gone if it leaves all of them too.
     */
    public void forgetBlocks(BlockPos min, BlockPos max)
    {
        for (DamageControl control : this.dc.values())
        {
            control.forget(min, max);
        }
    }

    public void spawnedEntity(Entity entity)
    {
        for (DamageControl control : this.dc.values())
        {
            control.addEntity(entity);
        }
    }
}
