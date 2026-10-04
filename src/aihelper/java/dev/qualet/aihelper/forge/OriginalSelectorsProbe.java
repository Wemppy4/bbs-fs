package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.NativeFormDraw;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import mchorse.bbs_mod.selectors.*;
import mchorse.bbs_mod.ui.dashboard.DashboardPanelRegistry;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.selectors.UISelectorsOverlayPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.nio.file.*;

/** Selector matching, native capability/update/render boundaries and the actual Dashboard editor. */
public final class OriginalSelectorsProbe
{
    private static MapType uiBackup;
    private static byte[] fileBackup;
    private static GuiScreen previousScreen;

    public static JsonObject run(JsonObject request) throws Exception
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getIntegratedServer() == null || !"ai_test".equals(mc.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("Selectors fixture is restricted to the ai_test world");
        if (request.has("test") && request.get("test").getAsBoolean()) return test();
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("begin_ui"))
        {
            if (uiBackup != null) throw new IllegalStateException("UI fixture already active");
            uiBackup = BBSModClient.getSelectors().toData();
            Path path = BBSMod.getSettingsPath("selectors.json").toPath();
            fileBackup = Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
            previousScreen = mc.currentScreen;
            EntitySelectors selectors = BBSModClient.getSelectors();
            selectors.selectors.clear(); selectors.selectors.add(selector()); selectors.update();
            UIDashboard dashboard = BBSModClient.getDashboard();
            UIScreen.open(dashboard);
            for (DashboardPanelRegistry.PinnedAction entry : DashboardPanelRegistry.pinned())
                if (entry.id.equals("selectors")) entry.action.accept(dashboard.context);
        }
        if (op.equals("finish_ui"))
        {
            if (uiBackup == null) throw new IllegalStateException("No selectors UI fixture is active");
            JsonObject out = new JsonObject(); out.addProperty("ok", true);
            try
            {
                EntitySelectors disk = new EntitySelectors(); disk.read();
                out.addProperty("savedName", disk.selectors.isEmpty() ? "" : disk.selectors.get(0).name);
                out.addProperty("savedEnabled", !disk.selectors.isEmpty() && disk.selectors.get(0).enabled);
            }
            finally
            {
                mc.displayGuiScreen(previousScreen);
                if (uiBackup != null) BBSModClient.getSelectors().fromData(uiBackup);
                restoreFile(fileBackup);
                uiBackup = null; fileBackup = null; previousScreen = null;
            }
            return out;
        }
        return snapshot();
    }

    private static EntitySelector selector()
    {
        EntitySelector selector = new EntitySelector(); selector.entity = new ResourceLocation("minecraft:pig");
        BlockForm block = new BlockForm(); block.blockState.set(Blocks.GOLD_BLOCK.getDefaultState()); selector.form = block;
        return selector;
    }

    private static JsonObject test() throws Exception
    {
        Minecraft mc = Minecraft.getMinecraft();
        EntitySelectors selectors = BBSModClient.getSelectors();
        MapType backup = selectors.toData();
        String whitelist = BBSSettings.entitySelectorsPropertyWhitelist.get();
        Path path = BBSMod.getSettingsPath("selectors.json").toPath();
        byte[] disk = Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        JsonObject out = new JsonObject(); out.addProperty("ok", true);
        try
        {
            EntityPig pig = new EntityPig(mc.world); pig.setPosition(mc.player.posX, mc.player.posY, mc.player.posZ);
            pig.setCustomNameTag("BBS selector fixture"); pig.setSaddled(true);
            pig.getEntityData().setTag("selector_probe", JsonToNBT.getTagFromJson("{a:1,b:2}"));
            EntitySelector rule = selector(); rule.name = "BBS selector fixture";
            rule.nbt = JsonToNBT.getTagFromJson("{ForgeData:{selector_probe:{a:1}},Saddle:1b}");
            out.addProperty("entityNameNbtMatch", rule.matches(pig));
            rule.nbt.setBoolean("Saddle", false); out.addProperty("compoundAndSiblingMismatch", !rule.matches(pig));
            rule.nbt.setBoolean("Saddle", true);
            rule.name = "different"; out.addProperty("nameMismatch", !rule.matches(pig)); rule.name = "BBS selector fixture";
            rule.enabled = false; out.addProperty("disabledMismatch", !rule.matches(pig)); rule.enabled = true;
            EntitySelector fallback = selector(); fallback.form = new MobForm(); ((MobForm)fallback.form).mobID.set("minecraft:pig");
            selectors.selectors.clear(); selectors.selectors.add(rule); selectors.selectors.add(fallback); selectors.update();
            out.addProperty("firstRuleWins", selectors.getSelectorFor(pig) == rule);
            selectors.save(); EntitySelectors loaded = new EntitySelectors(); loaded.read();
            out.addProperty("repositoryRoundtrip", BaseType.equals(selectors.toData(), loaded.toData()));
            loaded.read(); out.addProperty("reloadReplaces", loaded.selectors.size() == 2);
            SelectorOwner owner = SelectorOwnerCapability.get(pig);
            out.addProperty("nativeCapability", owner != null);
            if (owner == null) throw new IllegalStateException("Native AttachCapabilitiesEvent did not create selector owner");
            owner.check(); out.addProperty("independentForm", owner.getForm() instanceof BlockForm && owner.getForm() != rule.form);
            owner.entity.setPrevPrevBodyYaw(987); pig.onUpdate();
            out.addProperty("nativeTick", owner.entity.getPrevPrevBodyYaw() != 987);
            BBSSettings.entitySelectorsPropertyWhitelist.set("CustomName,Name,Saddle,ForgeData");
            pig.setSaddled(false); pig.ticksExisted += 11; owner.check();
            out.addProperty("nbtUpdatesSelection", owner.getForm() instanceof MobForm);
            pig.setSaddled(true); pig.ticksExisted += 11; owner.check();
            out.addProperty("nbtRestoresSelection", owner.getForm() instanceof BlockForm);
            rule.enabled = false; selectors.update(); owner.check();
            rule.enabled = true; selectors.update(); owner.check();
            out.addProperty("sameTickEdits", owner.getForm() instanceof BlockForm);
            out.add("nativeRender", render(pig, false));
            rule.enabled = false; selectors.update(); owner.check();
            out.add("recursiveMobRender", render(pig, true));
            selectors.selectors.clear(); selectors.update(); owner.check();
            out.addProperty("clearRestoresVanilla", owner.getForm() == null);
            out.addProperty("dashboardPinned", DashboardPanelRegistry.pinned().stream().anyMatch(entry -> entry.id.equals("selectors")));
            out.addProperty("glError", GL11.glGetError());
        }
        finally
        {
            selectors.fromData(backup); BBSSettings.entitySelectorsPropertyWhitelist.set(whitelist); restoreFile(disk);
        }
        return out;
    }

    public static final class RenderObserver
    {
        int replaced, nativeMob;
        @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
        public void observe(RenderLivingEvent.Pre<?> event)
        {
            if (event.isCanceled()) this.replaced++; else this.nativeMob++;
        }
    }

    private static JsonObject render(EntityPig pig, boolean recursive)
    {
        final int size = 160;
        JsonObject out = new JsonObject();
        RenderObserver observer = new RenderObserver(); MinecraftForge.EVENT_BUS.register(observer);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), row = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        IntBuffer viewport = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        FloatBuffer projection = BufferUtils.createFloatBuffer(16), model = BufferUtils.createFloatBuffer(16), clear = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model); GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), fog = GL11.glIsEnabled(GL11.GL_FOG);
        mchorse.bbs_mod.graphics.Framebuffer target = BBSModClient.getFramebuffers().getFormFramebuffers().get(size, size);
        try (NativeFormDraw.State ignored = new NativeFormDraw.State(); OptiFineShaders.LocalPass local = OptiFineShaders.localPass())
        {
            target.apply(); GL11.glDisable(GL11.GL_SCISSOR_TEST); GlStateManager.disableFog(); GlStateManager.disableLighting();
            GlStateManager.depthMask(true); GlStateManager.clearColor(0,0,0,0); target.clear();
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity(); GlStateManager.ortho(-2,2,-1,3,-20,20);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity(); GlStateManager.rotate(15,1,0,0); GlStateManager.rotate(30,0,1,0);
            Minecraft.getMinecraft().getRenderManager().renderEntity(pig, 0,0,0,0,0,false);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            ByteBuffer pixels = BufferUtils.createByteBuffer(size*size*4); GL11.glReadPixels(0,0,size,size,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            int count=0; for(int i=0;i<pixels.limit();i+=4) if((pixels.get(i+3)&255)>20) count++;
            out.addProperty("pixels", count); out.addProperty("replaced", observer.replaced); out.addProperty("nativeMob", observer.nativeMob);
            out.addProperty("recursionGuard", !recursive || observer.nativeMob > 0 && observer.replaced == 1);
        }
        finally
        {
            MinecraftForge.EVENT_BUS.unregister(observer);
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GL11.glLoadMatrix(projection);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GL11.glLoadMatrix(model);
            GlStateManager.matrixMode(matrixMode);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
            if(scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); if(fog) GlStateManager.enableFog();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
            BBSModClient.getFramebuffers().getFormFramebuffers().release(target);
        }
        return out;
    }

    private static JsonObject snapshot()
    {
        JsonObject out = new JsonObject(); out.addProperty("ok",true);
        UIBaseMenu menu = UIScreen.getCurrentMenu();
        if(menu == null) return out;
        for(UISelectorsOverlayPanel panel : menu.getRoot().getChildren(UISelectorsOverlayPanel.class))
        {
            if(!panel.canBeSeen()) continue;
            out.addProperty("panel",panel.getClass().getSimpleName()); out.addProperty("rules",panel.selectors.getList().size());
            out.add("name", area(panel.name)); out.add("entity",area(panel.entity)); out.add("enabled",area(panel.enabled)); out.add("close",area(panel.close));
            out.addProperty("nameText",panel.name.getText()); out.addProperty("entityText",panel.entity.getText());
        }
        out.addProperty("glError",GL11.glGetError()); return out;
    }

    private static JsonObject area(UIElement element)
    {
        JsonObject out=new JsonObject(); out.addProperty("x",element.area.x); out.addProperty("y",element.area.y);
        out.addProperty("w",element.area.w); out.addProperty("h",element.area.h); return out;
    }

    private static void restoreFile(byte[] data) throws Exception
    {
        Path path = BBSMod.getSettingsPath("selectors.json").toPath();
        if(data == null) Files.deleteIfExists(path); else Files.write(path,data);
    }
}
