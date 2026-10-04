package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/** Real client/server film messages and World mutation hook, restricted to the isolated QA world. */
public final class OriginalFilmBackendProbe
{
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static String id, replayId;
    private static Form previousMorph;
    private static boolean morphChanged;
    private static int requested;
    private static volatile int completed;
    private static volatile JsonObject result=new JsonObject();

    public static JsonObject handle(JsonObject input)
    {
        IntegratedServer server=MC.getIntegratedServer();
        if(server==null||MC.world==null||!new File(MC.gameDir,"saves/ai_test").getAbsoluteFile().equals(server.getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile()))
            throw new IllegalStateException("Film probe requires isolated ai_test integrated world");
        String op=input.has("op")?input.get("op").getAsString():"status";
        if(op.equals("prepare"))
        {
            if(id!=null)throw new IllegalStateException("Clean up the previous film probe first");
            id="__ai_film_"+UUID.randomUUID().toString().replace("-","");
            schedule(server,()->prepare(server));
        }
        else if(op.equals("damage"))schedule(server,()->damage(server.getWorld(0)));
        else if(op.equals("morph"))schedule(server,()->morph(server));
        else if(op.equals("snapshot"))schedule(server,()->snapshot(server));
        else if(op.equals("play")){requireFixture();ClientNetwork.sendToggleFilm(id,false);}
        else if(op.equals("pause")){requireFixture();ClientNetwork.sendPauseFilm(id);}
        else if(op.equals("stop")){requireFixture();ClientNetwork.sendToggleFilm(id,false);}
        else if(op.equals("cleanup"))schedule(server,()->cleanup(server));
        else if(!op.equals("status"))throw new IllegalArgumentException("Unknown film probe operation "+op);
        JsonObject state=new JsonObject();
        state.addProperty("ok",true);state.addProperty("requested",requested);state.addProperty("completed",completed);
        state.add("server",result);state.addProperty("filmId",id);state.addProperty("replayId",replayId);
        state.addProperty("handshake",ClientNetwork.isIsBBSModOnServer());
        if(id!=null)
        {
            BaseFilmController controller=BBSModClient.getFilms().getController(id);
            state.addProperty("clientPlaying",controller!=null);
            if(controller!=null){state.addProperty("clientPaused",controller.paused);state.addProperty("clientTick",controller.getTick());}
            Map<String,Integer> actors=BBSModClient.getFilms().getActors(id);
            Entity actor=actors==null||!actors.containsKey(replayId)?null:MC.world.getEntityByID(actors.get(replayId));
            state.addProperty("clientActor",actor instanceof ActorEntity);
            if(actor instanceof ActorEntity)
            {
                state.addProperty("clientActorId",actor.getEntityId());state.addProperty("clientActorX",actor.posX);
                state.addProperty("clientActorForm",((ActorEntity)actor).getForm() instanceof ModelForm);
            }
        }
        Morph morph=Morph.getMorph(MC.player);
        state.addProperty("clientMorph",morph!=null&&morph.getForm() instanceof ModelForm&&morph.getForm().hitbox.get());
        state.addProperty("clientWidth",MC.player.width);state.addProperty("clientHeight",MC.player.height);
        state.addProperty("glError",org.lwjgl.opengl.GL11.glGetError());
        return state;
    }
    private static void requireFixture(){if(id==null)throw new IllegalStateException("Prepare a film first");}
    private interface Work { JsonObject run() throws Exception; }
    private static void schedule(IntegratedServer server,Work work)
    {
        final int request=++requested;
        server.addScheduledTask(()->{
            try{result=work.run();}
            catch(Throwable error){JsonObject failure=new JsonObject();failure.addProperty("error",error.toString());result=failure;error.printStackTrace();}
            finally{completed=request;}
        });
    }
    private static EntityPlayerMP player(IntegratedServer server){return server.getPlayerList().getPlayerByUUID(MC.player.getUniqueID());}
    private static JsonObject prepare(IntegratedServer server)
    {
        EntityPlayerMP player=player(server);
        Film film=new Film();film.setId(id);Replay replay=film.replays.addReplay();replayId=replay.getId();
        ModelForm form=new ModelForm();form.model.set("helper_drone");replay.form.set(form);replay.actor.set(true);replay.actorPickup.set(false);
        replay.keyframes.x.insert(0,player.posX+4);replay.keyframes.x.insert(1200,player.posX+14);
        replay.keyframes.y.insert(0,player.posY+2);replay.keyframes.z.insert(0,player.posZ+5);
        if(!BBSMod.getFilms().save(id,(mchorse.bbs_mod.data.types.MapType)film.toData()))throw new IllegalStateException("Film save failed");
        Film loaded=BBSMod.getFilms().load(id);
        JsonObject out=new JsonObject();out.addProperty("prepared",loaded!=null&&loaded.replays.getById(replayId)!=null);
        out.addProperty("serverStorage",new File(server.getWorld(0).getSaveHandler().getWorldDirectory(),"bbs/films/"+id+".dat").isFile());return out;
    }
    private static JsonObject snapshot(IntegratedServer server)
    {
        JsonObject out=new JsonObject();ActionPlayer playback=id==null?null:BBSMod.getActions().getPlayer(id);
        out.addProperty("serverPlaying",playback!=null);
        if(playback!=null){out.addProperty("serverTick",playback.tick);out.addProperty("serverPaused",!playback.playing);}
        int count=0;for(Entity entity:server.getWorld(0).loadedEntityList)if(entity instanceof ActorEntity&&((ActorEntity)entity).getFilmId().equals(id)&&!entity.isDead)
        {count++;out.addProperty("serverActorId",entity.getEntityId());out.addProperty("serverActorX",entity.posX);}
        out.addProperty("serverActorCount",count);return out;
    }
    private static JsonObject damage(WorldServer world)
    {
        if(BBSMod.getActions().isTracking())throw new IllegalStateException("Run damage probe before any active playback");
        BlockPos pos=new BlockPos(240,12,240);
        net.minecraft.block.state.IBlockState previous=world.getBlockState(pos);
        if(previous.getBlock()!=Blocks.AIR)throw new IllegalStateException("Damage probe requires an empty test position");
        TileEntity tile=world.getTileEntity(pos);NBTTagCompound nbt=tile==null?null:tile.writeToNBT(new NBTTagCompound());
        boolean setting=BBSSettings.damageControl.get();Object first=new Object(),second=new Object();
        try
        {
            world.setBlockState(pos,Blocks.CHEST.getDefaultState(),3);
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos);chest.setCustomName("BBS probe chest");chest.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
            BBSSettings.damageControl.set(true);
            BBSMod.getActions().trackDamage(world,first);BBSMod.getActions().trackDamage(world,second);
            world.setBlockState(pos,Blocks.STONE.getDefaultState(),3); // Captured only by the real transformer.
            BBSMod.getActions().stopDamage(world,first);
            JsonObject out=new JsonObject();out.addProperty("sharedOwnerRetainsChange",world.getBlockState(pos).getBlock()==Blocks.STONE);
            BBSMod.getActions().stopDamage(world,second);
            TileEntity restored=world.getTileEntity(pos);
            out.addProperty("worldHookRestoredBlock",world.getBlockState(pos).getBlock()==Blocks.CHEST);
            out.addProperty("worldHookRestoredNbt",restored instanceof TileEntityChest&&((TileEntityChest)restored).getStackInSlot(0).getCount()==7&&((TileEntityChest)restored).getName().equals("BBS probe chest"));
            return out;
        }
        finally
        {
            BBSMod.getActions().stopDamage(world,first);BBSMod.getActions().stopDamage(world,second);
            TileEntity temporary=world.getTileEntity(pos);
            if(temporary instanceof TileEntityChest)((TileEntityChest)temporary).clear();
            world.setBlockState(pos,previous,3);
            if(nbt!=null){TileEntity restored=TileEntity.create(world,nbt);if(restored!=null)world.setTileEntity(pos,restored);}
            BBSSettings.damageControl.set(setting);
        }
    }
    private static JsonObject morph(IntegratedServer server)
    {
        Morph morph=Morph.getMorph(player(server));
        if(!morphChanged){previousMorph=FormUtils.copy(morph.getForm());morphChanged=true;}
        ModelForm form=new ModelForm();form.model.set("helper_drone");form.hitbox.set(true);form.hitboxWidth.set(0.9F);form.hitboxHeight.set(1.3F);
        morph.setForm(form);Morph detached=new Morph(null);detached.fromNbt(morph.toNbt());
        JsonObject out=new JsonObject();out.addProperty("morphCapabilityRoundTrip",detached.getForm() instanceof ModelForm&&detached.getForm().hitboxWidth.get()==0.9F);return out;
    }
    private static JsonObject cleanup(IntegratedServer server) throws Exception
    {
        if(id!=null)
        {
            BBSMod.getActions().stop(id);
            mchorse.bbs_mod.network.ServerNetwork.sendStopFilm(player(server),id);
            Files.deleteIfExists(new File(server.getWorld(0).getSaveHandler().getWorldDirectory(),"bbs/films/"+id+".dat").toPath());
        }
        if(morphChanged){Morph.getMorph(player(server)).setForm(previousMorph);morphChanged=false;previousMorph=null;}
        id=null;replayId=null;
        JsonObject out=new JsonObject();out.addProperty("cleaned",true);return out;
    }
}
