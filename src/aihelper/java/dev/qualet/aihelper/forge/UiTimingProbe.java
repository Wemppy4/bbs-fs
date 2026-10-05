package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.textures.UIPixelsEditor;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Samples the real screen boundary; optional wheel bytes go through Mouse.next and GuiScreen.handleInput. */
public final class UiTimingProbe
{
    private static final UiTimingProbe INSTANCE = new UiTimingProbe();
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static boolean registered, active, wheel;
    private static int requested, mismatches, withinTickChanges, durationDifferences;
    private static int injected, consumed, zoomChanges, withinTickZoomChanges, busyFrames;
    private static double maxError, previousZoom, savedZoomX, savedZoomY, savedShiftX, savedShiftY;
    private static float previousFraction;
    private static long previousTick;
    private static String error = "";
    private static JsonArray samples = new JsonArray();
    private static UIScreen screen;
    private static UIDashboard dashboard;
    private static Object panel;
    private static UIPixelsEditor pixels;
    private static ByteBuffer originalBuffer, injectedBuffer;
    private static final Map<Field, Object> mouseState = new LinkedHashMap<>();

    public static JsonObject run(JsonObject request) throws Exception
    {
        if (!MC.gameDir.getName().equals("run-forge1122-qa") || MC.player == null || MC.getIntegratedServer() == null
            || !"ai_test".equals(MC.getIntegratedServer().getFolderName()))
            throw new IllegalStateException("Timing probe requires run-forge1122-qa/ai_test");
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("start"))
        {
            if (active) throw new IllegalStateException("Stop the preceding timing capture first");
            if (!(MC.currentScreen instanceof UIScreen) || !(UIScreen.getCurrentMenu() instanceof UIDashboard))
                throw new IllegalStateException("Open a real Dashboard panel first");
            screen = (UIScreen) MC.currentScreen;
            dashboard = (UIDashboard) screen.getMenu(); panel = dashboard.getPanels().panel;
            requested = Math.max(2, Math.min(600, request.has("frames") ? request.get("frames").getAsInt()
                : request.has("N") ? request.get("N").getAsInt() : 60));
            wheel = request.has("wheel") && request.get("wheel").getAsBoolean();
            pixels = null;
            if (wheel)
            {
                if (Mouse.isGrabbed()) throw new IllegalStateException("Wheel capture requires an ungrabbed UI cursor");
                for (UIPixelsEditor candidate : dashboard.getRoot().getChildren(UIPixelsEditor.class))
                    if (candidate.canBeSeen()) { pixels = candidate; break; }
                if (pixels == null) throw new IllegalStateException("Open the actual texture pixels editor first");
                savedZoomX = pixels.scaleX.getZoom(); savedZoomY = pixels.scaleY.getZoom();
                savedShiftX = pixels.scaleX.getShift(); savedShiftY = pixels.scaleY.getShift();
                previousZoom = savedZoomX;
            }
            samples = new JsonArray(); error = ""; maxError = 0;
            mismatches = withinTickChanges = durationDifferences = 0;
            injected = consumed = zoomChanges = withinTickZoomChanges = busyFrames = 0;
            previousTick = Long.MIN_VALUE; previousFraction = Float.NaN;
            if (!registered) { MinecraftForge.EVENT_BUS.register(INSTANCE); registered = true; }
            active = true;
        }
        else if (op.equals("stop") || op.equals("cleanup")) finish();
        JsonObject out = new JsonObject(); out.addProperty("ok", error.isEmpty());
        out.addProperty("active", active); out.addProperty("done", !active && samples.size() == requested);
        out.addProperty("requested", requested); out.addProperty("collected", samples.size()); out.addProperty("error", error);
        out.addProperty("mismatches", mismatches); out.addProperty("maxFractionError", maxError);
        out.addProperty("withinTickFractionChanges", withinTickChanges); out.addProperty("durationDifferences", durationDifferences);
        out.addProperty("wheel", wheel); out.addProperty("wheelInjected", injected); out.addProperty("wheelConsumed", consumed);
        out.addProperty("zoomChangedFrames", zoomChanges); out.addProperty("withinTickZoomChanges", withinTickZoomChanges);
        out.addProperty("busyMouseFrames", busyFrames);
        out.addProperty("columns", "uiFraction,nativeFraction,frameDuration,uiTick,zoom,wheelConsumed");
        out.add("samples", new com.google.gson.JsonParser().parse(samples.toString()));
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void before(GuiScreenEvent.DrawScreenEvent.Pre event)
    {
        if (!active || !wheel || event.getGui() != screen) return;
        try
        {
            if (MC.currentScreen != screen || dashboard.getPanels().panel != panel)
                throw new IllegalStateException("Dashboard changed during capture");
            ByteBuffer buffer = (ByteBuffer) field(Mouse.class, "readBuffer").get(null);
            if (buffer.hasRemaining()) { busyFrames++; return; }
            for (String name : new String[]{"eventButton", "eventState", "event_dx", "event_dy", "event_dwheel",
                "event_x", "event_y", "event_nanos", "last_event_raw_x", "last_event_raw_y"})
            {
                Field member = field(Mouse.class, name); mouseState.put(member, member.get(null));
            }
            for (String name : new String[]{"verticalScroll", "lastScroll"})
            {
                Field member = field(Window.class, name); mouseState.put(member, member.get(null));
            }
            originalBuffer = buffer;
            float scale = BBSModClient.getGUIScale();
            int x = (int) ((pixels.area.x + pixels.area.w / 2) * scale);
            int y = MC.displayHeight - 1 - (int) ((pixels.area.y + pixels.area.h / 2) * scale);
            injectedBuffer = ByteBuffer.allocate(Mouse.EVENT_SIZE).order(buffer.order());
            injectedBuffer.put((byte) -1).put((byte) 0).putInt(x).putInt(y).putInt(120).putLong(System.nanoTime()).flip();
            field(Mouse.class, "readBuffer").set(null, injectedBuffer); injected++;
        }
        catch (Exception failure) { error = failure.toString(); finish(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void after(GuiScreenEvent.DrawScreenEvent.Post event)
    {
        if (!active) return;
        try
        {
            if (event.getGui() != screen || MC.currentScreen != screen || dashboard.getPanels().panel != panel)
                throw new IllegalStateException("Dashboard changed during capture");
            boolean delivered = injectedBuffer != null && !injectedBuffer.hasRemaining();
            if (delivered) consumed++;
            float fraction = dashboard.context.getTransition(), nativeFraction = MC.getRenderPartialTicks();
            float duration = MC.getTickLength(); long tick = dashboard.context.getTick();
            double difference = Math.abs(fraction - nativeFraction);
            maxError = Math.max(maxError, difference); if (difference > 0.00001) mismatches++;
            if (Math.abs(fraction - duration) > 0.00001) durationDifferences++;
            if (tick == previousTick && Math.abs(fraction - previousFraction) > 0.00001) withinTickChanges++;
            double zoom = pixels == null ? 0 : pixels.scaleX.getZoom();
            if (pixels != null && Math.abs(zoom - previousZoom) > 0.0000001)
            { zoomChanges++; if (tick == previousTick) withinTickZoomChanges++; }
            JsonArray sample = new JsonArray(); sample.add(fraction); sample.add(nativeFraction); sample.add(duration);
            sample.add(tick); sample.add(zoom); sample.add(delivered ? 1 : 0); samples.add(sample);
            previousTick = tick; previousFraction = fraction; previousZoom = zoom;
            restoreMouse();
            if (samples.size() >= requested) finish();
        }
        catch (Exception failure) { error = failure.toString(); finish(); }
    }

    private static Field field(Class<?> type, String name) throws Exception
    {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }

    private static void restoreMouse() throws Exception
    {
        if (originalBuffer != null) field(Mouse.class, "readBuffer").set(null, originalBuffer);
        for (Map.Entry<Field, Object> entry : mouseState.entrySet()) entry.getKey().set(null, entry.getValue());
        mouseState.clear(); originalBuffer = injectedBuffer = null;
    }

    private static void finish()
    {
        active = false;
        try { restoreMouse(); }
        catch (Exception failure) { error = failure.toString(); }
        if (pixels != null)
        {
            pixels.scaleX.set(savedShiftX, savedZoomX); pixels.scaleY.set(savedShiftY, savedZoomY);
            pixels = null;
        }
    }
}
