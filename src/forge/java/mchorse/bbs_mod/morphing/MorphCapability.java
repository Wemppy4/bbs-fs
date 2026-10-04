package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.forge.MorphPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Native persistence and player lifecycle; no mixin-based fake player interface. */
public class MorphCapability
{
    @CapabilityInject(IMorphProvider.class) public static Capability<IMorphProvider> CAPABILITY;
    public static void register()
    {
        CapabilityManager.INSTANCE.register(IMorphProvider.class, new Capability.IStorage<IMorphProvider>()
        {
            public NBTBase writeNBT(Capability<IMorphProvider> cap, IMorphProvider provider, EnumFacing side) { return provider.getMorph().toNbt(); }
            public void readNBT(Capability<IMorphProvider> cap, IMorphProvider provider, EnumFacing side, NBTBase nbt)
            {
                if (nbt instanceof NBTTagCompound) provider.getMorph().fromNbt((NBTTagCompound) nbt);
            }
        }, () -> new Provider(null));
    }
    public static class Provider implements IMorphProvider, ICapabilitySerializable<NBTTagCompound>
    {
        private final Morph morph;
        Provider(Entity entity) { this.morph = new Morph(entity); }
        public Morph getMorph() { return this.morph; }
        public boolean hasCapability(Capability<?> cap, EnumFacing side) { return cap == CAPABILITY; }
        public <T> T getCapability(Capability<T> cap, EnumFacing side) { return cap == CAPABILITY ? CAPABILITY.cast(this) : null; }
        public NBTTagCompound serializeNBT() { return this.morph.toNbt(); }
        public void deserializeNBT(NBTTagCompound nbt) { this.morph.fromNbt(nbt); }
    }
    @SubscribeEvent public void attach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof EntityPlayer) event.addCapability(new ResourceLocation("bbs", "morph"), new Provider(event.getObject()));
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event)
    {
        Morph morph = Morph.getMorph(event.player);
        if (morph == null) return;
        if (event.phase == TickEvent.Phase.START) morph.update(); else morph.updateDimensions();
    }
    @SubscribeEvent public void clonePlayer(net.minecraftforge.event.entity.player.PlayerEvent.Clone event)
    {
        Morph previous = Morph.getMorph(event.getOriginal()), next = Morph.getMorph(event.getEntityPlayer());
        if (previous != null && next != null) next.fromNbt(previous.toNbt());
    }
    @SubscribeEvent public void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event)
    {
        if (event.getTarget() instanceof EntityPlayer && event.getEntityPlayer() instanceof EntityPlayerMP)
            MorphPacket.sendTo((EntityPlayerMP) event.getEntityPlayer(), (EntityPlayer) event.getTarget());
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.player instanceof EntityPlayerMP) MorphPacket.synchronize((EntityPlayerMP) event.player);
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.player instanceof EntityPlayerMP) MorphPacket.synchronize((EntityPlayerMP) event.player);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.player instanceof EntityPlayerMP) MorphPacket.synchronize((EntityPlayerMP) event.player);
    }
}
