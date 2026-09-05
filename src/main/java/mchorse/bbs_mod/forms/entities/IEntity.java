package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.cubic.jem.CemVariables;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Interface that provides access to an "Entity" within forms for rendering
 * and updating.
 */
public interface IEntity
{
    public void setWorld(World world);

    public World getWorld();

    public Form getForm();

    public void setForm(Form form);

    public ItemStack getEquipmentStack(EquipmentSlot slot);

    public void setEquipmentStack(EquipmentSlot slot, ItemStack stack);

    /**
     * Hotbar cell at given index. Entities without a hotbar of their own (mobs, actors) still
     * answer for slot 0 with their main hand, since {@link #getSelectedSlot()} keeps them there
     * - so "the hand is the selected hotbar slot" holds for everyone.
     */
    public ItemStack getHotbarStack(int slot);

    public void setHotbarStack(int slot, ItemStack stack);

    /**
     * Whether the main hand is a view of the hotbar rather than a slot of its own.
     *
     * On a player it is: the hand is the selected cell, so writing to it means writing into
     * whichever cell is selected at that instant - and during playback the selection can still
     * be a frame behind, which drops the item into the cell the player just left.
     */
    public default boolean isMainHandInHotbar()
    {
        return false;
    }

    public int getSelectedSlot();

    public boolean isSneaking();

    public void setSneaking(boolean sneaking);

    public boolean isSprinting();

    public void setSprinting(boolean sprinting);

    public boolean isOnGround();

    public void setOnGround(boolean ground);

    public boolean isSwimming();

    public void setSwimming(boolean swimming);

    /**
     * Whether the entity rides something. A replay only writes the fact down - it never mounts
     * anyone, since the frame already says where the rider is - so this drives the pose and the
     * animation and nothing else.
     */
    public boolean isRiding();

    public void setRiding(boolean riding);

    /** Creative flight, as opposed to {@link #isFallFlying()}, which is an elytra. */
    public boolean isFlying();

    public void setFlying(boolean flying);

    public void swingArm();

    public float getHandSwingProgress(float tickDelta);

    public int getAge();

    /**
     * Whether this stands in for an actor rather than wrapping a Minecraft entity that actually spawned.
     *
     * <p>It is the age that makes the difference. A stand-in's age counts from the moment BBS made the
     * object — a film loading, a panel opening — not from a spawn in the world, and an animation that
     * treats a small age as "just spawned" then replays a spawn every time.</p>
     */
    public default boolean isStandIn()
    {
        return false;
    }

    /**
     * The CEM variables of this entity, or null when this implementation keeps none (every CEM model on
     * it then animates on its own, the way they all did before).
     *
     * <p>OptiFine scopes a pack's {@code var.*}/{@code varb.*} to the entity, so its models can read each
     * other — see {@link CemVariables}. Created on first use: an entity that never renders a .jem never
     * allocates one.</p>
     */
    public default CemVariables getCemVariables()
    {
        return null;
    }

    public void setAge(int ticks);

    public float getFallDistance();

    public void setFallDistance(float fallDistance);

    public int getHurtTimer();

    public void setHurtTimer(int hurtTimer);

    /** A stable identity for per-entity variety (CEM's {@code id}): the MC entity id, or a per-instance number for a stub. */
    public default int getId()
    {
        return 0;
    }

    /** Ticks since the entity died (vanilla {@code LivingEntity.deathTime}); 0 while alive. */
    public default int getDeathTime()
    {
        return 0;
    }

    /** Whether something rides this entity — the opposite of {@link #isRiding()}. */
    public default boolean isRidden()
    {
        return false;
    }

    /** Whether this is the baby variant of its kind. */
    public default boolean isChild()
    {
        return false;
    }

    public double getX();

    public double getPrevX();

    public void setPrevX(double x);

    public double getY();

    public double getPrevY();

    public void setPrevY(double y);

    public double getZ();

    public double getPrevZ();

    public void setPrevZ(double z);

    public void setPosition(double x, double y, double z);

    public double getEyeHeight();

    public Vec3d getVelocity();

    public void setVelocity(float x, float y, float z);

    public float getYaw();

    public float getPrevYaw();

    public void setYaw(float yaw);

    public void setPrevYaw(float prevYaw);

    public float getHeadYaw();

    public float getPrevHeadYaw();

    public void setHeadYaw(float headYaw);

    public void setPrevHeadYaw(float prevHeadYaw);

    public float getPitch();

    public float getPrevPitch();

    public void setPitch(float pitch);

    public void setPrevPitch(float prevPitch);

    public float getBodyYaw();

    public float getPrevBodyYaw();

    public float getPrevPrevBodyYaw();

    public void setBodyYaw(float bodyYaw);

    public void setPrevBodyYaw(float prevBodyYaw);

    public void setPrevPrevBodyYaw(float prevPrevBodyYaw);

    public float[] getExtraVariables();

    public float[] getPrevExtraVariables();

    public AABB getPickingHitbox();

    public void update();

    public default void copy(IEntity entity)
    {
        this.setForm(entity.getForm());

        for (EntityState state : EntityState.values())
        {
            state.set(this, state.get(entity));
        }

        this.setFallDistance(entity.getFallDistance());
        this.setHurtTimer(entity.getHurtTimer());

        this.setPrevX(entity.getPrevX());
        this.setPrevY(entity.getPrevY());
        this.setPrevZ(entity.getPrevZ());
        this.setPosition(entity.getX(), entity.getY(), entity.getZ());

        this.setPrevYaw(entity.getPrevYaw());
        this.setPrevHeadYaw(entity.getPrevHeadYaw());
        this.setPrevPitch(entity.getPrevPitch());
        this.setPrevBodyYaw(entity.getPrevBodyYaw());
        this.setPrevPrevBodyYaw(entity.getPrevPrevBodyYaw());

        this.setYaw(entity.getYaw());
        this.setHeadYaw(entity.getHeadYaw());
        this.setPitch(entity.getPitch());
        this.setBodyYaw(entity.getBodyYaw());

        this.setVelocity((float) entity.getVelocity().x, (float) entity.getVelocity().y, (float) entity.getVelocity().z);

        float[] extraVariables = this.getExtraVariables();
        float[] prevExtraVariables = this.getPrevExtraVariables();

        for (int i = 0; i < extraVariables.length; i++)
        {
            extraVariables[i] = entity.getExtraVariables()[i];
            prevExtraVariables[i] = entity.getPrevExtraVariables()[i];
        }
    }

    public LimbAnimator getLimbAnimator();

    public float getLimbPos(float tickDelta);

    public float getLimbSpeed(float tickDelta);

    /* Swimming */

    public float getLeaningPitch(float tickDelta);

    /**
     * How far the body has leant into a swim, 0 to 1.
     *
     * <p>Vanilla grows this a step per tick while the entity is in a swimming pose. A replay
     * can't do that: a timeline is random access, and anything a playback accumulates tick by
     * tick is wrong the moment someone scrubs to the middle of a swim. So the lean is recorded
     * as a value of its own and handed back here.</p>
     */
    public void setLeaningPitch(float leaningPitch);

    public boolean isTouchingWater();

    public EntityPose getEntityPose();

    public int getRoll();

    /** Ticks of roll, which ramps the elytra's dive and spins a riptide. Recorded, not counted. */
    public void setRoll(int roll);

    public boolean isFallFlying();

    public void setFallFlying(boolean fallFlying);

    public Vec3d getRotationVec(float transition);

    public Vec3d lerpVelocity(float transition);

    public boolean isUsingRiptide();
}