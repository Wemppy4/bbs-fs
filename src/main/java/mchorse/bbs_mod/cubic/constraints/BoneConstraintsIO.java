package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/**
 * The constraints exchange format: {@code {bones: {name: {enabled, min: [x,y,z], max: [x,y,z]}}}}.
 * It is what constraint presets are saved as, and what forms saved before the bones group stored
 * in their {@code constraints} blob — one reader serves both. The form itself now persists
 * constraints inside {@link ValueBones}, not in this format.
 */
public final class BoneConstraintsIO
{
    private BoneConstraintsIO()
    {
    }

    /**
     * Reads the exchange format into the bones group. With {@code reset}, every bone's
     * constraint is neutralized first — a preset is a complete state, so a bone absent
     * from it ends up unconstrained (legacy form loading passes false: the group is fresh).
     */
    public static void read(MapType root, ValueBones bones, boolean reset)
    {
        if (reset)
        {
            for (BaseValue value : bones.getAll())
            {
                if (value instanceof FormBone bone && bone.constraints.get().enabled)
                {
                    BoneConstraint neutral = bone.constraints.get().copy();

                    neutral.identity();
                    bone.constraints.set(neutral);
                }
            }
        }

        if (root == null || !root.has("bones", BaseType.TYPE_MAP))
        {
            return;
        }

        MapType boneMap = root.getMap("bones");

        for (String key : boneMap.keys())
        {
            if (key.isEmpty() || !boneMap.has(key, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = boneMap.getMap(key);

            if (!entry.getBool("enabled", true))
            {
                continue;
            }

            BoneConstraint constraint = new BoneConstraint();

            constraint.enabled = true;
            constraint.minX = getFloat(entry.getList("min"), 0, BoneConstraint.DEFAULT_MIN);
            constraint.minY = getFloat(entry.getList("min"), 1, BoneConstraint.DEFAULT_MIN);
            constraint.minZ = getFloat(entry.getList("min"), 2, BoneConstraint.DEFAULT_MIN);
            constraint.maxX = getFloat(entry.getList("max"), 0, BoneConstraint.DEFAULT_MAX);
            constraint.maxY = getFloat(entry.getList("max"), 1, BoneConstraint.DEFAULT_MAX);
            constraint.maxZ = getFloat(entry.getList("max"), 2, BoneConstraint.DEFAULT_MAX);

            bones.getOrCreate(key).constraints.set(constraint);
        }
    }

    /** Writes every enabled constraint in the exchange format; an empty result means none are. */
    public static MapType write(ValueBones bones)
    {
        MapType root = new MapType();
        MapType boneMap = new MapType();

        for (BaseValue value : bones.getAll())
        {
            if (!(value instanceof FormBone bone) || bone.getId().isEmpty())
            {
                continue;
            }

            BoneConstraint c = bone.constraints.get();

            if (!c.enabled)
            {
                continue;
            }

            MapType entry = new MapType();

            entry.putBool("enabled", true);

            ListType min = new ListType();

            min.addFloat(c.minX);
            min.addFloat(c.minY);
            min.addFloat(c.minZ);

            ListType max = new ListType();

            max.addFloat(c.maxX);
            max.addFloat(c.maxY);
            max.addFloat(c.maxZ);

            entry.put("min", min);
            entry.put("max", max);

            boneMap.put(bone.getId(), entry);
        }

        if (boneMap.size() == 0)
        {
            return new MapType();
        }

        root.put("bones", boneMap);

        return root;
    }

    private static float getFloat(ListType list, int index, float def)
    {
        BaseType element = list == null ? null : list.get(index);

        if (BaseType.isNumeric(element))
        {
            return element.asNumeric().floatValue();
        }

        return def;
    }
}
