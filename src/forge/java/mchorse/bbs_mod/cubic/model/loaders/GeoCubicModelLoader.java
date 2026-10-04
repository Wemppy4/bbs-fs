package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.cubic.geo.GeoModelParser;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public class GeoCubicModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Collection<Link> recursiveLinks = BBSMod.getProvider().getLinksFromPath(model, true);
        List<Link> modelGeo = IModelLoader.getLinks(links, ".geo.json");
        List<Link> modelAnimation = IModelLoader.getLinks(recursiveLinks, ".animation.json");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), recursiveLinks, ".png");

        if (modelGeo.isEmpty())
        {
            return null;
        }

        try (InputStream geoStream = BBSMod.getProvider().getAsset(modelGeo.get(0)))
        {
            JsonObject modelJson = new JsonParser().parse(IOUtils.readText(geoStream)).getAsJsonObject();
            Animations modelAnimations = new Animations(models.parser);

            for (Link link : modelAnimation)
            {
                try (InputStream stream = BBSMod.getProvider().getAsset(link))
                {
                    JsonObject jsonObject = new JsonParser().parse(IOUtils.readText(stream)).getAsJsonObject();
                    JsonObject animationsJson = jsonObject.get("animations").getAsJsonObject();

                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : animationsJson.entrySet())
                    {
                        String key = entry.getKey();
                        JsonObject animation = entry.getValue().getAsJsonObject();

                        modelAnimations.animations.put(key, GeoAnimationParser.parse(models.parser, key, animation));
                    }
                }
                catch (Exception e)
                {
                    System.err.println("Failed to load Bedrock entity .animation.json for model: " + model + " in " + link);
                }
            }

            Model modelModel = GeoModelParser.parse(modelJson, models.parser);
            ModelInstance newModel = new ModelInstance(id, modelModel, modelAnimations, modelTexture);
            newModel.setSourceFile(modelGeo.get(0));

            if (modelModel.topGroups.isEmpty())
            {
                return null;
            }

            newModel.applyConfig(config);

            return newModel;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load Bedrock entity .geo.json for model: " + model);

            e.printStackTrace();
        }

        return null;
    }
}
