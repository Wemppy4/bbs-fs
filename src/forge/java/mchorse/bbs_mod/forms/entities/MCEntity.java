package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.utils.AABB;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** BBS's entity contract over MCP 1.12.2 fields, used by recording and live morphs. */
public class MCEntity extends StubEntity
{
    private final Entity entity;
    private Vec3d previousVelocity = Vec3d.ZERO;
    private float previousPreviousBodyYaw;

    public MCEntity(Entity entity) { this.entity = entity; }
    public Entity getMcEntity() { return this.entity; }
    private EntityLivingBase living() { return this.entity instanceof EntityLivingBase ? (EntityLivingBase) this.entity : null; }
    @Override public boolean isStandIn() { return false; }
    @Override public World getWorld() { return this.entity.world; }
    @Override public int getId() { return this.entity.getEntityId(); }
    @Override public int getAge() { return this.entity.ticksExisted; }
    @Override public void setAge(int age) { this.entity.ticksExisted = age; }
    @Override public double getX() { return this.entity.posX; }
    @Override public double getY() { return this.entity.posY; }
    @Override public double getZ() { return this.entity.posZ; }
    @Override public double getPrevX() { return this.entity.prevPosX; }
    @Override public double getPrevY() { return this.entity.prevPosY; }
    @Override public double getPrevZ() { return this.entity.prevPosZ; }
    @Override public void setPrevX(double v) { this.entity.prevPosX = v; }
    @Override public void setPrevY(double v) { this.entity.prevPosY = v; }
    @Override public void setPrevZ(double v) { this.entity.prevPosZ = v; }
    @Override public void setPosition(double x, double y, double z) { this.entity.setPosition(x, y, z); }
    @Override public double getEyeHeight() { return this.entity.getEyeHeight(); }
    @Override public float getYaw() { return this.entity.rotationYaw; }
    @Override public float getPrevYaw() { return this.entity.prevRotationYaw; }
    @Override public void setYaw(float v) { this.entity.rotationYaw = v; }
    @Override public void setPrevYaw(float v) { this.entity.prevRotationYaw = v; }
    @Override public float getPitch() { return this.entity.rotationPitch; }
    @Override public float getPrevPitch() { return this.entity.prevRotationPitch; }
    @Override public void setPitch(float v) { this.entity.rotationPitch = v; }
    @Override public void setPrevPitch(float v) { this.entity.prevRotationPitch = v; }
    @Override public float getHeadYaw() { return this.entity.getRotationYawHead(); }
    @Override public void setHeadYaw(float v) { this.entity.setRotationYawHead(v); }
    @Override public float getPrevHeadYaw() { return living() == null ? getPrevYaw() : living().prevRotationYawHead; }
    @Override public void setPrevHeadYaw(float v) { if (living() != null) living().prevRotationYawHead = v; }
    @Override public float getBodyYaw() { return living() == null ? getYaw() : living().renderYawOffset; }
    @Override public float getPrevBodyYaw() { return living() == null ? getPrevYaw() : living().prevRenderYawOffset; }
    @Override public void setBodyYaw(float v) { if (living() != null) living().renderYawOffset = v; }
    @Override public void setPrevBodyYaw(float v) { if (living() != null) living().prevRenderYawOffset = v; }
    @Override public float getPrevPrevBodyYaw() { return this.previousPreviousBodyYaw; }
    @Override public void setPrevPrevBodyYaw(float v) { this.previousPreviousBodyYaw = v; }
    @Override public boolean isSneaking() { return this.entity.isSneaking(); }
    @Override public void setSneaking(boolean v) { this.entity.setSneaking(v); }
    @Override public boolean isSprinting() { return this.entity.isSprinting(); }
    @Override public void setSprinting(boolean v) { this.entity.setSprinting(v); }
    @Override public boolean isOnGround() { return this.entity.onGround; }
    @Override public void setOnGround(boolean v) { this.entity.onGround = v; }
    @Override public boolean isRiding() { return this.entity.isRiding(); }
    @Override public boolean isRidden() { return this.entity.isBeingRidden(); }
    @Override public boolean isFlying() { return this.entity instanceof EntityPlayer && ((EntityPlayer) this.entity).capabilities.isFlying; }
    @Override public void setFlying(boolean v) { if (this.entity instanceof EntityPlayer) ((EntityPlayer) this.entity).capabilities.isFlying = v; }
    @Override public boolean isSwimming() { return NativeLivingState.isSwimming(this.entity); }
    @Override public void setSwimming(boolean value) { NativeLivingState.setSwimming(this.entity, value); }
    @Override public EntityPose getEntityPose() { return NativeLivingState.getPose(this.entity); }
    @Override public void setFallFlying(boolean value) { NativeLivingState.setGliding(this.entity, value); }
    @Override public void setRoll(int value) { if (living() != null) NativeLivingState.setRoll(living(), value); }
    @Override public void setDeathTime(int value) { if (living() != null) living().deathTime = value; }
    @Override public boolean isFallFlying() { return living() != null && living().isElytraFlying(); }
    @Override public boolean isTouchingWater() { return this.entity.isInWater(); }
    @Override public boolean isBurning() { return this.entity.isBurning(); }
    @Override public boolean isInLava() { return this.entity.isInLava(); }
    @Override public boolean isClimbing() { return living() != null && living().isOnLadder(); }
    @Override public int getRoll() { return living() == null ? 0 : living().getTicksElytraFlying(); }
    @Override public float getFallDistance() { return this.entity.fallDistance; }
    @Override public void setFallDistance(float v) { this.entity.fallDistance = v; }
    @Override public int getHurtTimer() { return living() == null ? 0 : living().hurtTime; }
    @Override public void setHurtTimer(int v) { if (living() != null) living().hurtTime = v; }
    @Override public int getDeathTime() { return living() == null ? 0 : living().deathTime; }
    @Override public float getHealth() { return living() == null ? FULL_HEALTH : living().getHealth(); }
    @Override public float getMaxHealth() { return living() == null ? FULL_HEALTH : living().getMaxHealth(); }
    @Override public boolean isChild() { return living() != null && living().isChild(); }
    @Override public void swingArm() { if (living() != null) living().swingArm(EnumHand.MAIN_HAND); }
    @Override public float getHandSwingProgress(float partial) { return living() == null ? 0F : living().getSwingProgress(partial); }
    @Override public boolean isUsingItem() { return living() != null && living().isHandActive(); }
    @Override public boolean isBlocking() { return living() != null && living().isActiveItemStackBlocking(); }
    @Override public float getForwardSpeed() { return living() == null ? 0F : living().moveForward; }
    @Override public float getSidewaysSpeed() { return living() == null ? 0F : living().moveStrafing; }
    @Override public float getLimbPos(float partial) { return living() == null ? 0F : living().limbSwing - living().limbSwingAmount * (1F - partial); }
    @Override public float getLimbSpeed(float partial) { return living() == null ? 0F : living().prevLimbSwingAmount + (living().limbSwingAmount - living().prevLimbSwingAmount) * partial; }
    @Override public Vec3d getVelocity() { return new Vec3d(this.entity.motionX, this.entity.motionY, this.entity.motionZ); }
    @Override public void setVelocity(float x, float y, float z) { this.entity.motionX = x; this.entity.motionY = y; this.entity.motionZ = z; }
    @Override public Vec3d getRotationVec(float partial) { return this.entity.getLook(partial); }
    @Override public Vec3d lerpVelocity(float partial) { return this.previousVelocity.add(getVelocity().subtract(this.previousVelocity).scale(partial)); }
    @Override public void update() { this.previousVelocity = getVelocity(); this.previousPreviousBodyYaw = getPrevBodyYaw(); }
    @Override public AABB getPickingHitbox() { return new AABB(getX() - this.entity.width / 2F, getY(), getZ() - this.entity.width / 2F, this.entity.width, this.entity.height, this.entity.width); }
    @Override public ItemStack getEquipmentStack(EntityEquipmentSlot slot) { return living() == null ? ItemStack.EMPTY : living().getItemStackFromSlot(slot); }
    @Override public void setEquipmentStack(EntityEquipmentSlot slot, ItemStack stack) { if (living() != null && !ItemStack.areItemStacksEqual(getEquipmentStack(slot), stack)) living().setItemStackToSlot(slot, stack.copy()); }
    @Override public int getSelectedSlot() { return this.entity instanceof EntityPlayer ? ((EntityPlayer) this.entity).inventory.currentItem : 0; }
    @Override public boolean isMainHandInHotbar() { return this.entity instanceof EntityPlayer; }
    @Override public ItemStack getHotbarStack(int slot)
    {
        if (slot < 0 || slot >= HOTBAR_SIZE) return ItemStack.EMPTY;
        return this.entity instanceof EntityPlayer ? ((EntityPlayer) this.entity).inventory.getStackInSlot(slot) : slot == 0 ? getEquipmentStack(EntityEquipmentSlot.MAINHAND) : ItemStack.EMPTY;
    }
    @Override public void setHotbarStack(int slot, ItemStack stack)
    {
        if (slot < 0 || slot >= HOTBAR_SIZE) return;
        if (this.entity instanceof EntityPlayer) { if (!ItemStack.areItemStacksEqual(getHotbarStack(slot), stack)) ((EntityPlayer) this.entity).inventory.setInventorySlotContents(slot, stack.copy()); }
        else if (slot == 0) setEquipmentStack(EntityEquipmentSlot.MAINHAND, stack);
    }
}
