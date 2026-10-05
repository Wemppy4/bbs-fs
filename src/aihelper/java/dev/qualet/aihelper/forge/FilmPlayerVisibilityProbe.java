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
    private static int heldTicks, inputSamples;
    private static float maxForward, maxMove;
    private static java.nio.ByteBuffer keyboard;
    private static byte previousForward;
    private static boolean injected;
    private static int localMorphDraws;
    private static boolean observerInstalled;
    private static com.google.gson.JsonArray frames = new com.google.gson.JsonArray();

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
            if (!observerInstalled)
            {
                mchorse.bbs_mod.api.client.events.FormRenderEvents.BEFORE.register((form, context) ->
                {
                    Morph current = Morph.getMorph(MC.player);
                    if (active && current != null && context.entity == current.entity && !context.ui && !context.modelRenderer)
                        localMorphDraws++;
                });
                observerInstalled = true;
            }
            active = true;
            reset();
        }
        else if (op.equals("reset")) { reset(); frames = new com.google.gson.JsonArray(); }
        else if (op.equals("orbit") && active)
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            panel.replayEditor.setReplay(panel.getData().replays.getList().get(0));
            panel.getController().setPov(2);
        }
        else if (op.equals("control") && active)
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            panel.replayEditor.setReplay(panel.getData().replays.getList().get(0));
            panel.getController().toggleControl();
        }
        else if (op.equals("walk") && active)
        {
            heldTicks=20; inputSamples=0; maxForward=maxMove=0;
        }
        else if (op.equals("lighting") && active)
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            Replay replay = panel.getData().replays.getList().get(0);
            mchorse.bbs_mod.forms.forms.ModelForm model = new mchorse.bbs_mod.forms.forms.ModelForm();
            model.model.set("player/steve"); replay.form.set(model);
            replay.keyframes.armorHead.insert(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_HELMET));
            replay.keyframes.armorChest.insert(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_CHESTPLATE));
            replay.keyframes.armorLegs.insert(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_LEGGINGS));
            replay.keyframes.armorFeet.insert(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_BOOTS));
            replay.keyframes.bodyYaw.insert(0, 30D); replay.keyframes.headYaw.insert(0, 30D);
            Replay mob = panel.getData().replays.addReplay();
            MobForm llama = new MobForm(); llama.mobID.set("minecraft:llama"); mob.form.set(llama);
            mob.keyframes.x.insert(0, MC.player.posX - 1.5);
            mob.keyframes.y.insert(0, MC.player.posY); mob.keyframes.z.insert(0, MC.player.posZ);
            mob.keyframes.bodyYaw.insert(0, 30D); mob.keyframes.headYaw.insert(0, 30D);
            panel.fill(panel.getData()); panel.forceSave();
        }
        else if (op.equals("morph_edit") && active)
        {
            mchorse.bbs_mod.ui.morphing.UIMorphingPanel panel = BBSModClient.getDashboard().getPanel(mchorse.bbs_mod.ui.morphing.UIMorphingPanel.class);
            panel.palette.editor.edit(FormUtils.copy(Morph.getMorph(MC.player).getForm()));
            panel.palette.list.setVisible(false); panel.palette.editor.setVisible(true);
            /* A real last motion delta exposes replayed interpolation while the game is paused. */
            MC.player.prevPosX=MC.player.posX-.2;
            frames = new com.google.gson.JsonArray();
        }
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
            releaseKey(); heldTicks=0;
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
        out.addProperty("localMorphDraws", localMorphDraws); out.addProperty("shouldHide", MorphRenderer.shouldHide(MC.player));
        out.addProperty("cameraIsPlayer", MC.getRenderViewEntity() == MC.player);
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        out.addProperty("controlling", panel.getController().canControl());
        out.addProperty("inputSamples", inputSamples); out.addProperty("inputForward", maxForward); out.addProperty("moveForward", maxMove);
        out.addProperty("playerX", MC.player.posX); out.addProperty("playerZ", MC.player.posZ);
        out.addProperty("paused", MC.isGamePaused()); out.add("frames", new com.google.gson.JsonParser().parse(frames.toString()));
        net.minecraft.entity.player.EntityPlayerMP serverPlayer = MC.getIntegratedServer().getPlayerList().getPlayerByUUID(MC.player.getUniqueID());
        if(serverPlayer!=null){out.addProperty("serverX",serverPlayer.posX);out.addProperty("serverZ",serverPlayer.posZ);}
        mchorse.bbs_mod.ui.utils.Area a=panel.preview.shaders.area;
        JsonObject button=new JsonObject();button.addProperty("x",a.x);button.addProperty("y",a.y);button.addProperty("w",a.w);button.addProperty("h",a.h);button.addProperty("enabled",panel.preview.shaders.isEnabled());button.addProperty("visible",panel.preview.shaders.isVisible());out.add("shaderButton",button);
        out.addProperty("glError", org.lwjgl.opengl.GL11.glGetError());
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event)
    {
        if (!active) return;
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START && heldTicks > 0)
        {
            try
            {
                java.lang.reflect.Field field=org.lwjgl.input.Keyboard.class.getDeclaredField("keyDownBuffer");field.setAccessible(true);
                keyboard=(java.nio.ByteBuffer)field.get(null);
                int key=MC.gameSettings.keyBindForward.getKeyCode(); previousForward=keyboard.get(key);keyboard.put(key,(byte)1);injected=true;
            }
            catch(Exception error){throw new IllegalStateException(error);}
        }
        if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END && injected)
        {
            inputSamples++;maxForward=Math.max(maxForward,MC.player.movementInput.moveForward);maxMove=Math.max(maxMove,MC.player.moveForward);
            heldTicks--;releaseKey();
        }
    }
    private static void releaseKey()
    {
        if(injected){keyboard.put(MC.gameSettings.keyBindForward.getKeyCode(),previousForward);injected=false;}
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event)
    {
        if(!active||event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||frames.size()>=120)return;
        JsonObject f=new JsonObject();f.addProperty("paused",MC.isGamePaused());f.addProperty("raw",event.renderTickTime);
        f.addProperty("cameraX",BBSModClient.getCameraController().getPosition().x);
        f.addProperty("cameraY",BBSModClient.getCameraController().getPosition().y);
        f.addProperty("cameraZ",BBSModClient.getCameraController().getPosition().z);frames.add(f);
    }

    private static void reset() { localAttempts = localCanceled = localPosts = previewPosts = localMorphDraws = 0; }

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
