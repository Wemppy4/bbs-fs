package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.List;

/**
 * Applies weighted blending between current local bone rotations and solver output.
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
            applyWeightedRotationsCubic(cubic, rootParentRotation, ids, positions, factor);
            return;
        }

        if (model instanceof BOBJModel bobj)
        {
            applyWeightedRotationsBobj(bobj, rootParentRotation, ids, positions, factor);
        }
    }

    public static void applyWeightedRotations(Model model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float weight)
    {
        applyWeightedRotations((IModel) model, rootParentRotation, ids, positions, weight);
    }

    private static void applyWeightedRotationsCubic(Model model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float factor)
    {
        if (model == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        if (factor >= 1F - EPS)
        {
            CubicRenderer.applyRotations(model, rootParentRotation, ids, positions);
            return;
        }

        int rotCount = getRotationCount(ids, positions);

        if (rotCount <= 0)
        {
            return;
        }

        ModelGroup[] bones = new ModelGroup[rotCount];
        Quaternionf[] baseLocal = new Quaternionf[rotCount];
        float[] baseX = new float[rotCount];
        float[] baseY = new float[rotCount];
        float[] baseZ = new float[rotCount];

        for (int i = 0; i < rotCount; i++)
        {
            ModelGroup bone = model.getGroup(ids.get(i));

            if (bone == null)
            {
                return;
            }

            bones[i] = bone;
            baseX[i] = bone.current.rotate.x;
            baseY[i] = bone.current.rotate.y;
            baseZ[i] = bone.current.rotate.z;
            baseLocal[i] = toLocalRotation(bone.current.rotate, bone.current.rotate2);
        }

        CubicRenderer.applyRotations(model, rootParentRotation, ids, positions);

        for (int i = 0; i < rotCount; i++)
        {
            ModelGroup bone = bones[i];
            Quaternionf solved = toLocalRotation(bone.current.rotate, bone.current.rotate2);
            Quaternionf blended = new Quaternionf(baseLocal[i]).slerp(solved, factor);
            Vector3f euler = Matrices.toEulerZYXDegrees(blended);

            euler.x = wrapDegreesNear(euler.x, baseX[i]);
            euler.y = wrapDegreesNear(euler.y, baseY[i]);
            euler.z = wrapDegreesNear(euler.z, baseZ[i]);

            bone.current.rotate.set(euler);
            bone.current.rotate2.set(0F, 0F, 0F);
        }
    }

    private static void applyWeightedRotationsBobj(BOBJModel model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float factor)
    {
        if (model == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        if (factor >= 1F - EPS)
        {
            applyRotationsBobj(model, rootParentRotation, ids, positions);
            return;
        }

        int rotCount = getRotationCount(ids, positions);

        if (rotCount <= 0)
        {
            return;
        }

        Map<String, BOBJBone> bonesMap = model.getArmature().bones;
        BOBJBone[] bones = new BOBJBone[rotCount];
        Quaternionf[] baseLocal = new Quaternionf[rotCount];
        float[] baseX = new float[rotCount];
        float[] baseY = new float[rotCount];
        float[] baseZ = new float[rotCount];

        for (int i = 0; i < rotCount; i++)
        {
            BOBJBone bone = bonesMap.get(ids.get(i));

            if (bone == null)
            {
                return;
            }

            bones[i] = bone;
            baseX[i] = bone.transform.rotate.x;
            baseY[i] = bone.transform.rotate.y;
            baseZ[i] = bone.transform.rotate.z;
            baseLocal[i] = toLocalRotationRadians(bone.transform.rotate, bone.transform.rotate2);
        }

        applyRotationsBobj(model, rootParentRotation, ids, positions);

        for (int i = 0; i < rotCount; i++)
        {
            BOBJBone bone = bones[i];
            Quaternionf solved = toLocalRotationRadians(bone.transform.rotate, bone.transform.rotate2);
            Quaternionf blended = new Quaternionf(baseLocal[i]).slerp(solved, factor);
            Vector3f euler = new Quaternionf(blended).normalize().getEulerAnglesZYX(new Vector3f());

            euler.x = wrapRadiansNear(euler.x, baseX[i]);
            euler.y = wrapRadiansNear(euler.y, baseY[i]);
            euler.z = wrapRadiansNear(euler.z, baseZ[i]);

            bone.transform.rotate.set(euler);
            bone.transform.rotate2.set(0F, 0F, 0F);
        }
    }

    private static void applyRotationsBobj(BOBJModel model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions)
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

            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);
            localRot.mul(Matrices.twistAbout(toLocalRotationRadians(bone.transform.rotate, bone.transform.rotate2), restDirLocal));
            Vector3f eulerRad = new Quaternionf(localRot).normalize().getEulerAnglesZYX(new Vector3f());

            eulerRad.x = wrapRadiansNear(eulerRad.x, bone.transform.rotate.x);
            eulerRad.y = wrapRadiansNear(eulerRad.y, bone.transform.rotate.y);
            eulerRad.z = wrapRadiansNear(eulerRad.z, bone.transform.rotate.z);

            bone.transform.rotate.set(eulerRad);
            bone.transform.rotate2.set(0F, 0F, 0F);

            parentWorld.mul(new Quaternionf().rotationZYX(eulerRad.z, eulerRad.y, eulerRad.x));
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

    private static Quaternionf toLocalRotation(Vector3f rotate, Vector3f rotate2)
    {
        Quaternionf q = Matrices.toQuaternionZYXDegrees(rotate.x, rotate.y, rotate.z);

        if (rotate2.x != 0F || rotate2.y != 0F || rotate2.z != 0F)
        {
            q.mul(Matrices.toQuaternionZYXDegrees(rotate2.x, rotate2.y, rotate2.z));
        }

        return q;
    }

    private static Quaternionf toLocalRotationRadians(Vector3f rotate, Vector3f rotate2)
    {
        Quaternionf q = new Quaternionf().rotationZYX(rotate.z, rotate.y, rotate.x);

        if (rotate2.x != 0F || rotate2.y != 0F || rotate2.z != 0F)
        {
            q.mul(new Quaternionf().rotationZYX(rotate2.z, rotate2.y, rotate2.x));
        }

        return q;
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

    private static float wrapDegreesNear(float angle, float reference)
    {
        float delta = angle - reference;

        while (delta > 180F)
        {
            angle -= 360F;
            delta -= 360F;
        }

        while (delta < -180F)
        {
            angle += 360F;
            delta += 360F;
        }

        return angle;
    }

    private static float wrapRadiansNear(float angle, float reference)
    {
        float delta = angle - reference;
        float period = (float) (Math.PI * 2.0);
        float half = (float) Math.PI;

        while (delta > half)
        {
            angle -= period;
            delta -= period;
        }

        while (delta < -half)
        {
            angle += period;
            delta += period;
        }

        return angle;
    }
}
