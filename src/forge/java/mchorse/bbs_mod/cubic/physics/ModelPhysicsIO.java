package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.Map;

public final class ModelPhysicsIO
{
    private static final String KEY_BONES = "bones";
    private static final String KEY_END = "end";
    private static final String KEY_TARGET_BONE = "target_bone";
    private static final String KEY_GRAVITY = "gravity";
    private static final String KEY_DAMPING = "damping";
    private static final String KEY_STIFFNESS = "stiffness";
    private static final String KEY_ITERATIONS = "iterations";
    private static final String KEY_RELATIVE_GRAVITY = "relative_gravity";
    private static final String KEY_RELATIVE_GRAVITY_ROTATE_X = "relative_gravity_rotate_x";
    private static final String KEY_RELATIVE_GRAVITY_ROTATE_Y = "relative_gravity_rotate_y";
    private static final String KEY_RELATIVE_GRAVITY_ROTATE_Z = "relative_gravity_rotate_z";
    private static final String KEY_COLLISIONS = "collisions";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_WIND = "wind";
    private static final String KEY_WIND_STRENGTH = "strength";
    private static final String KEY_WIND_X = "x";
    private static final String KEY_WIND_Y = "y";
    private static final String KEY_WIND_Z = "z";
    private static final String KEY_WIND_TURBULENCE = "turbulence";
    private static final String KEY_WIND_TURBULENCE_SPEED = "turbulence_speed";
    private static final String KEY_WIND_TURBULENCE_SCALE = "turbulence_scale";
    private static final String KEY_WIND_LOCAL = "local";

    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final float DEFAULT_STIFFNESS = ModelPhysicsConfig.DEFAULT_STIFFNESS;
    private static final int DEFAULT_ITERATIONS = 4;
    private static final boolean DEFAULT_RELATIVE_GRAVITY = false;
    private static final float DEFAULT_RELATIVE_GRAVITY_ROTATE_X = 0F;
    private static final float DEFAULT_RELATIVE_GRAVITY_ROTATE_Y = 0F;
    private static final float DEFAULT_RELATIVE_GRAVITY_ROTATE_Z = 0F;
    private static final boolean DEFAULT_COLLISIONS = false;
    private static final float DEFAULT_RADIUS = 0.1F;
    private static final float DEFAULT_WEIGHT = ModelPhysicsConfig.DEFAULT_WEIGHT;
    private static final float DEFAULT_WIND_STRENGTH = ModelPhysicsConfig.Wind.NONE.strength();
    private static final float DEFAULT_WIND_X = ModelPhysicsConfig.Wind.NONE.x();
    private static final float DEFAULT_WIND_Y = ModelPhysicsConfig.Wind.NONE.y();
    private static final float DEFAULT_WIND_Z = ModelPhysicsConfig.Wind.NONE.z();
    private static final float DEFAULT_WIND_TURBULENCE = ModelPhysicsConfig.Wind.NONE.turbulence();
    private static final float DEFAULT_WIND_TURBULENCE_SPEED = ModelPhysicsConfig.Wind.NONE.turbulenceSpeed();
    private static final float DEFAULT_WIND_TURBULENCE_SCALE = ModelPhysicsConfig.Wind.NONE.turbulenceScale();
    private static final boolean DEFAULT_WIND_LOCAL = ModelPhysicsConfig.Wind.NONE.local();

    private ModelPhysicsIO()
    {
    }

