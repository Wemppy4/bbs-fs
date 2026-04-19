package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ModelIKRuntime
{
    private static final float HYSTERESIS_RAD = (float) Math.toRadians(20F);
    private static final float SINGULARITY_RAD = (float) Math.toRadians(3F);

    private static final class InstanceState
    {
        public final Map<String, Vector3f> prevNormals = new HashMap<>();
    }

    private static final WeakHashMap<ModelInstance, InstanceState> STATES = new WeakHashMap<>();

    private ModelIKRuntime()
    {
    }

    public static void clearCache()
    {
        ModelIKCache.clear();
        STATES.clear();
    }

    public static void invalidate(String modelId)
    {
        clearCache();
    }

    public static void apply(ModelInstance instance)
    {
        apply(instance, null, null);
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets)
    {
        apply(instance, controllerTargets, null);
    }

    public static void applyWithPoseFix(ModelInstance instance, Map<String, Float> poseFixByBone)
    {
        apply(instance, null, poseFixByBone);
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets, Map<String, Float> poseFixByBone)
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

        InstanceState state = STATES.computeIfAbsent(instance, (k) -> new InstanceState());
        ModelIKApplier.apply(model, chains, controllerTargets, state.prevNormals, HYSTERESIS_RAD, SINGULARITY_RAD, poseFixByBone);
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
            if (chain != null && chain.controller() != null && !chain.controller().isEmpty())
            {
                unique.add(chain.controller());
            }
        }

        return unique.isEmpty() ? java.util.Collections.emptyList() : new ArrayList<>(unique);
    }
}
