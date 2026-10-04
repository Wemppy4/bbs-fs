package mchorse.bbs_mod.network;

import io.netty.buffer.ByteBuf;
import mchorse.bbs_mod.forge.CommonProxy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Native transport for film payloads. Real films can exceed Forge's custom-payload
 * limit, so the serialized NBT is split and reassembled before dispatch on the game thread. */
public final class FilmNetwork
{
    private static final int CHUNK=24000, MAX_BYTES=32*1024*1024, MAX_PARTS=(MAX_BYTES+CHUNK-1)/CHUNK;
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    private static final Map<UUID,Map<Integer,Assembly>> SERVER=new HashMap<>();
    private static final Map<Integer,Assembly> CLIENT=new HashMap<>();
    public static void register()
    {
        CommonProxy.NETWORK.registerMessage(ServerHandler.class, ServerFragment.class,3,Side.SERVER);
        CommonProxy.NETWORK.registerMessage(ClientHandler.class, ClientFragment.class,4,Side.CLIENT);
    }
    public static void toServer(NBTTagCompound payload) { split(payload, p -> CommonProxy.NETWORK.sendToServer(new ServerFragment(p))); }
    public static void toClient(EntityPlayerMP player,NBTTagCompound payload) { split(payload,p -> CommonProxy.NETWORK.sendTo(new ClientFragment(p),player)); }
    public static void forget(EntityPlayerMP player){SERVER.remove(player.getUniqueID());}
    public static void resetServer(){SERVER.clear();}
    public static void resetClient(){CLIENT.clear();}
    private static void split(NBTTagCompound payload,Consumer<Fragment> send)
    {
        try
        {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            CompressedStreamTools.write(payload,new DataOutputStream(bytes));
            byte[] data=bytes.toByteArray();
            if(data.length>MAX_BYTES)throw new IllegalArgumentException("BBS film payload exceeds 32 MiB");
            int count=Math.max(1,(data.length+CHUNK-1)/CHUNK),id=SEQUENCE.incrementAndGet();
            for(int part=0;part<count;part++)send.accept(new Fragment(id,part,count,Arrays.copyOfRange(data,part*CHUNK,Math.min(data.length,(part+1)*CHUNK))));
        }
        catch(IOException e){throw new IllegalStateException("Cannot encode film packet",e);}
    }
    private static NBTTagCompound accept(Map<Integer,Assembly> pending,Fragment packet)
    {
        long now=System.currentTimeMillis();pending.values().removeIf(value -> now-value.created>60000L);
        if(!pending.containsKey(packet.id)&&pending.size()>=4)throw new IllegalArgumentException("Too many unfinished film payloads");
        Assembly assembly=pending.computeIfAbsent(packet.id,id -> new Assembly(packet.count,now));
        if(assembly.parts.length!=packet.count)throw new IllegalArgumentException("Inconsistent film fragment count");
        if(assembly.parts[packet.part]!=null)throw new IllegalArgumentException("Repeated film fragment");
        assembly.parts[packet.part]=packet.data;assembly.received++;assembly.bytes+=packet.data.length;
        if(assembly.bytes>MAX_BYTES){pending.remove(packet.id);throw new IllegalArgumentException("Film packet too large");}
        if(assembly.received!=packet.count)return null;
        pending.remove(packet.id);
        try
        {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(assembly.bytes);
            for(byte[] part:assembly.parts)bytes.write(part);
            return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),new NBTSizeTracker(MAX_BYTES));
        }
        catch(IOException e){throw new IllegalArgumentException("Malformed film packet",e);}
    }
    private static final class Assembly
    {
        final byte[][] parts;final long created;int received,bytes;
        Assembly(int count,long created){this.parts=new byte[count][];this.created=created;}
    }
    public static class Fragment implements IMessage
    {
        int id,part,count;byte[] data;
        public Fragment(){}
        Fragment(int id,int part,int count,byte[] data){this.id=id;this.part=part;this.count=count;this.data=data;}
        public void fromBytes(ByteBuf buffer)
        {
            id=buffer.readInt();part=buffer.readInt();count=buffer.readInt();int length=buffer.readInt();
            if(count<1||count>MAX_PARTS||part<0||part>=count||length<0||length>CHUNK||length>buffer.readableBytes())throw new IllegalArgumentException("Invalid film fragment");
            data=new byte[length];buffer.readBytes(data);
        }
        public void toBytes(ByteBuf buffer){buffer.writeInt(id);buffer.writeInt(part);buffer.writeInt(count);buffer.writeInt(data.length);buffer.writeBytes(data);}
    }
    public static final class ServerFragment extends Fragment
    { public ServerFragment(){} ServerFragment(Fragment p){super(p.id,p.part,p.count,p.data);} }
    public static final class ClientFragment extends Fragment
    { public ClientFragment(){} ClientFragment(Fragment p){super(p.id,p.part,p.count,p.data);} }
    public static final class ServerHandler implements IMessageHandler<ServerFragment,IMessage>
    {
        public IMessage onMessage(ServerFragment packet,MessageContext context)
        {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() ->
            {
                try
                {
                    NBTTagCompound data=accept(SERVER.computeIfAbsent(player.getUniqueID(),id->new HashMap<>()),packet);
                    if(data!=null)ServerNetwork.receive(player,data);
                }
                catch(RuntimeException error){mchorse.bbs_mod.BBSMod.LOGGER.warn("Rejected film packet from {}",player.getName(),error);}
            });return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<ClientFragment,IMessage>
    {
        public IMessage onMessage(ClientFragment packet,MessageContext context)
        {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
            {
                try { NBTTagCompound data=accept(CLIENT,packet);if(data!=null)ClientNetwork.receive(data); }
                catch(RuntimeException error){mchorse.bbs_mod.BBSMod.LOGGER.error("Invalid film response",error);}
            });return null;
        }
    }
    private FilmNetwork(){}
}
