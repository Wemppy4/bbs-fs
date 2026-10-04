package mchorse.bbs_mod.fonts.nativefonts;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Asynchronous, version-pinned default font lifecycle. Downloads and checksums never
 * run on the render thread. Temporary native text is always accompanied by visible status. */
public final class NativeDefaultFont
{
    public enum State { NEW, PREPARING, READY, ERROR }
    private static final NativeDefaultFont INSTANCE = new NativeDefaultFont();
    private static volatile State state = State.NEW;
    private static volatile String detail = "";
    private static volatile BitmapFontData pending;
    private static NativeBitmapFontRenderer renderer;
    private static boolean registered, needsRelayout;
    private static int bannerHeight;
    private NativeDefaultFont() {}
    public static State state() { return state; }
    public static String detail() { return detail; }
    public static Path cacheDirectory()
    { return new File(BBSMod.getAssetsFolder().getParentFile(), "runtime/fonts-1.20.4").toPath(); }

    /** Call from client init. renderer() also starts preparation on first BBS UI access. */
    public static synchronized void prepare()
    {
        if (!registered)
        {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            ((net.minecraft.client.resources.IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(manager -> reloadAtlases());
            registered = true;
        }
        if (state == State.PREPARING || state == State.READY) return;
        state = State.PREPARING;
        detail = "Проверка локального кэша";
        final Path folder = cacheDirectory();
        Thread worker = new Thread(() ->
        {
            try
            {
                FontAssetCache cache = new FontAssetCache(folder);
                cache.prepare(message -> detail = message);
                detail = "Чтение bitmap и Unihex";
                pending = BitmapFontData.load(cache);
                detail = "";
                state = State.READY;
            }
            catch (Exception e)
            {
                pending = null;
                detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                state = State.ERROR;
                if (BBSMod.LOGGER != null) BBSMod.LOGGER.error("Cannot prepare exact Minecraft 1.20.4 font", e);
            }
        }, "BBS-default-font-assets");
        worker.setDaemon(true);
        worker.start();
    }
    /** Client-thread only. The renderer uploads glyphs lazily when actually drawing. */
    public static FontRenderer renderer()
    {
        if (state == State.NEW) prepare();
        if (renderer == null && state == State.READY && pending != null)
        {
            renderer = new NativeBitmapFontRenderer(pending);
            pending = null;
            needsRelayout = true;
        }
        if (renderer != null) renderer.setBidiFlag(Minecraft.getMinecraft().fontRenderer.getBidiFlag());
        return renderer == null ? Minecraft.getMinecraft().fontRenderer : renderer;
    }
    /** Resource reload: source pixels stay immutable; GPU atlases are rebuilt on demand. */
    public static void reloadAtlases()
    {
        if (renderer != null)
        {
            pending = renderer.getData();
            renderer.close(); renderer = null;
        }
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || state != State.READY) return;
        renderer();
        if (!needsRelayout) return;
        needsRelayout = false;
        /* Widths change from temporary 1.12 Unicode metrics to the original bitmap metrics. */
        if (Minecraft.getMinecraft().currentScreen instanceof UIScreen)
            Minecraft.getMinecraft().currentScreen.initGui();
    }
    @SubscribeEvent public void drawGui(GuiScreenEvent.DrawScreenEvent.Post event) { drawStatus(); }
    @SubscribeEvent public void drawOverlay(RenderGameOverlayEvent.Post event)
    {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && Minecraft.getMinecraft().currentScreen == null) drawStatus();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void retryClick(GuiScreenEvent.MouseInputEvent.Pre event)
    {
        if (state != State.ERROR || Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int x = Mouse.getEventX() * resolution.getScaledWidth() / mc.displayWidth;
        int y = resolution.getScaledHeight() - Mouse.getEventY() * resolution.getScaledHeight() / mc.displayHeight - 1;
        if (x >= 4 && x <= resolution.getScaledWidth() - 4 && y >= resolution.getScaledHeight() - bannerHeight - 4 && y <= resolution.getScaledHeight() - 4)
        {
            event.setCanceled(true);
            prepare();
        }
    }
    private static void drawStatus()
    {
        State current = state;
        if (current != State.PREPARING && current != State.ERROR) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        ScaledResolution resolution = new ScaledResolution(mc);
        int width = resolution.getScaledWidth(), height = resolution.getScaledHeight();
        boolean failure = current == State.ERROR;
        String title = failure ? "Шрифт 1.20.4: ошибка. Нажмите здесь, чтобы повторить" : "Подготовка шрифта 1.20.4…";
        List<String> lines = font.listFormattedStringToWidth(detail, Math.max(40, width - 20));
        bannerHeight = 15 + lines.size() * 10;
        int y = height - bannerHeight - 4;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL20.glUseProgram(0);
        GlStateManager.disableDepth();
        try
        {
            Gui.drawRect(4, y, width - 4, height - 4, failure ? 0xee451d26 : 0xee202c36);
            font.drawStringWithShadow(title, 9, y + 4, failure ? 0xffffb7b7 : 0xffd3e8ff);
            y += 15;
            for (String line : lines) { font.drawStringWithShadow(line, 9, y, 0xffeeeeee); y += 10; }
        }
        finally
        {
            if (depth) GlStateManager.enableDepth();
            if (!blend) GlStateManager.disableBlend();
            GL20.glUseProgram(program);
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
