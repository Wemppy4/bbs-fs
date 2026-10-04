package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.resources.*;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** 1.12 has no ResourceManager.findResources. Enumerates names from the manager's
 * actual enabled packs, following Blockbuster 1.12 ResourcePackUtils; all content
 * is still opened through Minecraft's resource manager to retain pack priority.
 * Zip handles opened for enumeration are always closed, never the pack's handle. */
public final class NativeResourceIndex
{
    private NativeResourceIndex() {}

    public static Set<ResourceLocation> find(IResourceManager manager, String folder, String extension)
    {
        Set<ResourceLocation> result = new TreeSet<>();
        try
        {
            Object fallback = manager;
            if (manager instanceof SimpleReloadableResourceManager)
            {
                Map<?, ?> domains = (Map<?, ?>) read(manager, SimpleReloadableResourceManager.class, Map.class);
                fallback = domains.get("minecraft");
            }
            if (!(fallback instanceof FallbackResourceManager)) return result;
            List<?> packs = (List<?>) read(fallback, FallbackResourceManager.class, List.class);
            for (Object value : packs)
            {
                IResourcePack pack = unwrap((IResourcePack) value);
                if (pack instanceof AbstractResourcePack)
                {
                    File file = (File) read(pack, AbstractResourcePack.class, File.class);
                    collect(file, folder, extension, result);
                }
                else if (pack instanceof DefaultResourcePack)
                {
                    collectDefault((DefaultResourcePack) pack, folder, extension, result);
                }
            }
        }
        catch (ReflectiveOperationException | IOException error)
        {
            BBSMod.LOGGER.warn("Unable to enumerate Minecraft resources under " + folder, error);
        }
        return result;
    }

    private static IResourcePack unwrap(IResourcePack pack) throws ReflectiveOperationException
    {
        while (pack instanceof LegacyV2Adapter) pack = (IResourcePack) read(pack, pack.getClass(), IResourcePack.class);
        return pack;
    }

    private static Object read(Object owner, Class<?> declaring, Class<?> type) throws ReflectiveOperationException
    {
        for (Field field : declaring.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType()))
            {
                field.setAccessible(true);
                return field.get(owner);
            }
        }
        throw new NoSuchFieldException(declaring.getName() + ": " + type.getName());
    }

    private static void collectDefault(DefaultResourcePack pack, String folder, String extension, Set<ResourceLocation> result)
        throws ReflectiveOperationException, IOException
    {
        ResourceIndex index = (ResourceIndex) read(pack, DefaultResourcePack.class, ResourceIndex.class);
        Map<?, ?> files = (Map<?, ?>) read(index, ResourceIndex.class, Map.class);
        for (Object key : files.keySet())
        {
            String name = key.toString();
            if (name.startsWith("minecraft/")) add(name.substring("minecraft/".length()), folder, extension, result);
            else if (name.startsWith("minecraft:")) add(name.substring("minecraft:".length()), folder, extension, result);
        }
        /* Vanilla entity textures are in the client JAR, not the downloaded asset
         * index. The launch classpath also supports Forge's unpacked dev runtime. */
        Set<File> sources = new LinkedHashSet<>();
        if (Launch.classLoader != null) for (URL source : Launch.classLoader.getSources())
        {
            if (source.getProtocol().equals("file")) try { sources.add(new File(source.toURI())); }
            catch (URISyntaxException ignored) { }
        }
        Enumeration<URL> roots = DefaultResourcePack.class.getClassLoader().getResources("assets/minecraft/" + folder);
        while (roots.hasMoreElements())
        {
            URL root = roots.nextElement();
            try
            {
                if (root.getProtocol().equals("jar")) sources.add(new File(((JarURLConnection) root.openConnection()).getJarFileURL().toURI()));
                else if (root.getProtocol().equals("file"))
                {
                    Path path = Paths.get(root.toURI());
                    for (int i = 0; i < folder.split("/").length + 2; i++) path = path.getParent();
                    sources.add(path.toFile());
                }
            }
            catch (URISyntaxException ignored) { }
        }
        for (File source : sources) collect(source, folder, extension, result);
    }

    private static void collect(File source, String folder, String extension, Set<ResourceLocation> result) throws IOException
    {
        String prefix = "assets/minecraft/" + folder + "/";
        if (source.isDirectory())
        {
            Path root = source.toPath(), path = root.resolve(prefix);
            if (!Files.isDirectory(path)) return;
            try (java.util.stream.Stream<Path> files = Files.walk(path))
            {
                files.filter(Files::isRegularFile).forEach(file ->
                    add(root.relativize(file).toString().replace('\\', '/').substring("assets/minecraft/".length()), folder, extension, result));
            }
        }
        else if (source.isFile() && (source.getName().endsWith(".jar") || source.getName().endsWith(".zip")))
        {
            try (ZipFile zip = new ZipFile(source))
            {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements())
                {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.startsWith(prefix))
                        add(name.substring("assets/minecraft/".length()), folder, extension, result);
                }
            }
        }
    }

    private static void add(String path, String folder, String extension, Set<ResourceLocation> result)
    {
        if (path.startsWith(folder + "/") && path.endsWith(extension)) result.add(new ResourceLocation("minecraft", path));
    }
}
