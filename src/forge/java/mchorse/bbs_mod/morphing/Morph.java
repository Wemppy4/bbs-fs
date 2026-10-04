package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.Arrays;

/** Original morph state and lifecycle attached to players by a Forge capability. */
public class Morph
{
    private Form form;
    public final MCEntity entity;
    private boolean resized;

    /** A null target is a detached capability default used only for data serialization. */
    public Morph(Entity target) { this.entity = target == null ? null : new MCEntity(target); }
    public static Morph getMorph(Entity entity)
    {
        if (entity instanceof IMorphProvider) return ((IMorphProvider) entity).getMorph();
        if (entity == null || MorphCapability.CAPABILITY == null) return null;
        IMorphProvider provider = entity.getCapability(MorphCapability.CAPABILITY, null);
        return provider == null ? null : provider.getMorph();
    }
    public static Form getMobForm(EntityPlayer player)
    {
        Vec3d start = player.getPositionEyes(1F), end = start.add(player.getLook(1F).scale(64D));
        RayTraceResult block = player.world.rayTraceBlocks(start, end, false, false, true);
        double distance = block != null && block.typeOfHit == RayTraceResult.Type.BLOCK ? start.distanceTo(block.hitVec) : 64D;
        Entity nearest = null;
        for (Entity candidate : player.world.getEntitiesWithinAABBExcludingEntity(player,
            player.getEntityBoundingBox().expand(end.x - start.x, end.y - start.y, end.z - start.z).grow(1D)))
        {
            if (!candidate.canBeCollidedWith()) continue;
            AxisAlignedBB bounds = candidate.getEntityBoundingBox().grow(candidate.getCollisionBorderSize());
            RayTraceResult hit = bounds.calculateIntercept(start, end);
            double at = bounds.contains(start) ? 0D : hit == null ? Double.POSITIVE_INFINITY : start.distanceTo(hit.hitVec);
            if (at <= distance) { distance = at; nearest = candidate; }
        }
        return nearest == null ? null : createMobForm(nearest);
    }
    public static MobForm createMobForm(Entity target)
    {
        ResourceLocation key = EntityList.getKey(target);
        if (key == null) return null;
        NBTTagCompound nbt = target.writeToNBT(new NBTTagCompound());
        for (String transientKey : Arrays.asList("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
            "Invulnerable", "PortalCooldown", "UUID", "UUIDMost", "UUIDLeast")) nbt.removeTag(transientKey);
        MobForm form = new MobForm();
        form.mobID.set(key.toString()); form.mobNBT.set(nbt.toString());
        return form;
    }
    public Form getForm() { return this.form; }
    public void setForm(Form form)
    {
        Entity target = this.entity == null ? null : this.entity.getMcEntity();
        if (form == null && this.form != null && target instanceof EntityPlayer) this.form.onDemorph((EntityPlayer) target);
        this.form = form;
        if (form != null && target instanceof EntityPlayer)
        {
            form.onMorph((EntityPlayer) target);
            form.playMain();
        }
        updateDimensions();
        if (target instanceof net.minecraft.entity.player.EntityPlayerMP) mchorse.bbs_mod.forge.MorphPacket.synchronize((net.minecraft.entity.player.EntityPlayerMP) target);
    }
    public void update()
    {
        if (this.entity == null) return;
        this.entity.update();
        if (this.form != null) this.form.update(this.entity);
    }
    /** Forge PlayerTick.END follows vanilla updateSize, so the saved BBS dimensions take effect for the next move. */
    public void updateDimensions()
    {
        if (this.entity == null || !(this.entity.getMcEntity() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) this.entity.getMcEntity();
        if (this.form != null && this.form.hitbox.get())
        {
            float height = this.form.hitboxHeight.get() * (player.isSneaking() ? this.form.hitboxSneakMultiplier.get() : 1F);
            setDimensions(player, this.form.hitboxWidth.get(), height);
            player.eyeHeight = this.form.hitboxEyeHeight.get() * height + (player.isSneaking() ? 0.08F : 0F);
            this.resized = true;
        }
        else if (this.resized)
        {
            float height = player.isPlayerSleeping() ? 0.2F : player.isElytraFlying() ? 0.6F : player.isSneaking() ? 1.65F : 1.8F;
            setDimensions(player, player.isPlayerSleeping() ? 0.2F : 0.6F, height);
            player.eyeHeight = player.getDefaultEyeHeight();
            this.resized = false;
        }
    }
    private static void setDimensions(EntityPlayer player, float width, float height)
    {
        if (player.width == width && player.height == height) return;
        AxisAlignedBB box = player.getEntityBoundingBox();
        player.width = width; player.height = height;
        player.setEntityBoundingBox(new AxisAlignedBB(player.posX - width / 2D, box.minY, player.posZ - width / 2D,
            player.posX + width / 2D, box.minY + height, player.posZ + width / 2D));
    }
    public NBTTagCompound toNbt()
    {
        NBTTagCompound data = new NBTTagCompound();
        if (this.form != null) data.setTag("Form", DataStorageUtils.toNbt(FormUtils.toData(this.form)));
        return data;
    }
    public void fromNbt(NBTTagCompound data)
    {
        this.form = data.hasKey("Form", 10) ? FormUtils.fromData((MapType) DataStorageUtils.fromNbt(data.getCompoundTag("Form"))) : null;
    }
}