    public static ModelPhysicsConfig fromData(MapType map)
    {
        if (map == null || !map.has(KEY_BONES, BaseType.TYPE_MAP))
        {
            return null;
        }

        MapType bones = map.getMap(KEY_BONES);
        Map<String, ModelPhysicsConfig.Bone> out = new HashMap<>();

        for (String root : bones.keys())
        {
            if (!bones.has(root, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = bones.getMap(root);
            String end = entry.getString(KEY_END);
            String targetBone = entry.getString(KEY_TARGET_BONE, "");

            if (root == null || root.isEmpty() || end == null || end.isEmpty())
            {
                continue;
            }

            float gravity = entry.getFloat(KEY_GRAVITY, DEFAULT_GRAVITY);
            float damping = entry.getFloat(KEY_DAMPING, DEFAULT_DAMPING);
            float stiffness = entry.getFloat(KEY_STIFFNESS, DEFAULT_STIFFNESS);
            int iterations = entry.getInt(KEY_ITERATIONS, DEFAULT_ITERATIONS);
            boolean relativeGravity = entry.getBool(KEY_RELATIVE_GRAVITY, DEFAULT_RELATIVE_GRAVITY);
            float relativeGravityRotateX = entry.getFloat(KEY_RELATIVE_GRAVITY_ROTATE_X, DEFAULT_RELATIVE_GRAVITY_ROTATE_X);
            float relativeGravityRotateY = entry.getFloat(KEY_RELATIVE_GRAVITY_ROTATE_Y, DEFAULT_RELATIVE_GRAVITY_ROTATE_Y);
            float relativeGravityRotateZ = entry.getFloat(KEY_RELATIVE_GRAVITY_ROTATE_Z, DEFAULT_RELATIVE_GRAVITY_ROTATE_Z);
            boolean collisions = entry.getBool(KEY_COLLISIONS, DEFAULT_COLLISIONS);
            float radius = entry.getFloat(KEY_RADIUS, DEFAULT_RADIUS);
            float weight = entry.getFloat(KEY_WEIGHT, DEFAULT_WEIGHT);

            out.put(root, new ModelPhysicsConfig.Bone(end, targetBone, gravity, damping, stiffness, iterations, relativeGravity, relativeGravityRotateX, relativeGravityRotateY, relativeGravityRotateZ, collisions, radius, weight));
        }

        ModelPhysicsConfig.Wind wind = readWind(map);

        if (out.isEmpty() && wind.isDefault())
        {
            return null;
        }

        return new ModelPhysicsConfig(out, wind);
    }

    private static ModelPhysicsConfig.Wind readWind(MapType map)
    {
        if (!map.has(KEY_WIND, BaseType.TYPE_MAP))
        {
            return ModelPhysicsConfig.Wind.NONE;
        }

        MapType wind = map.getMap(KEY_WIND);
        float strength = wind.getFloat(KEY_WIND_STRENGTH, DEFAULT_WIND_STRENGTH);
        float x = wind.getFloat(KEY_WIND_X, DEFAULT_WIND_X);
        float y = wind.getFloat(KEY_WIND_Y, DEFAULT_WIND_Y);
        float z = wind.getFloat(KEY_WIND_Z, DEFAULT_WIND_Z);
        float turbulence = wind.getFloat(KEY_WIND_TURBULENCE, DEFAULT_WIND_TURBULENCE);
        float turbulenceSpeed = wind.getFloat(KEY_WIND_TURBULENCE_SPEED, DEFAULT_WIND_TURBULENCE_SPEED);
        float turbulenceScale = wind.getFloat(KEY_WIND_TURBULENCE_SCALE, DEFAULT_WIND_TURBULENCE_SCALE);
        boolean local = wind.getBool(KEY_WIND_LOCAL, DEFAULT_WIND_LOCAL);

        return new ModelPhysicsConfig.Wind(strength, x, y, z, turbulence, turbulenceSpeed, turbulenceScale, local);
    }

    public static MapType toData(ModelPhysicsConfig config)
    {
        MapType root = new MapType();
        MapType bones = new MapType();

        if (config != null && config.bones() != null)
        {
            for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
            {
                String rootId = entry.getKey();
                ModelPhysicsConfig.Bone bone = entry.getValue();

                if (rootId == null || rootId.isEmpty() || bone == null || bone.end() == null || bone.end().isEmpty())
                {
                    continue;
                }

                MapType map = new MapType();
                map.putString(KEY_END, bone.end());

                if (bone.targetBone() != null && !bone.targetBone().isEmpty())
                {
                    map.putString(KEY_TARGET_BONE, bone.targetBone());
                }

                map.putFloat(KEY_GRAVITY, bone.gravity());
                map.putFloat(KEY_DAMPING, bone.damping());

                if (bone.stiffness() != DEFAULT_STIFFNESS)
                {
                    map.putFloat(KEY_STIFFNESS, bone.stiffness());
                }

                map.putInt(KEY_ITERATIONS, bone.iterations());

                if (bone.relativeGravity())
                {
                    map.putBool(KEY_RELATIVE_GRAVITY, true);
                }

                if (bone.relativeGravityRotateX() != DEFAULT_RELATIVE_GRAVITY_ROTATE_X)
                {
                    map.putFloat(KEY_RELATIVE_GRAVITY_ROTATE_X, bone.relativeGravityRotateX());
                }

                if (bone.relativeGravityRotateY() != DEFAULT_RELATIVE_GRAVITY_ROTATE_Y)
                {
                    map.putFloat(KEY_RELATIVE_GRAVITY_ROTATE_Y, bone.relativeGravityRotateY());
                }

                if (bone.relativeGravityRotateZ() != DEFAULT_RELATIVE_GRAVITY_ROTATE_Z)
                {
                    map.putFloat(KEY_RELATIVE_GRAVITY_ROTATE_Z, bone.relativeGravityRotateZ());
                }

                if (bone.collisions())
                {
                    map.putBool(KEY_COLLISIONS, true);
                }

                if (bone.radius() != DEFAULT_RADIUS)
                {
                    map.putFloat(KEY_RADIUS, bone.radius());
                }

                if (bone.weight() != DEFAULT_WEIGHT)
                {
                    map.putFloat(KEY_WEIGHT, bone.weight());
                }

                bones.put(rootId, map);
            }
        }

        root.put(KEY_BONES, bones);
        writeWind(root, config == null ? null : config.wind());

        return root;
    }

    private static void writeWind(MapType root, ModelPhysicsConfig.Wind wind)
    {
        if (wind == null || wind.isDefault())
        {
            return;
        }

        MapType windMap = new MapType();

        if (wind.strength() != DEFAULT_WIND_STRENGTH)
        {
            windMap.putFloat(KEY_WIND_STRENGTH, wind.strength());
        }

        if (wind.x() != DEFAULT_WIND_X)
        {
            windMap.putFloat(KEY_WIND_X, wind.x());
        }

        if (wind.y() != DEFAULT_WIND_Y)
        {
            windMap.putFloat(KEY_WIND_Y, wind.y());
        }

        if (wind.z() != DEFAULT_WIND_Z)
        {
            windMap.putFloat(KEY_WIND_Z, wind.z());
        }

        if (wind.turbulence() != DEFAULT_WIND_TURBULENCE)
        {
            windMap.putFloat(KEY_WIND_TURBULENCE, wind.turbulence());
        }

        if (wind.turbulenceSpeed() != DEFAULT_WIND_TURBULENCE_SPEED)
        {
            windMap.putFloat(KEY_WIND_TURBULENCE_SPEED, wind.turbulenceSpeed());
        }

        if (wind.turbulenceScale() != DEFAULT_WIND_TURBULENCE_SCALE)
        {
            windMap.putFloat(KEY_WIND_TURBULENCE_SCALE, wind.turbulenceScale());
        }

        if (wind.local() != DEFAULT_WIND_LOCAL)
        {
            windMap.putBool(KEY_WIND_LOCAL, wind.local());
        }

        if (!windMap.isEmpty())
        {
            root.put(KEY_WIND, windMap);
        }
    }
}
