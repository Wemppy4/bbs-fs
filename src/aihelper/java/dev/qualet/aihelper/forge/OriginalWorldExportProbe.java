package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.*;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.WorldVideoExportSession;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forge.GlobalKeybinds;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.util.UUID;

/** Fixture/settings preparation only: all starts, toggles and film playback use real registered keys. */
public final class OriginalWorldExportProbe
{
    private static boolean active,resize,open,sound,audio,capture,realtime;
    private static int width,height,fps,blur;
    private static float delay;
    private static String path,id;
    private static Film previous;
    private static GuiScreen screen;
    private static boolean muted;
    private static float masterVolume;

    public static JsonObject run(JsonObject request)
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.getIntegratedServer()==null||!"ai_test".equals(mc.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("World export fixture is restricted to ai_test");
        String op=request.has("op")?request.get("op").getAsString():"status";
        if(op.equals("prepare"))
        {
            if(active||BBSModClient.getVideoRecorder().isRecording())throw new IllegalStateException("Finish the current export fixture first");
            UIDashboard dashboard=BBSModClient.getDashboard();UIFilmPanel panel=dashboard.getPanel(UIFilmPanel.class);
            previous=panel.getData();screen=mc.currentScreen;
            resize=BBSSettings.worldExportResizeWindow.get();open=BBSSettings.videoOpenFolderAfterExport.get();sound=BBSSettings.videoPlaySoundAfterExport.get();
            audio=BBSSettings.videoExportAudio.get();capture=BBSSettings.videoExportMinecraftSounds.get();realtime=BBSSettings.videoLimitFrameRate.get();
            width=BBSSettings.videoWidth.get();height=BBSSettings.videoHeight.get();fps=BBSSettings.videoFrameRate.get();blur=BBSSettings.videoMotionBlur.get();
            delay=BBSSettings.videoDelay.get();path=BBSSettings.videoExportPath.get();active=true;
            File movies=new File(mc.gameDir,".aihelper-world-export/"+UUID.randomUUID().toString());
            if(!movies.mkdirs())throw new IllegalStateException("Cannot create world export output folder");
            BBSSettings.videoExportPath.set(movies.getAbsolutePath());BBSSettings.videoOpenFolderAfterExport.set(false);BBSSettings.videoPlaySoundAfterExport.set(false);
            BBSSettings.worldExportResizeWindow.set(false);BBSSettings.videoWidth.set(320);BBSSettings.videoHeight.set(180);BBSSettings.videoFrameRate.set(30);
            BBSSettings.videoMotionBlur.set(0);BBSSettings.videoDelay.set(.15F);BBSSettings.videoExportAudio.set(true);BBSSettings.videoExportMinecraftSounds.set(false);
            BBSSettings.videoLimitFrameRate.set(false);
            Film film=new Film();id="dashboard_qa_world_export_"+UUID.randomUUID().toString().replace("-","");film.setId(id);
            IdleClip idle=new IdleClip();idle.duration.set(30);idle.position.get().point.set(mc.player.posX,mc.player.posY+mc.player.getEyeHeight(),mc.player.posZ);
            idle.position.get().angle.yaw=mc.player.rotationYaw;idle.position.get().angle.pitch=mc.player.rotationPitch;film.camera.addClip(idle);
            if(request.has("audio"))
            {
                AudioClientClip clip=new AudioClientClip();clip.duration.set(30);clip.layer.set(1);clip.audio.set(Link.assets(request.get("audio").getAsString()));film.camera.addClip(clip);
            }
            Replay replay=film.replays.addReplay();BlockForm form=new BlockForm();form.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());replay.form.set(form);
            Vec3d point=mc.player.getPositionEyes(1).add(mc.player.getLookVec().scale(4));replay.keyframes.x.insert(0,point.x);replay.keyframes.y.insert(0,point.y-.5);replay.keyframes.z.insert(0,point.z);
            UIScreen.open(dashboard);dashboard.setPanel(panel);panel.fill(film);panel.forceSave();
        }
        else if(op.equals("configure"))
        {
            if(!active||BBSModClient.getWorldExportSession().isExporting())throw new IllegalStateException("Configure only an idle active fixture");
            if(request.has("resize"))BBSSettings.worldExportResizeWindow.set(request.get("resize").getAsBoolean());
            if(request.has("delay"))BBSSettings.videoDelay.set(request.get("delay").getAsFloat());
            if(request.has("captureSounds"))BBSSettings.videoExportMinecraftSounds.set(request.get("captureSounds").getAsBoolean());
            if(request.has("mute")&&request.get("mute").getAsBoolean()&&!muted)
            {
                masterVolume=mc.gameSettings.getSoundLevel(net.minecraft.util.SoundCategory.MASTER);
                mc.gameSettings.setSoundLevel(net.minecraft.util.SoundCategory.MASTER,0F);muted=true;
            }
        }
        else if(op.equals("native_sound"))
        {
            if(!active||!BBSModClient.getMinecraftSoundCapture().isActive())throw new IllegalStateException("Start real capture before playing the native sound");
            mc.getSoundHandler().playSound(new net.minecraft.client.audio.PositionedSoundRecord(
                net.minecraft.init.SoundEvents.BLOCK_NOTE_PLING,net.minecraft.util.SoundCategory.BLOCKS,1F,1F,new net.minecraft.util.math.BlockPos(mc.player)));
        }
        else if(op.equals("cleanup"))
        {
            if(active)
            {
                BBSModClient.getWorldExportSession().cancel();
                BBSSettings.worldExportResizeWindow.set(resize);BBSSettings.videoOpenFolderAfterExport.set(open);BBSSettings.videoPlaySoundAfterExport.set(sound);
                BBSSettings.videoExportAudio.set(audio);BBSSettings.videoExportMinecraftSounds.set(capture);BBSSettings.videoLimitFrameRate.set(realtime);
                BBSSettings.videoWidth.set(width);BBSSettings.videoHeight.set(height);BBSSettings.videoFrameRate.set(fps);BBSSettings.videoMotionBlur.set(blur);BBSSettings.videoDelay.set(delay);BBSSettings.videoExportPath.set(path);
                BBSModClient.getDashboard().getPanel(UIFilmPanel.class).fill(previous);mc.displayGuiScreen(screen);active=false;
            }
            if(muted){mc.gameSettings.setSoundLevel(net.minecraft.util.SoundCategory.MASTER,masterVolume);muted=false;}
        }
        return snapshot();
    }

    private static JsonObject snapshot()
    {
        JsonObject out=new JsonObject();out.addProperty("ok",true);Minecraft mc=Minecraft.getMinecraft();
        WorldVideoExportSession session=BBSModClient.getWorldExportSession();
        out.addProperty("exporting",session.isExporting());out.addProperty("warming",session.isWarmingUp());out.addProperty("recording",session.isRecording());
        out.addProperty("film",session.getFilmId());out.addProperty("fixtureFilm",id);out.addProperty("filmRunning",id!=null&&BBSModClient.getFilms().has(id));
        out.addProperty("frames",BBSModClient.getVideoRecorder().getCounter());out.addProperty("remainingMs",session.getWarmupRemainingMs());
        out.addProperty("captureWidth",BBSRendering.getVideoWidth());out.addProperty("captureHeight",BBSRendering.getVideoHeight());
        out.addProperty("windowWidth",Display.getWidth());out.addProperty("windowHeight",Display.getHeight());out.addProperty("displayWidth",mc.displayWidth);out.addProperty("displayHeight",mc.displayHeight);
        out.addProperty("customSize",BBSRendering.isCustomSize());out.addProperty("output",BBSRendering.getVideoFolder().getAbsolutePath());
        out.addProperty("dashboardKey",GlobalKeybinds.DASHBOARD.getKeyCode());out.addProperty("worldKey",GlobalKeybinds.RECORD_VIDEO.getKeyCode());out.addProperty("filmKey",GlobalKeybinds.PLAY_FILM_AND_RECORD.getKeyCode());
        out.addProperty("failure",BBSModClient.getVideoRecorder().getFailure()==null?"":BBSModClient.getVideoRecorder().getFailure().toString());
        com.google.gson.JsonArray sounds=new com.google.gson.JsonArray();
        for(mchorse.bbs_mod.audio.MinecraftSoundCapture.CapturedSound sound:BBSModClient.getMinecraftSoundCapture().getSounds())
        {
            JsonObject captured=new JsonObject();captured.addProperty("resource",sound.location.toString());captured.addProperty("frame",sound.frame);sounds.add(captured);
        }
        out.add("capturedSounds",sounds);out.addProperty("captureActive",BBSModClient.getMinecraftSoundCapture().isActive());
        out.addProperty("glError",GL11.glGetError());return out;
    }
}
