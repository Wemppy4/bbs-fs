package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.blocks.*;
import mchorse.bbs_mod.actions.types.item.*;
import mchorse.bbs_mod.actions.types.chat.*;
import mchorse.bbs_mod.camera.clips.ClipCategories;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.network.FilmNetwork;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.PermissionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server lifecycle and native action capture. The transformer covers arbitrary block
 * mutation; Forge events cover player operations and entities admitted to the world. */
public final class FilmServerEvents
{
    private static boolean registered;
    public static void register()
    {
        if(registered)return;registered=true;FilmNetwork.register();MinecraftForge.EVENT_BUS.register(new FilmServerEvents());
        BBSMod.getFactoryActionClips()
            .register(Link.bbs("place_block"),PlaceBlockActionClip.class,new ClipFactoryData(Icons.BLOCK,Colors.INACTIVE).in(ClipCategories.BLOCKS))
            .register(Link.bbs("interact_block"),InteractBlockActionClip.class,new ClipFactoryData(Icons.FULLSCREEN,Colors.MAGENTA).in(ClipCategories.BLOCKS))
            .register(Link.bbs("break_block"),BreakBlockActionClip.class,new ClipFactoryData(Icons.BULLET,Colors.GREEN).in(ClipCategories.BLOCKS))
            .register(Link.bbs("use_item"),UseItemActionClip.class,new ClipFactoryData(Icons.POINTER,Colors.BLUE).in(ClipCategories.ITEMS))
            .register(Link.bbs("use_block_item"),UseBlockItemActionClip.class,new ClipFactoryData(Icons.BUCKET,Colors.CYAN).in(ClipCategories.ITEMS))
            .register(Link.bbs("release_use_item"),ReleaseUseItemActionClip.class,new ClipFactoryData(Icons.ARROW_UP,0x9457ff).in(ClipCategories.ITEMS))
            .register(Link.bbs("drop_item"),ItemDropActionClip.class,new ClipFactoryData(Icons.ARROW_DOWN,Colors.DEEP_PINK).in(ClipCategories.ITEMS))
            .register(Link.bbs("attack"),AttackActionClip.class,new ClipFactoryData(Icons.DROP,Colors.RED).in(ClipCategories.GENERAL));
    }
    public static void serverStarting(MinecraftServer server){PermissionUtils.register(server);}
    public static void serverStopping(){BBSMod.getActions().reset();FilmNetwork.resetServer();}
    private static EntityPlayerMP real(Entity player){return player instanceof EntityPlayerMP&&!(player instanceof SuperFakePlayer)?(EntityPlayerMP)player:null;}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)BBSMod.getActions().tick();}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event){EntityPlayerMP player=real(event.player);if(player!=null)ServerNetwork.sendHandshake(player);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){EntityPlayerMP player=real(event.player);if(player!=null){BBSMod.getActions().stopFor(player);FilmNetwork.forget(player);}}
    @SubscribeEvent public void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event)
    {
        EntityPlayerMP player=real(event.getEntityPlayer());
        if(player!=null&&event.getTarget() instanceof ActorEntity)
        {ActorEntity actor=(ActorEntity)event.getTarget();ServerNetwork.sendActor(player,actor.getFilmId(),actor.getReplayId(),actor.getEntityId());}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void joined(EntityJoinWorldEvent event)
    {if(event.getWorld() instanceof WorldServer && !(event.getEntity() instanceof EntityPlayer))BBSMod.getActions().spawnedEntity(event.getEntity());}
    @SubscribeEvent public void unload(WorldEvent.Unload event)
    {if(event.getWorld() instanceof WorldServer)BBSMod.getActions().resetDamage((WorldServer)event.getWorld());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void use(PlayerInteractEvent.RightClickItem event)
    {
        EntityPlayerMP player=real(event.getEntityPlayer());if(player==null)return;
        BBSMod.getActions().addAction(player,()->{UseItemActionClip clip=new UseItemActionClip();clip.itemStack.set(player.getHeldItem(event.getHand()).copy());clip.hand.set(event.getHand()==EnumHand.MAIN_HAND);return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent.RightClickBlock event)
    {
        EntityPlayerMP player=real(event.getEntityPlayer());if(player==null)return;
        BBSMod.getActions().addAction(player,()->{InteractBlockActionClip clip=new InteractBlockActionClip();clip.hit.setHitResult(new RayTraceResult(event.getHitVec(),event.getFace(),event.getPos()));clip.hand.set(event.getHand()==EnumHand.MAIN_HAND);return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void placed(BlockEvent.PlaceEvent event)
    {
        EntityPlayerMP player=real(event.getPlayer());if(player==null)return;
        BBSMod.getActions().addAction(player,()->{PlaceBlockActionClip clip=new PlaceBlockActionClip();clip.x.set(event.getPos().getX());clip.y.set(event.getPos().getY());clip.z.set(event.getPos().getZ());clip.state.set(event.getPlacedBlock());return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void broken(BlockEvent.BreakEvent event)
    {
        EntityPlayerMP player=real(event.getPlayer());if(player==null)return;
        BBSMod.getActions().addAction(player,()->{PlaceBlockActionClip clip=new PlaceBlockActionClip();clip.x.set(event.getPos().getX());clip.y.set(event.getPos().getY());clip.z.set(event.getPos().getZ());clip.drop.set(!player.capabilities.isCreativeMode);return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent event)
    {
        EntityPlayerMP player=real(event.getSource().getTrueSource());
        if(player==null||event.getSource().getImmediateSource()!=player)return;
        BBSMod.getActions().addAction(player,()->{AttackActionClip clip=new AttackActionClip();clip.damage.set(event.getAmount());return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void toss(ItemTossEvent event)
    {
        EntityPlayerMP player=real(event.getPlayer());if(player==null)return;EntityItem item=event.getEntityItem();
        BBSMod.getActions().addAction(player,()->{ItemDropActionClip clip=new ItemDropActionClip();clip.posX.set(item.posX);clip.posY.set(item.posY);clip.posZ.set(item.posZ);clip.velocityX.set((float)item.motionX);clip.velocityY.set((float)item.motionY);clip.velocityZ.set((float)item.motionZ);clip.itemStack.set(item.getItem().copy());return clip;});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void release(LivingEntityUseItemEvent.Stop event)
    {
        EntityPlayerMP player=real(event.getEntityLiving());if(player==null)return;
        BBSMod.getActions().addAction(player,()->
        {
            ReleaseUseItemActionClip clip=new ReleaseUseItemActionClip();ItemStack stack=event.getItem();
            clip.itemStack.set(stack.copy());clip.hand.set(player.getActiveHand()==EnumHand.MAIN_HAND);clip.charge.set(stack.getMaxItemUseDuration()-event.getDuration());
            if(stack.getItem() instanceof ItemBow)
            {
                ItemStack ammo=ItemStack.EMPTY;
                if(player.getHeldItemOffhand().getItem() instanceof ItemArrow)ammo=player.getHeldItemOffhand();
                else if(player.getHeldItemMainhand().getItem() instanceof ItemArrow)ammo=player.getHeldItemMainhand();
                else for(int i=0;i<player.inventory.getSizeInventory();i++)if(player.inventory.getStackInSlot(i).getItem() instanceof ItemArrow){ammo=player.inventory.getStackInSlot(i);break;}
                if(ammo.isEmpty()&&player.capabilities.isCreativeMode)ammo=new ItemStack(Items.ARROW);
                clip.projectile.set(ammo.copy());
            }
            return clip;
        });
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void chat(ServerChatEvent event)
    {BBSMod.getActions().addAction(event.getPlayer(),()->{ChatActionClip clip=new ChatActionClip();clip.message.set(event.getMessage());return clip;});}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void command(CommandEvent event)
    {
        EntityPlayerMP player=real(event.getSender().getCommandSenderEntity());if(player==null)return;
        BBSMod.getActions().addAction(player,()->{CommandActionClip clip=new CommandActionClip();clip.command.set(event.getCommand().getName()+" "+String.join(" ",event.getParameters()));return clip;});
    }
}
