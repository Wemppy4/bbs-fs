package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.*;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
import java.io.File;
import java.util.UUID;

/** Sets up a new isolated film; export is still started by the production Record button. */
public final class OriginalExportFixture
{
    private static Film previous;
    private static boolean active, audio, mute, open, sound;
    public static JsonObject handle(JsonObject request)
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(request.has("paths")){JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("assets",BBSMod.getAssetsFolder().getAbsolutePath());return out;}
        if(mc.getIntegratedServer()==null || !new File(mc.gameDir,"saves/ai_test").getAbsoluteFile().equals(mc.getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory().getAbsoluteFile()))
            throw new IllegalStateException("Export fixture requires isolated ai_test");
        UIDashboard dashboard=BBSModClient.getDashboard();dashboard.finishBuilding();
        UIFilmPanel panel=dashboard.getPanel(UIFilmPanel.class);
        if(request.has("cleanup"))
        {
            if(active){BBSSettings.videoExportAudio.set(audio);BBSSettings.videoMuteAudioWhileRender.set(mute);BBSSettings.videoOpenFolderAfterExport.set(open);BBSSettings.videoPlaySoundAfterExport.set(sound);panel.fill(previous);active=false;}
            return OriginalDashboardProbe.snapshot();
        }
        if(active)throw new IllegalStateException("Restore prior export fixture first");
        previous=panel.getData();audio=BBSSettings.videoExportAudio.get();mute=BBSSettings.videoMuteAudioWhileRender.get();open=BBSSettings.videoOpenFolderAfterExport.get();sound=BBSSettings.videoPlaySoundAfterExport.get();active=true;
        BBSSettings.videoExportAudio.set(true);BBSSettings.videoMuteAudioWhileRender.set(true);BBSSettings.videoOpenFolderAfterExport.set(false);BBSSettings.videoPlaySoundAfterExport.set(false);
        Film film=new Film();film.setId("dashboard_qa_export_"+UUID.randomUUID().toString().replace("-",""));
        IdleClip idle=new IdleClip();idle.duration.set(40);idle.position.get().point.set(mc.player.posX,mc.player.posY+mc.player.getEyeHeight(),mc.player.posZ);
        idle.position.get().angle.yaw=mc.player.rotationYaw+180;idle.position.get().angle.pitch=mc.player.rotationPitch;film.camera.addClip(idle);
        AudioClientClip clip=new AudioClientClip();clip.duration.set(40);clip.layer.set(1);clip.audio.set(Link.assets(request.get("audio").getAsString()));film.camera.addClip(clip);
        Replay replay=film.replays.addReplay();BlockForm form=new BlockForm();form.blockState.set(Blocks.GOLD_BLOCK.getDefaultState());replay.form.set(form);
        Vec3d point=mc.player.getPositionEyes(1).add(mc.player.getLookVec().scale(5));
        replay.keyframes.x.insert(0,point.x);replay.keyframes.y.insert(0,point.y-.5);replay.keyframes.z.insert(0,point.z);
        replay.axesPreview.set(true);replay.shadow.set(true);
        UIScreen.open(dashboard);dashboard.setPanel(panel);panel.fill(film);panel.forceSave();
        return OriginalDashboardProbe.snapshot();
    }
}
