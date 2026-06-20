package mchorse.bbs_mod.actions;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import org.jetbrains.annotations.Nullable;

public class SuperFakePlayerNetworkHandler extends ServerPlayNetworkHandler
{
    private static final ClientConnection FAKE_CONNECTION = new FakeClientConnection();

    public SuperFakePlayerNetworkHandler(ServerPlayerEntity player)
    {
        super(player.getEntityWorld().getServer(), FAKE_CONNECTION, player, ConnectedClientData.createDefault(player.getGameProfile(), false));
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener callbacks)
    {}

    private static final class FakeClientConnection extends ClientConnection
    {
        private FakeClientConnection()
        {
            super(NetworkSide.CLIENTBOUND);
        }

        public void setPacketListener(PacketListener packetListener)
        {}
    }
}