package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;

/** Client half of the real Forge film protocol. Methods dispatch only native packets. */
public final class ClientNetwork
{
    private static boolean server;
    private static int callbackSequence;
    private static final Map<Integer, Consumer<BaseType>> callbacks = new HashMap<>();
    public static boolean isIsBBSModOnServer(){return server;}
    public static void sendModelBlockTransforms(MapType data)
    {
        NBTTagCompound request = packet("model_item", "");
        request.setTag("data", DataStorageUtils.toNbt(data));
        FilmNetwork.toServer(request);
    }
    public static void sendModelBlockForm(net.minecraft.util.math.BlockPos pos, mchorse.bbs_mod.forge.ModelTileEntity tile)
    {
        NBTTagCompound request = packet("model_block", "");
        request.setLong("pos", pos.toLong());
        request.setTag("data", DataStorageUtils.toNbt(tile.getProperties().toData()));
        FilmNetwork.toServer(request);
    }
    public static void reset(){server=false;callbacks.clear();FilmNetwork.resetClient();FilmClientBridge.reset();}
    public static void sendManagerDataLoad(String id, Consumer<BaseType> callback)
    {
        MapType request = new MapType();
        request.putString("id", id);
        sendManagerData(RepositoryOperation.LOAD, request, callback);
    }
    public static void sendManagerData(RepositoryOperation operation, BaseType data, Consumer<BaseType> callback)
    {
        int id;
        do { id = callbackSequence++ & Integer.MAX_VALUE; } while (callbacks.containsKey(id));
        if (callback != null) callbacks.put(id, callback);
        try { sendManagerData(id, operation, data); }
        catch (RuntimeException error) { callbacks.remove(id); throw error; }
    }
    public static void sendManagerData(int callbackId, RepositoryOperation operation, BaseType data)
    {
        NBTTagCompound request = packet("manager", "");
        request.setInteger("callback", callbackId);
        request.setInteger("operation", operation.ordinal());
        request.setTag("data", DataStorageUtils.toNbt(data));
        FilmNetwork.toServer(request);
    }
    private static NBTTagCompound packet(String op,String id){NBTTagCompound p=new NBTTagCompound();p.setString("op",op);p.setString("film",id);return p;}
    public static void sendToggleFilm(String id,boolean camera){NBTTagCompound p=packet("toggle",id);p.setBoolean("camera",camera);FilmNetwork.toServer(p);}
    public static void sendPauseFilm(String id){FilmNetwork.toServer(packet("pause",id));}
    public static void sendActionRecording(String id,int replay,int tick,int countdown,boolean state)
    {NBTTagCompound p=packet("record",id);p.setInteger("replay",replay);p.setInteger("tick",tick);p.setInteger("countdown",countdown);p.setBoolean("state",state);FilmNetwork.toServer(p);}
    public static void sendActionState(String id,ActionState state,int tick)
    {NBTTagCompound p=packet("control",id);p.setInteger("state",state.ordinal());p.setInteger("tick",tick);FilmNetwork.toServer(p);}
    public static void sendSyncData(String id,BaseValue data)
    {
        NBTTagCompound p=packet("sync",id);NBTTagList path=new NBTTagList();
        for(String element:data.getPath().strings)path.appendTag(new NBTTagString(element));
        p.setTag("path",path);p.setTag("data",DataStorageUtils.toNbt(data.toData()));FilmNetwork.toServer(p);
    }
    public static void sendTeleport(EntityPlayer player,double x,double y,double z)
    {sendTeleport(x,y,z,player.getRotationYawHead(),player.getRotationYawHead(),player.rotationPitch);}
    public static void sendTeleport(double x,double y,double z,float yaw,float bodyYaw,float pitch)
    {NBTTagCompound p=packet("teleport","");p.setDouble("x",x);p.setDouble("y",y);p.setDouble("z",z);p.setFloat("yaw",yaw);p.setFloat("body",bodyYaw);p.setFloat("pitch",pitch);FilmNetwork.toServer(p);}
    public static void sendPlayerForm(Form form)
    {
        if(!server){Morph morph=Morph.getMorph(Minecraft.getMinecraft().player);if(morph!=null)morph.setForm(FormUtils.copy(form));return;}
        NBTTagCompound p=packet("morph","");if(form!=null)p.setTag("data",DataStorageUtils.toNbt(FormUtils.toData(form)));FilmNetwork.toServer(p);
    }
    public static void sendFormTrigger(String triggerId, int type)
    {
        if (!server) return;
        NBTTagCompound request = packet("form_trigger", "");
        request.setString("trigger", triggerId);
        request.setInteger("type", type);
        FilmNetwork.toServer(request);
    }

