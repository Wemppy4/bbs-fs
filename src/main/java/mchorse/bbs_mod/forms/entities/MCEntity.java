package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.mixin.EntityInvoker;
import mchorse.bbs_mod.mixin.LivingEntityRollAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MCEntity implements IEntity
{
    private Entity mcEntity;

    private float prevPrevBodyYaw;
    private Vec3d lastVelocity = Vec3d.ZERO;

    private float[] extraVariables = new float[10];
    private float[] prevExtraVariables = new float[10];

    public MCEntity(Entity mcEntity)
    {
        this.mcEntity = mcEntity;
    }

    public Entity getMcEntity()
    {
        return this.mcEntity;
    }

    @Override
    public void setWorld(World world)
    {}

    @Override
    public World getWorld()
    {
        return this.mcEntity.getWorld();
    }

    @Override
    public Form getForm()
    {
        Morph morph = Morph.getMorph(this.mcEntity);

        return morph == null ? null : morph.getForm();
    }

    @Override
    public void setForm(Form form)
    {
        Morph morph = Morph.getMorph(this.mcEntity);

        if (morph != null)
        {
            morph.setForm(form);
        }
    }

    @Override
    public ItemStack getEquipmentStack(EquipmentSlot slot)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.getEquippedStack(slot);
        }

        return ItemStack.EMPTY;
    }

    /**
     * The stack lands on a live entity, which vanilla is free to mutate, and it usually comes
     * out of a keyframe - so it's copied, and left alone when the cell already holds it. That
     * second part matters on players: the film writes every tick, and an unchanged write would
     * still count as an inventory change for the client sync.
     */
    @Override
    public void setEquipmentStack(EquipmentSlot slot, ItemStack stack)
    {
        if (this.mcEntity instanceof LivingEntity living && !ItemStack.areEqual(living.getEquippedStack(slot), stack))
        {
            living.equipStack(slot, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    @Override
    public ItemStack getHotbarStack(int slot)
    {
        if (this.mcEntity instanceof PlayerEntity player)
        {
            return player.getInventory().getStack(slot);
        }

        /* Mobs and actors have no hotbar, and their selected slot is 0 - so their main hand
         * is what slot 0 holds. */
        return slot == 0 ? this.getEquipmentStack(EquipmentSlot.MAINHAND) : ItemStack.EMPTY;
    }

    @Override
    public void setHotbarStack(int slot, ItemStack stack)
    {
        if (stack == null)
        {
            stack = ItemStack.EMPTY;
        }

        if (this.mcEntity instanceof PlayerEntity player)
        {
            if (!ItemStack.areEqual(player.getInventory().getStack(slot), stack))
            {
                player.getInventory().setStack(slot, stack.copy());
            }
        }
        else if (slot == 0)
        {
            this.setEquipmentStack(EquipmentSlot.MAINHAND, stack);
        }
    }

    @Override
    public boolean isMainHandInHotbar()
    {
        return this.mcEntity instanceof PlayerEntity;
    }

    @Override
    public int getSelectedSlot()
    {
        if (this.mcEntity instanceof PlayerEntity player)
        {
            return player.getInventory().selectedSlot;
        }

        return 0;
    }

    @Override
    public boolean isSneaking()
    {
        return this.mcEntity.isSneaking();
    }

    @Override
    public void setSneaking(boolean sneaking)
    {
        this.mcEntity.setSneaking(sneaking);
    }

    @Override
    public boolean isSprinting()
    {
        return this.mcEntity.isSprinting();
    }

    @Override
    public void setSprinting(boolean sprinting)
    {
        this.mcEntity.setSprinting(sprinting);
    }

    @Override
    public boolean isOnGround()
    {
        return this.mcEntity.isOnGround();
    }

    @Override
    public void setOnGround(boolean ground)
    {
        this.mcEntity.setOnGround(ground);
    }

    @Override
    public boolean isSwimming()
    {
        return this.mcEntity.isSwimming();
    }

    @Override
    public void setSwimming(boolean swimming)
    {
        this.mcEntity.setSwimming(swimming);
    }

    @Override
    public boolean isRiding()
    {
        return this.mcEntity.hasVehicle();
    }

    /**
     * Riding isn't a flag one can set - it's whether something is being ridden - and a replay
     * doesn't mount anyone, so there is nothing to write onto a live entity here.
     */
    @Override
    public void setRiding(boolean riding)
    {}

    @Override
    public boolean isFlying()
    {
        return this.mcEntity instanceof PlayerEntity player && player.getAbilities().flying;
    }

    /**
     * Deliberately nothing: creative flight is a permission on a real player, and a film has no
     * business granting or taking it. The recorded state only picks an animation.
     */
    @Override
    public void setFlying(boolean flying)
    {}

    @Override
    public void swingArm()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            living.swingHand(Hand.MAIN_HAND);
        }
    }

    @Override
    public float getHandSwingProgress(float tickDelta)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.getHandSwingProgress(tickDelta);
        }

        return 0F;
    }

    @Override
    public int getAge()
    {
        return this.mcEntity.age;
    }

    @Override
    public void setAge(int ticks)
    {
        this.mcEntity.age = ticks;
    }

    @Override
    public float getFallDistance()
    {
        return this.mcEntity.fallDistance;
    }

    @Override
    public void setFallDistance(float fallDistance)
    {
        this.mcEntity.fallDistance = fallDistance;
    }

    @Override
    public int getHurtTimer()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.hurtTime;
        }

        return 0;
    }

    @Override
    public void setHurtTimer(int hurtTimer)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            living.hurtTime = hurtTimer;
        }
    }

    @Override
    public double getX()
    {
        return this.mcEntity.getX();
    }

    @Override
    public double getPrevX()
    {
        return this.mcEntity.prevX;
    }

    @Override
    public void setPrevX(double x)
    {
        this.mcEntity.prevX = x;
    }

    @Override
    public double getY()
    {
        return this.mcEntity.getY();
    }

    @Override
    public double getPrevY()
    {
        return this.mcEntity.prevY;
    }

    @Override
    public void setPrevY(double y)
    {
        this.mcEntity.prevY = y;
    }

    @Override
    public double getZ()
    {
        return this.mcEntity.getZ();
    }

    @Override
    public double getPrevZ()
    {
        return this.mcEntity.prevZ;
    }

    @Override
    public void setPrevZ(double z)
    {
        this.mcEntity.prevZ = z;
    }

    @Override
    public void setPosition(double x, double y, double z)
    {
        this.mcEntity.setPosition(x, y, z);
    }

    @Override
    public double getEyeHeight()
    {
        return this.mcEntity.getEyeHeight(this.mcEntity.getPose());
    }

    @Override
    public Vec3d getVelocity()
    {
        return this.mcEntity.getVelocity();
    }

    @Override
    public void setVelocity(float x, float y, float z)
    {
        this.mcEntity.setVelocity(x, y, z);
    }

    @Override
    public float getYaw()
    {
        return this.mcEntity.getYaw();
    }

    @Override
    public float getPrevYaw()
    {
        return this.mcEntity.prevYaw;
    }

    @Override
    public void setYaw(float yaw)
    {
        this.mcEntity.setYaw(yaw);
    }

    @Override
    public void setPrevYaw(float prevYaw)
    {
        this.mcEntity.prevYaw = prevYaw;
    }

    @Override
    public float getHeadYaw()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.getHeadYaw();
        }

        return this.mcEntity.getYaw();
    }

    @Override
    public float getPrevHeadYaw()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.prevHeadYaw;
        }

        return this.mcEntity.prevYaw;
    }

    @Override
    public void setHeadYaw(float headYaw)
    {
        this.mcEntity.setHeadYaw(headYaw);
    }

    @Override
    public void setPrevHeadYaw(float prevHeadYaw)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            living.prevHeadYaw = prevHeadYaw;
        }
    }

    @Override
    public float getPitch()
    {
        return this.mcEntity.getPitch();
    }

    @Override
    public float getPrevPitch()
    {
        return this.mcEntity.prevPitch;
    }

    @Override
    public void setPitch(float pitch)
    {
        this.mcEntity.setPitch(pitch);
    }

    @Override
    public void setPrevPitch(float prevPitch)
    {
        this.mcEntity.prevPitch = prevPitch;
    }

    @Override
    public float getBodyYaw()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.bodyYaw;
        }

        return this.getHeadYaw();
    }

    @Override
    public float getPrevBodyYaw()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.prevBodyYaw;
        }

        return this.getPrevHeadYaw();
    }

    @Override
    public float getPrevPrevBodyYaw()
    {
        return this.prevPrevBodyYaw;
    }

    @Override
    public void setBodyYaw(float bodyYaw)
    {
        this.mcEntity.setBodyYaw(bodyYaw);
    }

    @Override
    public void setPrevBodyYaw(float prevBodyYaw)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            living.prevBodyYaw = prevBodyYaw;
        }
    }

    @Override
    public void setPrevPrevBodyYaw(float prevPrevBodyYaw)
    {
        this.prevPrevBodyYaw = prevPrevBodyYaw;
    }

    @Override
    public float[] getExtraVariables()
    {
        return this.extraVariables;
    }

    @Override
    public float[] getPrevExtraVariables()
    {
        return this.prevExtraVariables;
    }

    @Override
    public AABB getPickingHitbox()
    {
        float w = this.mcEntity.getWidth();
        float h = this.mcEntity.getHeight();

        return new AABB(
            this.getX() - w / 2, this.getY(), this.getZ() - w / 2,
            w, h, w
        );
    }

    @Override
    public void update()
    {
        this.lastVelocity = this.mcEntity.getVelocity();
        this.prevPrevBodyYaw = this.getPrevBodyYaw();

        for (int i = 0; i < this.extraVariables.length; i++)
        {
            this.prevExtraVariables[i] = this.extraVariables[i];
        }
    }

    @Override
    public LimbAnimator getLimbAnimator()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.limbAnimator;
        }

        return null;
    }

    @Override
    public float getLimbPos(float tickDelta)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.limbAnimator.getPos(tickDelta);
        }

        return 0F;
    }

    @Override
    public float getLimbSpeed(float tickDelta)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.limbAnimator.getSpeed(tickDelta);
        }

        return 0F;
    }

    @Override
    public float getLeaningPitch(float tickDelta)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.getLeaningPitch(tickDelta);
        }

        return 0F;
    }

    /**
     * The lean is vanilla's own on a live entity - it grows it every tick - so there is nothing
     * to hand it. Only the preview stub, which has no ticks of its own, needs to be told.
     */
    @Override
    public void setLeaningPitch(float leaningPitch)
    {}

    @Override
    public boolean isTouchingWater()
    {
        return this.mcEntity.isTouchingWater();
    }

    @Override
    public EntityPose getEntityPose()
    {
        return this.mcEntity.getPose();
    }

    @Override
    public int getRoll()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.getRoll();
        }

        return 0;
    }

    @Override
    public void setRoll(int roll)
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            ((LivingEntityRollAccessor) living).bbs$setRoll(roll);
        }
    }

    @Override
    public boolean isFallFlying()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.isFallFlying();
        }

        return false;
    }

    /**
     * Gliding is a tracked flag, and writing it on the server is what tells every client to
     * spread the wings - the same reason a played back actor's sprinting flag is written rather
     * than merely moved fast.
     */
    @Override
    public void setFallFlying(boolean fallFlying)
    {
        ((EntityInvoker) this.mcEntity).bbs$setFlag(EntityState.FALL_FLYING_FLAG, fallFlying);
    }

    @Override
    public Vec3d getRotationVec(float transition)
    {
        return this.mcEntity.getRotationVec(transition);
    }

    @Override
    public Vec3d lerpVelocity(float transition)
    {
        return this.lastVelocity.lerp(this.mcEntity.getVelocity(), transition);
    }

    @Override
    public boolean isUsingRiptide()
    {
        if (this.mcEntity instanceof LivingEntity living)
        {
            return living.isUsingRiptide();
        }

        return false;
    }
}