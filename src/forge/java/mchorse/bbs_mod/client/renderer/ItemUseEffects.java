package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.util.math.Vec3d;
import java.util.Random;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Vanilla's eating and drinking effects for actors playing back a film: the
 * crumbs flying out of the mouth, the chewing and gulping, the burp at the end.
 *
 * <p>Live players get all of this from vanilla itself - it runs while the item
 * use ticks. A film's actor never ticks one: the use is a clip, so the schedule
 * ({@code LivingEntity.tickItemStackUsage} and its consumption effects, taken
 * off the 1.20.4 bytecode) is replayed from the clip's own phase instead. The
 * long window an author may give a clip is respected: crumbs keep flying for as
 * long as the eating lasts.</p>
 */
public class ItemUseEffects
{
    /** The last film tick each hand emitted on, per actor. */
    private static final Map<IEntity, int[]> LAST = new WeakHashMap<>();

    public static void tick(Replay replay, IEntity entity, int tick)
    {
        emit(replay, entity, tick, true);
        emit(replay, entity, tick, false);
    }

    public static void clear()
    {
        LAST.clear();
    }

    private static void emit(Replay replay, IEntity entity, int tick, boolean mainHand)
    {
        int[] last = LAST.computeIfAbsent(entity, (e) -> new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE});
        int index = mainHand ? 0 : 1;
        int previous = last[index];

        last[index] = tick;

        /* Effects ride a running film only: a paused timeline would spit crumbs
         * forever and a scrubbed one would spit a whole meal at once. */
        if (tick != previous + 1)
        {
            return;
        }

        ItemUsePose.Use use = ReplayItemUse.compute(replay, tick, mainHand);

        if (use == null)
        {
            /* The bite that just ended: vanilla's finishing burst and burp. */
            ItemUsePose.Use before = ReplayItemUse.compute(replay, tick - 1, mainHand);

            if (before != null && before.action() == EnumAction.EAT)
            {
                spawn(entity, before.stack(), EnumAction.EAT, 16);
                burp(entity, before.stack());
            }

            return;
        }

        if (use.action() != EnumAction.EAT && use.action() != EnumAction.DRINK)
        {
            return;
        }

        /* shouldSpawnConsumptionEffects: snacks emit from the first tick, a full
         * meal only after 7 ticks, and either way every 4th tick of the use. */
        int left = Math.round(use.window() - use.elapsed());
        boolean due = use.stack().getItem() instanceof ItemFood && use.stack().getMaxItemUseDuration() <= 16;

        due |= left <= use.window() - 7F;

        if (due && left % 4 == 0)
        {
            spawn(entity, use.stack(), use.action(), 5);
        }
    }

    /** {@code LivingEntity.spawnConsumptionEffects} plus its {@code spawnItemParticles}. */
    private static void spawn(IEntity entity, ItemStack stack, EnumAction action, int count)
    {
        World world = entity.getWorld();

        if (world == null || stack.isEmpty())
        {
            return;
        }

        Random random = world.rand;
        double x = entity.getX();
        double y = entity.getY() + entity.getEyeHeight();
        double z = entity.getZ();

        if (action == EnumAction.DRINK)
        {
            playSound(world, x, y, z, SoundEvents.ENTITY_GENERIC_DRINK, 0.5F, random.nextFloat() * 0.1F + 0.9F);

            return;
        }

        float pitch = -entity.getPitch() * 0.017453292F;
        float yaw = -entity.getYaw() * 0.017453292F;

        for (int i = 0; i < count; i++)
        {
            Vec3d velocity = new Vec3d((random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0D)
                .rotatePitch(pitch)
                .rotateYaw(yaw);
            Vec3d position = new Vec3d((random.nextFloat() - 0.5D) * 0.3D, -random.nextFloat() * 0.6D - 0.3D, 0.6D)
                .rotatePitch(pitch)
                .rotateYaw(yaw)
                .add(x, y, z);

            world.spawnParticle(EnumParticleTypes.ITEM_CRACK, position.x, position.y, position.z, velocity.x, velocity.y + 0.05D, velocity.z, Item.getIdFromItem(stack.getItem()), stack.getMetadata());
        }

        playSound(world, x, y, z, SoundEvents.ENTITY_GENERIC_EAT,
            0.5F + 0.5F * random.nextInt(2),
            (random.nextFloat() - random.nextFloat()) * 0.2F + 1F);
    }

    /**
     * ⚠ {@code World#playSound(PlayerEntity except, ...)} means the opposite of
     * what it reads like on the client: {@link WorldClient} plays the sound only
     * when {@code except} IS the local player (vanilla calls it from the local
     * player's own code, everyone else's sounds arrive as packets). Passing
     * {@code null} would be silence, so the client entry point is used directly.
     */
    private static void playSound(World world, double x, double y, double z, SoundEvent sound, float volume, float pitch)
    {
        if (sound != null && world instanceof WorldClient clientWorld)
        {
            clientWorld.playSound(x, y, z, sound, SoundCategory.PLAYERS, volume, pitch, false);
        }
    }

    /** {@code PlayerEntity.eatFood}'s tail: only actual food burps. */
    private static void burp(IEntity entity, ItemStack stack)
    {
        World world = entity.getWorld();

        if (world == null || !(stack.getItem() instanceof ItemFood))
        {
            return;
        }

        playSound(world, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_PLAYER_BURP, 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
    }
}
