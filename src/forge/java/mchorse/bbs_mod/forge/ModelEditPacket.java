package mchorse.bbs_mod.forge;
import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
public class ModelEditPacket implements IMessage {
    private BlockPos pos;
    private String data;
    private boolean place;
    public ModelEditPacket() { }
    public ModelEditPacket(BlockPos pos, ModelForm form) { this.place=pos==null; this.pos=pos==null?BlockPos.ORIGIN:pos; data=DataToString.toString(form.toData()); }
    public void fromBytes(ByteBuf buffer) { place=buffer.readBoolean(); pos=BlockPos.fromLong(buffer.readLong()); data=ByteBufUtils.readUTF8String(buffer); }
    public void toBytes(ByteBuf buffer) { buffer.writeBoolean(place); buffer.writeLong(pos.toLong()); ByteBufUtils.writeUTF8String(buffer,data); }
    public static class Handler implements IMessageHandler<ModelEditPacket,IMessage> {
        public IMessage onMessage(ModelEditPacket message, MessageContext context) {
            final EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.capabilities.isCreativeMode || message.data.length()>65536
                    || !mchorse.bbs_mod.utils.PermissionUtils.arePanelsAllowed(player.getServer(),player)) return;
                BlockPos pos=message.place?player.getPosition().offset(player.getHorizontalFacing(),3):message.pos;
                if(player.getDistanceSq(pos)>4096 || !player.world.isBlockLoaded(pos)) return;
                try {
                    ModelForm form=new ModelForm(); form.fromData(DataToString.mapFromString(message.data));
                    if(message.place) {
                        if(!player.world.isAirBlock(pos)) {player.sendMessage(new net.minecraft.util.text.TextComponentString("BBS: placement position is occupied"));return;}
                        player.world.setBlockState(pos,CommonProxy.MODEL_BLOCK.getDefaultState(),3);
                    }
                    if(!(player.world.getTileEntity(pos) instanceof ModelTileEntity)) return;
                    ModelTileEntity tile=(ModelTileEntity)player.world.getTileEntity(pos);
                    tile.getProperties().setForm(form); tile.markDirty();
                    player.world.notifyBlockUpdate(pos,tile.getBlockType().getDefaultState(),tile.getBlockType().getDefaultState(),3);
                } catch (Exception e) { mchorse.bbs_mod.BBSMod.LOGGER.warn("Invalid model edit",e); }
            });
            return null;
        }
    }
}
