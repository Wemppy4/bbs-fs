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
    public static final net.minecraft.creativetab.CreativeTabs BBS_TAB = new net.minecraft.creativetab.CreativeTabs("bbs.main") {
        @Override public ItemStack createIcon() { return new ItemStack(MODEL_BLOCK); }
    };
    public static final ModelBlock MODEL_BLOCK = new ModelBlock();
    public static final mchorse.bbs_mod.items.GunItem GUN_ITEM = new mchorse.bbs_mod.items.GunItem();
    public static final Item STRUCTURE_WAND = new Item().setRegistryName("bbs", "structure_wand")
        .setTranslationKey("bbs.structure_wand").setMaxStackSize(1).setCreativeTab(net.minecraft.creativetab.CreativeTabs.TOOLS);
    public static final Block[] CHROMA_BLOCKS = { new ChromaBlock("red"),new ChromaBlock("green"),new ChromaBlock("blue"),new ChromaBlock("cyan"),new ChromaBlock("magenta"),new ChromaBlock("yellow"),new ChromaBlock("black"),new ChromaBlock("white") };
    static { MODEL_BLOCK.setCreativeTab(BBS_TAB);GUN_ITEM.setCreativeTab(BBS_TAB);STRUCTURE_WAND.setCreativeTab(BBS_TAB); }
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
        GunNetwork.register();
    }
    public void init() { }
    public void openModel(ModelTileEntity tile) { }
    @SubscribeEvent public static void entities(RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry> event) {
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
            .entity(mchorse.bbs_mod.entity.ActorEntity.class).id(new ResourceLocation("bbs", "actor"), 0)
            .name("bbs.actor").tracker(128, 3, true).build());
        event.getRegistry().register(net.minecraftforge.fml.common.registry.EntityEntryBuilder.create()
            .entity(mchorse.bbs_mod.entity.GunProjectileEntity.class).id(new ResourceLocation("bbs", "gun_projectile"), 1)
            .name("bbs.gun_projectile").tracker(128, 1, true).build());
    }
    @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof mchorse.bbs_mod.entity.ActorEntity && event.getEntityPlayer() instanceof net.minecraft.entity.player.EntityPlayerMP)
            ActorFormPacket.sendTo((net.minecraft.entity.player.EntityPlayerMP) event.getEntityPlayer(), (mchorse.bbs_mod.entity.ActorEntity) event.getTarget());
    }
    @SubscribeEvent public static void sounds(RegistryEvent.Register<net.minecraft.util.SoundEvent> event) {
        event.getRegistry().register(BBSMod.CLICK);
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(MODEL_BLOCK);event.getRegistry().registerAll(CHROMA_BLOCKS); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(MODEL_BLOCK).setRegistryName(MODEL_BLOCK.getRegistryName()));
        event.getRegistry().register(STRUCTURE_WAND);
        event.getRegistry().register(GUN_ITEM);
        for(Block block:CHROMA_BLOCKS)event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
    }
}
