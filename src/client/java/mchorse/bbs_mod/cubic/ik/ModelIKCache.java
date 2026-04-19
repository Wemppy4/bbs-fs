package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

final class ModelIKCache
{
    private ModelIKCache()
    {
    }

    public record CompiledChain(String controller, String locator, String root, float poleX, float poleY, float poleZ, ModelIKConfig.PoleSpace poleSpace, float weight, List<String> chainRootToEffector)
    {
    }

    public record Compiled(List<CompiledChain> chains)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();

    private record EmbeddedCompiled(IModel model, List<CompiledChain> chains)
    {
    }

    public static void clear()
    {
        EMBEDDED.clear();
    }

    public static Compiled getFromData(IModel model, MapType data)
    {
        if (model == null || data == null)
        {
            return null;
        }

        EmbeddedCompiled cached = EMBEDDED.get(data);

        if (cached != null && cached.model == model)
        {
            return new Compiled(cached.chains);
        }

        ModelIKConfig config = ModelIKIO.fromData(data);
        List<CompiledChain> compiled = compile(model, config);

        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled);
        EMBEDDED.put(data, next);

        return new Compiled(compiled);
    }

    private static List<CompiledChain> compile(IModel model, ModelIKConfig config)
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

            if (!chain.enabled())
            {
                continue;
            }

            if (!model.getAllGroupKeys().contains(chain.controller()) || !model.getAllGroupKeys().contains(chain.locator()) || !model.getAllGroupKeys().contains(chain.root()))
            {
                continue;
            }

            List<String> chainIds = buildChainIds(model, chain.locator(), chain.root());

            if (chainIds.size() < 2)
            {
                continue;
            }

            out.add(new CompiledChain(chain.controller(), chain.locator(), chain.root(), chain.poleX(), chain.poleY(), chain.poleZ(), chain.poleSpace(), ModelIKConfig.DEFAULT_WEIGHT, chainIds));
        }

        return out;
    }

    private static List<String> buildChainIds(IModel model, String effectorId, String rootId)
    {
        List<String> list = new ArrayList<>();
        String group = effectorId;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (group.equals(rootId))
            {
                java.util.Collections.reverse(list);
                return list;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        return java.util.Collections.emptyList();
    }
}
