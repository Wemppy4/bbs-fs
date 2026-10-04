package mchorse.bbs_mod.forge;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class MorphPacket implements IMessage
{
    private int entityId;
    private NBTTagCompound data;
    public MorphPacket() {}
    public MorphPacket(EntityPlayer player)
    {
        this.entityId = player.getEntityId();
        Morph morph = Morph.getMorph(player);
        this.data = morph == null ? new NBTTagCompound() : morph.toNbt();
    }
    public static void sendTo(EntityPlayerMP recipient, EntityPlayer player) { CommonProxy.NETWORK.sendTo(new MorphPacket(player), recipient); }
    public static void synchronize(EntityPlayerMP player)
    {
        MorphPacket packet = new MorphPacket(player);
        CommonProxy.NETWORK.sendTo(packet, player);
        CommonProxy.NETWORK.sendToAllTracking(packet, player);
    }
    public void fromBytes(ByteBuf buffer) { this.entityId = buffer.readInt(); this.data = ByteBufUtils.readTag(buffer); }
    public void toBytes(ByteBuf buffer) { buffer.writeInt(this.entityId); ByteBufUtils.writeTag(buffer, this.data); }
    public static class Handler implements IMessageHandler<MorphPacket, IMessage>
    {
        public IMessage onMessage(MorphPacket packet, MessageContext context)
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                Entity player = mc.world == null ? null : mc.world.getEntityByID(packet.entityId);
                Morph morph = Morph.getMorph(player);
                if (morph != null && packet.data != null)
                {
                    Morph incoming = new Morph(null);
                    incoming.fromNbt(packet.data);
                    morph.setForm(incoming.getForm());
                }
            });
            return null;
        }
    }
}
