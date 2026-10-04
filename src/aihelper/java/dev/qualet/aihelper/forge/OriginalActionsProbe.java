package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionRecorder;
import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.film.Film;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Invoke transformed Minecraft methods while the real production recorder owns a temporary take. */
public final class OriginalActionsProbe {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static volatile JsonObject result=new JsonObject();
    private static volatile String error;
    private static volatile int completed;
    private static int requested;
    public static JsonObject run(JsonObject request) {
        if(MC.getIntegratedServer()==null||MC.player==null||!new File(MC.gameDir,"saves/ai_test").getAbsoluteFile().equals(MC.getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile()))
            throw new IllegalStateException("Action boundary probe requires ai_test integrated world");
        if(request.has("test")&&request.get("test").getAsBoolean()) {
            final int task=++requested;error=null;
            MC.getIntegratedServer().addScheduledTask(()->{
                try { result=test(); }
                catch(Throwable failure) { error=failure.toString();failure.printStackTrace(); }
                finally { completed=task; }
            });
        }
        JsonObject out=new JsonObject();out.addProperty("ok",error==null);out.addProperty("requested",requested);out.addProperty("completed",completed);
        if(error!=null)out.addProperty("error",error);out.add("result",result);return out;
    }
    private static JsonObject test() throws Exception {
        EntityPlayerMP player=MC.getIntegratedServer().getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
        WorldServer world=player.getServerWorld();ActionManager actions=BBSMod.getActions();
        Field field=ActionManager.class.getDeclaredField("recorders");field.setAccessible(true);
        if(((Map<?,?>)field.get(actions)).containsKey(player))throw new IllegalStateException("An active user recording must not be replaced");
        ItemStack original=player.getHeldItemMainhand().copy();
        BlockPos position=new BlockPos(player.posX+3,250,player.posZ+3);
        Film film=new Film();film.setId("__aihelper_capture_"+System.nanoTime());
        boolean recording=false;
        JsonObject out=new JsonObject();
        try {
            ItemStack fixture=new ItemStack(Items.STICK,3);NBTTagCompound nbt=new NBTTagCompound();nbt.setString("capture","fixture");fixture.setTagCompound(nbt);
            player.setHeldItem(EnumHand.MAIN_HAND,fixture);
            actions.startRecording(film,player,7,0,-1);recording=true;
            /* Call the actual methods; directly calling FilmActionHooks would not test the transformer. */
            fixture.onItemUse(player,world,position,EnumHand.MAIN_HAND,EnumFacing.UP,.25F,.5F,.75F);
            world.sendBlockBreakProgress(player.getEntityId(),position,4);
            ActionRecorder recorder=actions.stopRecording(player);recording=false;
            List<UseBlockItemActionClip> uses=recorder.getClips().getClips(UseBlockItemActionClip.class);
            List<BreakBlockActionClip> breaks=recorder.getClips().getClips(BreakBlockActionClip.class);
            out.addProperty("useBlockCaptured",uses.size()==1);out.addProperty("breakProgressCaptured",breaks.size()==1);
            if(!uses.isEmpty()) {
                UseBlockItemActionClip use=uses.get(0);RayTraceResult hit=use.hit.getHitResult();
                out.addProperty("itemSnapshot",use.itemStack.get().getItem()==Items.STICK&&use.itemStack.get().getCount()==3&&"fixture".equals(use.itemStack.get().getTagCompound().getString("capture")));
                out.addProperty("handAndHit",use.hand.get()&&position.equals(hit.getBlockPos())&&hit.sideHit==EnumFacing.UP&&Math.abs(hit.hitVec.x-position.getX()-.25)<.001&&Math.abs(hit.hitVec.z-position.getZ()-.75)<.001);
                out.addProperty("recordedTick",use.tick.get()==7);
            }
            if(!breaks.isEmpty()) {
                BreakBlockActionClip clip=breaks.get(0);
                out.addProperty("breakProgressAndPosition",clip.progress.get()==4&&clip.x.get()==position.getX()&&clip.y.get()==position.getY()&&clip.z.get()==position.getZ());
            }
        } finally {
            if(recording)actions.stopRecording(player);
            world.sendBlockBreakProgress(player.getEntityId(),position,-1);
            player.setHeldItem(EnumHand.MAIN_HAND,original);player.inventoryContainer.detectAndSendChanges();
        }
        out.addProperty("heldItemRestored",ItemStack.areItemStacksEqual(original,player.getHeldItemMainhand()));
        out.addProperty("recorderReleased",!((Map<?,?>)field.get(actions)).containsKey(player)&&actions.getPlayer(film.getId())==null);
        return out;
    }
}