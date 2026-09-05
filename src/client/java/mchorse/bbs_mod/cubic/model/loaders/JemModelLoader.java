package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.jem.CemHierarchy;
import mchorse.bbs_mod.cubic.jem.JemModelParser;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for OptiFine CEM models (.jem entity model + referenced .jpm part models). Converts the
 * geometry into BBS's {@link Model} system via {@link JemModelParser}, the same way
 * {@link GeoCubicModelLoader} handles Bedrock .geo.json.
 *
 * <p>The texture reference inside the .jem/.jpm is intentionally ignored — these models use a single
 * texture, resolved here from the model folder (first PNG), like the other loaders.</p>
 */
public class JemModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Collection<Link> recursiveLinks = BBSMod.getProvider().getLinksFromPath(model, true);
        List<Link> modelJem = IModelLoader.getLinks(links, ".jem");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), recursiveLinks, ".png");

        if (modelJem.isEmpty())
        {
            return null;
        }

        Map<String, JsonObject> jpms = this.loadJpms(recursiveLinks);

        try (InputStream stream = BBSMod.getProvider().getAsset(modelJem.get(0)))
        {
            JsonObject jem = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();
            String entity = StringUtils.removeExtension(StringUtils.fileName(modelJem.get(0).path));
            JemModelParser.Result result = JemModelParser.parse(jem, jpms::get, models.parser, this.hierarchy(entity, config));
            Model modelModel = result.model();

            for (String warning : result.warnings())
            {
                System.err.println("OptiFine CEM model " + model + ": " + warning);
            }

            if (modelModel.topGroups.isEmpty())
            {
                return null;
            }

            ModelInstance newModel = new ModelInstance(id, modelModel, new Animations(models.parser), modelTexture);

            newModel.cemAnimation = result.animation();

            /* CEM models routinely overlap layers (headwear/jacket/sleeves); disable culling by
             * default so inner/overlapping faces don't vanish. A config.json can still override it. */
            newModel.config.culling.set(false);

            newModel.applyConfig(config);

            return newModel;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load OptiFine CEM .jem model: " + model);

            e.printStackTrace();
        }

        return null;
    }

    /**
     * The vanilla rig for this model: the built-in table for the entity (the .jem's file name), with the
     * {@code config.json}'s {@code cem_parents} laid over it — so any entity can be fixed with data.
     * Read straight off the map: the config is applied to the instance after parsing, and the parser
     * needs the hierarchy before.
     */
    private CemHierarchy hierarchy(String entity, MapType config)
    {
        Map<String, String> parents = new LinkedHashMap<>();

        if (config != null)
        {
            MapType overrides = config.getMap("cem_parents");

            for (String child : overrides.keys())
            {
                parents.put(child, overrides.getString(child));
            }
        }

        return CemHierarchy.forEntity(entity).withParents(parents);
    }

    /**
     * Preload every .jpm file in the model folder, keyed by file name (with and without extension),
     * so {@link JemModelParser} can resolve {@code "model"} references (typically same-folder names).
     */
    private Map<String, JsonObject> loadJpms(Collection<Link> links)
    {
        Map<String, JsonObject> jpms = new HashMap<>();

        for (Link link : IModelLoader.getLinks(links, ".jpm"))
        {
            try (InputStream stream = BBSMod.getProvider().getAsset(link))
            {
                JsonObject jpm = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();
                String name = StringUtils.fileName(link.path);

                jpms.put(name, jpm);
                jpms.put(StringUtils.removeExtension(name), jpm);
            }
            catch (Exception e)
            {
                System.err.println("Failed to load OptiFine CEM .jpm part: " + link);
            }
        }

        return jpms;
    }
}
