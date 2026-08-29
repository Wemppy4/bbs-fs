package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.mixin.EntityInvoker;
import mchorse.bbs_mod.mixin.LivingEntityRollAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionPlayer
{
    public Film film;
    public int tick;
    public boolean playing = true;
    public int countdown;
    public int exception;
    public PlayerType type;

    public boolean syncing;
    private boolean pendingResync;

    private ServerPlayerEntity serverPlayer;
    private ServerWorld world;
    private int duration;

    private Map<String, LivingEntity> actors = new HashMap<>();

    private Form cachedForm;

    /**
     * The film dresses the first person player for the duration of the playback, so what it
     * takes over has to be given back. It borrows exactly what it drives - the hotbar, the
     * armour and the off hand - and never the rest of the inventory, which no camera can see.
     */
    private boolean borrowedEquipment;
    private List<ItemStack> cachedHotbar = new ArrayList<>();
    private Map<EquipmentSlot, ItemStack> cachedEquipment = new EnumMap<>(EquipmentSlot.class);
    private int cacheSelectedSlot;
    private IEntity fpEntity;

    private float cacheHp;
    private int cacheHunger;
    private int cacheXpLevel;
    private float cacheXpProgress;

    public ActionPlayer(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.countdown = countdown;
        this.exception = exception;
        this.type = type;

        this.serverPlayer = serverPlayer;
        this.duration = film.camera.calculateDuration();

        this.updateReplayEntities();

        Replay fpReplay = film.getFirstPersonReplay();

        if (this.type == PlayerType.NORMAL && this.serverPlayer != null && fpReplay != null)
        {
            this.borrowEquipment(fpReplay.keyframes);

            Morph morph = Morph.getMorph(this.serverPlayer);

            if (morph != null)
            {
                this.cachedForm = FormUtils.copy(morph.getForm());
            }

            ServerNetwork.sendMorphToTracked(this.serverPlayer, fpReplay.form.get());

            this.cacheHp = this.serverPlayer.getHealth();
            this.cacheHunger = this.serverPlayer.getHungerManager().getFoodLevel();
            this.cacheXpLevel = this.serverPlayer.experienceLevel;
            this.cacheXpProgress = this.serverPlayer.experienceProgress;

            applyFilmPlayerSettingsTo(this.serverPlayer, this.film.hp.get(), this.film.hunger.get(), this.film.xpLevel.get(), this.film.xpProgress.get());
        }
    }

    /** Equipment slots the film drives directly; the hotbar is driven by slot index instead. */
    private static final EquipmentSlot[] BORROWED_SLOTS = {EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private void borrowEquipment(ReplayKeyframes keyframes)
    {
        PlayerInventory inventory = this.serverPlayer.getInventory();

        this.borrowedEquipment = true;
        this.cacheSelectedSlot = inventory.selectedSlot;
        this.fpEntity = new MCEntity(this.serverPlayer);

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            this.cachedHotbar.add(inventory.getStack(i).copy());

            /* Cells the replay says nothing about are left to the world during playback (see
             * ReplayKeyframes#applyEquipment), but they're still emptied once - otherwise the
             * player's own things would wander into frame. */
            if (!keyframes.drivesHotbarSlot(i))
            {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }

        for (EquipmentSlot slot : BORROWED_SLOTS)
        {
            this.cachedEquipment.put(slot, this.serverPlayer.getEquippedStack(slot).copy());

            if (keyframes.getEquipmentChannel(slot).isEmpty())
            {
                this.serverPlayer.equipStack(slot, ItemStack.EMPTY);
            }
        }
    }

    private void returnEquipment()
    {
        PlayerInventory inventory = this.serverPlayer.getInventory();

        /* Playback can be stopped more than once (the film ends, then the manager stops it) */
        this.borrowedEquipment = false;

        for (int i = 0; i < this.cachedHotbar.size(); i++)
        {
            inventory.setStack(i, this.cachedHotbar.get(i));
        }

        for (Map.Entry<EquipmentSlot, ItemStack> entry : this.cachedEquipment.entrySet())
        {
            this.serverPlayer.equipStack(entry.getKey(), entry.getValue());
        }

        ServerNetwork.sendSelectedSlot(this.serverPlayer, this.cacheSelectedSlot);
    }

    public static void applyFilmPlayerSettingsTo(ServerPlayerEntity player, float hp, float hunger, int xpLevel, float xpProgress)
    {
        player.setHealth(hp);
        player.getHungerManager().setFoodLevel((int) hunger);
        player.setExperienceLevel(xpLevel);
        player.experienceProgress = xpProgress;
    }

    public void updateReplayEntities()
    {
        for (LivingEntity entity : this.actors.values())
        {
            if (!entity.isPlayer())
            {
                entity.discard();
            }
        }

        this.actors.clear();

        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);
            boolean isActor = replay.actor.get() || replay.fp.get();

            if (i == this.exception || !isActor || !replay.enabled.get())
            {
                continue;
            }

            if (replay.fp.get() && this.serverPlayer != null)
            {
                if (this.type == PlayerType.NORMAL)
                {
                    this.actors.put(replay.getId(), this.serverPlayer);
                }
            }
            else
            {
                ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, this.world);

                actor.setForm(FormUtils.copy(replay.form.get()));

                this.apply(actor, replay, this.tick, false);
                this.actors.put(replay.getId(), actor);
                this.world.spawnEntity(actor);
            }
        }

        for (ServerPlayerEntity player : this.world.getPlayers())
        {
            ServerNetwork.sendActors(player, this.film.getId(), this.actors);
        }
    }

    public ServerWorld getWorld()
    {
        return this.world;
    }

    public boolean isPlayedBy(ServerPlayerEntity player)
    {
        return this.serverPlayer == player;
    }

    public void apply(LivingEntity actor, Replay replay, float tick, boolean ticking)
    {
        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);
        float yawHead = replay.keyframes.headYaw.interpolate(tick).floatValue();
        float yawBody = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
        float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();

        Vec3d pos = actor.getPos();
        boolean grounded = replay.keyframes.grounded.interpolate(tick) > 0;

        if (ticking)
        {
            /* Probe downwards so vanilla's collision registers the floor - see
             * ReplayKeyframes#GRAVITY_PROBE. */
            double dY = y - pos.y - (grounded ? ReplayKeyframes.GRAVITY_PROBE : 0D);

            actor.move(MovementType.SELF, new Vec3d(x - pos.x, dY, z - pos.z));
        }

        actor.setPosition(x, y, z);
        actor.setYaw(yawHead);
        actor.setHeadYaw(yawHead);
        actor.setPitch(pitch);
        actor.setBodyYaw(yawBody);
        boolean sneaking = EntityState.isOn(replay.keyframes.state(EntityState.SNEAKING).interpolate(tick));
        boolean swimming = EntityState.isOn(replay.keyframes.state(EntityState.SWIMMING).interpolate(tick));
        boolean gliding = EntityState.isOn(replay.keyframes.state(EntityState.GLIDING).interpolate(tick));

        actor.setSneaking(sneaking);
        actor.setOnGround(grounded);

        /* The sprinting flag is tracked data, so setting it here is what makes the
         * client spawn vanilla's sprinting particles for this actor. Swimming and gliding are
         * the same kind of thing: the flag is what spreads the elytra's wings and lays the body
         * flat on every client watching, and neither would happen from the frame alone. */
        actor.setSprinting(EntityState.isOn(replay.keyframes.state(EntityState.SPRINTING).interpolate(tick)));
        actor.setSwimming(swimming);
        ((EntityInvoker) actor).bbs$setFlag(EntityState.FALL_FLYING_FLAG, gliding);
        actor.setPose(EntityState.pose(gliding, swimming, sneaking));

        /* Vanilla counts the roll up while flying, and an actor is placed rather than flown, so
         * the recorded count is handed over - it's what ramps the elytra's dive. */
        ((LivingEntityRollAccessor) actor).bbs$setRoll(replay.keyframes.roll.interpolate(tick).intValue());

        /* Riding and creative flight are recorded but not written here: a replay doesn't mount
         * anyone, and flight is a permission on a real player. Both only pick an animation. */

        if (actor instanceof ServerPlayerEntity player)
        {
            /* On a player equipStack() is a write into the real inventory, so the replay may
             * only dress one whose equipment the film borrowed at startup and gives back on
             * stop. A replay turned first person mid-playback borrowed nothing and dresses
             * nobody. */
            if (this.borrowedEquipment)
            {
                this.dressPlayer(player, replay.keyframes, tick);
            }
        }
        else
        {
            actor.equipStack(EquipmentSlot.MAINHAND, replay.keyframes.getMainHandStack(tick));
            actor.equipStack(EquipmentSlot.OFFHAND, replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY));
            actor.equipStack(EquipmentSlot.HEAD, replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY));
            actor.equipStack(EquipmentSlot.CHEST, replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY));
            actor.equipStack(EquipmentSlot.LEGS, replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY));
            actor.equipStack(EquipmentSlot.FEET, replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY));
        }

        double vx = x - replay.keyframes.x.interpolate(tick - 1);
        double vy = y - replay.keyframes.y.interpolate(tick - 1);
        double vz = z - replay.keyframes.z.interpolate(tick - 1);

        if (vy == 0D)
        {
            vy = -ReplayKeyframes.GRAVITY_PROBE;
        }

        actor.setVelocity(vx, vy, vz);

        actor.fallDistance = replay.keyframes.fall.interpolate(tick).floatValue();
    }

    /**
     * Lay the replay's frame out onto the first person player: nine hotbar cells, the armour,
     * the off hand and the selection. Nothing is put into the main hand - that's the selected
     * cell, and it's already there.
     */
    private void dressPlayer(ServerPlayerEntity player, ReplayKeyframes keyframes, float tick)
    {
        /* Selection first, so anything reading "the hand" during this frame reads the cell the
         * frame means rather than the one it just left. */
        int slot = keyframes.getSelectedSlot(tick);

        if (player.getInventory().selectedSlot != slot)
        {
            ServerNetwork.sendSelectedSlot(player, slot);
        }

        keyframes.applyEquipment(tick, this.fpEntity);
    }

    public boolean tick()
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return false;
        }

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                this.apply(entry.getValue(), replay, this.tick, true);
            }
        }

        if (!this.playing)
        {
            return false;
        }

        if (this.tick >= 0)
        {
            this.applyAction();
        }

        this.tick += 1;

        return !this.syncing && this.tick >= this.duration;
    }

    private void applyAction()
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            if (i == this.exception)
            {
                continue;
            }

            Replay replay = list.get(i);

            if (!replay.enabled.get())
            {
                continue;
            }

            LivingEntity actor = this.actors.get(replay.getId());

            replay.applyActions(actor, fakePlayer, this.film, this.tick);
        }

        /* Chests the clips of this tick still hold open go up, the rest come
         * down - including when the film was scrubbed rather than played */
        fakePlayer.flushLids();
    }

    public void syncData(DataPath key, BaseType data)
    {
        /* findRecursively (not getRecursively) so an unresolvable path doesn't
         * throw and abort the whole server task. */
        BaseValue baseValue = this.film.findRecursively(key);

        if (baseValue != null)
        {
            this.pendingResync = false;
            baseValue.fromData(data);

            if (baseValue == this.film || baseValue.getId().equals("actor") || baseValue.getId().equals("enabled") || baseValue.getId().equals("replays"))
            {
                this.updateReplayEntities();
            }
        }
        else if (!this.pendingResync && this.serverPlayer != null)
        {
            /* The client edited a path we don't have (e.g. a keyframe it just
             * inserted but hasn't structurally synced yet). Ask it to re-send the
             * whole film so we catch up; debounced until that full data arrives. */
            this.pendingResync = true;

            ServerNetwork.requestFilmResync(this.serverPlayer, this.film.getId());
        }
    }

    public void goTo(int tick)
    {
        this.goTo(this.tick, tick);
    }

    public void goTo(int from, int tick)
    {
        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                this.apply(entry.getValue(), replay, this.tick, false);
            }
        }

        if (from != tick)
        {
            this.tick = from;

            while (this.tick != tick)
            {
                this.tick += this.tick > tick ? -1 : 1;

                this.applyAction();
            }
        }
    }

    public void stop()
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.getIfPresent(this.world);

        /* Nothing asks for a lid any more, so every one the film opened closes */
        if (fakePlayer != null)
        {
            fakePlayer.flushLids();
        }

        for (LivingEntity value : this.actors.values())
        {
            if (!value.isPlayer())
            {
                value.discard();
            }
        }

        /* Every way a playback ends comes through here - the film reaching its end, the editor
         * stopping it, the player disconnecting, the server shutting down - so this is the one
         * place that has to let damage control go. Releasing a hold that was already released
         * does nothing, which is what makes stopping twice harmless. */
        BBSMod.getActions().stopDamage(this.world, this);

        /* Whether the equipment was borrowed, not whether it would be borrowed now: the film's
         * first person replay can be toggled off mid-playback, and then there would be nothing
         * to give back. */
        if (this.borrowedEquipment)
        {
            this.returnEquipment();

            ServerNetwork.sendMorphToTracked(this.serverPlayer, this.cachedForm);

            this.serverPlayer.setHealth(this.cacheHp);
            this.serverPlayer.getHungerManager().setFoodLevel(this.cacheHunger);
            this.serverPlayer.experienceProgress = this.cacheXpProgress;
            this.serverPlayer.setExperienceLevel(this.cacheXpLevel);
        }
    }

    public void toggle()
    {
        this.playing = !this.playing;
    }
}
