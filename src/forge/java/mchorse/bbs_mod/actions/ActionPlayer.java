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
import mchorse.bbs_mod.forms.entities.NativeLivingState;
import net.minecraft.entity.player.EntityPlayer;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
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

    private EntityPlayerMP serverPlayer;
    private WorldServer world;
    private int duration;

    private Map<String, EntityLivingBase> actors = new HashMap<>();

    private Form cachedForm;

    /**
     * The film dresses the first person player for the duration of the playback, so what it
     * takes over has to be given back. It borrows exactly what it drives - the hotbar, the
     * armour and the off hand - and never the rest of the inventory, which no camera can see.
     */
    private boolean borrowedEquipment;
    private List<ItemStack> cachedHotbar = new ArrayList<>();
    private Map<EntityEquipmentSlot, ItemStack> cachedEquipment = new EnumMap<>(EntityEquipmentSlot.class);
    private int cacheSelectedSlot;
    private IEntity fpEntity;

    private float cacheHp;
    private int cacheHunger;
    private int cacheXpLevel;
    private float cacheXpProgress;

    public ActionPlayer(EntityPlayerMP serverPlayer, WorldServer world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.countdown = countdown;
        this.exception = exception;
        this.type = type;

        this.serverPlayer = serverPlayer;
        this.duration = film.calculateDuration();

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
            this.cacheHunger = this.serverPlayer.getFoodStats().getFoodLevel();
            this.cacheXpLevel = this.serverPlayer.experienceLevel;
            this.cacheXpProgress = this.serverPlayer.experience;

            applyFilmPlayerSettingsTo(this.serverPlayer, this.film.hp.get(), this.film.hunger.get(), this.film.xpLevel.get(), this.film.xpProgress.get());
        }
    }

    /** Equipment slots the film drives directly; the hotbar is driven by slot index instead. */
    private static final EntityEquipmentSlot[] BORROWED_SLOTS = {EntityEquipmentSlot.OFFHAND, EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    private void borrowEquipment(ReplayKeyframes keyframes)
    {
        InventoryPlayer inventory = this.serverPlayer.inventory;

        this.borrowedEquipment = true;
        this.cacheSelectedSlot = inventory.currentItem;
        this.fpEntity = new MCEntity(this.serverPlayer);

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            this.cachedHotbar.add(inventory.getStackInSlot(i).copy());

            /* A hotbar the replay says nothing about is left to the world during playback (see
             * ReplayKeyframes#applyEquipment), but it's still emptied once - otherwise the
             * player's own things would wander into frame. */
            if (!keyframes.drivesHotbar())
            {
                inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }

        for (EntityEquipmentSlot slot : BORROWED_SLOTS)
        {
            this.cachedEquipment.put(slot, this.serverPlayer.getItemStackFromSlot(slot).copy());

            if (keyframes.getEquipmentChannel(slot).isEmpty())
            {
                this.serverPlayer.setItemStackToSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private void returnEquipment()
    {
        InventoryPlayer inventory = this.serverPlayer.inventory;

        /* Playback can be stopped more than once (the film ends, then the manager stops it) */
        this.borrowedEquipment = false;

        for (int i = 0; i < this.cachedHotbar.size(); i++)
        {
            inventory.setInventorySlotContents(i, this.cachedHotbar.get(i));
        }

        for (Map.Entry<EntityEquipmentSlot, ItemStack> entry : this.cachedEquipment.entrySet())
        {
            this.serverPlayer.setItemStackToSlot(entry.getKey(), entry.getValue());
        }

        ServerNetwork.sendSelectedSlot(this.serverPlayer, this.cacheSelectedSlot);
    }

    public static void applyFilmPlayerSettingsTo(EntityPlayerMP player, float hp, float hunger, int xpLevel, float xpProgress)
    {
        player.setHealth(hp);
        player.getFoodStats().setFoodLevel((int) hunger);
        player.experienceLevel = xpLevel;
        player.experience = xpProgress;
    }

    /**
     * Reconcile the bodies in the world with the replays that ask for one. Whoever is still wanted
     * and still alive is left standing: this runs on every structural edit of the film, and razing
     * the whole cast each time handed every actor a new entity id - clients kept pointing at ids
     * that had just been discarded, and the bodies blinked out of the scene for a tick or two.
     */
    public void updateReplayEntities()
    {
        Map<String, EntityLivingBase> previous = new HashMap<>(this.actors);

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
                EntityLivingBase kept = previous.remove(replay.getId());

                /* Kept only when it is still an actor's body: a replay that just stopped being
                 * first person leaves the player behind under the same key, and the player is
                 * nobody's to keep driving as a puppet. */
                if (kept instanceof ActorEntity actor && !actor.isDead)
                {
                    this.actors.put(replay.getId(), actor);
                }
                else
                {
                    this.actors.put(replay.getId(), this.spawnActor(replay));
                }
            }
        }

        for (EntityLivingBase entity : previous.values())
        {
            if (!(entity instanceof EntityPlayer))
            {
                if (entity instanceof ActorEntity actor)
                {
                    actor.dropPickedUp();
                }

                entity.setDead();
            }
        }

        this.broadcastActors();
    }

    /** Restore the cast for an editor restart without replacing healthy actors or the player. */
    public void resetActorsForRestart()
    {
        this.tick = 0;

        for (EntityLivingBase entity : this.actors.values())
        {
            if (!(entity instanceof ActorEntity actor))
            {
                continue;
            }

            if (actor.getHealth() <= 0F && !actor.isDead)
            {
                actor.setDead();
            }

            if (!actor.isDead)
            {
                actor.setHealth(actor.getMaxHealth());
                actor.hurtTime = 0;
                actor.deathTime = 0;
                actor.hurtResistantTime = 0;
            }
        }

        /* Missing/killed shells are recreated; healthy ones keep their network IDs. */
        this.updateReplayEntities();
    }

    private ActorEntity spawnActor(Replay replay)
    {
        ActorEntity actor = new ActorEntity(this.world);

        actor.setReplay(this.film.getId(), replay.getId());
        actor.setPickUpItems(replay.actorPickup.get());
        actor.setForm(FormUtils.copy(replay.form.get()));

        this.apply(actor, replay, this.tick, false);
        this.world.spawnEntity(actor);

        return actor;
    }

    private void broadcastActors()
    {
        for (EntityPlayer player : this.world.playerEntities)
        {
            if (player instanceof EntityPlayerMP) ServerNetwork.sendActors((EntityPlayerMP) player, this.film.getId(), this.actors);
        }
    }

    /**
     * Actors are props the film puts out, not creatures the world keeps, and the world may take one
     * away at any moment: a chunk that unloaded and came back drops its actor on the despawn flag.
     * The film would then keep driving a corpse - the take simply loses a body and never gets it
     * back. Anything missing is put back where its keyframes say it stands, and the map goes out
     * again so clients stop pointing at a dead id.
     *
     * <p>A body someone KILLED is the exception, and the reason the removal is asked for by name:
     * being hit is what the flag is for, and a death is a thing being filmed, not an accident to
     * undo. Putting it straight back made an actor unkillable by any means - a blow, a mob, even
     * {@code /kill} - because the next tick spawned a replacement. It stays down until the film is
     * restarted, which is what builds the cast again.</p>
     */
    private void reviveLostActors()
    {
        List<String> lost = null;

        for (Map.Entry<String, EntityLivingBase> entry : this.actors.entrySet())
        {
            EntityLivingBase actor = entry.getValue();

            if (!(actor instanceof EntityPlayer) && actor.isDead)
            {
                if (lost == null)
                {
                    lost = new ArrayList<>();
                }

                lost.add(entry.getKey());
            }
        }

        if (lost == null)
        {
            return;
        }

        for (String id : lost)
        {
            Replay replay = (Replay) this.film.replays.get(id);
            EntityLivingBase previous = this.actors.get(id);
            boolean killed = previous != null && (previous.getHealth() <= 0F || previous.deathTime > 0);

            if (killed || replay == null || !replay.enabled.get())
            {
                this.actors.remove(id);
            }
            else
            {
                this.actors.put(id, this.spawnActor(replay));
            }
        }

        this.broadcastActors();
    }

    public WorldServer getWorld()
    {
        return this.world;
    }

    /**
     * By uuid rather than by identity: dying and respawning hands the same person a brand new
     * {@link EntityPlayerMP}, and a playback compared by reference then belonged to nobody.
     * It outlived the disconnect that should have ended it, still holding the equipment it had
     * borrowed - which the player never got back.
     */
    public boolean isPlayedBy(EntityPlayerMP player)
    {
        return this.serverPlayer != null && player != null && this.serverPlayer.getUniqueID().equals(player.getUniqueID());
    }

    /** The same person, but the entity they are now - a respawn replaces the object entirely. */
    private void refreshPlayer()
    {
        if (this.serverPlayer == null)
        {
            return;
        }

        EntityPlayerMP live = this.world.getMinecraftServer().getPlayerList().getPlayerByUUID(this.serverPlayer.getUniqueID());

        if (live != null)
        {
            this.serverPlayer = live;
            this.fpEntity = new MCEntity(live);
        }
    }

    public void apply(EntityLivingBase actor, Replay replay, float tick, boolean ticking)
    {
        /* Replay-local, the way the client already reads it when it draws: a looping replay wraps
         * the film's tick into its own window. The server never wrapped, so a looping replay's body
         * stood at the unwrapped tick - off in a part of the take the loop never reaches - while
         * the drawn one played its loop. */
        tick = replay.getTick((int) tick);

        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);
        float yawHead = replay.keyframes.headYaw.interpolate(tick).floatValue();
        float yawBody = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
        float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();

        Vec3d pos = actor.getPositionVector();
        boolean grounded = replay.keyframes.grounded.interpolate(tick) > 0;

        if (ticking)
        {
            /* Probe downwards so vanilla's collision registers the floor - see
             * ReplayKeyframes#GRAVITY_PROBE. */
            double dY = y - pos.y - (grounded ? ReplayKeyframes.GRAVITY_PROBE : 0D);

            actor.move(MoverType.SELF, x - pos.x, dY, z - pos.z);
        }

        actor.setPosition(x, y, z);
        actor.rotationYaw = yawHead;
        actor.rotationYawHead = yawHead;
        actor.rotationPitch = pitch;
        actor.renderYawOffset = yawBody;
        boolean sneaking = EntityState.isOn(replay.keyframes.state(EntityState.SNEAKING).interpolate(tick));
        boolean swimming = EntityState.isOn(replay.keyframes.state(EntityState.SWIMMING).interpolate(tick));
        boolean gliding = EntityState.isOn(replay.keyframes.state(EntityState.GLIDING).interpolate(tick));

        actor.setSneaking(sneaking);
        actor.onGround = grounded;

        /* The sprinting flag is tracked data, so setting it here is what makes the
         * client spawn vanilla's sprinting particles for this actor. Swimming and gliding are
         * the same kind of thing: the flag is what spreads the elytra's wings and lays the body
         * flat on every client watching, and neither would happen from the frame alone. */
        actor.setSprinting(EntityState.isOn(replay.keyframes.state(EntityState.SPRINTING).interpolate(tick)));
        NativeLivingState.setPose(actor, EntityState.pose(gliding, swimming, sneaking));
        NativeLivingState.setGliding(actor, gliding);

        /* Vanilla counts the roll up while flying, and an actor is placed rather than flown, so
         * the recorded count is handed over - it's what ramps the elytra's dive. */
        NativeLivingState.setRoll(actor, replay.keyframes.roll.interpolate(tick).intValue());

        /* Riding and creative flight are recorded but not written here: a replay doesn't mount
         * anyone, and flight is a permission on a real player. Both only pick an animation. */

        if (actor instanceof EntityPlayerMP player)
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
            actor.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, replay.keyframes.getMainHandStack(tick));
            actor.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY));
            actor.setItemStackToSlot(EntityEquipmentSlot.HEAD, replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY));
            actor.setItemStackToSlot(EntityEquipmentSlot.CHEST, replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY));
            actor.setItemStackToSlot(EntityEquipmentSlot.LEGS, replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY));
            actor.setItemStackToSlot(EntityEquipmentSlot.FEET, replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY));
        }

        double vx = x - replay.keyframes.x.interpolate(tick - 1);
        double vy = y - replay.keyframes.y.interpolate(tick - 1);
        double vz = z - replay.keyframes.z.interpolate(tick - 1);

        if (vy == 0D)
        {
            vy = -ReplayKeyframes.GRAVITY_PROBE;
        }

        actor.motionX = vx;
        actor.motionY = vy;
        actor.motionZ = vz;

        actor.fallDistance = replay.keyframes.fall.interpolate(tick).floatValue();
    }

    /**
     * Lay the replay's frame out onto the first person player: nine hotbar cells, the armour,
     * the off hand and the selection. Nothing is put into the main hand - that's the selected
     * cell, and it's already there.
     */
    private void dressPlayer(EntityPlayerMP player, ReplayKeyframes keyframes, float tick)
    {
        /* Selection first, so anything reading "the hand" during this frame reads the cell the
         * frame means rather than the one it just left. */
        int slot = keyframes.getSelectedSlot(tick);

        if (player.inventory.currentItem != slot)
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

        this.reviveLostActors();

        for (Map.Entry<String, EntityLivingBase> entry : this.actors.entrySet())
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

            EntityLivingBase actor = this.actors.get(replay.getId());

            /* Replay-local for the same reason the pose is - see apply(). The client answers its
             * own clips on the wrapped tick, so an unwrapped one here had the two disagreeing. */
            replay.applyActions(actor, fakePlayer, this.film, replay.getTick(this.tick));
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

            /* The edit may have moved the film's end - a keyframe past the camera's last clip, or
             * a replay switched off - and the playback stops (and takes its actors down) by it. */
            this.duration = this.film.calculateDuration();

            if (baseValue == this.film || baseValue.getId().equals("actor") || baseValue.getId().equals("fp") || baseValue.getId().equals("enabled") || baseValue.getId().equals("replays"))
            {
                this.updateReplayEntities();
            }
            else if (baseValue.getId().equals("actor_pickup") && baseValue.getParent() instanceof Replay replay)
            {
                if (this.actors.get(replay.getId()) instanceof ActorEntity actor)
                {
                    actor.setPickUpItems(replay.actorPickup.get());
                }
            }
            else if (baseValue.getId().equals("form") && baseValue.getParent() instanceof Replay replay)
            {
                /* A costume change, not a change of cast: the body already standing there takes the
                 * new form. Rebuilding the cast over it would hand every actor in the film a fresh
                 * entity id, and until now nothing happened at all - the actor kept its old form
                 * until something else forced a restart. */
                if (this.actors.get(replay.getId()) instanceof ActorEntity actor)
                {
                    actor.setForm(FormUtils.copy(replay.form.get()));
                }
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
        if (from != tick)
        {
            this.tick = from;

            while (this.tick != tick)
            {
                this.tick += this.tick > tick ? -1 : 1;

                this.applyAction();
            }
        }
        else
        {
            /* Nothing to replay, but the destination is still the destination: a rewind to the
             * very frame the walk would have started from has to land there all the same. */
            this.tick = tick;
        }

        /* Poses after the actions and at the destination, not before: replaying the actions walks
         * the tick counter, and an actor placed before that walk stands on the frame it is leaving
         * rather than the one it was asked for. A restart jumps from 0, so that stale frame was
         * the very beginning - the actor appeared at the origin for a tick before catching up. */
        for (Map.Entry<String, EntityLivingBase> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                this.apply(entry.getValue(), replay, this.tick, false);
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

        for (EntityLivingBase value : this.actors.values())
        {
            NativeLivingState.clearPose(value);

            if (!(value instanceof EntityPlayer))
            {
                /* Before the body goes: what it swept up during the take is the world's, not the
                 * film's, and this is the last moment there is anywhere to put it back. */
                if (value instanceof ActorEntity actor)
                {
                    actor.dropPickedUp();
                }

                value.setDead();
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
            this.refreshPlayer();

            this.returnEquipment();

            ServerNetwork.sendMorphToTracked(this.serverPlayer, this.cachedForm);

            this.serverPlayer.setHealth(this.cacheHp);
            this.serverPlayer.getFoodStats().setFoodLevel(this.cacheHunger);
            this.serverPlayer.experience = this.cacheXpProgress;
            this.serverPlayer.experienceLevel = this.cacheXpLevel;
        }
    }

    public void toggle()
    {
        this.playing = !this.playing;
    }
}
