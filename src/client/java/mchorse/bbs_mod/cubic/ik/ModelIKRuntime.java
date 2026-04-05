package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import java.util.List;

public final class ModelIKRuntime
{
    private ModelIKRuntime()
    {
    }

    public static void clearCache()
    {
        ModelIKCache.clear();
    }

    public static void invalidate(String modelId)
    {
        ModelIKCache.invalidate(modelId);
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.get(instance.id, model);

        if (compiled == null)
        {
            return;
        }

        List<ModelIKCache.CompiledChain> chains = compiled.chains();

        if (chains == null || chains.isEmpty())
        {
            return;
        }

        ModelIKApplier.apply(model, chains);
    }
}
