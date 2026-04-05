package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelIKCache
{
    private ModelIKCache()
    {
    }

    public record CompiledChain(String controller, String locator, String root, List<String> chainRootToEffector)
    {
    }

    public record Compiled(File file, long lastModified, List<CompiledChain> chains)
    {
    }

    private static final Map<String, Compiled> CACHE = new HashMap<>();

    public static void clear()
    {
        CACHE.clear();
    }

    public static void invalidate(String modelId)
    {
        if (modelId != null && !modelId.isEmpty())
        {
            CACHE.remove(modelId);
        }
    }

    public static Compiled get(String modelId, Model model)
    {
        if (modelId == null || modelId.isEmpty() || model == null)
        {
            return null;
        }

        File file = ModelIKIO.getFile(modelId);
        long lm = file != null && file.exists() ? file.lastModified() : -1L;

        Compiled cached = CACHE.get(modelId);

        if (cached != null && cached.lastModified == lm)
        {
            return cached;
        }

        ModelIKConfig config = ModelIKIO.read(modelId);
        List<CompiledChain> compiled = compile(model, config);

        Compiled next = compiled.isEmpty() ? null : new Compiled(file, lm, compiled);

        if (next == null)
        {
            CACHE.remove(modelId);
        }
        else
        {
            CACHE.put(modelId, next);
        }

        return next;
    }

    private static List<CompiledChain> compile(Model model, ModelIKConfig config)
    {
        if (config == null || config.chains() == null || config.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>(config.chains().size());

        for (ModelIKConfig.Chain chain : config.chains())
        {
            if (chain == null)
            {
                continue;
            }

            ModelGroup controller = model.getGroup(chain.controller());
            ModelGroup effector = model.getGroup(chain.locator());
            ModelGroup root = model.getGroup(chain.root());

            if (controller == null || effector == null || root == null)
            {
                continue;
            }

            List<String> chainIds = buildChainIds(effector, root);

            if (chainIds.size() < 2)
            {
                continue;
            }

            out.add(new CompiledChain(chain.controller(), chain.locator(), chain.root(), chainIds));
        }

        return out;
    }

    private static List<String> buildChainIds(ModelGroup effector, ModelGroup root)
    {
        List<String> list = new ArrayList<>();
        ModelGroup group = effector;

        while (group != null)
        {
            list.add(group.id);

            if (group == root)
            {
                java.util.Collections.reverse(list);
                return list;
            }

            group = group.parent;
        }

        return java.util.Collections.emptyList();
    }
}
