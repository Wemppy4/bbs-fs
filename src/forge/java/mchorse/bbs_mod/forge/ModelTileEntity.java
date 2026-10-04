package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.data.DataToString;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

/** Persistence and vanilla update packets follow bb-sources/TileEntityModel. */
public class ModelTileEntity extends TileEntity implements net.minecraft.util.ITickable
{
    public ModelForm form = new ModelForm();
    private mchorse.bbs_mod.forms.entities.StubEntity renderEntity;
    public mchorse.bbs_mod.forms.entities.StubEntity getRenderEntity() {
        if(renderEntity==null)renderEntity=new mchorse.bbs_mod.forms.entities.StubEntity(world);
        renderEntity.setWorld(world);renderEntity.setForm(form);
        renderEntity.setPosition(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        renderEntity.setPrevX(renderEntity.getX());renderEntity.setPrevY(renderEntity.getY());renderEntity.setPrevZ(renderEntity.getZ());
        return renderEntity;
    }
    @Override public void update() {
        if(world!=null && world.isRemote) {
            mchorse.bbs_mod.forms.entities.StubEntity entity=getRenderEntity();
            entity.setAge((int)world.getTotalWorldTime());
            form.update(entity);
        }
    }
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("BBSForm", DataToString.toString(form.toData()));
        return tag;
    }
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("BBSForm")) {
            try { ModelForm loaded = new ModelForm(); loaded.fromData(DataToString.mapFromString(tag.getString("BBSForm"))); form = loaded; }
            catch (Exception e) { BBSMod.LOGGER.error("Invalid BBS model block at " + pos, e); }
        }
    }
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
    public AxisAlignedBB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }
    public double getMaxRenderDistanceSquared() { return 65536; }
}
