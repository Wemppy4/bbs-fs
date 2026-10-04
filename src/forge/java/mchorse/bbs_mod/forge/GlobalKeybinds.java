package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.WorldVideoExportSession;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIDrawContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The original BBS global shortcuts over Forge's rebindable key settings. */
public final class GlobalKeybinds
{
    public static final KeyBinding DASHBOARD = key("dashboard",Keyboard.KEY_0);
    public static final KeyBinding ITEM_EDITOR = key("item_editor",Keyboard.KEY_HOME);
    public static final KeyBinding PLAY_FILM = key("play_film",Keyboard.KEY_RCONTROL);
    public static final KeyBinding PAUSE_FILM = key("pause_film",Keyboard.KEY_BACKSLASH);
    public static final KeyBinding RECORD_REPLAY = key("record_replay",Keyboard.KEY_RMENU);
    public static final KeyBinding RECORD_VIDEO = key("record_video",Keyboard.KEY_F4);
    public static final KeyBinding PLAY_FILM_AND_RECORD = key("play_film_and_record",Keyboard.KEY_F6);
    public static final KeyBinding OPEN_REPLAYS = key("open_replays",Keyboard.KEY_RSHIFT);
    public static final KeyBinding OPEN_MORPHING = key("open_morphing",Keyboard.KEY_B);
    public static final KeyBinding DEMORPH = key("demorph",Keyboard.KEY_PERIOD);
    public static final KeyBinding TELEPORT = key("teleport",Keyboard.KEY_Y);
    private static final KeyBinding[] ALL = {DASHBOARD,ITEM_EDITOR,PLAY_FILM,PAUSE_FILM,RECORD_REPLAY,RECORD_VIDEO,PLAY_FILM_AND_RECORD,OPEN_REPLAYS,OPEN_MORPHING,DEMORPH,TELEPORT};
    private boolean migrated;

    private static KeyBinding key(String id,int code) { return new KeyBinding("key.bbs."+id,code,"category.bbs.main"); }
    public static void register()
    {
        for(KeyBinding binding:ALL) ClientRegistry.registerKeyBinding(binding);
        MinecraftForge.EVENT_BUS.register(new GlobalKeybinds());
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(!this.migrated){this.migrated=true;this.migrateLegacyDashboard(mc);}
        if(mc.world==null||mc.player==null){for(KeyBinding key:ALL)while(key.isPressed()){}return;}
        while(DASHBOARD.isPressed())UIScreen.open(BBSModClient.getDashboard());
        while(ITEM_EDITOR.isPressed())
        {
            mchorse.bbs_mod.blocks.entities.ModelProperties properties=BBSModClient.getItemStackProperties(mc.player.getHeldItemMainhand());
            if(properties!=null)UIScreen.open(new mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu(properties));
        }
        while(PLAY_FILM.isPressed()){UIFilmPanel panel=selectedFilm();if(panel!=null)Films.playFilm(panel.getData().getId(),false);}
        while(PAUSE_FILM.isPressed()){UIFilmPanel panel=selectedFilm();if(panel!=null)Films.pauseFilm(panel.getData().getId());}
        while(RECORD_REPLAY.isPressed())recordReplay();
        while(RECORD_VIDEO.isPressed())
        {
            WorldVideoExportSession session=BBSModClient.getWorldExportSession();
            if(session.isExporting())session.cancel();else session.start(null,null);
        }
        while(PLAY_FILM_AND_RECORD.isPressed())recordFilm();
        while(OPEN_REPLAYS.isPressed())
        {
            UIDashboard dashboard=BBSModClient.getDashboard();UIScreen.open(dashboard);
            if(dashboard.getPanels().panel instanceof UIFilmPanel)
            {
                UIFilmPanel panel=(UIFilmPanel)dashboard.getPanels().panel;
                if(panel.getData()!=null)panel.showPanel(panel.replayEditor);
            }
            else dashboard.setPanel(dashboard.getPanel(UIFilmPanel.class));
        }
        while(OPEN_MORPHING.isPressed())
        {
            UIDashboard dashboard=BBSModClient.getDashboard();UIScreen.open(dashboard);dashboard.setPanel(dashboard.getPanel(UIMorphingPanel.class));
        }
        while(DEMORPH.isPressed())ClientNetwork.sendPlayerForm(null);
        while(TELEPORT.isPressed())
        {
            UIFilmPanel panel=BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            if(panel!=null)panel.replayEditor.teleport();
        }
    }

