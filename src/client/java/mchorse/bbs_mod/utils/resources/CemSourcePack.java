package mchorse.bbs_mod.utils.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The OptiFine CEM models of the installed resource packs, served as if they were models of the
 * user's own — so a pack's cow shows up in the form palette the moment the pack is enabled, without
 * anyone copying files into {@code config/bbs/assets/models/}.
 *
 * <p>It registers under {@link Link#ASSETS} alongside the folder and the jar, after both, which is
 * what makes the whole existing pipeline carry it: model ids, {@code config.json}, the palette, the
 * lazy loader. Being last also means a folder of the user's under the same id wins, so a pack model
 * can be overridden or given a config without touching the pack.</p>
 *
 * <p>A pack keeps its models flat in one folder and its textures somewhere else entirely, while BBS
 * addresses a model by the folder it sits in. So the pack presents a folder per model:</p>
 *
 * <pre>
 * models/cem/&lt;entity&gt;/&lt;entity&gt;.jem   the entity model
 * models/cem/&lt;entity&gt;/&lt;name&gt;.jpm     only the part models that .jem refers to
 * models/cem/&lt;entity&gt;/model.png       the entity's texture, when it can be resolved
 * </pre>
 *
 * <p>Everything is read through Minecraft's own {@link ResourceManager}, which is the point: it
 * already merges the enabled packs in the right order and resolves a pack's format overlays (Fresh
 * Animations ships four), so what BBS loads is what OptiFine would have loaded. Reading the zips
 * directly would take the wrong overlay.</p>
 *
 * <p>Nothing here is a file the user owns, so {@link #getFile}/{@link #getLink} answer null. That
 * disables saving the model geometry (a .jem is not editable anyway) and keeps the watchdog out of
 * it; changes arrive through {@link #reindex()} on a resource reload instead.</p>
 */
public class CemSourcePack implements ISourcePack
{
    /**
     * The folder every pack model lands in, so its id reads {@code cem/<entity>}. It is an id, not a
     * word: what the palette shows instead is {@link mchorse.bbs_mod.ui.UIKeys#FORMS_CATEGORIES_MODELS_PACKS}.
     * Changing it would change every id, and ids are saved inside forms and films.
     */
    public static final String NAME = "cem";

    /** Where a pack model lands. */
    public static final String FOLDER = "models/" + NAME + "/";

    /** Where OptiFine keeps entity models inside a resource pack. */
    private static final String CEM = "optifine/cem";

    /** Where the entity textures a model may use live. */
    private static final String TEXTURES = "textures/entity";

    /** The name BBS's model loaders look for first when picking a model's texture. */
    private static final String TEXTURE = "model.png";

    private final ResourceManager manager;

    /**
     * Synthetic path (without the source) &rarr; the resource behind it. Replaced whole on a reload
     * rather than edited, because the model loader reads it off its own thread.
     */
    private volatile Map<String, Identifier> assets = new HashMap<>();

    public CemSourcePack()
    {
        this.manager = MinecraftClient.getInstance().getResourceManager();

        this.reindex();
    }

    /** Rebuild the index from the packs as they are enabled now. */
    public void reindex()
    {
        Map<String, Identifier> assets = new TreeMap<>();
        Textures textures = this.textures();

        for (Identifier jem : this.manager.findResources(CEM, (id) -> isMinecraft(id) && id.getPath().endsWith(".jem")).keySet())
        {
            /* The path under the CEM folder without its extension: "cow", or "boat/bamboo" for the
             * ones a pack files away in a subfolder. The last segment names the model's folder, and
             * the .jem inside carries the same name so the loader picks it over any sibling. */
            String model = jem.getPath().substring(CEM.length() + 1, jem.getPath().length() - 4);
            String name = model.substring(model.lastIndexOf('/') + 1);
            String folder = FOLDER + model + "/";

            assets.put(folder + name + ".jem", jem);

            this.collectParts(jem, folder, assets);

            Identifier texture = this.resolveTexture(textures, name);

            if (texture != null)
            {
                assets.put(folder + TEXTURE, texture);
            }
        }

        this.assets = assets;
    }

    /**
     * Put every part model the entity model pulls in beside it, under its own file name — which is
     * how {@link mchorse.bbs_mod.cubic.model.loaders.JemModelLoader} keys them. Only the ones it
     * actually refers to: a pack's CEM folder holds a part model for every entity, and serving all
     * of them into every model's folder would be a listing of thousands.
     */
    private void collectParts(Identifier jem, String folder, Map<String, Identifier> assets)
    {
        Set<String> visited = new HashSet<>();
        Set<String> queue = new HashSet<>(this.references(jem));

        while (!queue.isEmpty())
        {
            String reference = queue.iterator().next();

            queue.remove(reference);

            if (!visited.add(reference))
            {
                continue;
            }

            Identifier part = this.resolve(jem, reference);

            if (part == null)
            {
                continue;
            }

            assets.put(folder + reference.substring(reference.lastIndexOf('/') + 1), part);

            queue.addAll(this.references(part));
        }
    }

    /** The {@code "model"} references inside a .jem/.jpm — a part model may pull in another. */
    private Set<String> references(Identifier id)
    {
        Set<String> references = new HashSet<>();

        try (InputStream stream = this.open(id))
        {
            if (stream == null)
            {
                return references;
            }

            collectReferences(JsonParser.parseString(IOUtils.readText(stream)), references);
        }
        catch (Exception e)
        {
            System.err.println("CEM model " + id + " could not be read for its part models: " + e);
        }

        return references;
    }

    private static void collectReferences(JsonElement element, Set<String> references)
    {
        if (element instanceof JsonObject object)
        {
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive())
                {
                    references.add(entry.getValue().getAsString());
                }
                else
                {
                    collectReferences(entry.getValue(), references);
                }
            }
        }
        else if (element instanceof JsonArray array)
        {
            for (JsonElement child : array)
            {
                collectReferences(child, references);
            }
        }
    }

    /** A reference is looked for beside the model first, then at the root of the CEM folder. */
    private Identifier resolve(Identifier jem, String reference)
    {
        String path = jem.getPath();
        String beside = path.substring(0, path.lastIndexOf('/') + 1) + reference;

        for (String candidate : new String[] {beside, CEM + "/" + reference})
        {
            Identifier id = tryIdentifier(candidate);

            if (id != null && this.manager.getResource(id).isPresent())
            {
                return id;
            }
        }

        return null;
    }

    /**
     * Where a model's texture is looked for. A .jem never says which one it wears - OptiFine dresses it
     * in the vanilla one - so the entity's name is all there is to go on, and a pack's own repaint wins
     * by sitting at the same path. Paths are relative to {@link #TEXTURES}.
     */
    private static final class Textures
    {
        final Map<String, Identifier> byPath = new LinkedHashMap<>();
        final Map<String, Identifier> byName = new LinkedHashMap<>();
        final Map<String, List<Identifier>> byFolder = new LinkedHashMap<>();
    }

    /** Variant prefixes a pack puts on an entity that wears the plain one's texture. */
    private static final String[] PREFIXES = {"cold_", "warm_"};

    /**
     * Suffixes naming a layer over an entity rather than an entity: the wool over a sheep, the armour
     * over a horse, the outer skin of a stray. Stripped one after another, so {@code sheep_wool_undercoat}
     * comes back to {@code sheep}.
     */
    private static final String[] LAYERS = {
        "_outer", "_saddle", "_armor", "_decor", "_patch", "_collar", "_wool", "_charge",
        "_undercoat", "_harness", "_ropes", "_big", "_medium", "_small", "_pattern_a", "_pattern_b",
        "_a", "_b", "_left", "_right"
    };

    /**
     * Entities whose texture no rule can find, because vanilla files it under another name entirely.
     * Kept small on purpose: everything a rule can reach is left to the rules.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("chest", "chest/normal.png"),
        Map.entry("chest_large", "chest/normal_left.png"),
        Map.entry("ender_chest", "chest/ender.png"),
        Map.entry("trapped_chest", "chest/trapped.png"),
        Map.entry("trapped_chest_large", "chest/trapped_left.png"),
        Map.entry("elder_guardian", "guardian_elder.png"),
        Map.entry("giant", "zombie/zombie.png"),
        Map.entry("magma_cube", "slime/magmacube.png"),
        Map.entry("mooshroom", "cow/red_mooshroom.png"),
        Map.entry("player", "player/wide/steve.png"),
        Map.entry("player_slim", "player/slim/steve.png"),
        Map.entry("puffer_fish", "fish/pufferfish.png"),
        Map.entry("raft", "boat/bamboo.png"),
        Map.entry("sheep_wool", "sheep/sheep_fur.png"),
        Map.entry("shulker_box", "shulker/shulker.png"),
        Map.entry("skeleton_horse", "horse/horse_skeleton.png"),
        Map.entry("tropical_fish", "fish/tropical_a.png"),
        Map.entry("zombie_horse", "horse/horse_zombie.png")
    );

    private Textures textures()
    {
        Textures textures = new Textures();

        /* Sorted, so a name two packs both answer resolves the same way twice. */
        for (Identifier id : new TreeMap<>(this.manager.findResources(TEXTURES, (l) -> isMinecraft(l) && l.getPath().endsWith(".png"))).keySet())
        {
            String path = id.getPath().substring(TEXTURES.length() + 1);
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);

            textures.byPath.putIfAbsent(path, id);
            textures.byName.putIfAbsent(name, id);

            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1))
            {
                textures.byFolder.computeIfAbsent(path.substring(0, slash), (k) -> new ArrayList<>()).add(id);
            }
        }

        return textures;
    }

    /**
     * The texture an entity wears, in the order the answers are trusted: the table for the ones vanilla
     * files under another name, then a file called after the entity, then the entity's own folder for
     * the ones that come in variants (a cat, a horse, a boat). A variant's default is a guess by
     * definition - the point is that the model arrives wearing something rather than the missing-texture
     * checkerboard, and the form's own texture overrides it either way.
     */
    private Identifier resolveTexture(Textures textures, String entity)
    {
        Collection<String> names = variants(entity);

        for (String name : names)
        {
            Identifier id = textures.byPath.get(ALIASES.getOrDefault(name, ""));

            if (id != null)
            {
                return id;
            }
        }

        for (String name : names)
        {
            Identifier id = textures.byName.get(name);

            if (id != null)
            {
                return id;
            }
        }

        for (String name : names)
        {
            Identifier id = pickFromFolder(textures, name);

            if (id != null)
            {
                return id;
            }
        }

        return null;
    }

    /**
     * The names to look an entity up under: itself, then without the variant prefix, the baby suffix and
     * a trailing number, then without the layer suffixes as well.
     */
    private static Collection<String> variants(String entity)
    {
        Set<String> names = new LinkedHashSet<>();
        String name = entity;

        names.add(name);

        for (String prefix : PREFIXES)
        {
            if (name.startsWith(prefix))
            {
                name = name.substring(prefix.length());
            }
        }

        if (name.endsWith("_baby"))
        {
            name = name.substring(0, name.length() - 5);
        }

        names.add(name = stripDigits(name));

        for (boolean stripped = true; stripped; )
        {
            stripped = false;

            for (String suffix : LAYERS)
            {
                if (name.endsWith(suffix) && name.length() > suffix.length())
                {
                    name = name.substring(0, name.length() - suffix.length());
                    stripped = true;
                }
            }
        }

        if (name.startsWith("head_"))
        {
            name = name.substring(5);
        }

        names.add(name = stripDigits(name));

        /* Every minecart is drawn on the one texture, whatever it carries. */
        if (name.endsWith("_minecart"))
        {
            names.add("minecart");
        }

        return names;
    }

    /**
     * One texture out of an entity's folder: the one named after it, else one named after it with
     * something appended that is not a layer ({@code horse_black} but not {@code cat_collar}), else the
     * first. Files sitting straight in the folder are preferred over a nested {@code armor/} and such.
     */
    private static Identifier pickFromFolder(Textures textures, String folder)
    {
        List<Identifier> all = textures.byFolder.get(folder);

        if (all == null)
        {
            return null;
        }

        int depth = folder.length() - folder.replace("/", "").length();
        List<Identifier> direct = new ArrayList<>();

        for (Identifier id : all)
        {
            String path = id.getPath().substring(TEXTURES.length() + 1);

            if (path.length() - path.replace("/", "").length() == depth + 1)
            {
                direct.add(id);
            }
        }

        List<Identifier> pool = direct.isEmpty() ? all : direct;
        String name = folder.substring(folder.lastIndexOf('/') + 1);

        for (Identifier id : pool)
        {
            if (fileName(id).equals(name))
            {
                return id;
            }
        }

        for (Identifier id : pool)
        {
            String file = fileName(id);

            if (file.startsWith(name) && !isLayer(file))
            {
                return id;
            }
        }

        return pool.get(0);
    }

    private static String fileName(Identifier id)
    {
        String path = id.getPath();

        return path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
    }

    private static boolean isLayer(String name)
    {
        for (String suffix : LAYERS)
        {
            if (name.endsWith(suffix))
            {
                return true;
            }
        }

        return false;
    }

    private static String stripDigits(String name)
    {
        int end = name.length();

        while (end > 0 && Character.isDigit(name.charAt(end - 1)))
        {
            end -= 1;
        }

        return name.substring(0, end);
    }

    private static boolean isMinecraft(Identifier id)
    {
        return id.getNamespace().equals("minecraft");
    }

    /** {@code new Identifier} throws on anything it does not consider a legal path. */
    private static Identifier tryIdentifier(String path)
    {
        try
        {
            return new Identifier("minecraft", path);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private InputStream open(Identifier id) throws IOException
    {
        Optional<Resource> resource = this.manager.getResource(id);

        return resource.isPresent() ? resource.get().getInputStream() : null;
    }

    @Override
    public String getPrefix()
    {
        return Link.ASSETS;
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.assets.containsKey(link.path);
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        Identifier id = this.assets.get(link.path);

        return id == null ? null : this.open(id);
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
        /* The root lists everything; any other folder is asked for with a trailing slash. */
        String path = link.path.isEmpty() || link.path.equals("/") ? "" : link.path.endsWith("/") ? link.path : link.path + "/";

        /* Everything served lives under one folder; anything else asked for is another pack's. */
        if (!path.isEmpty() && !FOLDER.startsWith(path) && !path.startsWith(FOLDER))
        {
            return;
        }

        for (String asset : this.assets.keySet())
        {
            if (!asset.startsWith(path))
            {
                continue;
            }

            String rest = asset.substring(path.length());
            int slash = rest.indexOf('/');

            if (slash < 0)
            {
                links.add(Link.assets(asset));
            }
            else if (recursive)
            {
                links.add(Link.assets(asset));
                links.add(Link.assets(path + rest.substring(0, slash) + "/"));
            }
            else
            {
                links.add(Link.assets(path + rest.substring(0, slash) + "/"));
            }
        }
    }
}
