package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig.BoneConstraint;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        clearCache();
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        ModelIKCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm form && form.ik.get() instanceof MapType map)
        {
            compiled = ModelIKCache.getFromData(model, map);
        }

        if (compiled == null)
        {
            return;
        }

        List<ModelIKCache.CompiledChain> chains = compiled.chains();

        if (chains == null || chains.isEmpty())
        {
            return;
        }

        Map<String, BoneConstraint> boneLimits = ModelConstraintsRuntime.getBones(instance);
        Map<String, IKControl> controlOverrides = instance.form instanceof ModelForm form ? form.ikControlOverrides : null;

        ModelIKApplier.apply(model, chains, controllerTargets, poleTargets, controlOverrides, boneLimits);
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return java.util.Collections.emptyList();
        }

        IModel model = instance.model;

        ModelIKCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm form && form.ik.get() instanceof MapType map)
        {
            compiled = ModelIKCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain != null && chain.target() != null && !chain.target().isEmpty())
            {
                unique.add(chain.target());
            }
        }

        return unique.isEmpty() ? java.util.Collections.emptyList() : new ArrayList<>(unique);
    }

    /** The pole-target bones of all enabled chains that have one — the film keys a pole anchor sheet off each. */
    public static List<String> getPoleControllers(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return java.util.Collections.emptyList();
        }

        IModel model = instance.model;

        ModelIKCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm form && form.ik.get() instanceof MapType map)
        {
            compiled = ModelIKCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain != null && chain.pole() && chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                unique.add(chain.poleTarget());
            }
        }

        return unique.isEmpty() ? java.util.Collections.emptyList() : new ArrayList<>(unique);
    }
}
