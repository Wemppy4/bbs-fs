package mchorse.bbs_mod.addons;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.api.BBSAddonMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every BBS addon of this game, with what BBS learned about it while loading it: its entry point
 * objects, the settings it registered and the first thing it broke. The addons window reads it.
 *
 * <p>An addon is a mod with a {@code bbs-addon} or {@code bbs-client-addon} entry point; both
 * halves of one mod are one addon.</p>
 */
public final class Addons
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Addon> ADDONS = new LinkedHashMap<>();
    private static final Map<Object, Addon> BY_SUBSCRIBER = Collections.synchronizedMap(new IdentityHashMap<>());

    /** The subscriber whose method the event bus is calling right now, on this thread. */
    private static final ThreadLocal<Object> POSTING = new ThreadLocal<>();

    private Addons()
    {}

    /**
     * Creates the entry point objects of one entry point and hands each to the given consumer.
     *
     * <p>An entry point that can't be created (a missing class, a constructor that throws) used to
     * take the whole game down with it; now the addon is marked as crashed and the rest load.</p>
     */
    public static synchronized void load(String entrypoint, Consumer<Object> register)
    {
        for (EntrypointContainer<BBSAddonMod> container : FabricLoader.getInstance().getEntrypointContainers(entrypoint, BBSAddonMod.class))
        {
            ModContainer mod = container.getProvider();
            Addon addon = ADDONS.computeIfAbsent(mod.getMetadata().getId(), (id) -> new Addon(mod));
            Object subscriber;

            try
            {
                subscriber = container.getEntrypoint();
            }
            catch (Throwable e)
            {
                LOGGER.error("BBS addon {} could not be loaded!", addon.getId(), e);
                addon.fail(entrypoint, e);

                continue;
            }

            addon.subscribers.add(subscriber);
            BY_SUBSCRIBER.put(subscriber, addon);
            register.accept(subscriber);
        }
    }

    public static synchronized Collection<Addon> getAll()
    {
        return new ArrayList<>(ADDONS.values());
    }

    /** Marks the start of a subscriber's call; returns what was posting before, for {@link #leave(Object)}. */
    public static Object enter(Object subscriber)
    {
        Object previous = POSTING.get();

        POSTING.set(subscriber);

        return previous;
    }

    public static void leave(Object previous)
    {
        if (previous == null)
        {
            POSTING.remove();
        }
        else
        {
            POSTING.set(previous);
        }
    }

    /** A subscriber threw while handling the given event. BBS's own subscribers aren't addons and are skipped. */
    public static void fail(Object subscriber, String where, Throwable error)
    {
        Addon addon = BY_SUBSCRIBER.get(subscriber);

        if (addon != null)
        {
            addon.fail(where, error);
        }
    }

    /** Settings registered while an addon's subscriber is being called belong to that addon. */
    public static void claimSettings(String settingsId)
    {
        Object subscriber = POSTING.get();
        Addon addon = subscriber == null ? null : BY_SUBSCRIBER.get(subscriber);

        if (addon != null && !addon.settings.contains(settingsId))
        {
            addon.settings.add(settingsId);
        }
    }
}
