package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ModelIKIO
{
    private ModelIKIO()
    {
    }

    public static File getFile(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        return BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/ik.json");
    }

    public static ModelIKConfig read(String modelId)
    {
        File file = getFile(modelId);

        if (file == null || !file.exists())
        {
            return null;
        }

        MapType map = readMap(file);

        if (map == null || map.isEmpty())
        {
            return null;
        }

        List<ModelIKConfig.Chain> chains = new ArrayList<>();
        List<String> controllers = new ArrayList<>(map.keys());

        for (String controller : controllers)
        {
            if (!map.has(controller, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = map.getMap(controller);
            String locator = entry.getString("locator");
            String root = entry.getString("root");

            if (locator.isEmpty() || root.isEmpty())
            {
                continue;
            }

            chains.add(new ModelIKConfig.Chain(controller, locator, root));
        }

        return chains.isEmpty() ? null : new ModelIKConfig(chains);
    }

    private static MapType readMap(File file)
    {
        try
        {
            BaseType parsed = DataToString.fromString(IOUtils.readText(file));

            if (parsed instanceof MapType map)
            {
                return map;
            }
        }
        catch (Exception e)
        {}

        return null;
    }
}
