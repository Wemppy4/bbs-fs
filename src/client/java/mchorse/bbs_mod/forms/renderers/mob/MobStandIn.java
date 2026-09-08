package mchorse.bbs_mod.forms.renderers.mob;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.mixin.EntityInvoker;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.utils.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * A vanilla entity standing in for a form's actor, so vanilla's own model code can be run for it.
 *
 * <p>The mob form renders through the game's entity renderer, and a CEM model runs the vanilla model's
 * animation under its pack's program ({@code CemVanillaStage}); both need a real {@link Entity} of the
 * right kind that vanilla's code will accept, posed the way the form's actor is. This makes one — an
 * entity type's own instance in the client world, or a player for the player's model — and keeps it in
 * step with the actor tick by tick: the limbs, the hand swing, head and body yaw, the position, the
 * flags vanilla's animation reads, the equipment, the age. The entity is remade when what it is made
 * from changes.</p>
 */
public class MobStandIn
{
    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNbt = "";
    private boolean lastSlim;

    private float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    /** The entity standing in, or null while there is none — see {@link #ensure}. */
    public Entity entity()
    {
        return this.entity;
    }

    /**
     * The entity for an id, made on the first ask and remade when the id, the NBT or the arm width
     * changes. Null when the game has no world to make it in, or no such kind of entity: an id the
     * registry does not know is not the pig it answers with by default. The player has no entity type
     * to create from — asked for one ({@code player}), a player of the given arm width stands in.
     */
    public Entity ensure(String id, String nbt, boolean slim, boolean player)
    {
        if (!this.lastId.equals(id) || !this.lastNbt.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNbt = nbt;
            this.lastSlim = slim;
            this.entity = null;
        }

        ClientWorld world = MinecraftClient.getInstance().world;

        if (this.entity != null || world == null)
        {
            return this.entity;
        }

        NbtCompound compound = new NbtCompound();

        try
        {
            compound = (new StringNbtReader(new StringReader(nbt))).parseCompound();
        }
        catch (Exception e)
        {}

        Identifier identifier = Identifier.tryParse(id);
        EntityType<?> type = identifier != null && Registries.ENTITY_TYPE.containsId(identifier) ? Registries.ENTITY_TYPE.get(identifier) : null;

        this.entity = type == null ? null : type.create(world);

        if (this.entity == null && player)
        {
            this.entity = new OtherClientPlayerEntity(world, slim ? SLIM : WIDE);
            this.entity.getDataTracker().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }

        if (this.entity != null)
        {
            compound.putString("id", id);
            this.entity.readNbt(compound);
            this.entity.noClip = true;
        }

        return this.entity;
    }

    /** Step the entity and bring it in line with the actor: what vanilla's animation will read off it next frame. */
    public void tick(IEntity source)
    {
        if (this.entity == null)
        {
            return;
        }

        this.entity.tick();

        this.entity.prevPitch = this.prevPitch;
        this.entity.prevYaw = 0F;

        if (this.entity instanceof LivingEntity livingEntity)
        {
            livingEntity.prevHeadYaw = this.prevYawHead;
            livingEntity.prevBodyYaw = 0F;

            /* Limb swing is so ugly */
            if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor a && source.getLimbAnimator() instanceof LimbAnimatorAccessor b)
            {
                a.setPrevSpeed(b.getPrevSpeed());
                a.setSpeed(b.getSpeed());
                a.setPos(b.getPos());
            }

            /* Arm swing */
            float handSwingProgress = source.getHandSwingProgress(0F);

            if (handSwingProgress < this.prevHandSwing)
            {
                this.prevHandSwing = 0;
            }

            if (handSwingProgress > 0 && this.prevHandSwing == 0)
            {
                livingEntity.swingHand(Hand.MAIN_HAND);
            }

            this.prevHandSwing = handSwingProgress;
        }

        this.entity.setYaw(0F);
        this.entity.setHeadYaw(source.getHeadYaw() - source.getBodyYaw());
        this.entity.setPitch(source.getPitch());
        this.entity.setBodyYaw(0F);

        this.entity.setPos(source.getX(), source.getY(), source.getZ());
        this.entity.setOnGround(source.isOnGround());
        this.entity.setSneaking(source.isSneaking());
        this.entity.setSprinting(source.isSprinting());
        this.entity.setSwimming(source.isSwimming());
        ((EntityInvoker) this.entity).bbs$setFlag(EntityState.FALL_FLYING_FLAG, source.isFallFlying());
        this.entity.setPose(EntityState.pose(source));
        this.entity.equipStack(EquipmentSlot.MAINHAND, source.getEquipmentStack(EquipmentSlot.MAINHAND));
        this.entity.equipStack(EquipmentSlot.OFFHAND, source.getEquipmentStack(EquipmentSlot.OFFHAND));
        this.entity.equipStack(EquipmentSlot.HEAD, source.getEquipmentStack(EquipmentSlot.HEAD));
        this.entity.equipStack(EquipmentSlot.CHEST, source.getEquipmentStack(EquipmentSlot.CHEST));
        this.entity.equipStack(EquipmentSlot.LEGS, source.getEquipmentStack(EquipmentSlot.LEGS));
        this.entity.equipStack(EquipmentSlot.FEET, source.getEquipmentStack(EquipmentSlot.FEET));
        this.entity.age = source.getAge();
        this.entity.noClip = true;

        this.prevYawHead = source.getHeadYaw() - source.getBodyYaw();
        this.prevPitch = source.getPitch();
    }
}