    private static UIFilmPanel selectedFilm()
    {
        UIDashboard dashboard=BBSModClient.getDashboardIfCreated();
        if(dashboard==null)return null;
        UIFilmPanel panel=dashboard.getPanel(UIFilmPanel.class);
        return panel==null||panel.getData()==null?null:panel;
    }

    private static void recordFilm()
    {
        UIFilmPanel panel=selectedFilm();if(panel==null)return;
        String id=panel.getData().getId();WorldVideoExportSession session=BBSModClient.getWorldExportSession();
        if(session.isExporting()){if(id.equals(session.getFilmId()))session.cancel();}
        else session.start(id,panel.getData());
    }

    private static void recordReplay()
    {
        UIFilmPanel panel=BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        if(panel==null||panel.getData()==null)return;
        Recorder recorder=BBSModClient.getFilms().getRecorder();
        if(recorder!=null)
        {
            recorder=BBSModClient.getFilms().stopRecording();
            if(recorder!=null&&!recorder.hasNotStarted())panel.applyRecordedKeyframes(recorder,panel.getData());
        }
        else
        {
            Replay replay=panel.replayEditor.getReplay();int index=panel.getData().replays.getList().indexOf(replay);
            if(index>=0)BBSModClient.getFilms().startRecording(panel.getData(),index,0);
        }
    }

    private void migrateLegacyDashboard(Minecraft mc)
    {
        if(DASHBOARD.getKeyCode()!=Keyboard.KEY_F6||DASHBOARD.getKeyModifier()!=KeyModifier.NONE)return;
        File options=new File(mc.gameDir,"options.txt");
        try
        {
            /* A saved world-record shortcut identifies an already migrated options file.
             * Any other Dashboard key/modifier is an explicit user mapping and is retained. */
            if(options.isFile())for(String line:Files.readAllLines(options.toPath(),StandardCharsets.UTF_8))
                if(line.startsWith("key_key.bbs.play_film_and_record:"))return;
            DASHBOARD.setKeyCode(Keyboard.KEY_0);KeyBinding.resetKeyBindingArrayAndHash();mc.gameSettings.saveOptions();
            BBSMod.LOGGER.info("Migrated the Forge prototype Dashboard shortcut from F6 to the original BBS 0 key");
        }
        catch(java.io.IOException error){BBSMod.LOGGER.warn("Could not migrate the prototype Dashboard shortcut",error);}
    }

    /** After the captured world was copied, so the operator's timer never enters the video. */
    @SubscribeEvent public void overlay(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||!BBSSettings.recordingOverlays.get())return;
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.currentScreen!=null)return;
        WorldVideoExportSession session=BBSModClient.getWorldExportSession();String label;
        if(session.isWarmingUp())label=String.valueOf(Math.max(0,(int)Math.ceil(session.getWarmupRemainingMs()/50D))/20F);
        else if(session.isRecording())label=UIKeys.FILM_VIDEO_RECORDING.format(BBSModClient.getVideoRecorder().getCounter(),RECORD_VIDEO.getDisplayName()).get();
        else return;
        mc.entityRenderer.setupOverlayRendering();
        Batcher2D batcher=new Batcher2D(new UIDrawContext(new ScaledResolution(mc).getScaleFactor()));batcher.beginBatch();
        try{batcher.icon(Icons.SPHERE,Colors.RED|Colors.A100,21,5,1F,0F);batcher.textCard(label,24,9,Colors.WHITE,Colors.A50);}
        finally{batcher.endBatch();}
    }
}
