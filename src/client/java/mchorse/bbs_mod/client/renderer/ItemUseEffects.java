package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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

        LivingEntity user = ItemUsePose.livingOf(entity);
        ItemUsePose.Use use = ReplayItemUse.compute(replay, tick, mainHand, user);

        if (use == null)
        {
            /* The bite that just ended: vanilla's finishing burst and burp. */
            ItemUsePose.Use before = ReplayItemUse.compute(replay, tick - 1, mainHand, user);

            if (before != null && before.action() == UseAction.EAT)
            {
                spawn(entity, before.stack(), UseAction.EAT, 16);
                burp(entity, before.stack());
            }

            return;
        }

        if (use.action() != UseAction.EAT && use.action() != UseAction.DRINK)
        {
            return;
        }

        /* shouldSpawnConsumptionEffects: 1.21.1 dropped the snack exception the
         * older versions had - the crumbs start once 21.875% of the use is
         * behind, and then fly every 4th tick left. */
        int max = Math.round(use.window());
        int left = Math.round(use.window() - use.elapsed());
        boolean due = max - left > (int) (max * 0.21875F);

        if (due && left % 4 == 0)
        {
            spawn(entity, use.stack(), use.action(), 5);
        }
    }

    /** {@code LivingEntity.spawnConsumptionEffects} plus its {@code spawnItemParticles}. */
    private static void spawn(IEntity entity, ItemStack stack, UseAction action, int count)
    {
        World world = entity.getWorld();

        if (world == null || stack.isEmpty())
        {
            return;
        }

        Random random = world.random;
        double x = entity.getX();
        double y = entity.getY() + entity.getEyeHeight();
        double z = entity.getZ();

        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);

        /* 1.21.4+ folded the eating and drinking sounds, and the "does it even make crumbs"
         * answer, into the consumable component - getEatSound/getDrinkSound are gone. */
        if (consumable != null && !consumable.hasConsumeParticles())
        {
            count = 0;
        }

        if (action == UseAction.DRINK)
        {
            playSound(world, x, y, z, consumeSound(entity, consumable, stack), 0.5F, MathHelper.nextBetween(random, 0.9F, 1F));

            return;
        }

        float pitch = -entity.getPitch() * 0.017453292F;
        float yaw = -entity.getYaw() * 0.017453292F;

        for (int i = 0; i < count; i++)
        {
            Vec3d velocity = new Vec3d((random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0D)
                .rotateX(pitch)
                .rotateY(yaw);
            Vec3d position = new Vec3d((random.nextFloat() - 0.5D) * 0.3D, -random.nextFloat() * 0.6D - 0.3D, 0.6D)
                .rotateX(pitch)
                .rotateY(yaw)
                .add(x, y, z);

            world.addParticleClient(new ItemStackParticleEffect(ParticleTypes.ITEM, stack), position.x, position.y, position.z, velocity.x, velocity.y + 0.05D, velocity.z);
        }

        playSound(world, x, y, z, consumeSound(entity, consumable, stack),
            random.nextBoolean() ? 0.5F : 1F,
            random.nextTriangular(1F, 0.2F));
    }

    /**
     * The sound eating or drinking this stack makes: the body gets to answer for itself
     * (ConsumableSoundProvider - vanilla asks the entity, not the item), and the component's
     * own sound otherwise.
     */
    private static SoundEvent consumeSound(IEntity entity, ConsumableComponent consumable, ItemStack stack)
    {
        LivingEntity living = ItemUsePose.livingOf(entity);

        if (living instanceof ConsumableComponent.ConsumableSoundProvider provider)
        {
            return provider.getConsumeSound(stack);
        }

        return consumable == null ? null : consumable.sound().value();
    }

    /**
     * ⚠ {@code World#playSound(PlayerEntity except, ...)} means the opposite of
     * what it reads like on the client: {@link ClientWorld} plays the sound only
     * when {@code except} IS the local player (vanilla calls it from the local
     * player's own code, everyone else's sounds arrive as packets). Passing
     * {@code null} would be silence, so the client entry point is used directly.
     */
    private static void playSound(World world, double x, double y, double z, SoundEvent sound, float volume, float pitch)
    {
        if (sound != null && world instanceof ClientWorld clientWorld)
        {
            clientWorld.playSoundClient(x, y, z, sound, SoundCategory.PLAYERS, volume, pitch, false);
        }
    }

    /** {@code PlayerEntity.eatFood}'s tail: only actual food burps. */
    private static void burp(IEntity entity, ItemStack stack)
    {
        World world = entity.getWorld();

        if (world == null || stack.get(DataComponentTypes.FOOD) == null)
        {
            return;
        }

        playSound(world, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_PLAYER_BURP, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
    }
}
