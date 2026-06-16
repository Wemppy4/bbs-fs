package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

final class ModelIKCache
{
    private ModelIKCache()
    {
    }

    public record CompiledChain(String tip, String target, boolean pole, String poleTarget, float softness, float weight, List<String> chainRootToEffector)
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
            return Collections.emptyList();
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

            if (!model.getAllGroupKeys().contains(chain.tip()) || !model.getAllGroupKeys().contains(chain.target()))
            {
                continue;
            }

            List<String> chainIds = buildChainIds(model, chain.tip(), chain.chainLength());

            if (chainIds.size() < 2)
            {
                continue;
            }

            /* A pole target that does not resolve to a real bone falls back to
             * the automatic hinge (an empty pole target), so a stale reference
             * never breaks the chain. */
            String poleTarget = chain.poleTarget();

            if (poleTarget != null && !poleTarget.isEmpty() && !model.getAllGroupKeys().contains(poleTarget))
            {
                poleTarget = "";
            }

            out.add(new CompiledChain(chain.tip(), chain.target(), chain.pole(), poleTarget, chain.softness(), chain.weight(), chainIds));
        }

        return out;
    }

    /**
     * Walks up the hierarchy from {@code tip}, collecting up to {@code chainLength}
     * bones ({@code 0} = all the way to the root), and returns them ordered
     * root-to-tip.
     */
    private static List<String> buildChainIds(IModel model, String tip, int chainLength)
    {
        List<String> list = new ArrayList<>();
        String group = tip;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (chainLength > 0 && list.size() >= chainLength)
            {
                break;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        Collections.reverse(list);

        return list;
    }
}
