package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StructureSaver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists and loads structure NBT files from the two places they can live.
 *
 * <p>A world's own, under {@code <world>/generated/<namespace>/structures/**.nbt} — what a vanilla
 * structure block writes. Those carry the id the block gave them, {@code namespace:path/name}, and
 * exist only while an integrated server does: on a dedicated server the client cannot reach the
 * save, so that half of the list is empty.</p>
 *
 * <p>And BBS's own, under {@code <assets>/structures/**.nbt}, asked of the asset provider so a
 * source pack can carry them too. Those are addressed as {@code assets:path/name} and follow BBS
 * rather than the world — the same structure is there in every save, which is the point of them.
 * The {@code assets} namespace is therefore taken: a world folder by that name is shadowed.</p>
 *
 * <p>Loaded structures are cached per id; the cache is dropped when the server instance
 * changes (world switch) or via {@link #invalidate()} (the structure picker calls it so
 * re-saved structures get picked up).</p>
 */
public class StructureManager
{
    /**
     * Parsed structures, kept softly: a renderer holds its own hard reference to the data it is
     * drawing, so anything only this map still points at is a structure nobody is using. Letting
     * the GC take those under pressure costs a re-parse and bounds a session that opened a lot of
     * big structures; a hard map would hold every one of them until the world changed.
     */
    private static final Map<String, SoftReference<StructureRenderData>> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    /**
     * A structure that exists in memory only — the wand's region shown in the save dialog before
     * there is a file. Kept apart from the cache so a save, which drops the cache, doesn't take the
     * preview with it; the dialog clears it when it closes.
     */
    private static StructureRenderData preview;

    /** Ids under this prefix are memory-only and never looked for on disk. */
    private static final String PREVIEW_PREFIX = "bbs:preview/";

    /** Ids under this prefix come from BBS's own folder rather than from the world. */
    private static final String ASSETS_PREFIX = Link.ASSETS + Link.SOURCE_SEPARATOR;

    private static final String EXTENSION = ".nbt";

    private static int previews;

    private static MinecraftServer lastServer;
    private static int generation;

    public static void setPreview(StructureRenderData data)
    {
        preview = data;
    }

    /**
     * A fresh id for the next preview. It has to be fresh: a renderer reloads on a change of
     * structure name, so a reused id would leave it showing the previous capture.
     */
    public static String nextPreviewId()
    {
        return PREVIEW_PREFIX + (++previews);
    }

    /**
     * Bumped every time the cache is dropped. Renderers keep their own derived state (parsed data,
     * baked geometry, block entities bound to a structure world) which the cache knows nothing
     * about — comparing generations is how they learn to throw it away.
     */
    public static int getGeneration()
    {
        checkServer();

        return generation;
    }

    public static void invalidate()
    {
        CACHE.clear();
        FAILED.clear();

        generation += 1;
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

    /** The folder BBS's own structures are read from and dropped into. */
    public static File getAssetsFolder()
    {
        return BBSMod.getAssetsPath(StructureSaver.ASSETS_FOLDER);
    }

    /** The id a structure of BBS's own is addressed by, from its path under the folder. */
    public static String assetId(String path)
    {
        return ASSETS_PREFIX + path;
    }

    /** {@code assets:path/name} for the file this link points at. */
    private static String toAssetId(Link link)
    {
        String path = link.path.substring(StructureSaver.ASSETS_FOLDER.length() + 1);

        return assetId(path.substring(0, path.length() - EXTENSION.length()));
    }

    /** The file {@code assets:path/name} names, for the provider to look up. */
    private static Link toAssetLink(String id)
    {
        return Link.assets(StructureSaver.ASSETS_FOLDER + "/" + id.substring(ASSETS_PREFIX.length()) + EXTENSION);
    }

    /**
     * @return ids for every structure BBS can reach: {@code assets:path/name} for its own,
     *         {@code namespace:path/name} for the world's.
     */
    public static List<String> getStructureIds()
    {
        checkServer();

        List<String> ids = new ArrayList<>();

        collectAssetIds(ids);
        collectWorldIds(ids);

        return ids;
    }

    /** BBS's own structures, from every source pack that answers to {@code assets}. */
    private static void collectAssetIds(List<String> ids)
    {
        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets(StructureSaver.ASSETS_FOLDER)))
        {
            if (link.path.endsWith(EXTENSION))
            {
                ids.add(toAssetId(link));
            }
        }
    }

    private static void collectWorldIds(List<String> ids)
    {
        Path generated = getGeneratedPath();

        if (generated == null || !Files.isDirectory(generated))
        {
            return;
        }

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
                    files.filter((p) -> p.getFileName().toString().endsWith(EXTENSION)).forEach((file) ->
                    {
                        String relative = structures.relativize(file).toString().replace('\\', '/');

                        relative = relative.substring(0, relative.length() - EXTENSION.length());
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
    }

    /** @return parsed structure for the id, or null (missing/broken file, empty id, no source). */
    public static StructureRenderData get(String id)
    {
        checkServer();

        if (id != null && id.startsWith(PREVIEW_PREFIX))
        {
            /* Answered from the slot or not at all — a preview has no file, and letting one fall
             * through would park a dead id in FAILED for the rest of the session */
            return preview != null && preview.id.equals(id) ? preview : null;
        }

        if (id == null || id.isEmpty() || FAILED.contains(id))
        {
            return null;
        }

        SoftReference<StructureRenderData> cached = CACHE.get(id);
        StructureRenderData data = cached == null ? null : cached.get();

        if (data != null)
        {
            return data;
        }

        try
        {
            NbtCompound root = id.startsWith(ASSETS_PREFIX) ? readAsset(id) : readGenerated(id);

            if (root == null)
            {
                FAILED.add(id);

                return null;
            }

            data = StructureRenderData.parse(id, root);
            CACHE.put(id, new SoftReference<>(data));

            return data;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            FAILED.add(id);

            return null;
        }
    }

    /** BBS's own structure, wherever the provider finds it — the assets folder or a source pack. */
    private static NbtCompound readAsset(String id) throws Exception
    {
        Link link = toAssetLink(id);

        /* No climbing out of the structures folder with an id full of ".." */
        if (link.path.contains("..") || !BBSMod.getProvider().hasAsset(link))
        {
            return null;
        }

        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            return NbtIo.readCompressed(stream);
        }
    }

    /** The world's own structure, as a vanilla structure block wrote it. */
    private static NbtCompound readGenerated(String id) throws Exception
    {
        Path generated = getGeneratedPath();

        if (generated == null)
        {
            return null;
        }

        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);

        Path file = generated.resolve(namespace).resolve("structures").resolve(path + EXTENSION).normalize();

        /* No escaping the generated folder via weird ids */
        if (!file.startsWith(generated.normalize()) || !Files.isRegularFile(file))
        {
            return null;
        }

        return NbtIo.readCompressed(file.toFile());
    }
}
