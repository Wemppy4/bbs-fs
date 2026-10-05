package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.List;

/** Optional OptiFine pack selection, applied between frames, outside all GL scopes. */
@Mod.EventBusSubscriber(modid = "bbs", value = Side.CLIENT)
public final class OptiFineShaderPacks
{
    private static final Class<?> SHADERS = find();
    private static String lastPack = "", pending;

    private static Class<?> find()
    {
        try { return Class.forName("net.optifine.shaders.Shaders"); }
        catch (ClassNotFoundException absent) { return null; }
    }

    public static boolean available() { return SHADERS != null; }

    private static Object call(String name, Class<?>[] types, Object... args)
    {
        try { return SHADERS.getMethod(name, types).invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("OptiFine " + name, error); }
    }

    private static Object call(String name) { return call(name, new Class<?>[0]); }

    public static String current()
    {
        if (!available()) return "";
        String pack = (String) call("getShaderPackName");
        return pack == null || pack.equals("OFF") ? "" : pack;
    }

    public static List<String> list()
    {
        List<String> result = new ArrayList<>();
        if (available()) for (Object value : (List<?>) call("listOfShaders"))
            if (!"OFF".equals(value)) result.add(value.toString());
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static void select(String pack)
    {
        if (available()) pending = pack;
    }

    public static boolean toggle()
    {
        String selected = pending == null ? current() : pending;
        if (!selected.isEmpty()) { lastPack = selected; select(""); }
        else if (!lastPack.isEmpty()) select(lastPack);
        else return false;
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || pending == null) return;
        String selected = pending, previous = current();
        pending = null;
        try
        {
            apply(selected);
            if (!selected.isEmpty() && !OptiFineShaders.isLoaded())
                throw new IllegalStateException("Could not load shader pack: " + selected);
            if (!selected.isEmpty()) lastPack = selected;
            else if (!previous.isEmpty()) lastPack = previous;
            call("storeConfig");
        }
        catch (RuntimeException error)
        {
            try { apply(previous); call("storeConfig"); }
            catch (RuntimeException restore) { error.addSuppressed(restore); }
            BBSMod.LOGGER.error("Could not switch OptiFine shader pack", error);
            if (UIScreen.getCurrentMenu() != null)
                UIScreen.getCurrentMenu().context.notifyError(UIKeys.UTILITY_SHADER_ERROR);
        }
    }

    private static void apply(String pack)
    {
        call("setShaderPack", new Class<?>[]{String.class}, pack.isEmpty() ? "OFF" : pack);
        call("uninit");
    }

    private OptiFineShaderPacks() {}
}
