package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.*;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forge.MorphPacket;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.PermissionUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Authoritative film controls. All receive calls run on the actual server thread. */
public final class ServerNetwork
{
    private static NBTTagCompound packet(String op,String id){NBTTagCompound p=new NBTTagCompound();p.setString("op",op);p.setString("film",id);return p;}
    public static void sendHandshake(EntityPlayerMP player){FilmNetwork.toClient(player,packet("hello",""));}
    public static void sendMorphToTracked(EntityPlayerMP player,Form form)
    {Morph morph=Morph.getMorph(player);if(morph!=null){morph.setForm(FormUtils.copy(form));MorphPacket.synchronize(player);}}
    public static void sendSelectedSlot(EntityPlayerMP player,int slot)
    {player.inventory.currentItem=Math.max(0,Math.min(8,slot));player.connection.sendPacket(new SPacketHeldItemChange(player.inventory.currentItem));}
    public static void sendActors(EntityPlayerMP player,String id,Map<String,? extends EntityLivingBase> actors)
    {
        NBTTagCompound p=packet("actors",id),values=new NBTTagCompound();
        for(Map.Entry<String,? extends EntityLivingBase> entry:actors.entrySet())values.setInteger(entry.getKey(),entry.getValue().getEntityId());
        p.setTag("actors",values);FilmNetwork.toClient(player,p);
    }
    public static void sendActor(EntityPlayerMP player,String id,String replay,int entity)
    {NBTTagCompound p=packet("actors",id),values=new NBTTagCompound();values.setInteger(replay,entity);p.setTag("actors",values);p.setBoolean("merge",true);FilmNetwork.toClient(player,p);}
    public static void sendStopFilm(EntityPlayerMP player,String id){FilmNetwork.toClient(player,packet("stop",id));}
    public static void sendPauseFilm(EntityPlayerMP player,String id){FilmNetwork.toClient(player,packet("pause",id));}
    public static void requestFilmResync(EntityPlayerMP player,String id){FilmNetwork.toClient(player,packet("resync",id));}
    public static void sendRecordedActions(EntityPlayerMP player,String id,int replay,int tick,Clips clips)
    {NBTTagCompound p=packet("recorded",id);p.setInteger("replay",replay);p.setInteger("tick",tick);p.setTag("data",DataStorageUtils.toNbt(clips.toData()));FilmNetwork.toClient(player,p);}
    public static void sendPlayFilm(EntityPlayerMP player,WorldServer world,String id,boolean camera)
    {
        Film film=BBSMod.getFilms().load(id);if(film==null)return;
        BBSMod.getActions().play(player,world,film,0);
        NBTTagCompound p=packet("play",id);p.setBoolean("camera",camera);p.setTag("data",DataStorageUtils.toNbt(film.toData()));
        for(EntityPlayer watcher:world.playerEntities)if(watcher instanceof EntityPlayerMP)FilmNetwork.toClient((EntityPlayerMP)watcher,p);
    }
    public static void sendPlayFilm(EntityPlayerMP player,String id,boolean camera)
    {
        Film film=BBSMod.getFilms().load(id);if(film==null)return;
        BBSMod.getActions().play(player,player.getServerWorld(),film,0);
        NBTTagCompound p=packet("play",id);p.setBoolean("camera",camera);p.setTag("data",DataStorageUtils.toNbt(film.toData()));
        FilmNetwork.toClient(player,p);
    }
    public static void sendModelBlockState(EntityPlayerMP player,net.minecraft.util.math.BlockPos pos,String state)
    {
        NBTTagCompound p=packet("model_state","");p.setLong("pos",pos.toLong());p.setString("state",state);FilmNetwork.toClient(player,p);
    }
    public static void sendReloadModelBlocks(EntityPlayerMP player,int range)
    {
        NBTTagCompound p=packet("model_refresh","");p.setInteger("range",range);FilmNetwork.toClient(player,p);
    }
    public static void sendCheatsPermission(EntityPlayerMP player,boolean enabled)
    {
        /* Native vanilla uses entity status 24..28 for client command permission levels. */
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityStatus(player,(byte)(enabled?28:24)));
    }
    private static void validateId(String id)
    {
        if(id.isEmpty()||id.length()>1024||id.indexOf('\\')>=0||id.indexOf(':')>=0||id.startsWith("/")||Arrays.asList(id.split("/")).contains(".."))
            throw new IllegalArgumentException("Invalid film path");
    }
    public static void receive(EntityPlayerMP player,NBTTagCompound p)
    {
        if (p.getString("op").equals("share"))
        {
            if (!p.hasUniqueId("player") || !p.hasKey("data", 10)) return;
            EntityPlayerMP recipient = player.getServer().getPlayerList().getPlayerByUUID(p.getUniqueId("player"));
            if (recipient != null)
            {
                NBTTagCompound response = packet("shared", "");
                response.setTag("data", p.getCompoundTag("data").copy());
                FilmNetwork.toClient(recipient, response);
            }
            return;
        }
        if(!PermissionUtils.arePanelsAllowed(player.getServer(),player))return;
        String op=p.getString("op"),id=p.getString("film");
        if (op.equals("model_item"))
        {
            if (!player.capabilities.isCreativeMode || !p.hasKey("data", 10)) return;
            net.minecraft.item.ItemStack stack = player.getHeldItemMainhand().copy();
            if (stack.getItem() == mchorse.bbs_mod.forge.CommonProxy.GUN_ITEM)
            {
                mchorse.bbs_mod.items.GunProperties gun = new mchorse.bbs_mod.items.GunProperties();
                gun.fromData(DataStorageUtils.fromNbt(p.getTag("data")).asMap());
                NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
                tag.setTag("GunData", DataStorageUtils.toNbt(gun.toData()));
                stack.setTagCompound(tag);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, stack);
                player.inventoryContainer.detectAndSendChanges();return;
            }
            if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(mchorse.bbs_mod.forge.CommonProxy.MODEL_BLOCK)) return;
            mchorse.bbs_mod.blocks.entities.ModelProperties checked = new mchorse.bbs_mod.blocks.entities.ModelProperties();
            checked.fromData(DataStorageUtils.fromNbt(p.getTag("data")).asMap());
            NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            NBTTagCompound tileTag = root.getCompoundTag("BlockEntityTag");
            tileTag.setTag("Properties", DataStorageUtils.toNbt(checked.toData()));
            root.setTag("BlockEntityTag", tileTag); stack.setTagCompound(root);
            player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, stack);
            player.inventoryContainer.detectAndSendChanges();
            return;
        }
        ActionManager actions=BBSMod.getActions();
        if(!op.equals("morph")&&!op.equals("teleport")&&!op.equals("manager")&&!op.equals("cut_structure")&&!op.equals("save_structure")&&!op.equals("player_settings")&&!op.equals("model_block"))validateId(id);
        switch(op)
        {
            case "model_block":
                net.minecraft.util.math.BlockPos blockPos = net.minecraft.util.math.BlockPos.fromLong(p.getLong("pos"));
                if (!player.capabilities.isCreativeMode || !player.world.isBlockLoaded(blockPos) || player.getDistanceSq(blockPos) > 4096 || !p.hasKey("data", 10)) break;
                net.minecraft.tileentity.TileEntity tile = player.world.getTileEntity(blockPos);
                if (tile instanceof mchorse.bbs_mod.forge.ModelTileEntity)
                    ((mchorse.bbs_mod.forge.ModelTileEntity) tile).updateForm(DataStorageUtils.fromNbt(p.getTag("data")).asMap(), player.world);
                break;
            case "manager":receiveManagerData(player,p);break;
            case "player_settings":
                ActionPlayer.applyFilmPlayerSettingsTo(player, p.getFloat("hp"), p.getFloat("hunger"), p.getInteger("xpLevel"), p.getFloat("xpProgress"));
                if (p.hasKey("equipment", 9))
                {
                    mchorse.bbs_mod.film.replays.ReplayKeyframes.applyPackedEquipment(new mchorse.bbs_mod.forms.entities.MCEntity(player), DataStorageUtils.fromNbt(p.getTag("equipment")).asList());
                    sendSelectedSlot(player, p.getInteger("slot"));
                }
                break;
            case "cut_structure":
                NBTTagCompound cut = packet("structure_cut", "");
                cut.setString("name", p.getString("name"));
                cut.setBoolean("ok", mchorse.bbs_mod.forms.structure.StructureOperations.cut(player.getServerWorld(), p.getString("name"), net.minecraft.util.math.BlockPos.fromLong(p.getLong("from")), net.minecraft.util.math.BlockPos.fromLong(p.getLong("to"))));
                FilmNetwork.toClient(player, cut);
                break;
            case "save_structure":
                NBTTagCompound saved = packet("structure_saved", "");
                saved.setString("name", p.getString("name"));
                saved.setBoolean("ok", mchorse.bbs_mod.forms.structure.StructureOperations.save(player.getServerWorld(), p.getString("name"), net.minecraft.util.math.BlockPos.fromLong(p.getLong("from")), net.minecraft.util.math.BlockPos.fromLong(p.getLong("to"))));
                FilmNetwork.toClient(player, saved);
                break;
            case "toggle":
                if(actions.getPlayer(id)!=null)
                {actions.stop(id);for(EntityPlayerMP watcher:player.getServer().getPlayerList().getPlayers())sendStopFilm(watcher,id);}
                else sendPlayFilm(player,player.getServerWorld(),id,p.getBoolean("camera"));
                break;
            case "pause":
                ActionPlayer current=actions.getPlayer(id);if(current!=null)current.toggle();
                for(EntityPlayerMP watcher:player.getServer().getPlayerList().getPlayers())sendPauseFilm(watcher,id);
                break;
            case "record":
                if(p.getBoolean("state"))
                {
                    Film film=BBSMod.getFilms().load(id);
                    if(film!=null)actions.startRecording(film,player,0,Math.max(0,p.getInteger("countdown")),p.getInteger("replay"));
                }
                else
                {
                    ActionRecorder recorder=actions.stopRecording(player);
                    if(recorder!=null)sendRecordedActions(player,id,p.getInteger("replay"),p.getInteger("tick"),recorder.composeClips());
                }
                break;
            case "control":control(player,id,p.getInteger("state"),p.getInteger("tick"));break;
            case "sync":
                NBTTagList raw=p.getTagList("path",8);List<String> path=new ArrayList<>();
                for(int i=0;i<raw.tagCount();i++)path.add(raw.getStringTagAt(i));
                actions.syncData(id,new DataPath(path),DataStorageUtils.fromNbt(p.getTag("data")));break;
            case "teleport":
                double x=p.getDouble("x"),y=p.getDouble("y"),z=p.getDouble("z");float yaw=p.getFloat("yaw"),pitch=p.getFloat("pitch"),body=p.getFloat("body");
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(body))throw new IllegalArgumentException("Invalid teleport");
                player.connection.setPlayerLocation(x,y,z,yaw,pitch);player.rotationYawHead=yaw;player.renderYawOffset=body;break;
            case "morph":sendMorphToTracked(player,p.hasKey("data")?FormUtils.fromData(DataStorageUtils.fromNbt(p.getTag("data"))):null);break;
            default:throw new IllegalArgumentException("Unknown film request: "+op);
        }
    }
    private static void control(EntityPlayerMP player,String id,int ordinal,int tick)
    {
        if(ordinal<0||ordinal>=ActionState.values().length)throw new IllegalArgumentException("Invalid action state");
        ActionManager actions=BBSMod.getActions();ActionPlayer current=actions.getPlayer(id);ActionState state=ActionState.values()[ordinal];
        if(state==ActionState.STOP){actions.stop(id);return;}
        if(state==ActionState.RESTART)
        {
            boolean rewind=current!=null&&current.type==PlayerType.FILM_EDITOR&&current.isPlayedBy(player)&&current.getWorld()==player.getServerWorld();
            if(!rewind)
            {
                Film film=current==null?(BBSMod.getFilms().exists(id)?BBSMod.getFilms().load(id):null):current.film;
                if(current!=null)actions.stop(id);
                current=film==null?null:actions.play(player,player.getServerWorld(),film,tick,PlayerType.FILM_EDITOR);
            }
            else{actions.restoreDamage(current.getWorld());current.resetActorsForRestart();}
            if(current!=null){current.syncing=true;current.playing=false;current.goTo(0,tick);}
            sendStopFilm(player,id);return;
        }
        if(current==null)return;
        current.goTo(tick);
        if(state==ActionState.PLAY)current.playing=true;
        if(state==ActionState.PAUSE)current.playing=false;
    }
    private static void receiveManagerData(EntityPlayerMP player, NBTTagCompound request)
    {
        int ordinal = request.getInteger("operation");
        if (ordinal < 0 || ordinal >= RepositoryOperation.values().length)
            throw new IllegalArgumentException("Invalid repository operation");
        BaseType response;
        try
        {
            BaseType data = DataStorageUtils.fromNbt(request.getTag("data"));
            if (data == null || !data.isMap()) throw new IllegalArgumentException("Repository request must be a map");
            response = FilmRepositoryOperations.apply(BBSMod.getFilms(), RepositoryOperation.values()[ordinal], data.asMap());
        }
        catch (RuntimeException error)
        {
            BBSMod.LOGGER.warn("Rejected film repository operation from {}", player.getName(), error);
            response = new ByteType(false);
        }
        if (request.getInteger("callback") >= 0)
            sendManagerData(player, request.getInteger("callback"), RepositoryOperation.values()[ordinal], response);
    }
    public static void sendManagerData(EntityPlayerMP player, int callbackId, RepositoryOperation operation, BaseType data)
    {
        NBTTagCompound response = packet("manager", "");
        response.setInteger("callback", callbackId);
        response.setInteger("operation", operation.ordinal());
        response.setTag("data", DataStorageUtils.toNbt(data));
        FilmNetwork.toClient(player, response);
    }
    private ServerNetwork(){}
}
