package mchorse.bbs_mod;

import mchorse.bbs_mod.utils.watchdog.WatchDog;
import net.minecraft.client.MinecraftClient;

import java.io.File;

public class BBSResources
{
    private static WatchDog watchDog;

    /**
     * Bumped on every change the watchdog sees in the assets folder. A browser over the
     * assets compares it against what it last saw and relists — no per-instance listener
     * to register and forget, no polling of the disk.
     */
    private static int assetsVersion;

    public static void init()
    {
        setupWatchdog();

        BBSModClient.getFormCategories().setup();
    }

    public static void setupWatchdog()
    {
        File assetsFolder = BBSMod.getAssetsFolder();

        watchDog = new WatchDog(assetsFolder, false, (runnable) -> MinecraftClient.getInstance().execute(runnable));
        watchDog.getProxy().register(BBSModClient.getTextures());
        watchDog.getProxy().register(BBSModClient.getModels());
        watchDog.getProxy().register(BBSModClient.getSounds());
        watchDog.getProxy().register(BBSModClient.getFormCategories());
        watchDog.getProxy().register((path, event) -> assetsVersion += 1);

        watchDog.start();
    }

    public static int getAssetsVersion()
    {
        return assetsVersion;
    }

    /** For code that changed the assets itself and wants browsers to relist right away. */
    public static void markAssetsChanged()
    {
        assetsVersion += 1;
    }

    public static void stopWatchdog()
    {
        if (watchDog != null)
        {
            watchDog.stop();
            watchDog = null;
        }
    }

    public static void tick()
    {
        if (watchDog != null)
        {
            watchDog.getProxy().tick();
        }
    }
}
