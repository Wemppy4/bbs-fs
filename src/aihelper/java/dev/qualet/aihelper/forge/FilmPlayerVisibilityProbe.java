package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;

/** Observes actual world render events in the real film editor; never posts synthetic render events. */
public final class FilmPlayerVisibilityProbe
{
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final FilmPlayerVisibilityProbe INSTANCE = new FilmPlayerVisibilityProbe();
    private static boolean active, keepFrame;
    private static int cameraMode, perspective, localAttempts, localCanceled, localPosts, previewPosts;
    private static Film previous;
    private static Form previousMorph;
    private static String id;

    public static JsonObject run(JsonObject request)
    {
        if (!MC.gameDir.getName().equals("run-forge1122-qa") || MC.player == null || MC.getIntegratedServer() == null
            || !"ai_test".equals(MC.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("Film visibility probe requires run-forge1122-qa/ai_test");
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("prepare"))
        {
            if (active) throw new IllegalStateException("Clean up the preceding film visibility fixture");
            if (!(UIScreen.getCurrentMenu() instanceof UIDashboard)
                || !(((UIDashboard) UIScreen.getCurrentMenu()).getPanels().panel instanceof UIFilmPanel))
                throw new IllegalStateException("Open the actual film editor first");
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            previous = panel.getData();
            Morph morph = Morph.getMorph(MC.player);
            previousMorph = morph == null ? null : FormUtils.copy(morph.getForm());
            keepFrame = BBSSettings.editorKeepFrameOnExit.get();
            cameraMode = panel.getController().getPovMode();
            perspective = MC.gameSettings.thirdPersonView;
            BBSSettings.editorKeepFrameOnExit.set(false);
            panel.getController().setPov(0);
            Film film = new Film();
            id = "aihelper_film_visibility_" + UUID.randomUUID().toString().replace("-", "");
            film.setId(id);
            IdleClip camera = new IdleClip();
            camera.duration.set(1200);
            camera.position.get().point.set(MC.player.posX, MC.player.posY + .9, MC.player.posZ - 6);
            camera.position.get().angle.yaw = 180;
            camera.position.get().angle.pitch = 0;
            film.camera.addClip(camera);
            Replay replay = film.replays.addReplay();
            MobForm actor = new MobForm(); actor.mobID.set("minecraft:player"); replay.form.set(actor);
            replay.keyframes.x.insert(0, MC.player.posX + 1.5);
            replay.keyframes.y.insert(0, MC.player.posY);
            replay.keyframes.z.insert(0, MC.player.posZ);
            panel.fill(film); panel.forceSave();
            ClientNetwork.sendPlayerForm(null);
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            active = true;
            reset();
        }
        else if (op.equals("reset")) reset();
        else if (op.equals("morph"))
        {
            if (!active) throw new IllegalStateException("Prepare the visibility fixture first");
            BlockForm form = new BlockForm(); form.blockState.set(Blocks.REDSTONE_BLOCK.getDefaultState());
            ClientNetwork.sendPlayerForm(form);
        }
        else if (op.equals("demorph") && active) ClientNetwork.sendPlayerForm(null);
        else if (op.equals("third_person") && active) MC.gameSettings.thirdPersonView = 1;
        else if (op.equals("cleanup") && active)
        {
            MinecraftForge.EVENT_BUS.unregister(INSTANCE);
            active = false;
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            panel.fill(previous);
            panel.getController().setPov(cameraMode);
            BBSSettings.editorKeepFrameOnExit.set(keepFrame);
            MC.gameSettings.thirdPersonView = perspective;
            ClientNetwork.sendPlayerForm(previousMorph);
            final String fixture = id;
            MC.getIntegratedServer().addScheduledTask(() -> BBSMod.getFilms().delete(fixture));
        }
        JsonObject out = new JsonObject(); out.addProperty("ok", true); out.addProperty("active", active);
        out.addProperty("fixture", id); out.addProperty("hidePlayer", MorphRenderer.hidePlayer);
        Morph morph = Morph.getMorph(MC.player);
        out.addProperty("hasMorph", morph != null && morph.getForm() != null);
        out.addProperty("localAttempts", localAttempts); out.addProperty("localCanceled", localCanceled);
        out.addProperty("localPosts", localPosts); out.addProperty("previewPosts", previewPosts);
        out.addProperty("cameraIsPlayer", MC.getRenderViewEntity() == MC.player);
        out.addProperty("glError", org.lwjgl.opengl.GL11.glGetError());
        return out;
    }

    private static void reset() { localAttempts = localCanceled = localPosts = previewPosts = 0; }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void beforePlayer(RenderPlayerEvent.Pre event)
    {
        if (active && event.getEntityPlayer() == MC.player)
        {
            localAttempts++;
            if (event.isCanceled()) localCanceled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterPlayer(RenderPlayerEvent.Post event)
    {
        if (!active) return;
        if (event.getEntityPlayer() == MC.player) localPosts++;
        else previewPosts++;
    }
}
