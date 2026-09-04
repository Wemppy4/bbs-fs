package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.model.loaders.BOBJModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.CubicModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.GeoCubicModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.VoxModelLoader;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.ShapeKeysManager;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ModelManager implements IWatchDogListener
{
    public static final String MODELS_PREFIX = "models/";

    /**
     * Model loaders an addon added.
     *
     * <p>Suppliers rather than loaders: the list is rebuilt from scratch on every asset
     * reload, so anything added to it directly would survive exactly until the user saved a
     * file in the assets folder.</p>
     */
    private static final List<Supplier<IModelLoader>> EXTRA_LOADERS = new ArrayList<>();

    /* Loaded models only (a concurrent map holds no nulls); which keys were ever queued lives
     * in {@link #requested} — both sides are touched by the render thread and the loader. */
    public final Map<String, ModelInstance> models = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    public final List<IModelLoader> loaders = new ArrayList<>();
    public final AssetProvider provider;
    public final MolangParser parser;

    private ModelLoader loader = new ModelLoader(this);

    public ModelManager(AssetProvider provider)
    {
        this.provider = provider;
        this.parser = new MolangParser();

        MolangHelper.registerVars(this.parser);

        this.setupLoaders();
    }

    private void setupLoaders()
    {
        this.loaders.clear();
        this.loaders.addAll(createLoaders());
    }

    /** Teaches BBS to read a model format of an addon's. */
    public static void registerLoader(Supplier<IModelLoader> loader)
    {
        EXTRA_LOADERS.add(loader);
    }

    private static List<IModelLoader> createLoaders()
    {
        List<IModelLoader> loaders = new ArrayList<>();

        loaders.add(new BOBJModelLoader());
        loaders.add(new CubicModelLoader());
        loaders.add(new GeoCubicModelLoader());
        loaders.add(new VoxModelLoader());

        for (Supplier<IModelLoader> extra : EXTRA_LOADERS)
        {
            loaders.add(extra.get());
        }

        return loaders;
    }

    /**
     * Get all models that can be loaded by
     */
    public List<String> getAvailableKeys()
    {
        List<Link> models = new ArrayList<>(BBSMod.getProvider().getLinksFromPath(Link.assets("models"), true));
        Set<String> keys = new HashSet<>();

        models.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        for (Link link : models)
        {
            if (this.isRelodable(link))
            {
                String path = link.path;

                int slash = path.indexOf('/');
                int lastSlash = path.lastIndexOf('/');

                if (slash != lastSlash)
                {
                    path = path.substring(slash + 1, lastSlash);

                    keys.add(path);
                }
            }
        }

        return new ArrayList<>(keys);
    }

    public ModelInstance getModel(String id)
    {
        ModelInstance model = this.models.get(id);

        if (model != null)
        {
            return model;
        }

        /* Queued exactly once; a failed load stays in requested and is never retried, which
         * is what the old null-in-the-map marker meant. */
        if (this.requested.add(id))
        {
            this.loader.add(id);
        }

        return null;
    }

    public ModelInstance loadModel(String id)
    {
        ModelInstance model = null;
        Link modelLink = Link.assets(MODELS_PREFIX + id);
        Collection<Link> links = this.provider.getLinksFromPath(modelLink, true);
        MapType config = this.loadConfig(modelLink);

        /* Fresh loaders per load: BOBJModelLoader keeps state between calls, and the shared
         * set would leak one model's leftovers into the next. */
        for (IModelLoader loader : createLoaders())
        {
            model = loader.load(id, this, modelLink, links, config);

            if (model != null)
            {
                break;
            }
        }

        if (model == null)
        {
            System.err.println("Model \"" + id + "\" wasn't loaded properly, or was loaded with no top level groups!");
        }
        else
        {
            System.out.println("Model \"" + id + "\" was loaded!");

            model.setup();
            this.models.put(id, model);
        }

        return model;
    }

    private MapType loadConfig(Link modelLink)
    {
        try (InputStream asset = this.provider.getAsset(modelLink.combine("config.json")))
        {
            String string = IOUtils.readText(asset);

            return (MapType) DataToString.fromString(string);
        }
        catch (Exception e)
        {}

        return null;
    }

    /**
     * Write a model's {@link ModelConfig} back to its {@code config.json}, in the user assets folder
     * ({@code config/bbs/assets/models/<id>/}). For a built-in model served from the jar this forks a
     * user copy that overrides it on the next load. Returns whether the file was written.
     */
    public boolean saveConfig(String id, ModelConfig config)
    {
        return this.saveConfig(id, config.toData().asMap());
    }

    public boolean saveConfig(String id, MapType data)
    {
        File file = this.provider.getFile(Link.assets(MODELS_PREFIX + id).combine("config.json"));

        if (file == null)
        {
            return false;
        }

        file.getParentFile().mkdirs();

        return DataToString.writeSilently(file, data, true);
    }

    /**
     * Write a model's groups back to the file it was read from, over the file as it stands: its
     * animations stay as they are (the instance's list also holds the ones the config pulls in from
     * other files, and those must not end up baked in), and so does anything else in it, such as
     * the exporter's version stamp. Only for a model the editor may edit
     * ({@link ModelInstance#getModelFile()}); returns whether the file was written.
     */
    public boolean saveModel(ModelInstance instance, List<String[]> renames)
    {
        Link link = instance.getModelFile();
        File file = link == null ? null : this.provider.getFile(link);

        if (file == null || !(instance.getModel() instanceof Model model))
        {
            return false;
        }

        MapType data = this.readModelFile(file);

        data.put("model", model.toData());

        if (!renames.isEmpty() && data.has("animations"))
        {
            renameAnimationBones(data.getMap("animations"), renames);
        }

        return DataToString.writeSilently(file, data, true);
    }

    /** Move the bone keys of every animation in the file along the renames, oldest first. */
    private static void renameAnimationBones(MapType animations, List<String[]> renames)
    {
        for (Map.Entry<String, BaseType> entry : animations)
        {
            if (!entry.getValue().isMap() || !entry.getValue().asMap().has("groups"))
            {
                continue;
            }

            MapType groups = entry.getValue().asMap().getMap("groups");

            for (String[] rename : renames)
            {
                if (groups.has(rename[0]))
                {
                    BaseType part = groups.get(rename[0]);

                    groups.remove(rename[0]);
                    groups.put(rename[1], part);
                }
            }
        }
    }

    /** The file as it stands, to be written over in place; an empty ordered map when it can't be read. */
    private MapType readModelFile(File file)
    {
        MapType data = null;

        try
        {
            data = CubicLoader.loadFile(new FileInputStream(file));
        }
        catch (IOException e)
        {
            System.err.println("Failed to read the model file before saving it: " + file);
        }

        return data == null ? new MapType(false) : data;
    }

    public void reload()
    {
        for (ModelInstance model : this.models.values())
        {
            if (model != null)
            {
                model.delete();
            }
        }

        this.models.clear();
        this.requested.clear();
        ModelPhysicsRuntime.clearCache();
        PoseManager.INSTANCE.clear();
        ShapeKeysManager.INSTANCE.clear();
        this.setupLoaders();
    }

    public boolean isRelodable(Link link)
    {
        if (!link.path.startsWith(MODELS_PREFIX))
        {
            return false;
        }

        if (link.path.contains("/animations/") || link.path.contains("/shapes/"))
        {
            return false;
        }

        return link.path.endsWith(".bbs.json")
            || link.path.endsWith(".geo.json")
            || link.path.endsWith(".bobj")
            || link.path.endsWith(".obj")
            || link.path.endsWith(".animation.json")
            || link.path.endsWith(".vox")
            || link.path.endsWith("/config.json");
    }

    /**
     * Watch dog listener implementation. This is a pretty bad hardcoded
     * solution that would only work for the cubic model loader.
     */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        if (this.isRelodable(link))
        {
            String key = StringUtils.parentPath(link.path.substring(MODELS_PREFIX.length()));
            ModelInstance model = this.models.remove(key);

            /* Un-mark it too, or the next getModel would treat the key as already queued and
             * the edited model would never reload. */
            this.requested.remove(key);

            if (model != null)
            {
                model.delete();
            }
        }
    }
}
