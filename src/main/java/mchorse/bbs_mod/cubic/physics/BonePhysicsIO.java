package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueWindControl;

/**
 * The physics exchange format: {@code {"bones": {root: {...}}, "wind": {...}}} — chains keyed by
 * their root bone, plus the one global wind. It is what physics presets are saved as, and what
 * forms saved before the bones group stored in their {@code physics} blob — one reader serves
 * both. The form itself now persists the chains inside {@link ValueBones}, per bone, and the wind
 * as its own {@code wind} property.
 */
public final class BonePhysicsIO
{
    private BonePhysicsIO()
    {
    }

    /**
     * Reads the exchange format into the bones group and the wind property. With {@code reset},
     * every bone's chain and the wind are neutralized first — a preset is a complete state, so a
     * chain absent from it ends up gone (legacy form loading passes false: the group is fresh).
     */
    public static void read(MapType root, ValueBones bones, ValueWindControl wind, boolean reset)
    {
        if (reset)
        {
            for (BaseValue value : bones.getAll())
            {
                if (value instanceof FormBone bone && (bone.hasPhysicsChain() || !bone.physics.get().isDefault()))
                {
                    clearChain(bone);
                }
            }

            if (wind != null)
            {
                wind.set(new WindControl());
            }
        }

        if (root == null || root.isEmpty())
        {
            return;
        }

        if (root.has("bones", BaseType.TYPE_MAP))
        {
            MapType bonesMap = root.getMap("bones");

            for (String rootBone : bonesMap.keys())
            {
                if (rootBone.isEmpty() || !bonesMap.has(rootBone, BaseType.TYPE_MAP))
                {
                    continue;
                }

                MapType entry = bonesMap.getMap(rootBone);
                String end = entry.getString("end");

                if (end.isEmpty())
                {
                    continue;
                }

                FormBone bone = bones.getOrCreate(rootBone);

                bone.physicsEnd.set(end);
                bone.physicsTargetBone.set(entry.getString("target_bone", ""));
                bone.physicsIterations.set(Math.max(1, entry.getInt("iterations", 4)));
                bone.physicsCollisions.set(entry.getBool("collisions", false));
                bone.physicsRadius.set(Math.max(0F, entry.getFloat("radius", 0.1F)));
                bone.physicsRelativeGravity.set(entry.getBool("relative_gravity", false));
                bone.physicsGravityRotateX.set(entry.getFloat("relative_gravity_rotate_x", 0F));
                bone.physicsGravityRotateY.set(entry.getFloat("relative_gravity_rotate_y", 0F));
                bone.physicsGravityRotateZ.set(entry.getFloat("relative_gravity_rotate_z", 0F));

                PhysicsControl control = new PhysicsControl();

                control.gravity = entry.getFloat("gravity", PhysicsControl.DEFAULT_GRAVITY);
                control.damping = entry.getFloat("damping", PhysicsControl.DEFAULT_DAMPING);
                control.stiffness = clamp01(entry.getFloat("stiffness", PhysicsControl.DEFAULT_STIFFNESS));
                control.weight = clamp01(entry.getFloat("weight", PhysicsControl.DEFAULT_WEIGHT));
                control.enabled = entry.getBool("enabled", true);

                bone.physics.set(control);
            }
        }

        if (wind != null && root.has("wind", BaseType.TYPE_MAP))
        {
            MapType windMap = root.getMap("wind");
            WindControl control = new WindControl();

            control.strength = Math.max(0F, windMap.getFloat("strength", 0F));
            control.x = windMap.getFloat("x", 1F);
            control.y = windMap.getFloat("y", 0F);
            control.z = windMap.getFloat("z", 0F);
            control.turbulence = clamp01(windMap.getFloat("turbulence", 0.5F));
            control.turbulenceSpeed = Math.max(0F, windMap.getFloat("turbulence_speed", 1F));
            control.turbulenceScale = Math.max(0F, windMap.getFloat("turbulence_scale", 1F));
            control.local = windMap.getBool("local", false);

            wind.set(control);
        }
    }

    /** Writes every configured chain and the wind in the exchange format; empty means neither. */
    public static MapType write(ValueBones bones, ValueWindControl wind)
    {
        MapType root = new MapType();
        MapType bonesMap = new MapType();

        for (BaseValue value : bones.getAll())
        {
            if (!(value instanceof FormBone bone) || bone.getId().isEmpty() || !bone.hasPhysicsChain())
            {
                continue;
            }

            MapType entry = new MapType();
            PhysicsControl control = bone.physics.get();

            entry.putString("end", bone.physicsEnd.get());

            if (!bone.physicsTargetBone.get().isEmpty())
            {
                entry.putString("target_bone", bone.physicsTargetBone.get());
            }

            entry.putFloat("gravity", control.gravity);
            entry.putFloat("damping", control.damping);

            if (control.stiffness != PhysicsControl.DEFAULT_STIFFNESS)
            {
                entry.putFloat("stiffness", control.stiffness);
            }

            entry.putInt("iterations", bone.physicsIterations.get());

            if (bone.physicsRelativeGravity.get())
            {
                entry.putBool("relative_gravity", true);
            }

            if (bone.physicsGravityRotateX.get() != 0F)
            {
                entry.putFloat("relative_gravity_rotate_x", bone.physicsGravityRotateX.get());
            }

            if (bone.physicsGravityRotateY.get() != 0F)
            {
                entry.putFloat("relative_gravity_rotate_y", bone.physicsGravityRotateY.get());
            }

            if (bone.physicsGravityRotateZ.get() != 0F)
            {
                entry.putFloat("relative_gravity_rotate_z", bone.physicsGravityRotateZ.get());
            }

            if (bone.physicsCollisions.get())
            {
                entry.putBool("collisions", true);
            }

            if (bone.physicsRadius.get() != 0.1F)
            {
                entry.putFloat("radius", bone.physicsRadius.get());
            }

            if (control.weight != PhysicsControl.DEFAULT_WEIGHT)
            {
                entry.putFloat("weight", control.weight);
            }

            if (!control.enabled)
            {
                entry.putBool("enabled", false);
            }

            bonesMap.put(bone.getId(), entry);
        }

        WindControl control = wind == null ? null : wind.get();
        boolean hasWind = control != null && !control.isDefault();

        if (bonesMap.isEmpty() && !hasWind)
        {
            return root;
        }

        root.put("bones", bonesMap);

        if (hasWind)
        {
            MapType windMap = new MapType();

            windMap.putFloat("strength", control.strength);
            windMap.putFloat("x", control.x);
            windMap.putFloat("y", control.y);
            windMap.putFloat("z", control.z);
            windMap.putFloat("turbulence", control.turbulence);
            windMap.putFloat("turbulence_speed", control.turbulenceSpeed);
            windMap.putFloat("turbulence_scale", control.turbulenceScale);
            windMap.putBool("local", control.local);

            root.put("wind", windMap);
        }

        return root;
    }

    /** Neutralizes a bone's physics chain: the addresses, the configuration and the scalars. */
    public static void clearChain(FormBone bone)
    {
        bone.physicsEnd.set("");
        bone.physicsTargetBone.set("");
        bone.physicsIterations.set(4);
        bone.physicsCollisions.set(false);
        bone.physicsRadius.set(0.1F);
        bone.physicsRelativeGravity.set(false);
        bone.physicsGravityRotateX.set(0F);
        bone.physicsGravityRotateY.set(0F);
        bone.physicsGravityRotateZ.set(0F);
        bone.physics.set(new PhysicsControl());
    }

    private static float clamp01(float value)
    {
        return value < 0F ? 0F : Math.min(value, 1F);
    }
}
