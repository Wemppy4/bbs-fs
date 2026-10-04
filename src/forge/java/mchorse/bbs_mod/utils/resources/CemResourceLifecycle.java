package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.cubic.jem.VanillaRigs;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import java.util.*;

/** Resource-pack changes drive the same CEM catalog/cache invalidation as BBS FS. */
public final class CemResourceLifecycle
{
    private static CemSourcePack pack;
    private CemResourceLifecycle() {}

    public static CemSourcePack getPack() { return pack; }

    /** Call once after the shared asset provider and ModelManager are ready. */
    public static void install()
    {
        if (pack != null) return;
        pack = new CemSourcePack();
        BBSMod.getProvider().register(pack);
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
            .registerReloadListener(manager -> reload());
    }

    private static void reload()
    {
        if (pack == null) return;
        Set<Link> changed = new HashSet<>();
        pack.getLinksFromPath(changed, Link.assets(CemSourcePack.FOLDER), true);
        synchronized (BBSModClient.getModels())
        {
            pack.reindex();
            VanillaRigs.clear();
            BBSModClient.getModels().forgetFolder(CemSourcePack.NAME + "/");
        }
        pack.getLinksFromPath(changed, Link.assets(CemSourcePack.FOLDER), true);
        for (Link link : changed) BBSModClient.getTextures().delete(link);
        BBSModClient.getFormCategories().reloadAssets();
        BBSResources.markAssetsChanged();
    }
}
