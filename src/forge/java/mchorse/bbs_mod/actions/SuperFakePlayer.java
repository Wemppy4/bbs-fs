package mchorse.bbs_mod.actions;

import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.inventory.IInventory;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** Native Forge actor for server-side film actions; no connection or GUI is opened. */
public class SuperFakePlayer extends FakePlayer
{
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("12345678-9abc-def1-2345-6789abcdef69"), "[BBS Player]");
    private static final Map<WorldServer, WeakReference<SuperFakePlayer>> PLAYERS = new WeakHashMap<>();

    public static SuperFakePlayer get(WorldServer world)
    {
        SuperFakePlayer player = getIfPresent(world);
        if (player == null) { player = new SuperFakePlayer(world); PLAYERS.put(world, new WeakReference<>(player)); }
        return player;
    }

    public static SuperFakePlayer getIfPresent(WorldServer world)
    {
        WeakReference<SuperFakePlayer> reference = PLAYERS.get(world);
        return reference == null ? null : reference.get();
    }

    private final java.util.Set<net.minecraft.util.math.BlockPos> openLids = new java.util.HashSet<>();
    private final java.util.Set<net.minecraft.util.math.BlockPos> wantedLids = new java.util.HashSet<>();
    public void wantLidOpen(net.minecraft.util.math.BlockPos pos)
    {
        if (ContainerLid.isLidded(this.world, pos)) this.wantedLids.add(pos.toImmutable());
    }
    public void flushLids()
    {
        for (net.minecraft.util.math.BlockPos pos : this.wantedLids)
            if (this.openLids.add(pos)) ContainerLid.setOpen(this.world, pos, true);
        java.util.Iterator<net.minecraft.util.math.BlockPos> iterator = this.openLids.iterator();
        while (iterator.hasNext())
        {
            net.minecraft.util.math.BlockPos pos = iterator.next();
            if (!this.wantedLids.contains(pos)) { ContainerLid.setOpen(this.world, pos, false); iterator.remove(); }
        }
        this.wantedLids.clear();
    }

    private SuperFakePlayer(WorldServer world)
    {
        super(world, PROFILE);
        this.connection = new NetHandlerPlayServer(world.getMinecraftServer(), new NetworkManager(EnumPacketDirection.CLIENTBOUND), this)
        {
            @Override public void sendPacket(Packet<?> packet) {}
        };
    }

    @Override public boolean canUseCommand(int level, String command) { return level <= 2; }
    @Override public boolean sendCommandFeedback() { return false; }
    @Override public Vec3d getPositionVector() { return new Vec3d(this.posX, this.posY, this.posZ); }
    @Override public void displayGUIChest(IInventory inventory) {}
    @Override public void displayGui(IInteractionObject object) {}
    @Override public void openEditSign(TileEntitySign sign) {}
    @Override public void openGuiHorseInventory(AbstractHorse horse, IInventory inventory) {}
}
