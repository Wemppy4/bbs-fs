package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.StatePlayer;
import mchorse.bbs_mod.graphics.window.InputCodes;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IntHashMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Real LWJGL event -> registered Forge listener -> native fragmented packet roundtrip.
 * A scoped self-tracker makes the existing connection an observer without a second game process. */
public final class OriginalHotkeysProbe
{
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static volatile boolean active;
    private static boolean restoreScreen;
    private static volatile int requested, completed;
    private static volatile String error;
    private static Form previousMorph;
    private static ItemStack previousMain, previousOff;
    private static GuiScreen previousScreen;
    private static List<Form> recent;
    private static List<UserFormCategory> users;
    private static EntityTrackerEntry tracking;
    private static boolean addedTracker;

    public static JsonObject run(JsonObject request) throws Exception
    {
        if (!MC.gameDir.getName().equals("run-forge1122-qa") || MC.player == null || MC.getIntegratedServer() == null
            || !"ai_test".equals(MC.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("Hotkey probe requires the separate run-forge1122-qa/ai_test client");
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("prepare"))
        {
            if (active || requested != completed) throw new IllegalStateException("Cleanup the preceding fixture");
            previousScreen = MC.currentScreen; restoreScreen = true; MC.displayGuiScreen(null);
            FormCategory category = BBSModClient.getFormCategories().getRecentForms().getCategories().get(0);
            recent = new ArrayList<>(category.getForms()); category.getDirectForms().clear();
            BlockForm recentForm = form("qa_recent"); recentForm.hotkey.set(InputCodes.KEY_K); category.getDirectForms().add(recentForm);
            users = new ArrayList<>(BBSModClient.getFormCategories().getUserForms().categories);
            BBSModClient.getFormCategories().getUserForms().categories.clear();
            for (String name : new String[]{"qa_user_first", "qa_user_second"})
            {
                UserFormCategory user = new UserFormCategory(IKey.raw(name), new ValueBoolean("visible", true), BBSModClient.getFormCategories().getUserForms());
                BlockForm value = form(name); value.hotkey.set(InputCodes.KEY_K); user.getDirectForms().add(value);
                BBSModClient.getFormCategories().getUserForms().categories.add(user);
            }
            active = true;
            schedule(() ->
            {
                EntityPlayerMP player = player();
                previousMorph = FormUtils.copy(Morph.getMorph(player).getForm());
                previousMain = player.getHeldItemMainhand().copy(); previousOff = player.getHeldItemOffhand().copy();
                BlockForm morph = stateForm("qa_morph");
                ServerNetwork.sendMorphToTracked(player, morph);
                player.setHeldItem(EnumHand.MAIN_HAND, model(stateForm("qa_main")));
                player.setHeldItem(EnumHand.OFF_HAND, gun(stateForm("qa_off")));
                player.inventoryContainer.detectAndSendChanges();
                Field field = ReflectionHelper.findField(EntityTracker.class, "trackedEntityHashTable", "field_72794_c");
                IntHashMap<EntityTrackerEntry> entries = (IntHashMap<EntityTrackerEntry>) field.get(player.getServerWorld().getEntityTracker());
                tracking = entries.lookup(player.getEntityId());
                if (tracking == null) throw new IllegalStateException("Real player has no native tracker");
                addedTracker = tracking.trackingPlayers.add(player);
            });
        }
        else if (op.equals("cleanup"))
        {
            if (active)
            {
                FormCategory category = BBSModClient.getFormCategories().getRecentForms().getCategories().get(0);
                category.getDirectForms().clear(); category.getDirectForms().addAll(recent);
                BBSModClient.getFormCategories().getUserForms().categories.clear(); BBSModClient.getFormCategories().getUserForms().categories.addAll(users);
                schedule(() ->
                {
                    EntityPlayerMP player = player();
                    if (tracking != null && addedTracker) tracking.trackingPlayers.remove(player);
                    tracking = null; addedTracker = false;
                    ServerNetwork.sendMorphToTracked(player, previousMorph);
                    player.setHeldItem(EnumHand.MAIN_HAND, previousMain); player.setHeldItem(EnumHand.OFF_HAND, previousOff);
                    player.inventoryContainer.detectAndSendChanges();
                    active = false;
                });
            }
        }
        else if (!op.equals("status"))
        {
            if (!active || requested != completed) throw new IllegalStateException("Wait for the hotkey fixture");
            if (op.equals("clear")) clear();
            else if (op.equals("main")) { clear(); morph().states.getAllTyped().get(0).keybind.set(0); }
            else if (op.equals("off")) { clear(); main().states.getAllTyped().get(0).keybind.set(0); }
            else if (op.equals("user")) BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getDirectForms().clear();
            else if (op.equals("gui")) MC.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
            else if (op.equals("close_gui")) MC.displayGuiScreen(null);
            else if (op.equals("key"))
            {
                int code = Keyboard.getKeyIndex(request.get("key").getAsString().toUpperCase(java.util.Locale.ROOT));
                keyboard(code, !request.has("up") || !request.get("up").getAsBoolean(), request.has("repeat") && request.get("repeat").getAsBoolean());
            }
            else throw new IllegalArgumentException("Unknown hotkey operation " + op);
        }
        if (!active && requested == completed && op.equals("status") && restoreScreen)
        { MC.displayGuiScreen(previousScreen); previousScreen = null; restoreScreen = false; }
        JsonObject out = new JsonObject(); out.addProperty("ok", error == null); out.addProperty("active", active);
        out.addProperty("requested", requested); out.addProperty("completed", completed); if (error != null) out.addProperty("error", error);
        out.addProperty("morph", name(morph())); out.addProperty("main", name(main())); out.addProperty("off", name(off()));
        out.addProperty("morphStates", count(morph())); out.addProperty("mainStates", count(main())); out.addProperty("offStates", count(off()));
        out.addProperty("nativeL", Keyboard.KEY_L); out.addProperty("storedL", InputCodes.fromNative(Keyboard.KEY_L));
        out.addProperty("screen", MC.currentScreen == null ? "none" : MC.currentScreen.getClass().getSimpleName());
        return out;
    }

    private interface Work { void run() throws Exception; }
    private static void schedule(Work work)
    {
        if (requested != completed) throw new IllegalStateException("Server operation still active");
        int task = ++requested; error = null;
        MC.getIntegratedServer().addScheduledTask(() ->
        {
            try { work.run(); } catch (Throwable failure) { error = failure.toString(); failure.printStackTrace(); }
            finally { completed = task; }
        });
    }
    private static EntityPlayerMP player() { return MC.getIntegratedServer().getPlayerList().getPlayerByUUID(MC.player.getUniqueID()); }
    private static String name(Form form) { return form == null ? "none" : form.name.get(); }
    private static Form morph() { Morph morph = Morph.getMorph(MC.player); return morph == null ? null : morph.getForm(); }
    private static Form main() { ModelProperties p = BBSModClient.getItemStackProperties(MC.player.getHeldItemMainhand()); return p == null ? null : p.getForm(); }
    private static Form off() { ModelProperties p = BBSModClient.getItemStackProperties(MC.player.getHeldItemOffhand()); return p == null ? null : p.getForm(); }
    private static int count(Form form) throws Exception
    {
        if (form == null) return 0;
        Field field = Form.class.getDeclaredField("statePlayers"); field.setAccessible(true);
        return ((List<StatePlayer>) field.get(form)).size();
    }
    private static void clear() { for (Form form : new Form[]{morph(), main(), off()}) if (form != null) form.clearStatePlayers(); }
    private static BlockForm form(String name) { BlockForm form = new BlockForm(); form.name.set(name); form.blockState.set(Blocks.GOLD_BLOCK.getDefaultState()); return form; }
    private static BlockForm stateForm(String name)
    {
        BlockForm form = form(name); AnimationState state = form.states.addState();
        state.id.set(name); state.customId.set(name); state.keybind.set(InputCodes.KEY_L); state.duration.set(1200); return form;
    }
    private static ItemStack model(Form form)
    {
        ModelProperties properties = new ModelProperties(); properties.setForm(form);
        ItemStack stack = new ItemStack(CommonProxy.MODEL_BLOCK); NBTTagCompound root = new NBTTagCompound(), tile = new NBTTagCompound();
        tile.setTag("Properties", DataStorageUtils.toNbt(properties.toData())); root.setTag("BlockEntityTag", tile); stack.setTagCompound(root); return stack;
    }
    private static ItemStack gun(Form form)
    {
        GunProperties properties = new GunProperties(); properties.setForm(form);
        ItemStack stack = new ItemStack(CommonProxy.GUN_ITEM); NBTTagCompound root = new NBTTagCompound(); root.setTag("GunData", DataStorageUtils.toNbt(properties.toData())); stack.setTagCompound(root); return stack;
    }
    /** Forge's KeyInputEvent reads LWJGL's current record. Restore it immediately after dispatch. */
    private static void keyboard(int key, boolean pressed, boolean repeat) throws Exception
    {
        if (key == Keyboard.KEY_NONE) throw new IllegalArgumentException("Unknown native key");
        Field current = Keyboard.class.getDeclaredField("current_event"); current.setAccessible(true); Object event = current.get(null);
        Field keyField = event.getClass().getDeclaredField("key"), stateField = event.getClass().getDeclaredField("state"), repeatField = event.getClass().getDeclaredField("repeat");
        keyField.setAccessible(true); stateField.setAccessible(true); repeatField.setAccessible(true);
        int oldKey = keyField.getInt(event); boolean oldState = stateField.getBoolean(event), oldRepeat = repeatField.getBoolean(event);
        try
        {
            keyField.setInt(event, key); stateField.setBoolean(event, pressed); repeatField.setBoolean(event, repeat);
            MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
        }
        finally { keyField.setInt(event, oldKey); stateField.setBoolean(event, oldState); repeatField.setBoolean(event, oldRepeat); }
    }
}
