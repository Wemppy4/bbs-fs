package mchorse.bbs_mod.forms.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** Native living flags and BBS-only poses. A modern swimming pose has no vanilla 1.12
 * tracked field, so BBS's renderer reads it through IEntity instead. */
public final class NativeLivingState
{
    private static final Map<Entity, EntityPose> POSES = new WeakHashMap<>();
    private static final Field ROLL = ReflectionHelper.findField(EntityLivingBase.class, "ticksElytraFlying", "field_184629_bo");
    public static void setPose(Entity entity, EntityPose pose)
    {
        if (pose == EntityPose.SWIMMING) POSES.put(entity, pose); else POSES.remove(entity);
    }
    public static void clearPose(Entity entity) { POSES.remove(entity); }
    public static EntityPose getPose(Entity entity)
    {
        EntityPose pose = POSES.get(entity);
        if (pose != null) return pose;
        return EntityState.pose(entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isElytraFlying(), false, entity.isSneaking());
    }
    public static boolean isSwimming(Entity entity) { return POSES.get(entity) == EntityPose.SWIMMING; }
    public static void setSwimming(Entity entity, boolean value)
    {
        if (value) POSES.put(entity, EntityPose.SWIMMING);
        else if (POSES.get(entity) == EntityPose.SWIMMING) POSES.remove(entity);
    }
    public static void setGliding(Entity entity, boolean value)
    {
        byte flags = entity.getDataManager().get(Access.flags());
        entity.getDataManager().set(Access.flags(), (byte) (value ? flags | 128 : flags & ~128));
    }
    public static void setRoll(EntityLivingBase entity, int value)
    {
        try { ROLL.setInt(entity, value); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot set native elytra timer", error); }
    }
    public static DataParameter<Byte> handStates() { return Access.hands(); }
    private abstract static class Access extends EntityLivingBase
    {
        private Access(World world) { super(world); }
        private static DataParameter<Byte> flags() { return FLAGS; }
        private static DataParameter<Byte> hands() { return HAND_STATES; }
    }
    private NativeLivingState() {}
}
