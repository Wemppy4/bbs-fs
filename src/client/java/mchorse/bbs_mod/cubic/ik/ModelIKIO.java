package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IK config IO. The stored shape is {@code {"chains": {tip: {...}}, "bones":
 * {bone: {...}}}} — chains keyed by their tip bone, per-bone joint freedom
 * keyed by bone. Data written before the joints existed was the flat chains
 * map itself (no wrapper); it is still read: a map WITHOUT a "chains" key is
 * taken as the legacy flat form, so old scenes and presets load unchanged.
 *
 * <p>That missing wrapper doubles as the version marker of the IK redesign:
 * a flat map was authored on the OLD position-level solver, so its chains
 * migrate with {@code classic} ON — a two-bone limb keeps solving the way it
 * was posed and nothing an animator already tuned shifts under them. Chains
 * of another shape carry the flag harmlessly: the analytic path takes only
 * two directed bones and hands everything else to the core (the same core
 * they would land on anyway, the old FABRIK/CCD branches being gone), and
 * the panel marks that fallback right on the toggle.</p>
 */
public final class ModelIKIO
{
    private static final String KEY_CHAINS = "chains";
    private static final String KEY_BONES = "bones";

    private static final String KEY_TARGET = "target";
    private static final String KEY_CHAIN_LENGTH = "chain_length";
    private static final String KEY_POLE = "pole";
    private static final String KEY_POLE_TARGET = "pole_target";
    private static final String KEY_POLE_ANGLE = "pole_angle";
    private static final String KEY_SOFTNESS = "softness";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_TIP_ROTATION = "tip_rotation";
    private static final String KEY_STRETCH = "stretch";
    private static final String KEY_CLASSIC = "classic";

    private static final String KEY_LOCK = "lock";
    private static final String KEY_LIMITED = "limited";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_STIFFNESS = "stiffness";

    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_POLE = true;

    private ModelIKIO()
    {
    }

