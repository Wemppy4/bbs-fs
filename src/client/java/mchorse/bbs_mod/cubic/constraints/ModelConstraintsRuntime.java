package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ModelConstraintsRuntime
{
    private ModelConstraintsRuntime()
    {
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        Map<String, BoneConstraint> bones = getBones(instance);

        if (bones.isEmpty())
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

    /**
     * The form's enabled constraints by bone name, read fresh from the bone properties each
     * frame (a track's runtime override on the property is picked up for free that way).
     */
    public static Map<String, BoneConstraint> getBones(ModelInstance instance)
    {
        if (!(instance != null && instance.form instanceof ModelForm form))
        {
            return Collections.emptyMap();
        }

        Map<String, BoneConstraint> bones = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                BoneConstraint constraint = bone.constraints.get();

                if (constraint.enabled)
                {
                    if (bones == null)
                    {
                        bones = new HashMap<>();
                    }

                    bones.put(bone.getId(), constraint);
                }
            }
        }

        return bones == null ? Collections.emptyMap() : bones;
    }

    /**
     * Clamps a bone's EVALUATED rotation (the constraint-stack result so far — FK, IK, physics),
     * not its FK channels: the evaluated rotation is decomposed to the euler branch nearest the FK
     * channels (a per-frame-stable reference, no frame-to-frame stranding), clamped per axis, and
     * written back to {@code orient}. The channels stay read-only FK truth, and an IK/physics
     * result survives the limit instead of being discarded for the clamped FK pose (limits used to
     * null {@code orient}, visually destroying the solve on a constrained chain bone). Works on
     * quaternion-mode bones too — the clamp reads the evaluated rotation, never a stale euler.
     */
    private static void applyToModel(Model model, Map<String, BoneConstraint> bones)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null)
            {
                continue;
            }

            BoneConstraint c = bones.get(group.id);

            if (c == null || !c.enabled)
            {
                continue;
            }

            Vector3f euler = Matrices.toCompatibleEulerZYXDegrees(group.evaluatedRotation(), group.current.rotate, new Vector3f());

            clamp(euler, c, 1F);

            group.orient = Matrices.toLocalRotationZYXDegrees(euler);
        }
    }

    /** See {@link #applyToModel}; BOBJ channels are radians, the constraint limits are degrees. */
    private static void applyToBobj(BOBJModel model, Map<String, BoneConstraint> bones)
    {
        for (BOBJBone bone : model.getArmature().orderedBones)
        {
            if (bone == null)
            {
                continue;
            }

            BoneConstraint c = bones.get(bone.name);

            if (c == null || !c.enabled)
            {
                continue;
            }

            Vector3f euler = Matrices.toCompatibleEulerZYXRadians(bone.evaluatedRotation(), bone.transform.rotate, new Vector3f());

            clamp(euler, c, MathUtils.PI / 180F);

            bone.orient = Matrices.toLocalRotationZYXRadians(euler);
        }
    }

    /** Clamps euler angles to the constraint's limits, {@code scale} converting the degree limits to the angles' unit. */
    private static void clamp(Vector3f euler, BoneConstraint c, float scale)
    {
        euler.x = clampAxis(euler.x, c.minX * scale, c.maxX * scale);
        euler.y = clampAxis(euler.y, c.minY * scale, c.maxY * scale);
        euler.z = clampAxis(euler.z, c.minZ * scale, c.maxZ * scale);
    }

    private static float clampAxis(float value, float min, float max)
    {
        if (min > max)
        {
            float t = min;
            min = max;
            max = t;
        }

        return MathUtils.clamp(value, min, max);
    }
}
