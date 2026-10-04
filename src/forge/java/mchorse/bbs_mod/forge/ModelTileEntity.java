package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/** Original BBS properties on Forge's tile lifecycle; NBT synchronization follows Blockbuster. */
public class ModelTileEntity extends TileEntity implements ITickable
{
    private final ModelProperties properties = new ModelProperties();
    private StubEntity entity;
    private float lastYaw = Float.NaN;
    private float currentYaw = Float.NaN;

    public ModelProperties getProperties() { return this.properties; }
    public StubEntity getEntity() { return this.getRenderEntity(); }
    public StubEntity getRenderEntity()
    {
        if (this.entity == null) this.entity = new StubEntity(this.world);
        this.entity.setWorld(this.world);
        this.entity.setForm(this.properties.getForm());
        this.entity.setPosition(this.pos.getX() + 0.5, this.pos.getY(), this.pos.getZ() + 0.5);
        this.entity.setPrevX(this.entity.getX());
        this.entity.setPrevY(this.entity.getY());
        this.entity.setPrevZ(this.entity.getZ());
        this.properties.getEquipment().apply(this.entity);
        return this.entity;
    }
    public String getName()
    {
        Form form = this.properties.getForm();
        return "(" + this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ() + ")"
            + (form == null ? "" : " " + form.getDisplayName());
    }
    public void setLookYaw(float yaw) { this.lastYaw = this.currentYaw = yaw; }
    public float updateLookYawContinuous(float yaw)
    {
        if (Float.isNaN(this.currentYaw)) this.setLookYaw(yaw);
        float diff = yaw - this.lastYaw;
        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);
        this.currentYaw += diff;
        this.lastYaw = yaw;
        return this.currentYaw;
    }
    public void resetLookYaw() { this.lastYaw = this.currentYaw = Float.NaN; }
    public void snapLookYawToBase(float last, float current) { this.lastYaw = last; this.currentYaw = current; }
    @Override public void update()
    {
        mchorse.bbs_mod.api.events.ModelBlockEntityUpdateCallback.EVENT.invoker().update(this);
        if (this.world != null && this.world.isRemote)
        {
            StubEntity target = this.getRenderEntity();
            target.update();
            this.properties.update(target);
        }
    }
    public AxisAlignedBB getShape() { return this.properties.getBody().buildShape(this.properties.getForm(), this.properties.getTransform()); }
    public void updateForm(MapType data, World world)
    {
        this.properties.fromData(data);
        this.resetLookYaw();
        this.markDirty();
        IBlockState state = world.getBlockState(this.pos);
        world.checkLight(this.pos);
        world.notifyBlockUpdate(this.pos, state, state, 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setTag("Properties", DataStorageUtils.toNbt(this.properties.toData()));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        try
        {
            BaseType data = DataStorageUtils.fromNbt(tag.getTag("Properties"));
            if (data instanceof MapType) this.properties.fromData((MapType) data);
            else if (tag.hasKey("BBSForm", 8))
                this.properties.setForm(FormUtils.fromData(DataToString.mapFromString(tag.getString("BBSForm"))));
        }
        catch (Exception error) { BBSMod.LOGGER.error("Invalid BBS model block at " + this.pos, error); }
        this.resetLookYaw();
    }
    @Override public NBTTagCompound getUpdateTag() { return this.writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(this.pos, 0, this.getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet)
    {
        this.readFromNBT(packet.getNbtCompound());
        if (this.world != null) this.world.checkLight(this.pos);
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }
    @Override public boolean canRenderBreaking() { return true; }
    @Override public double getMaxRenderDistanceSquared() { return this.properties.isGlobal() ? Double.MAX_VALUE : 65536; }
}
