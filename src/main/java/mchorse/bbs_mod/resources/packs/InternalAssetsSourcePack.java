package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.DataPath;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InternalAssetsSourcePack implements ISourcePack
{
    private String prefix;
    private String internalPrefix;
    private Class clazz;

    private boolean isForge;

    private List<String> zipCache = new ArrayList<>();

    public InternalAssetsSourcePack()
    {
        this(Link.ASSETS, "assets/bbs/assets", InternalAssetsSourcePack.class);
    }

    public InternalAssetsSourcePack(String prefix, String internalPrefix, Class clazz)
    {
        this.prefix = prefix;
        this.internalPrefix = internalPrefix;
        this.clazz = clazz;

        try
        {
            Class.forName("net.minecraftforge.common.MinecraftForge");

            isForge = true;
        }
        catch (Exception e)
        {}
    }

    @Override
    public String getPrefix()
    {
        return this.prefix;
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.clazz.getClassLoader().getResource(this.internalPrefix + "/" + link.path) != null;
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        return this.clazz.getClassLoader().getResourceAsStream(this.internalPrefix + "/" + link.path);
    }

    @Override
    public File getFile(Link link)
    {
        return null;
    }

    @Override
    public Link getLink(File file)
    {
        return null;
    }

    @Override
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        if (isForge)
        {
            this.stupidWorkaround(links, link, recursive);

            return;
        }

        URL url = this.clazz.getProtectionDomain().getCodeSource().getLocation();

        try
        {
            File file = Paths.get(url.toURI()).toFile();

            if (file.isDirectory())
            {
                this.getLinksFromFolder(this.getResourcesFolder(file), link, links, recursive);
            }
            else if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"))
            {
                this.getLinksFromZipFile(file, link, links, recursive);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.stupidWorkaround(links, link, recursive);
        }
    }

    private void stupidWorkaround(Collection<Link> links, Link link, boolean recursive)
    {
        /* Forge throws some exception due to the way Connector works, it can't find the
         * jar file for some reason... so I have to resort to such ugly piece of code for
         * it to work correctly. I should probably ask on Connector's Discord what I can do,
         * but whatever for now... */
        String version = "";

        for (ModContainer allMod : FabricLoader.getInstance().getAllMods())
        {
            if (allMod.getMetadata().getId().equals(BBSMod.MOD_ID))
            {
                version = allMod.getMetadata().getVersion().getFriendlyString();

                break;
            }
        }

        if (version.isEmpty())
        {
            return;
        }

        File mods = new File(FabricLoader.getInstance().getGameDir().toFile(), "mods");

        if (mods.isDirectory())
        {
            for (File file : mods.listFiles())
            {
                String name = file.getName();

                if (name.startsWith("bbs") && name.contains(version) && name.endsWith(".jar"))
                {
                    this.getLinksFromZipFile(file, link, links, recursive);
                }
            }
        }
    }

    /**
     * Get resources folder. In case this is run in development environment,
     * the project can be compiled to two folders "classes/" and "resources/."
     * To get the right folder, this method checks if the folder with
     * assets exists.
     */
    private File getResourcesFolder(File file)
    {
        if (new File(file, this.internalPrefix).exists())
        {
            return file;
        }

        for (File subFile : file.getParentFile().listFiles())
        {
            if (new File(subFile, this.internalPrefix).exists())
            {
                return subFile;
            }
        }

        /* In development environment, the assets are separate from classes, and for this
         * reason for the files to be found, I have to use this ugly workaround. Also, I
         * don't think it works outside of IntelliJ, so RIP... */
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
        {
            File resources = new File(file.getParentFile().getParentFile().getParentFile(), "resources/client/");

            if (resources.isDirectory())
            {
                return resources;
            }
        }

        return file;
    }

    private void getLinksFromFolder(File folder, Link link, Collection<Link> links, boolean recursive)
    {
        File file = new File(folder, this.internalPrefix + "/" + link.path);

        ExternalAssetsSourcePack.getLinksFromPathRecursively(file, links, link, link.path, recursive ? 9999 : 1);
    }

    /* Zip handling */

    /**
     * Synchronized: the model loader thread and the main thread both list assets, and the lazy
     * cache fill plus the iteration below must not interleave.
     */
    private synchronized void getLinksFromZipFile(File file, Link link, Collection<Link> links, boolean recursive)
    {
        /* The jar doesn't change within a session, so it is opened and read exactly once —
         * it used to be re-opened on every listing even with the cache already filled. */
        if (this.zipCache.isEmpty())
        {
            try (ZipFile zipFile = new ZipFile(file))
            {
                Enumeration<? extends ZipEntry> it = zipFile.entries();

                while (it.hasMoreElements())
                {
                    String name = it.nextElement().getName();

                    if (name.startsWith(this.internalPrefix))
                    {
                        this.zipCache.add(name);
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        this.handleLinksFromZipFile(link, links, recursive);
    }

    private void handleLinksFromZipFile(Link link, Collection<Link> links, boolean recursive)
    {
        DataPath assetsPath = new DataPath(this.internalPrefix + "/");

        for (String zipName : this.zipCache)
        {
            DataPath zipPath = new DataPath(zipName);
            DataPath fullPath = new DataPath(assetsPath + "/" + link.path);

            if (!zipPath.equals(fullPath) && zipPath.startsWith(fullPath))
            {
                for (int i = 0; i < assetsPath.size(); i++)
                {
                    zipPath.strings.remove(0);
                    fullPath.strings.remove(0);
                }

                if (!recursive && zipPath.size() != fullPath.size() + 1)
                {
                    continue;
                }

                links.add(new Link(this.prefix, zipPath + (zipPath.folder ? "/" : "")));
            }
        }
    }
}