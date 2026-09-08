package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.jem.CemHierarchy;
import mchorse.bbs_mod.cubic.jem.VanillaRigs;
import mchorse.bbs_mod.cubic.jem.JemModelParser;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
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

        Link chosen = this.pickJem(modelJem, model);
        List<String> warnings = new ArrayList<>();

        for (Link ignored : modelJem)
        {
            if (ignored != chosen)
            {
                warnings.add(StringUtils.fileName(ignored.path) + " is left out - a folder is one model, and " + StringUtils.fileName(chosen.path)
                    + " is the one loaded; give the other .jem a folder of its own (an entity's cape or armour is a separate model "
                    + "in CEM too, and it can then wear its own texture)");
            }
        }

        Map<String, JsonObject> jpms = this.loadJpms(recursiveLinks);

        try (InputStream stream = BBSMod.getProvider().getAsset(chosen))
        {
            JsonObject jem = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();
            String entity = StringUtils.removeExtension(StringUtils.fileName(chosen.path));
            JemModelParser.Result result = JemModelParser.parse(jem, jpms::get, models.parser, this.hierarchy(entity, config));
            Model modelModel = result.model();

            warnings.addAll(result.warnings());

            for (String warning : warnings)
            {
                System.err.println("OptiFine CEM model " + model + ": " + warning);
            }

            if (modelModel.topGroups.isEmpty())
            {
                return null;
            }

            ModelInstance newModel = new ModelInstance(id, modelModel, new Animations(models.parser), modelTexture);

            newModel.cemAnimation = result.animation();
            newModel.warnings.addAll(warnings);

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
     * Which .jem a folder holding several stands for: the one named after the folder, else the first by
     * name. A CEM pack ships an entity as a set of models — {@code wolf.jem} beside {@code wolf_armor.jem},
     * {@code player.jem} beside {@code player_cape.jem} — and BBS loads a folder as one model, so one of
     * them has to win. Naming settles it, and the base model wins by name in the packs seen so far
     * (a variant carries a suffix), rather than whichever file the filesystem happened to answer first.
     */
    private Link pickJem(List<Link> jems, Link model)
    {
        String folder = StringUtils.fileName(model.path);

        for (Link link : jems)
        {
            if (StringUtils.removeExtension(StringUtils.fileName(link.path)).equals(folder))
            {
                return link;
            }
        }

        return jems.get(0);
    }

    /**
     * The vanilla rig for this model, in the order corrections are made: what Minecraft's own model for
     * the entity says ({@link VanillaRigs}), then the hand-checked table for what it cannot answer, then
     * this model's own {@code config.json} — so any entity can be fixed with data.
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

        return VanillaRigs.of(entity).with(CemHierarchy.forEntity(entity)).withParents(parents);
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
