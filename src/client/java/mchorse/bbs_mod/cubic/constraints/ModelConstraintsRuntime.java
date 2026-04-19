package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ModelConstraintsRuntime
{
    private static final WeakHashMap<MapType, Map<String, ModelConstraintsConfig.BoneConstraint>> EMBEDDED = new WeakHashMap<>();

    private ModelConstraintsRuntime()
    {
    }

    public static void clearCache()
    {
        EMBEDDED.clear();
    }

    public static void invalidate(String modelId)
    {
        EMBEDDED.clear();
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> bones = getBones(instance);

        if (bones == null || bones.isEmpty())
        {
            return;
        }

        if (instance.model instanceof Model model)
        {
            applyToModel(model, bones);
        }
        else if (instance.model instanceof BOBJModel bobj)
        {
            applyToBobj(bobj, bones);
        }
    }

    public static Map<String, ModelConstraintsConfig.BoneConstraint> getBones(ModelInstance instance)
    {
        if (instance != null && instance.form instanceof ModelForm form && form.constraints.get() instanceof MapType map)
        {
            Map<String, ModelConstraintsConfig.BoneConstraint> cached = EMBEDDED.get(map);

            if (cached != null)
            {
                return cached;
            }

            ModelConstraintsConfig config = ModelConstraintsIO.fromData(map);
            Map<String, ModelConstraintsConfig.BoneConstraint> bones = config == null || config.bones() == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(config.bones()));

            EMBEDDED.put(map, bones);

            return bones;
        }

        return Collections.emptyMap();
    }

    private static void applyToModel(Model model, Map<String, ModelConstraintsConfig.BoneConstraint> bones)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null)
            {
                continue;
            }

            ModelConstraintsConfig.BoneConstraint c = bones.get(group.id);

            if (c == null || !c.enabled())
            {
                continue;
            }

            float minX = c.minX();
            float minY = c.minY();
            float minZ = c.minZ();
            float maxX = c.maxX();
            float maxY = c.maxY();
            float maxZ = c.maxZ();

            if (minX > maxX)
            {
                float t = minX;
                minX = maxX;
                maxX = t;
            }

            if (minY > maxY)
            {
                float t = minY;
                minY = maxY;
                maxY = t;
            }

            if (minZ > maxZ)
            {
                float t = minZ;
                minZ = maxZ;
                maxZ = t;
            }

            group.current.rotate.x = MathUtils.clamp(group.current.rotate.x, minX, maxX);
            group.current.rotate.y = MathUtils.clamp(group.current.rotate.y, minY, maxY);
            group.current.rotate.z = MathUtils.clamp(group.current.rotate.z, minZ, maxZ);
        }
    }

    private static void applyToBobj(BOBJModel model, Map<String, ModelConstraintsConfig.BoneConstraint> bones)
    {
        for (BOBJBone bone : model.getArmature().orderedBones)
        {
            if (bone == null)
            {
                continue;
            }

            ModelConstraintsConfig.BoneConstraint c = bones.get(bone.name);

            if (c == null || !c.enabled())
            {
                continue;
            }

            float minX = (float) Math.toRadians(c.minX());
            float minY = (float) Math.toRadians(c.minY());
            float minZ = (float) Math.toRadians(c.minZ());
            float maxX = (float) Math.toRadians(c.maxX());
            float maxY = (float) Math.toRadians(c.maxY());
            float maxZ = (float) Math.toRadians(c.maxZ());

            if (minX > maxX)
            {
                float t = minX;
                minX = maxX;
                maxX = t;
            }

            if (minY > maxY)
            {
                float t = minY;
                minY = maxY;
                maxY = t;
            }

            if (minZ > maxZ)
            {
                float t = minZ;
                minZ = maxZ;
                maxZ = t;
            }

            bone.transform.rotate.x = MathUtils.clamp(bone.transform.rotate.x, minX, maxX);
            bone.transform.rotate.y = MathUtils.clamp(bone.transform.rotate.y, minY, maxY);
            bone.transform.rotate.z = MathUtils.clamp(bone.transform.rotate.z, minZ, maxZ);
        }
    }

}
