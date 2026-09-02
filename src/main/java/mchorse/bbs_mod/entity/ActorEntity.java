package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActorEntity extends LivingEntity implements IEntityFormProvider
{
    public static DefaultAttributeContainer.Builder createActorAttributes()
    {
        return LivingEntity.createLivingAttributes()
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1D)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1D)
            .add(EntityAttributes.GENERIC_ATTACK_SPEED)
            .add(EntityAttributes.GENERIC_LUCK);
    }

    private boolean despawn;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    /**
     * Which replay of which film put this body here. A client needs the pairing to know that this
     * entity is a replay's body rather than a creature, and it has to be able to learn that from
     * the entity alone - the map of actors is broadcast when they spawn, which is of no use to
     * anyone who starts seeing one later.
     */
    private String filmId = "";
    private String replayId = "";

    private boolean pickUpItems = true;
    private final List<ItemStack> pickedUp = new ArrayList<>();

    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    public void setReplay(String filmId, String replayId)
    {
        this.filmId = filmId;
        this.replayId = replayId;
    }

    public String getFilmId()
    {
        return this.filmId;
    }

    public String getReplayId()
    {
        return this.replayId;
    }

    public MCEntity getEntity()
    {
        return this.entity;
    }

    @Override
    public int getEntityId()
    {
        return this.getId();
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        Form lastForm = this.form;

        this.form = form;

        if (!this.getWorld().isClient())
        {
            if (lastForm != null) lastForm.onDemorph(this);
            if (form != null) form.onMorph(this);
        }

        /* The body changed, so the box around it has to change too */
        this.calculateDimensions();
    }

    /**
     * The form's own hitbox, when it declares one. An actor exists so blows land on it, and a box
     * of vanilla's player size around a four-block model means only its ankles can be hit - the
     * flag promised a body in the world and delivered a shin. Same properties the picking box in
     * the editor already reads, so the two agree.
     */
    @Override
    protected EntityDimensions getBaseDimensions(EntityPose pose)
    {
        if (this.form == null || !this.form.hitbox.get())
        {
            return super.getBaseDimensions(pose);
        }

        float width = this.form.hitboxWidth.get();
        float height = this.form.hitboxHeight.get();

        if (pose == EntityPose.CROUCHING)
        {
            height *= this.form.hitboxSneakMultiplier.get();
        }

        /* Since 1.21.1 the eye height rides the dimensions instead of an override of its own */
        return EntityDimensions.changing(width, height).withEyeHeight(this.form.hitboxEyeHeight.get());
    }

    /**
     * An actor is a prop, not a creature. Being hit is the whole point of the flag, but dying is
     * not: the film has no notion of a dead actor, so a killed one simply left a hole in the take
     * that nothing filled. Everything about the blow still happens - the flash, the sound, the
     * knockback the next keyframe undoes - only the health never runs out. Damage that bypasses
     * invulnerability is let through, which keeps {@code /kill} as the way out.
     */
    @Override
    public boolean damage(DamageSource source, float amount)
    {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return super.damage(source, amount);
        }

        return super.damage(source, 0F);
    }

    @Override
    public boolean shouldRender(double distance)
    {
        double d = this.getBoundingBox().getAverageSideLength();

        if (Double.isNaN(d))
        {
            d = 1D;
        }

        return distance < (d * 256D) * (d * 256D);
    }

    @Override
    public Iterable<ItemStack> getHandItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.MAINHAND), this.getEquippedStack(EquipmentSlot.OFFHAND));
    }

    @Override
    public Iterable<ItemStack> getArmorItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.FEET), this.getEquippedStack(EquipmentSlot.LEGS), this.getEquippedStack(EquipmentSlot.CHEST), this.getEquippedStack(EquipmentSlot.HEAD));
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return this.equipment.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }

    @Override
    public void tick()
    {
        super.tick();

        this.tickHandSwing();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }

        if (this.getWorld().isClient)
        {
            return;
        }

        if (!this.pickUpItems)
        {
            return;
        }

        /* Pickup items */
        Box box = this.getBoundingBox().expand(1D, 0.5D, 1D);
        List<Entity> list = this.getWorld().getOtherEntities(this, box);

        for (Entity entity : list)
        {
            if (entity instanceof ItemEntity itemEntity)
            {
                ItemStack itemStack = itemEntity.getStack();
                int i = itemStack.getCount();

                if (!entity.isRemoved() && !itemEntity.cannotPickup())
                {
                    ((ServerWorld) this.getWorld()).getChunkManager().sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), i));

                    /* Kept, not destroyed: an actor has no inventory to put this in, so what it
                     * swept up used to simply cease to exist - a take rolling near someone's
                     * dropped things ate them. Held until the film stops, then put back. */
                    this.pickedUp.add(itemStack.copy());

                    entity.discard();
                }
            }
        }
    }

    public void setPickUpItems(boolean pickUpItems)
    {
        this.pickUpItems = pickUpItems;
    }

    /** Put back everything this body swept up, where it now stands. */
    public void dropPickedUp()
    {
        if (this.pickedUp.isEmpty() || this.getWorld().isClient())
        {
            return;
        }

        for (ItemStack stack : this.pickedUp)
        {
            ItemEntity item = new ItemEntity(this.getWorld(), this.getX(), this.getY() + 0.5D, this.getZ(), stack);

            item.setToDefaultPickupDelay();
            this.getWorld().spawnEntity(item);
        }

        this.pickedUp.clear();
    }

    @Override
    public void checkDespawn()
    {
        super.checkDespawn();

        if (this.despawn)
        {
            this.discard();
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        ServerNetwork.sendEntityForm(player, this);

        /* Who this body belongs to, told to whoever just came within sight of it. The cast map is
         * broadcast when the actors spawn and never again, so a player who joined, changed
         * dimension or simply walked over later had no way of pairing this entity with its replay. */
        if (!this.replayId.isEmpty())
        {
            ServerNetwork.sendActor(player, this.filmId, this.replayId, this.getId());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt)
    {
        super.readCustomDataFromNbt(nbt);

        this.despawn = nbt.getBoolean("despawn");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt)
    {
        super.writeCustomDataToNbt(nbt);

        nbt.putBoolean("despawn", true);
    }

    @Override
    protected int getPermissionLevel()
    {
        return 4;
    }
}