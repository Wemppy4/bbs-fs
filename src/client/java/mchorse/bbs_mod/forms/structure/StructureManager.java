package mchorse.bbs_mod.forms.structure;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists and loads structure NBT files from the current world's {@code generated} folder
 * ({@code <world>/generated/<namespace>/structures/**.nbt}, written by vanilla structure
 * blocks). Works only with an integrated server (singleplayer) — on a dedicated server the
 * client has no access to the save, so the list is empty.
 *
 * <p>Loaded structures are cached per id; the cache is dropped when the server instance
 * changes (world switch) or via {@link #invalidate()} (the structure picker calls it so
 * re-saved structures get picked up).</p>
 */
public class StructureManager
{
    private static final Map<String, StructureRenderData> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private static MinecraftServer lastServer;

    public static void invalidate()
    {
        CACHE.clear();
        FAILED.clear();
    }

    /** Drop caches when the integrated server changes (entering/leaving a world). */
    private static void checkServer()
    {
        MinecraftServer server = MinecraftClient.getInstance().getServer();

        if (server != lastServer)
        {
            lastServer = server;

            invalidate();
        }
    }

    private static Path getGeneratedPath()
    {
        MinecraftServer server = MinecraftClient.getInstance().getServer();

        return server == null ? null : server.getSavePath(WorldSavePath.GENERATED);
    }

    /** @return ids like {@code namespace:path/name} for every *.nbt under generated structures. */
    public static List<String> getStructureIds()
    {
        checkServer();

        Path generated = getGeneratedPath();

        if (generated == null || !Files.isDirectory(generated))
        {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();

        try (Stream<Path> namespaces = Files.list(generated))
        {
            namespaces.filter(Files::isDirectory).forEach((namespace) ->
            {
                Path structures = namespace.resolve("structures");

                if (!Files.isDirectory(structures))
                {
                    return;
                }

                try (Stream<Path> files = Files.walk(structures))
                {
                    files.filter((p) -> p.getFileName().toString().endsWith(".nbt")).forEach((file) ->
                    {
                        String relative = structures.relativize(file).toString().replace('\\', '/');

                        relative = relative.substring(0, relative.length() - ".nbt".length());
                        ids.add(namespace.getFileName() + ":" + relative);
                    });
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return ids;
    }

    /** @return parsed structure for the id, or null (missing/broken file, empty id, no server). */
    public static StructureRenderData get(String id)
    {
        checkServer();

        if (id == null || id.isEmpty() || FAILED.contains(id))
        {
            return null;
        }

        StructureRenderData data = CACHE.get(id);

        if (data != null)
        {
            return data;
        }

        Path generated = getGeneratedPath();

        if (generated == null)
        {
            return null;
        }

        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);

        Path file = generated.resolve(namespace).resolve("structures").resolve(path + ".nbt").normalize();

        /* No escaping the generated folder via weird ids */
        if (!file.startsWith(generated.normalize()) || !Files.isRegularFile(file))
        {
            FAILED.add(id);

            return null;
        }

        try
        {
            NbtCompound root = NbtIo.readCompressed(file, NbtTagSizeTracker.ofUnlimitedBytes());

            data = StructureRenderData.parse(id, root);
            CACHE.put(id, data);

            return data;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            FAILED.add(id);

            return null;
        }
    }
}
