package mchorse.bbs_mod.api;

import mchorse.bbs_mod.BBSMod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native Forge discovery; no client class is resolved while collecting ASM metadata. */
public final class AddonLifecycle {
    private static final Set<String> REGISTERED = new HashSet<>();
    private static final List<ASMDataTable.ASMData> CLIENT = new ArrayList<>();
    private static boolean commonReady;
    private static boolean clientReady;
    private AddonLifecycle() {}

    public static synchronized void registerCommon(ASMDataTable table) {
        if (commonReady) return;
        commonReady = true;
        for (ModContainer container : Loader.instance().getModList()) {
            Object instance = container.getMod();
            if (instance instanceof BBSAddonMod) {
                BBSAddon annotation = instance.getClass().getAnnotation(BBSAddon.class);
                if (annotation == null || !annotation.clientOnly()) register((BBSAddonMod) instance);
            }
        }
        List<ASMDataTable.ASMData> entries = new ArrayList<>(table.getAll(BBSAddon.class.getName()));
        entries.sort(Comparator.comparing(ASMDataTable.ASMData::getClassName));
        for (ASMDataTable.ASMData entry : entries) {
            if (Boolean.TRUE.equals(entry.getAnnotationInfo().get("clientOnly"))) CLIENT.add(entry);
            else instantiate(entry);
        }
    }

    public static synchronized void registerClient() {
        if (clientReady) return;
        if (!commonReady) throw new IllegalStateException("BBS common addon discovery has not run");
        clientReady = true;
        for (ASMDataTable.ASMData entry : CLIENT) instantiate(entry);
        CLIENT.clear();
    }

    /** Explicit registration for a Forge proxy that already owns its addon instance. */
    public static synchronized void register(BBSAddonMod addon) {
        if (REGISTERED.add(addon.getClass().getName())) {
            BBSMod.events.register(addon);
            BBSMod.LOGGER.info("Registered BBS addon {}", addon.getClass().getName());
        }
    }

    public static synchronized int getRegisteredCount() { return REGISTERED.size(); }

    private static void instantiate(ASMDataTable.ASMData entry) {
        if (REGISTERED.contains(entry.getClassName())) return;
        try {
            /* Reuse a Forge-owned instance, rather than constructing a second @Mod object. */
            for (ModContainer container : Loader.instance().getModList()) {
                Object instance = container.getMod();
                if (instance != null && instance.getClass().getName().equals(entry.getClassName())) {
                    if (!(instance instanceof BBSAddonMod)) throw new IllegalArgumentException("Entrypoint must implement BBSAddonMod");
                    register((BBSAddonMod) instance);
                    return;
                }
            }
            Class<?> type = Class.forName(entry.getClassName(), true, Loader.instance().getModClassLoader());
            if (!BBSAddonMod.class.isAssignableFrom(type)) throw new IllegalArgumentException("Entrypoint must implement BBSAddonMod");
            register((BBSAddonMod) type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            throw new IllegalStateException("Could not initialize BBS addon " + entry.getClassName(), error);
        }
    }
}