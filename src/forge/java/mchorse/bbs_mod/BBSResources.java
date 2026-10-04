package mchorse.bbs_mod;

import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.resources.MultiLinkThread;
import mchorse.bbs_mod.utils.watchdog.WatchDog;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class BBSResources
{
    private static volatile List<WatchDog> watchDogs = java.util.Collections.emptyList();
    private static volatile long watchDogGeneration;

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

    public static synchronized void setupWatchdog()
    {
        stopWatchdog();

        File assetsFolder = BBSMod.getAssetsFolder();
        File worldFolder = BBSMod.getWorldAssetsFolder();
        List<WatchDog> watchers = new ArrayList<>();

        watchers.add(createWatchdog(assetsFolder));

        if (worldFolder != null && !assetsFolder.equals(worldFolder))
        {
            watchers.add(createWatchdog(worldFolder));
        }

        watchDogs = java.util.Collections.unmodifiableList(new ArrayList<>(watchers));

        for (WatchDog watcher : watchDogs)
        {
            watcher.start();
        }
    }

    private static WatchDog createWatchdog(File folder)
    {
        long generation = watchDogGeneration;
        WatchDog watchDog = new WatchDog(folder, false, (runnable) -> Minecraft.getMinecraft().addScheduledTask(() ->
        {
            if (generation == watchDogGeneration)
            {
                runnable.run();
            }
        }));
        watchDog.getProxy().register(BBSModClient.getTextures());
        watchDog.getProxy().register(BBSModClient.getModels());
        watchDog.getProxy().register(BBSModClient.getSounds());
        watchDog.getProxy().register(BBSModClient.getFonts());
        watchDog.getProxy().register(BBSModClient.getFormCategories());
        watchDog.getProxy().register((path, event) -> assetsVersion += 1);

        return watchDog;
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

    public static synchronized void stopWatchdog()
    {
        watchDogGeneration += 1;

        for (WatchDog watchDog : watchDogs)
        {
            watchDog.stop();
        }

        watchDogs = java.util.Collections.emptyList();
    }

    public static void enterWorld()
    {
        var server = Minecraft.getMinecraft().getIntegratedServer();
        File folder = null;

        if (server != null)
        {
            try
            {
                folder = new File(server.getWorld(0).getSaveHandler().getWorldDirectory(), "bbs/assets");

                for (String path : java.util.Arrays.asList("models", "textures", "audio", "video", "fonts", "particles", "structures"))
                {
                    Files.createDirectories(folder.toPath().resolve(path));
                }
            }
            catch (IOException e)
            {
                /* Do not continue with an incomplete world asset library. */
                e.printStackTrace();
                if (Minecraft.getMinecraft().getConnection() != null)
                    Minecraft.getMinecraft().getConnection().getNetworkManager()
                        .closeChannel(new TextComponentString(UIKeys.WORLD_ASSETS_ERROR.get()));

                return;
            }
        }

        useAssetsFolder(folder);
    }

    public static void leaveWorld()
    {
        useAssetsFolder(null);
    }

    private static void useAssetsFolder(File folder)
    {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread())
            throw new IllegalStateException("Asset library changes must run on the client thread");

        File current = BBSMod.getWorldAssetsFolder();

        if (java.util.Objects.equals(current == null ? null : current.toPath().toAbsolutePath().normalize(),
            folder == null ? null : folder.toPath().toAbsolutePath().normalize()))
        {
            return;
        }

        stopWatchdog();

        MultiLinkThread.clear();

        /* Wait for the current background model load before replacing its provider's source. */
        synchronized (BBSModClient.getModels())
        {
            BBSMod.getDynamicSourcePack().setSecondary(folder == null ? null :
                new ExternalAssetsSourcePack(Link.ASSETS, folder).providesFiles());
            BBSModClient.getModels().reload();
        }

        BBSModClient.getVideos().delete();
        BBSModClient.getSounds().deleteSounds();
        BBSModClient.getFonts().delete();
        BBSModClient.getTextures().delete();
        mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
        StructureManager.invalidate();
        BBSModClient.reloadLanguage(BBSModClient.getLanguageKey());
        BBSModClient.getFormCategories().reloadAssets();
        markAssetsChanged();
        setupWatchdog();
    }

    public static void tick()
    {
        for (WatchDog current : watchDogs)
        {
            current.getProxy().tick();
        }
    }
}
