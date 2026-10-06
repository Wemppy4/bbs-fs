package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.forge.ModelItemRenderer;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Method;

/** Opt-in regressions, restricted to the disposable QA client. */
public final class Feedback26Probe
{
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static ModelProperties editing;
    private static UIModelBlockEditorMenu editor;
    private static ItemStack previousHand;
    private static ModelForm actionForm;
    private static mchorse.bbs_mod.ui.film.UIFilmPanel filmPanel;
    private static mchorse.bbs_mod.film.Film previousFilm;
    private static int playerDraws;
    private static final Feedback26Probe OBSERVER = new Feedback26Probe();
    private static boolean observing;
    private static final String MODEL = "__feedback26_actions";

    public static JsonObject run(JsonObject request) throws Exception
    {
        if (!MC.gameDir.getName().equals("run-forge1122-qa") || MC.world == null)
            throw new IllegalStateException("Requires separate QA world");
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        JsonObject out = new JsonObject(); out.addProperty("ok", true);
        if (op.equals("item_state"))
        {
            ItemStack held = MC.player.getHeldItemMainhand();
            ItemStack cached = net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.ItemRenderer.class, MC.entityRenderer.itemRenderer, "itemStackMainHand", "field_187467_d");
            out.addProperty("held", held.serializeNBT().toString()); out.addProperty("cached", cached.serializeNBT().toString());
            out.addProperty("sameItem", held == cached); out.addProperty("tagsEqual", ItemStack.areItemStackTagsEqual(held, cached));
            out.addProperty("viewIsPlayer", MC.getRenderViewEntity() == MC.player); out.addProperty("perspective", MC.gameSettings.thirdPersonView);
            out.addProperty("hideGUI", MC.gameSettings.hideGUI); out.addProperty("paused", MC.isGamePaused());
        }
        else if (op.equals("input"))
        {
            GuiScreen previous = MC.currentScreen;
            UIBaseMenu menu = new UIBaseMenu() {};
            UITextbox text = new UITextbox(); text.relative(menu.getRoot()).xy(40,40).w(300);
            menu.getRoot().add(text); UIScreen.open(menu); menu.context.focus(text);
            Method key = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class, "keyTyped", "func_73869_a", char.class, int.class);
            for (char c : "testЁ".toCharArray()) key.invoke(MC.currentScreen, c, Keyboard.KEY_NONE);
            out.addProperty("typed", text.getText());
            key.invoke(MC.currentScreen, '\b', Keyboard.KEY_BACK);
            out.addProperty("backspace", text.getText());
            key.invoke(MC.currentScreen, '\b', Keyboard.KEY_BACK);
            out.addProperty("backspaceTwice", text.getText());
            key.invoke(MC.currentScreen, (char)127, Keyboard.KEY_DELETE);
            out.addProperty("delete", text.getText());
            MC.displayGuiScreen(previous);
        }
        else if (op.equals("particles"))
        {
            out.addProperty("destroySuppressed", CommonProxy.MODEL_BLOCK.addDestroyEffects(MC.world, MC.player.getPosition(), MC.effectRenderer));
            out.addProperty("hitSuppressed", CommonProxy.MODEL_BLOCK.addHitEffects(CommonProxy.MODEL_BLOCK.getDefaultState(), MC.world, null, MC.effectRenderer));
        }
        else if (op.equals("item"))
        {
            if (!observing) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(OBSERVER); observing = true; }
            playerDraws = 0;
            previousHand = MC.player.getHeldItemMainhand();
            ItemStack item = new ItemStack(CommonProxy.MODEL_BLOCK);
            MC.player.setHeldItem(EnumHand.MAIN_HAND, item);
            editing = BBSModClient.getItemStackProperties(item);
            BlockForm form = new BlockForm(); form.blockState.set(Blocks.GOLD_BLOCK.getDefaultState()); editing.setForm(form);
            editor = new UIModelBlockEditorMenu(editing); UIScreen.open(editor);
            editor.sectionFp.getChildren(UIPropTransform.class).get(0).setT(null, 2, 3, 4);
            out.addProperty("editorX", editing.getTransformFirstPerson().translate.x);
            out.addProperty("sameStackX", BBSModClient.getItemStackProperties(item).getTransformFirstPerson().translate.x);
            out.addProperty("copiedStackX", BBSModClient.getItemStackProperties(item.copy()).getTransformFirstPerson().translate.x);
            Method section = UIModelBlockEditorMenu.class.getDeclaredMethod("setSection", mchorse.bbs_mod.ui.framework.elements.UIElement.class);
            section.setAccessible(true); section.invoke(editor, editor.sectionTp);
        }
        else if (op.equals("actions") || op.equals("film"))
        {
            MolangParser parser = new MolangParser(); Animations animations = new Animations(parser);
            for (String name : new String[]{"idle","running","sprinting","crouching","crouching_idle","falling","flying","flying_idle","swimming","swimming_idle","riding","riding_idle"})
            { Animation a = new Animation(name, parser); a.setLength(2); animations.add(a); }
            BBSModClient.getModels().models.put(MODEL, new ModelInstance(MODEL, new Model(parser), animations, null));
            actionForm = new ModelForm(); actionForm.model.set(MODEL);
            ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(actionForm);
            StubEntity entity = new StubEntity(); entity.setOnGround(true);
            actionForm.update(entity); out.addProperty("idle", active(renderer));
            entity.setPosition(1,0,0); actionForm.update(entity); out.addProperty("walk", active(renderer));
            entity.setSprinting(true); actionForm.update(entity); out.addProperty("sprint", active(renderer));
            entity.setSprinting(false); entity.setSneaking(true); actionForm.update(entity); out.addProperty("crouch", active(renderer));
            if (op.equals("actions"))
            {
                Morph.getMorph(MC.player).setForm(actionForm);
                MC.displayGuiScreen(null);
            }
            else
            {
                mchorse.bbs_mod.film.Film film = new mchorse.bbs_mod.film.Film(); film.setId("__feedback26_film");
                mchorse.bbs_mod.film.replays.Replay replay = film.replays.addReplay(); replay.form.set(actionForm);
                replay.keyframes.x.insert(0, MC.player.posX); replay.keyframes.x.insert(1200, MC.player.posX + 120);
                replay.keyframes.y.insert(0, MC.player.posY); replay.keyframes.z.insert(0, MC.player.posZ + 4);
                replay.keyframes.grounded.insert(0, 1D);
                mchorse.bbs_mod.camera.clips.overwrite.IdleClip clip = new mchorse.bbs_mod.camera.clips.overwrite.IdleClip();
                clip.duration.set(1200); clip.position.get().point.set(MC.player.posX, MC.player.posY + 1, MC.player.posZ - 3);
                clip.position.get().angle.yaw = 180; film.camera.addClip(clip);
                mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = BBSModClient.getDashboard();
                filmPanel = dashboard.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
                previousFilm = filmPanel.getData();
                UIScreen.open(dashboard); dashboard.getPanels().setPanel(filmPanel);
                filmPanel.fill(film); filmPanel.togglePlayback();
            }
        }
        else if (op.equals("film_control"))
        {
            if (filmPanel.isRunning()) filmPanel.togglePlayback();
            filmPanel.replayEditor.setReplay(filmPanel.getData().replays.getList().get(0));
            filmPanel.getController().setPov(2);
            filmPanel.getController().toggleControl();
        }
        else if (op.equals("film_sprint") || op.equals("film_walk") || op.equals("film_idle") || op.equals("film_crouch"))
        {
            MC.player.setSprinting(op.equals("film_sprint"));
            MC.player.setSneaking(op.equals("film_crouch"));
            MC.player.movementInput.sneak = op.equals("film_crouch");
            if (op.equals("film_walk")) MC.player.setPosition(MC.player.posX + 1, MC.player.posY, MC.player.posZ);
            Morph.getMorph(MC.player).update();
        }
        else if (op.equals("film_release")) filmPanel.getController().toggleControl();
        else if (op.equals("item_cache"))
        {
            for (int i = 0; i < 25; i++) ModelItemRenderer.INSTANCE.tick();
            out.addProperty("retained", editing == BBSModClient.getItemStackProperties(MC.player.getHeldItemMainhand().copy()));
            ItemStack gun = new ItemStack(CommonProxy.GUN_ITEM);
            mchorse.bbs_mod.blocks.entities.ModelProperties gunProperties = BBSModClient.getItemStackProperties(gun);
            gunProperties.getTransformThirdPerson().translate.x = 7;
            out.addProperty("gunCopyX", BBSModClient.getItemStackProperties(gun.copy()).getTransformThirdPerson().translate.x);
            gun.setTagCompound(new net.minecraft.nbt.NBTTagCompound()); gun.getTagCompound().setString("probe", "changed");
            out.addProperty("changedNbtIndependent", BBSModClient.getItemStackProperties(gun) != gunProperties);
        }
        else if (op.equals("item_mode"))
        {
            String name = request.get("mode").getAsString();
            mchorse.bbs_mod.ui.framework.elements.UIElement target = name.equals("fp") ? editor.sectionFp : name.equals("tp") ? editor.sectionTp : name.equals("gui") ? editor.sectionInventory : editor.sectionDefault;
            Method section = UIModelBlockEditorMenu.class.getDeclaredMethod("setSection", mchorse.bbs_mod.ui.framework.elements.UIElement.class);
            section.setAccessible(true); section.invoke(editor, target);
            if (request.has("x")) target.getChildren(UIPropTransform.class).get(0).setT(null, request.get("x").getAsFloat(), 0, 0);
        }
        else if (op.equals("cleanup"))
        {
            if (filmPanel != null) { if (filmPanel.getController().isControlling()) filmPanel.getController().toggleControl(); if (filmPanel.isRunning()) filmPanel.togglePlayback(); filmPanel.fill(previousFilm); filmPanel = null; }
            MC.displayGuiScreen(null);
            if (previousHand != null) MC.player.setHeldItem(EnumHand.MAIN_HAND, previousHand);
            Morph.getMorph(MC.player).setForm(null);
            BBSModClient.getModels().models.remove(MODEL);
            editor = null; editing = null; previousHand = null; actionForm = null;
        }
        if (filmPanel != null)
        {
            java.lang.reflect.Field field = mchorse.bbs_mod.ui.film.controller.UIFilmController.class.getDeclaredField("editorController"); field.setAccessible(true);
            mchorse.bbs_mod.film.BaseFilmController controller = (mchorse.bbs_mod.film.BaseFilmController) field.get(filmPanel.getController());
            mchorse.bbs_mod.forms.entities.IEntity entity = controller.entities.values().iterator().next();
            ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(entity.getForm());
            renderer.ensureAnimator(0);
            out.addProperty("filmTick", filmPanel.getCursor()); out.addProperty("actorAge", entity.getAge());
            out.addProperty("actorX", entity.getX()); out.addProperty("actorAction", active(renderer));
            out.addProperty("actorAnimatorX", ((Animator) renderer.getAnimator()).prevX);
            out.addProperty("playing", filmPanel.getController().isPlaying());
            out.addProperty("controlling", filmPanel.getController().isControlling());
            out.addProperty("actorIsPlayer", entity == Morph.getMorph(MC.player).entity);
            out.addProperty("actorIsTickedForm", entity.getForm() == Morph.getMorph(MC.player).getForm());
        }
        if (editor != null)
        {
            out.addProperty("perspective", MC.gameSettings.thirdPersonView);
            out.addProperty("viewIsPlayer", MC.getRenderViewEntity() == MC.player);
            out.addProperty("renderManagerViewIsPlayer", MC.getRenderManager().renderViewEntity == MC.player);
            out.addProperty("playerIsUser", MC.player.isUser());
            out.addProperty("shouldHide", mchorse.bbs_mod.client.renderer.MorphRenderer.shouldHide(MC.player));
            out.addProperty("livePropertiesSame", editing == BBSModClient.getItemStackProperties(MC.player.getHeldItemMainhand()));
            out.addProperty("playerDraws", playerDraws);
        }
        if (actionForm != null)
        {
            ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(actionForm);
            out.addProperty("active", active(renderer));
            Animator animator = (Animator) renderer.getAnimator();
            out.addProperty("animationPrevX", animator.prevX);
            out.addProperty("playerX", MC.player.posX);
            out.addProperty("playerAge", MC.player.ticksExisted);
        }
        return out;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void playerDraw(net.minecraftforge.client.event.RenderLivingEvent.Pre<?> event)
    {
        if (event.getEntity() == MC.player) playerDraws++;
    }

    private static String active(ModelFormRenderer renderer)
    {
        Animator animator = (Animator) renderer.getAnimator();
        return animator == null || animator.active == null ? null : animator.active.action.id;
    }
}
