package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

public final class ModelIKIO
{
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

        List<ModelIKConfig.Chain> chains = new ArrayList<>();

        for (String tip : new ArrayList<>(map.keys()))
        {
            if (!map.has(tip, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = map.getMap(tip);
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

            chains.add(new ModelIKConfig.Chain(tip, target, chainLength, pole, poleTarget, poleAngle, softness, weight, enabled, tipRotation, stretch));
        }

        return chains.isEmpty() ? null : new ModelIKConfig(chains);
    }

    public static MapType toData(ModelIKConfig config)
    {
        MapType ik = new MapType();

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

                ik.put(chain.tip(), entry);
            }
        }

        return ik;
    }
}
