package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.List;

/**
 * Directs solver output (physics, short IK chains) onto bones as evaluated orientations: each
 * bone's local rotation is rebuilt from its solved segment, blended against the evaluated FK base
 * by the stage weight, and written to {@code orient} — the channels stay read-only FK truth (the
 * constraint-stack contract; see {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#orient}).
 */
public final class ModelRotationBlender
{
    private static final float EPS = 1.0e-6f;

    private ModelRotationBlender()
    {
    }

    public static void applyWeightedRotations(IModel model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float weight)
    {
        float factor = clamp01(weight);

        if (factor <= EPS)
        {
            return;
        }

        if (model instanceof Model cubic)
        {
            CubicRenderer.applyRotations(cubic, rootParentRotation, ids, positions, factor);
            return;
        }

        if (model instanceof BOBJModel bobj)
        {
            applyRotationsBobj(bobj, rootParentRotation, ids, positions, factor);
        }
    }

    /**
     * The BOBJ analogue of {@link CubicRenderer#applyRotations}: rebuilds each bone's local
     * rotation from its solved segment (keeping the FK twist about the limb axis), blends it
     * against the evaluated FK base by {@code factor}, and writes the result to
     * {@link BOBJBone#orient}. The parent frame advances by the applied (blended) rotation, the
     * same frame the armature establishes for children.
     */
    private static void applyRotationsBobj(BOBJModel model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float factor)
    {
        if (model == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        Map<String, BOBJBone> bones = model.getArmature().bones;
        Quaternionf parentWorld = new Quaternionf(rootParentRotation);
        int boneCount = ids.size();
        int rotCount = getRotationCount(ids, positions);

        for (int i = 0; i < rotCount; i++)
        {
            BOBJBone bone = bones.get(ids.get(i));
            BOBJBone child = i + 1 < boneCount ? bones.get(ids.get(i + 1)) : null;

            if (bone == null)
            {
                return;
            }

            Vector3f restDirLocal = getBobjRestDirection(model, bone, child, ids, i);
            Vector3f desiredDirWorld = new Vector3f(positions[i + 1]).sub(positions[i]);

            if (restDirLocal.lengthSquared() < EPS * EPS || desiredDirWorld.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            restDirLocal.normalize();
            desiredDirWorld.normalize();

            Quaternionf invParent = new Quaternionf(parentWorld).invert();
            Vector3f desiredDirLocal = new Vector3f(desiredDirWorld);
            invParent.transform(desiredDirLocal);

            if (desiredDirLocal.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            desiredDirLocal.normalize();

            Quaternionf base = bone.evaluatedRotation();
            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);

            localRot.mul(Matrices.twistAbout(base, restDirLocal));

            Quaternionf applied = factor >= 1F - EPS ? localRot : new Quaternionf(base).slerp(localRot, factor);

            bone.orient = applied;
            parentWorld.mul(applied);
        }
    }

    public static Vector3f getBobjRestDirection(BOBJModel model, BOBJBone bone, BOBJBone child, List<String> ids, int index)
    {
        if (child != null)
        {
            Vector3f out = child.relBoneMat.getTranslation(new Vector3f());

            if (out.lengthSquared() > EPS * EPS)
            {
                return out;
            }
        }

        if (index > 0)
        {
            Vector3f out = bone.relBoneMat.getTranslation(new Vector3f());

            if (out.lengthSquared() > EPS * EPS)
            {
                return out;
            }
        }

        for (BOBJBone candidate : model.getArmature().orderedBones)
        {
            if (candidate != null && candidate.parentBone == bone)
            {
                Vector3f out = candidate.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }
        }

        return new Vector3f(0F, -1F, 0F);
    }

    private static int getRotationCount(List<String> ids, Vector3f[] positions)
    {
        int boneCount = ids.size();
        boolean hasTip = positions.length >= boneCount + 1;

        return boneCount - 1 + (hasTip ? 1 : 0);
    }

    private static float clamp01(float value)
    {
        if (value < 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }
}