    private static void receiveFormTrigger(NBTTagCompound packet)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        net.minecraft.entity.Entity entity = mc.world.getEntityByID(packet.getInteger("entity"));
        int type = packet.getInteger("type");
        if (type < ServerNetwork.STATE_TRIGGER_MORPH || type > ServerNetwork.STATE_TRIGGER_OFF_HAND_ITEM) return;
        String trigger = packet.getString("trigger");
        Morph morph = Morph.getMorph(entity);
        if (morph != null && morph.getForm() != null) morph.getForm().playState(trigger);
        if (entity instanceof net.minecraft.entity.EntityLivingBase && type > 0)
        {
            net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
            mchorse.bbs_mod.blocks.entities.ModelProperties properties = BBSModClient.getItemStackProperties(
                type == ServerNetwork.STATE_TRIGGER_MAIN_HAND_ITEM ? living.getHeldItemMainhand() : living.getHeldItemOffhand());
            if (properties != null && properties.getForm() != null) properties.getForm().playState(trigger);
        }
    }

    public static void sendSharedForm(Form form, UUID player)
    {
        NBTTagCompound request = packet("share", "");
        request.setUniqueId("player", player);
        MapType data = FormUtils.toData(form);
        request.setTag("data", DataStorageUtils.toNbt(data == null ? new MapType() : data));
        FilmNetwork.toServer(request);
    }
    public static void sendApplyFilmPlayerSettingsToPlayer(Film film, int tick)
    {
        NBTTagCompound request = packet("player_settings", "");
        request.setFloat("hp", film.hp.get());
        request.setFloat("hunger", film.hunger.get());
        request.setInteger("xpLevel", film.xpLevel.get());
        request.setFloat("xpProgress", film.xpProgress.get());
        mchorse.bbs_mod.film.replays.Replay replay = film.getFirstPersonReplay();
        if (replay != null)
        {
            request.setInteger("slot", replay.keyframes.getSelectedSlot(tick));
            request.setTag("equipment", DataStorageUtils.toNbt(replay.keyframes.packEquipment(tick)));
        }
        FilmNetwork.toServer(request);
    }
    public static void sendCutStructure(String name, net.minecraft.util.math.BlockPos from, net.minecraft.util.math.BlockPos to)
    {
        NBTTagCompound request = packet("cut_structure", "");
        request.setString("name", name);
        request.setLong("from", from.toLong());
        request.setLong("to", to.toLong());
        FilmNetwork.toServer(request);
    }
    public static void sendSaveStructure(String name, net.minecraft.util.math.BlockPos from, net.minecraft.util.math.BlockPos to)
    {
        NBTTagCompound request = packet("save_structure", "");
        request.setString("name", name);
        request.setLong("from", from.toLong());
        request.setLong("to", to.toLong());
        FilmNetwork.toServer(request);
    }
    private static void receiveSharedForm(NBTTagCompound packet)
    {
        Form form = FormUtils.fromData(DataStorageUtils.fromNbt(packet.getTag("data")));
        if (form == null) return;
        mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = BBSModClient.getDashboard();
        if (mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() == null)
            mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
        dashboard.setPanel(dashboard.getPanel(mchorse.bbs_mod.ui.morphing.UIMorphingPanel.class));
        BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).addForm(form);
        dashboard.context.notifyInfo(mchorse.bbs_mod.ui.UIKeys.FORMS_SHARED_NOTIFICATION.format(form.getDisplayName()));
    }
    public static void receive(NBTTagCompound p)
    {
        String id=p.getString("film");
        switch(p.getString("op"))
        {
            case "hello":server=true;break;
            case "shared":receiveSharedForm(p);break;
            case "form_trigger":receiveFormTrigger(p);break;
            case "model_state":
                Minecraft mc=Minecraft.getMinecraft();
                if(mc.world!=null)
                {
                    net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(p.getLong("pos")));
                    if(tile instanceof mchorse.bbs_mod.forge.ModelTileEntity)
                    {
                        Form modelForm=((mchorse.bbs_mod.forge.ModelTileEntity)tile).getProperties().getForm();
                        if(modelForm!=null)modelForm.playState(p.getString("state"));
                    }
                }
                break;
            case "model_refresh":
                int range=p.getInteger("range");
                for(mchorse.bbs_mod.forge.ModelTileEntity model:mchorse.bbs_mod.client.BBSRendering.capturedModelBlocks)
                {
                    mchorse.bbs_mod.blocks.entities.ModelProperties properties=model.getProperties();
                    properties.setForm(FormUtils.copy(properties.getForm()));
                    int random=(int)(Math.random()*range);
                    while(random-->0)properties.update(model.getEntity());
                }
                break;
            case "structure_cut":mchorse.bbs_mod.forms.structure.StructureCut.onCut(p.getBoolean("ok"), p.getString("name"));break;
            case "structure_saved":mchorse.bbs_mod.forms.structure.StructureWand.onSaved(p.getBoolean("ok"), p.getString("name"));break;
            case "manager":
                Consumer<BaseType> callback = callbacks.remove(p.getInteger("callback"));
                if (callback != null) callback.accept(DataStorageUtils.fromNbt(p.getTag("data")));
                break;
            case "play":
                Film film=new Film();film.setId(id);film.fromData(DataStorageUtils.fromNbt(p.getTag("data")));Films.playFilm(film,p.getBoolean("camera"));break;
            case "stop":Films.stopFilm(id);break;
            case "pause":Films.togglePauseFilm(id);break;
            case "actors":
                Map<String,Integer> actors=new HashMap<>();NBTTagCompound values=p.getCompoundTag("actors");
                for(String key:values.getKeySet())actors.put(key,values.getInteger(key));
                BBSModClient.getFilms().updateActors(id,actors,p.getBoolean("merge"));break;
            case "recorded":FilmClientBridge.receiveActions(id,p.getInteger("replay"),p.getInteger("tick"),DataStorageUtils.fromNbt(p.getTag("data")));break;
            case "resync":Film edited=FilmClientBridge.editedFilm();if(edited!=null&&edited.getId().equals(id))sendSyncData(id,edited);break;
            default:throw new IllegalArgumentException("Unknown film response: "+p.getString("op"));
        }
    }
    private ClientNetwork(){}
}
