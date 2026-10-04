package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = BBSMod.MOD_ID)
public class CommonProxy
{
    public static final ModelBlock MODEL_BLOCK = new ModelBlock();
    public static final net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper NETWORK =
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.newSimpleChannel("bbs");
    public void preInit() {
        GameRegistry.registerTileEntity(ModelTileEntity.class, new ResourceLocation("bbs", "model"));
        NETWORK.registerMessage(ModelEditPacket.Handler.class,ModelEditPacket.class,0,net.minecraftforge.fml.relauncher.Side.SERVER);
        NETWORK.registerMessage(ActorFormPacket.Handler.class, ActorFormPacket.class, 1, net.minecraftforge.fml.relauncher.Side.CLIENT);
        NETWORK.registerMessage(MorphPacket.Handler.class, MorphPacket.class, 2, net.minecraftforge.fml.relauncher.Side.CLIENT);
        mchorse.bbs_mod.morphing.MorphCapability.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new mchorse.bbs_mod.morphing.MorphCapability());
        FilmServerEvents.register();
    }
    public void init() { }
    public void openModel(ModelTileEntity tile) { }
    @SubscribeEvent public static void entities(RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry> event) {
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
            .entity(mchorse.bbs_mod.entity.ActorEntity.class).id(new ResourceLocation("bbs", "actor"), 0)
            .name("bbs.actor").tracker(128, 3, true).build());
    }
    @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof mchorse.bbs_mod.entity.ActorEntity && event.getEntityPlayer() instanceof net.minecraft.entity.player.EntityPlayerMP)
            ActorFormPacket.sendTo((net.minecraft.entity.player.EntityPlayerMP) event.getEntityPlayer(), (mchorse.bbs_mod.entity.ActorEntity) event.getTarget());
    }
    @SubscribeEvent public static void sounds(RegistryEvent.Register<net.minecraft.util.SoundEvent> event) {
        event.getRegistry().register(BBSMod.CLICK);
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(MODEL_BLOCK); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(MODEL_BLOCK).setRegistryName(MODEL_BLOCK.getRegistryName()));
    }
}