    public static ModelIKConfig fromData(MapType map)
    {
        if (map == null || map.isEmpty())
        {
            return null;
        }

        boolean wrapped = map.has(KEY_CHAINS, BaseType.TYPE_MAP);
        MapType chainsMap = wrapped ? map.getMap(KEY_CHAINS) : map;

        /* Pre-redesign data: the old solver is what these chains were posed
         * against, so they come back with it on. */
        boolean defaultClassic = wrapped ? ModelIKConfig.DEFAULT_CLASSIC : true;

        List<ModelIKConfig.Chain> chains = new ArrayList<>();

        for (String tip : new ArrayList<>(chainsMap.keys()))
        {
            if (!chainsMap.has(tip, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = chainsMap.getMap(tip);
            String target = entry.getString(KEY_TARGET);

            if (target.isEmpty())
            {
                continue;
            }

            int chainLength = entry.getInt(KEY_CHAIN_LENGTH, ModelIKConfig.DEFAULT_CHAIN_LENGTH);
            boolean pole = entry.getBool(KEY_POLE, DEFAULT_POLE);
            String poleTarget = entry.getString(KEY_POLE_TARGET);
            float poleAngle = (float) entry.getDouble(KEY_POLE_ANGLE, ModelIKConfig.DEFAULT_POLE_ANGLE);
            float softness = (float) entry.getDouble(KEY_SOFTNESS, ModelIKConfig.DEFAULT_SOFTNESS);
            float weight = (float) entry.getDouble(KEY_WEIGHT, ModelIKConfig.DEFAULT_WEIGHT);
            boolean enabled = entry.getBool(KEY_ENABLED, DEFAULT_ENABLED);
            boolean tipRotation = entry.getBool(KEY_TIP_ROTATION, ModelIKConfig.DEFAULT_TIP_ROTATION);
            boolean stretch = entry.getBool(KEY_STRETCH, ModelIKConfig.DEFAULT_STRETCH);
            boolean classic = entry.getBool(KEY_CLASSIC, defaultClassic);

            chains.add(new ModelIKConfig.Chain(tip, target, chainLength, pole, poleTarget, poleAngle, softness, weight, enabled, tipRotation, stretch, classic));
        }

        Map<String, ModelIKConfig.JointDoF> bones = new HashMap<>();

        if (map.has(KEY_BONES, BaseType.TYPE_MAP))
        {
            MapType bonesMap = map.getMap(KEY_BONES);

            for (String bone : bonesMap.keys())
            {
                if (!bonesMap.has(bone, BaseType.TYPE_MAP))
                {
                    continue;
                }

                ModelIKConfig.JointDoF joint = jointFromData(bonesMap.getMap(bone));

                if (!joint.isFree())
                {
                    bones.put(bone, joint);
                }
            }
        }

        return chains.isEmpty() && bones.isEmpty() ? null : new ModelIKConfig(chains, bones);
    }

    public static MapType toData(ModelIKConfig config)
    {
        MapType root = new MapType();
        MapType chains = new MapType();

        if (config != null && config.chains() != null)
        {
            for (ModelIKConfig.Chain chain : config.chains())
            {
                if (chain == null || chain.tip() == null || chain.tip().isEmpty())
                {
                    continue;
                }

                if (chain.target() == null || chain.target().isEmpty())
                {
                    continue;
                }

                MapType entry = new MapType();
                entry.putString(KEY_TARGET, chain.target());
                entry.putBool(KEY_ENABLED, chain.enabled());

                if (chain.chainLength() != ModelIKConfig.DEFAULT_CHAIN_LENGTH)
                {
                    entry.putInt(KEY_CHAIN_LENGTH, chain.chainLength());
                }

                if (chain.pole() != DEFAULT_POLE)
                {
                    entry.putBool(KEY_POLE, chain.pole());
                }

                if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
                {
                    entry.putString(KEY_POLE_TARGET, chain.poleTarget());
                }

                if (chain.poleAngle() != ModelIKConfig.DEFAULT_POLE_ANGLE)
                {
                    entry.putDouble(KEY_POLE_ANGLE, chain.poleAngle());
                }

                if (chain.softness() != ModelIKConfig.DEFAULT_SOFTNESS)
                {
                    entry.putDouble(KEY_SOFTNESS, chain.softness());
                }

                if (chain.weight() != ModelIKConfig.DEFAULT_WEIGHT)
                {
                    entry.putDouble(KEY_WEIGHT, chain.weight());
                }

                if (chain.tipRotation() != ModelIKConfig.DEFAULT_TIP_ROTATION)
                {
                    entry.putBool(KEY_TIP_ROTATION, chain.tipRotation());
                }

                if (chain.stretch() != ModelIKConfig.DEFAULT_STRETCH)
                {
                    entry.putBool(KEY_STRETCH, chain.stretch());
                }

                if (chain.classic() != ModelIKConfig.DEFAULT_CLASSIC)
                {
                    entry.putBool(KEY_CLASSIC, chain.classic());
                }

                chains.put(chain.tip(), entry);
            }
        }

        MapType bones = new MapType();

        if (config != null && config.bones() != null)
        {
            for (Map.Entry<String, ModelIKConfig.JointDoF> entry : config.bones().entrySet())
            {
                String bone = entry.getKey();
                ModelIKConfig.JointDoF joint = entry.getValue();

                if (bone == null || bone.isEmpty() || joint == null || joint.isFree())
                {
                    continue;
                }

                bones.put(bone, jointToData(joint));
            }
        }

        if (chains.isEmpty() && bones.isEmpty())
        {
            return root;
        }

        root.put(KEY_CHAINS, chains);

        if (!bones.isEmpty())
        {
            root.put(KEY_BONES, bones);
        }

        return root;
    }

    private static ModelIKConfig.JointDoF jointFromData(MapType map)
    {
        boolean lockX = false, lockY = false, lockZ = false;
        boolean limitX = false, limitY = false, limitZ = false;
        float minX = ModelIKConfig.JointDoF.DEFAULT_MIN, minY = minX, minZ = minX;
        float maxX = ModelIKConfig.JointDoF.DEFAULT_MAX, maxY = maxX, maxZ = maxX;
        float stiffnessX = 0F, stiffnessY = 0F, stiffnessZ = 0F;

        if (map.has(KEY_LOCK, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_LOCK);

            lockX = list.getBool(0);
            lockY = list.getBool(1);
            lockZ = list.getBool(2);
        }

        if (map.has(KEY_LIMITED, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_LIMITED);

            limitX = list.getBool(0);
            limitY = list.getBool(1);
            limitZ = list.getBool(2);
        }

        if (map.has(KEY_MIN, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_MIN);

            minX = getFloat(list, 0, minX);
            minY = getFloat(list, 1, minY);
            minZ = getFloat(list, 2, minZ);
        }

        if (map.has(KEY_MAX, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_MAX);

            maxX = getFloat(list, 0, maxX);
            maxY = getFloat(list, 1, maxY);
            maxZ = getFloat(list, 2, maxZ);
        }

        if (map.has(KEY_STIFFNESS, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_STIFFNESS);

            stiffnessX = getFloat(list, 0, 0F);
            stiffnessY = getFloat(list, 1, 0F);
            stiffnessZ = getFloat(list, 2, 0F);
        }

        return new ModelIKConfig.JointDoF(lockX, lockY, lockZ,
            limitX, minX, maxX,
            limitY, minY, maxY,
            limitZ, minZ, maxZ,
            stiffnessX, stiffnessY, stiffnessZ);
    }

    private static MapType jointToData(ModelIKConfig.JointDoF joint)
    {
        MapType map = new MapType();

        if (joint.lockX() || joint.lockY() || joint.lockZ())
        {
            ListType lock = new ListType();

            lock.addBool(joint.lockX());
            lock.addBool(joint.lockY());
            lock.addBool(joint.lockZ());
            map.put(KEY_LOCK, lock);
        }

        if (joint.limitX() || joint.limitY() || joint.limitZ())
        {
            ListType limited = new ListType();

            limited.addBool(joint.limitX());
            limited.addBool(joint.limitY());
            limited.addBool(joint.limitZ());
            map.put(KEY_LIMITED, limited);

            ListType min = new ListType();

            min.addFloat(joint.minX());
            min.addFloat(joint.minY());
            min.addFloat(joint.minZ());
            map.put(KEY_MIN, min);

            ListType max = new ListType();

            max.addFloat(joint.maxX());
            max.addFloat(joint.maxY());
            max.addFloat(joint.maxZ());
            map.put(KEY_MAX, max);
        }

        if (joint.stiffnessX() > 0F || joint.stiffnessY() > 0F || joint.stiffnessZ() > 0F)
        {
            ListType stiffness = new ListType();

            stiffness.addFloat(joint.stiffnessX());
            stiffness.addFloat(joint.stiffnessY());
            stiffness.addFloat(joint.stiffnessZ());
            map.put(KEY_STIFFNESS, stiffness);
        }

        return map;
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
