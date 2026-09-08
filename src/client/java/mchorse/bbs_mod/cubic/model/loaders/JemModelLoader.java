package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.jem.CemHierarchy;
import mchorse.bbs_mod.cubic.jem.CemNames;
import mchorse.bbs_mod.cubic.jem.VanillaRigs;
import mchorse.bbs_mod.cubic.jem.JemModelParser;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Vector3f;

import java.io.IOException;
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
 * <p>The texture reference inside the .jem/.jpm is intentionally ignored — the model's texture is
 * resolved from the model folder ({@code model.png} first), like the other loaders. A layer over the
 * entity in the same folder — {@code <entity>_outer.jem}, {@code _armor}, {@code _saddle},
 * {@code _wool}… — is folded into the model as a material of its own, wearing the texture of
 * {@code textures/<layer>/} (see {@link JemModelParser#graft} and {@link CemNames#layer}).</p>
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
        String entity = StringUtils.removeExtension(StringUtils.fileName(chosen.path));
        CemHierarchy hierarchy = this.hierarchy(entity, config);
        Map<String, JsonObject> jpms = this.loadJpms(recursiveLinks);
        List<String> warnings = new ArrayList<>();

        try
        {
            JemModelParser.Result result = this.parse(chosen, jpms, models, hierarchy);
            Model modelModel = result.model();

            warnings.addAll(result.warnings());

            if (modelModel.topGroups.isEmpty())
            {
                this.print(model, warnings);

                return null;
            }

            /* The folder's other files: a layer over the entity is folded into its model as a
             * material, anything else is left out - a folder is one model. */
            List<String> layers = new ArrayList<>();

            for (Link link : modelJem)
            {
                if (link == chosen)
                {
                    continue;
                }

                String name = StringUtils.removeExtension(StringUtils.fileName(link.path));
                CemNames.Layer layer = CemNames.layer(name);

                if (layer != null && layer.base().equals(entity))
                {
                    JemModelParser.Result folded = this.parse(link, jpms, models, hierarchy);

                    for (String warning : folded.warnings())
                    {
                        warnings.add(layer.name() + ": " + warning);
                    }

                    for (String warning : JemModelParser.graft(result, folded, layer.name()))
                    {
                        warnings.add(layer.name() + ": " + warning);
                    }

                    layers.add(layer.name());
                }
                else
                {
                    warnings.add(name + ".jem is left out - a folder is one model, and " + entity + ".jem is the one loaded; a layer over it ("
                        + entity + "_outer, _armor, _saddle, _wool...) would be folded in, anything else wants a folder of its own");
                }
            }

            this.print(model, warnings);

            ModelInstance newModel = new ModelInstance(id, modelModel, new Animations(models.parser), modelTexture);

            newModel.cemAnimation = result.animation();
            newModel.cemAnimation.jem = entity;
            newModel.warnings.addAll(warnings);

            this.materials(newModel, model, recursiveLinks, entity, modelTexture, layers);

            /* CEM models routinely overlap layers (headwear/jacket/sleeves); disable culling by
             * default so inner/overlapping faces don't vanish. A config.json can still override it. */
            newModel.config.culling.set(false);

            /* Vanilla draws the young at half size with the feet on the ground, whatever the model -
             * AnimalModel and BipedEntityModel scale a child's body parts by 0.5 as they render - and a
             * pack draws its _baby file at the adult's scale knowing that. The model's own scale carries
             * it here, and a config.json can still override it. */
            if (CemNames.baby(entity))
            {
                newModel.config.scale.set(new Vector3f(0.5F));
            }

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

    /** Read and parse one .jem of the folder, against the model's rig. */
    private JemModelParser.Result parse(Link link, Map<String, JsonObject> jpms, ModelManager models, CemHierarchy hierarchy) throws IOException
    {
        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            JsonObject jem = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();

            return JemModelParser.parse(jem, jpms::get, models.parser, hierarchy);
        }
    }

    private void print(Link model, List<String> warnings)
    {
        for (String warning : warnings)
        {
            System.err.println("OptiFine CEM model " + model + ": " + warning);
        }
    }

    /**
     * With a layer folded in the model has more than one texture, and the base becomes a material too,
     * named after the entity, so that everything keyed by material comes on: the texture picked per
     * material, the material tab, the texture tracks. A layer wears the texture of its own folder,
     * {@code textures/<layer>/}, {@code model.png} first; without one it falls back to the base's at
     * render time, and the folder is surfaced for the user to drop one into. A model with no layer
     * stays a single-texture one.
     */
    private void materials(ModelInstance instance, Link model, Collection<Link> links, String entity, Link texture, List<String> layers)
    {
        if (layers.isEmpty())
        {
            return;
        }

        for (ModelGroup group : instance.model.getAllGroups())
        {
            for (ModelCube cube : group.cubes)
            {
                if (cube.material.isEmpty())
                {
                    cube.material = entity;
                }
            }
        }

        instance.materials.add(entity);

        if (texture != null)
        {
            instance.materialTextures.put(entity, texture);
        }

        for (String layer : layers)
        {
            Link preferred = model.combine("textures/" + layer + "/model.png");
            Link layerTexture = links.contains(preferred) ? preferred : IModelLoader.findMaterialTexture(links, model, layer);

            instance.materials.add(layer);

            if (layerTexture != null)
            {
                instance.materialTextures.put(layer, layerTexture);
            }
            else
            {
                IModelLoader.ensureMaterialFolder(BBSMod.getProvider(), model, layer);
            }
        }
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
     * The vanilla rig for this model: what Minecraft's own model for the entity says ({@link VanillaRigs}),
     * with this model's own {@code config.json} laid over it — so any entity can be fixed with data. The
     * game is asked about the entity behind the file name ({@link CemNames#entity}): a
     * {@code cold_cow_baby} is a cow. Read straight off the map: the config is applied to the instance
     * after parsing, and the parser needs the hierarchy before.
     */
    private CemHierarchy hierarchy(String file, MapType config)
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

        return VanillaRigs.of(CemNames.entity(file)).withParents(parents);
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
