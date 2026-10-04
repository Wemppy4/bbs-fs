package mchorse.bbs_mod.forge;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.items.GunProperties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Initial state travels with Forge spawn data; impacts update all players tracking the entity. */
public final class GunNetwork {
    private GunNetwork() {}
    public static void register() {
        CommonProxy.NETWORK.registerMessage(StateHandler.class,State.class,5,Side.CLIENT);
        CommonProxy.NETWORK.registerMessage(ZoomHandler.class,Zoom.class,6,Side.SERVER);
    }
    public static void sendState(GunProjectileEntity projectile) { CommonProxy.NETWORK.sendToAllTracking(new State(projectile),projectile); }
    public static void sendZoom(boolean zoom) { CommonProxy.NETWORK.sendToServer(new Zoom(zoom)); }
    public static final class State implements IMessage {
        private int entity;private NBTTagCompound data;
        public State() {}
        State(GunProjectileEntity projectile) { this.entity=projectile.getEntityId();this.data=projectile.snapshot(); }
        @Override public void fromBytes(ByteBuf buf) { this.entity=buf.readInt();this.data=ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(this.entity);ByteBufUtils.writeTag(buf,this.data); }
    }
    public static final class StateHandler implements IMessageHandler<State,IMessage> {
        @Override public IMessage onMessage(State message,MessageContext context) {
            net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(()->{
                Entity entity=mc.world==null?null:mc.world.getEntityByID(message.entity);
                if(entity instanceof GunProjectileEntity&&message.data!=null)((GunProjectileEntity)entity).applySnapshot(message.data);
            });return null;
        }
    }
    public static final class Zoom implements IMessage {
        private boolean zoom;
        public Zoom() {}
        Zoom(boolean value) { this.zoom=value; }
        @Override public void fromBytes(ByteBuf buf) { this.zoom=buf.readBoolean(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(this.zoom); }
    }
    public static final class ZoomHandler implements IMessageHandler<Zoom,IMessage> {
        @Override public IMessage onMessage(Zoom message,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(player.getHeldItemMainhand().getItem()!=CommonProxy.GUN_ITEM)return;
                GunProperties properties=GunProperties.get(player.getHeldItemMainhand());
                String command=message.zoom?properties.cmdZoomOn:properties.cmdZoomOff;
                if(!command.isEmpty())player.getServer().getCommandManager().executeCommand(player,command);
            });return null;
        }
    }
}