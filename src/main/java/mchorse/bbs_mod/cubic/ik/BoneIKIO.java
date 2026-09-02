package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/**
 * The IK exchange format: {@code {"chains": {tip: {...}}, "bones": {bone: {...}}}} — chains keyed
 * by their tip bone, per-bone joint freedom keyed by bone. It is what IK presets are saved as, and
 * what forms saved before the bones group stored in their {@code ik} blob — one reader serves both.
 * The form itself now persists IK inside {@link ValueBones}, per bone.
 *
 * <p>Data written before the joints existed was the flat chains map itself (no wrapper); it is
 * still read: a map WITHOUT a "chains" key is taken as the legacy flat form. That missing wrapper
 * doubles as the version marker of the IK redesign: a flat map was authored on the OLD
 * position-level solver, so its chains migrate with {@code classic} ON — a two-bone limb keeps
 * solving the way it was posed and nothing an animator already tuned shifts under them.</p>
 */
public final class BoneIKIO
{
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_POLE = true;

    private BoneIKIO()
    {
    }

    /**
     * Reads the exchange format into the bones group. With {@code reset}, every bone's chain and
     * joint are neutralized first — a preset is a complete state, so a chain absent from it ends
     * up gone (legacy form loading passes false: the group is fresh).
     */
    public static void read(MapType root, ValueBones bones, boolean reset)
    {
        if (reset)
        {
            for (BaseValue value : bones.getAll())
            {
                if (!(value instanceof FormBone bone))
                {
                    continue;
                }

                if (bone.hasChain() || !bone.ik.get().isDefault())
                {
                    clearChain(bone);
                }

                if (!bone.joint.get().isFree())
                {
                    bone.joint.set(new JointDoF());
                }
            }
        }

        if (root == null || root.isEmpty())
        {
            return;
        }

        boolean wrapped = root.has("chains", BaseType.TYPE_MAP);
        MapType chainsMap = wrapped ? root.getMap("chains") : root;

        /* Pre-redesign data: the old solver is what these chains were posed
         * against, so they come back with it on. */
        boolean defaultClassic = !wrapped;

        for (String tip : chainsMap.keys())
        {
            if (tip.isEmpty() || !chainsMap.has(tip, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = chainsMap.getMap(tip);
            String target = entry.getString("target");

            if (target.isEmpty())
            {
                continue;
            }

            FormBone bone = bones.getOrCreate(tip);

            bone.ikTarget.set(target);
            bone.ikPoleTarget.set(entry.getString("pole_target"));
            bone.ikChainLength.set(Math.max(0, entry.getInt("chain_length", 0)));
            bone.ikTipRotation.set(entry.getBool("tip_rotation", false));
            bone.ikStretch.set(entry.getBool("stretch", false));
            bone.ikClassic.set(entry.getBool("classic", defaultClassic));

            IKControl control = new IKControl();

            control.enabled = entry.getBool("enabled", DEFAULT_ENABLED);
            control.pole = entry.getBool("pole", DEFAULT_POLE);
            control.poleAngle = (float) entry.getDouble("pole_angle", IKControl.DEFAULT_POLE_ANGLE);
            control.softness = clamp01((float) entry.getDouble("softness", IKControl.DEFAULT_SOFTNESS));
            control.weight = clamp01((float) entry.getDouble("weight", IKControl.DEFAULT_WEIGHT));

            bone.ik.set(control);
        }

        if (root.has("bones", BaseType.TYPE_MAP))
        {
            MapType bonesMap = root.getMap("bones");

            for (String name : bonesMap.keys())
            {
                if (name.isEmpty() || !bonesMap.has(name, BaseType.TYPE_MAP))
                {
                    continue;
                }

                JointDoF joint = new JointDoF();

                joint.fromData(bonesMap.getMap(name));

                if (!joint.isFree())
                {
                    bones.getOrCreate(name).joint.set(joint);
                }
            }
        }
    }

    /** Writes every configured chain and non-free joint in the exchange format; empty means none. */
    public static MapType write(ValueBones bones)
    {
        MapType root = new MapType();
        MapType chains = new MapType();
        MapType joints = new MapType();

        for (BaseValue value : bones.getAll())
        {
            if (!(value instanceof FormBone bone) || bone.getId().isEmpty())
            {
                continue;
            }

            if (bone.hasChain())
            {
                MapType entry = new MapType();
                IKControl control = bone.ik.get();

                entry.putString("target", bone.ikTarget.get());
                entry.putBool("enabled", control.enabled);

                if (bone.ikChainLength.get() != 0)
                {
                    entry.putInt("chain_length", bone.ikChainLength.get());
                }

                if (control.pole != DEFAULT_POLE)
                {
                    entry.putBool("pole", control.pole);
                }

                if (!bone.ikPoleTarget.get().isEmpty())
                {
                    entry.putString("pole_target", bone.ikPoleTarget.get());
                }

                if (control.poleAngle != IKControl.DEFAULT_POLE_ANGLE)
                {
                    entry.putDouble("pole_angle", control.poleAngle);
                }

                if (control.softness != IKControl.DEFAULT_SOFTNESS)
                {
                    entry.putDouble("softness", control.softness);
                }

                if (control.weight != IKControl.DEFAULT_WEIGHT)
                {
                    entry.putDouble("weight", control.weight);
                }

                if (bone.ikTipRotation.get())
                {
                    entry.putBool("tip_rotation", true);
                }

                if (bone.ikStretch.get())
                {
                    entry.putBool("stretch", true);
                }

                if (bone.ikClassic.get())
                {
                    entry.putBool("classic", true);
                }

                chains.put(bone.getId(), entry);
            }

            JointDoF joint = bone.joint.get();

            if (!joint.isFree())
            {
                MapType map = new MapType();

                joint.toData(map);
                joints.put(bone.getId(), map);
            }
        }

        if (chains.isEmpty() && joints.isEmpty())
        {
            return root;
        }

        root.put("chains", chains);

        if (!joints.isEmpty())
        {
            root.put("bones", joints);
        }

        return root;
    }

    /** Neutralizes a bone's chain: the addresses, the modes and the scalars all back to default. */
    public static void clearChain(FormBone bone)
    {
        bone.ikTarget.set("");
        bone.ikPoleTarget.set("");
        bone.ikChainLength.set(0);
        bone.ikTipRotation.set(false);
        bone.ikStretch.set(false);
        bone.ikClassic.set(false);
        bone.ik.set(new IKControl());
    }

    private static float clamp01(float value)
    {
        return value < 0F ? 0F : Math.min(value, 1F);
    }
}
