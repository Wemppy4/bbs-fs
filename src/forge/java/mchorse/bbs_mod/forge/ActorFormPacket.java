package mchorse.bbs_mod.forge;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Actor spawn and later form/identity changes share one authoritative payload. */
public class ActorFormPacket implements IMessage
{
    private int entityId;
    private NBTTagCompound data;
    public ActorFormPacket() {}
    public ActorFormPacket(ActorEntity actor) { this.entityId = actor.getEntityId(); this.data = snapshot(actor); }
    public static NBTTagCompound snapshot(ActorEntity actor)
    {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("Film", actor.getFilmId());
        data.setString("Replay", actor.getReplayId());
        if (actor.getForm() != null) data.setTag("Form", DataStorageUtils.toNbt(FormUtils.toData(actor.getForm())));
        return data;
    }
    public static void apply(ActorEntity actor, NBTTagCompound data)
    {
        if (data == null) return;
        actor.setReplay(data.getString("Film"), data.getString("Replay"));
        actor.setForm(data.hasKey("Form", 10) ? FormUtils.fromData((MapType) DataStorageUtils.fromNbt(data.getCompoundTag("Form"))) : null);
    }
    public static void sendToTracking(ActorEntity actor)
    {
        CommonProxy.NETWORK.sendToAllTracking(new ActorFormPacket(actor), actor);
    }
    public static void sendTo(EntityPlayerMP player, ActorEntity actor) { CommonProxy.NETWORK.sendTo(new ActorFormPacket(actor), player); }
    public void fromBytes(ByteBuf buffer) { this.entityId = buffer.readInt(); this.data = ByteBufUtils.readTag(buffer); }
    public void toBytes(ByteBuf buffer) { buffer.writeInt(this.entityId); ByteBufUtils.writeTag(buffer, this.data); }
    public static class Handler implements IMessageHandler<ActorFormPacket, IMessage>
    {
        public IMessage onMessage(ActorFormPacket packet, MessageContext context)
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                Entity entity = mc.world == null ? null : mc.world.getEntityByID(packet.entityId);
                if (entity instanceof ActorEntity) apply((ActorEntity) entity, packet.data);
            });
            return null;
        }
    }
}
