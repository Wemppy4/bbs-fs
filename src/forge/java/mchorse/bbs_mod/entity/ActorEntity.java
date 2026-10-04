package mchorse.bbs_mod.entity;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.forge.ActorFormPacket;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketCollectItem;
import net.minecraft.util.EnumHandSide;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A film's real server-side body, including equipment, hitbox and reversible item pickup. */
public class ActorEntity extends EntityLivingBase implements IEntityFormProvider, IEntityAdditionalSpawnData
{
    private boolean despawn;
    private final MCEntity entity = new MCEntity(this);
    private Form form;
    private String filmId = "", replayId = "";
    private boolean pickUpItems = true;
    private final List<ItemStack> pickedUp = new ArrayList<>();
    private final Map<EntityEquipmentSlot, ItemStack> equipment = new EnumMap<>(EntityEquipmentSlot.class);

    public ActorEntity(World world)
    {
        super(world);
        this.setSize(0.6F, 1.8F);
    }

    @Override protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.LUCK);
    }

    public void setReplay(String filmId, String replayId)
    {
        this.filmId = filmId == null ? "" : filmId;
        this.replayId = replayId == null ? "" : replayId;
        if (!this.world.isRemote) ActorFormPacket.sendToTracking(this);
    }
    public String getFilmId() { return this.filmId; }
    public String getReplayId() { return this.replayId; }
    public MCEntity getEntity() { return this.entity; }
    public Form getForm() { return this.form; }

    public void setForm(Form form)
    {
        Form last = this.form;
        this.form = form;
        if (!this.world.isRemote)
        {
            if (last != null) last.onDemorph(this);
            if (form != null) form.onMorph(this);
        }
        updateDimensions();
        if (!this.world.isRemote) ActorFormPacket.sendToTracking(this);
    }

    private void updateDimensions()
    {
        float width = 0.6F, height = 1.8F;
        if (this.form != null && this.form.hitbox.get())
        {
            width = this.form.hitboxWidth.get();
            height = this.form.hitboxHeight.get() * (isSneaking() ? this.form.hitboxSneakMultiplier.get() : 1F);
        }
        if (this.width != width || this.height != height) this.setSize(width, height);
    }
    @Override public float getEyeHeight()
    {
        return this.form != null && this.form.hitbox.get() ? this.form.hitboxEyeHeight.get() : super.getEyeHeight();
    }
    @Override public boolean isInRangeToRenderDist(double distance)
    {
        double size = this.getEntityBoundingBox().getAverageEdgeLength();
        if (Double.isNaN(size)) size = 1D;
        return distance < size * size * 256D * 256D;
    }
    @Override public Iterable<ItemStack> getHeldEquipment()
    {
        return Arrays.asList(getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), getItemStackFromSlot(EntityEquipmentSlot.OFFHAND));
    }
    @Override public Iterable<ItemStack> getArmorInventoryList()
    {
        return Arrays.asList(getItemStackFromSlot(EntityEquipmentSlot.FEET), getItemStackFromSlot(EntityEquipmentSlot.LEGS),
            getItemStackFromSlot(EntityEquipmentSlot.CHEST), getItemStackFromSlot(EntityEquipmentSlot.HEAD));
    }
    @Override public ItemStack getItemStackFromSlot(EntityEquipmentSlot slot) { return this.equipment.getOrDefault(slot, ItemStack.EMPTY); }
    @Override public void setItemStackToSlot(EntityEquipmentSlot slot, ItemStack stack) { this.equipment.put(slot, stack == null ? ItemStack.EMPTY : stack); }
    @Override public EnumHandSide getPrimaryHand() { return EnumHandSide.RIGHT; }

    @Override public void onUpdate()
    {
        if (this.despawn) { this.setDead(); return; }
        super.onUpdate();
        this.updateArmSwingProgress();
        this.updateDimensions();
        if (this.form != null) this.form.update(this.entity);
        if (this.world.isRemote || !this.pickUpItems) return;
        for (Entity candidate : this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().grow(1D, 0.5D, 1D)))
        {
            if (!(candidate instanceof EntityItem)) continue;
            EntityItem item = (EntityItem) candidate;
            if (item.isDead || item.cannotPickup()) continue;
            ItemStack stack = item.getItem();
            ((WorldServer) this.world).getEntityTracker().sendToTracking(item, new SPacketCollectItem(item.getEntityId(), getEntityId(), stack.getCount()));
            this.pickedUp.add(stack.copy());
            item.setDead();
        }
    }
    public void setPickUpItems(boolean value) { this.pickUpItems = value; }
    public void dropPickedUp()
    {
        if (this.world.isRemote) return;
        for (ItemStack stack : this.pickedUp)
        {
            EntityItem item = new EntityItem(this.world, this.posX, this.posY + 0.5D, this.posZ, stack);
            item.setDefaultPickupDelay();
            this.world.spawnEntity(item);
        }
        this.pickedUp.clear();
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt)
    {
        super.readEntityFromNBT(nbt);
        this.despawn = nbt.getBoolean("despawn");
    }
    @Override public void writeEntityToNBT(NBTTagCompound nbt)
    {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("despawn", true);
    }
    @Override public boolean canUseCommand(int level, String command) { return level <= 4; }
    @Override public void writeSpawnData(ByteBuf buffer) { ByteBufUtils.writeTag(buffer, ActorFormPacket.snapshot(this)); }
    @Override public void readSpawnData(ByteBuf buffer) { ActorFormPacket.apply(this, ByteBufUtils.readTag(buffer)); }
}
