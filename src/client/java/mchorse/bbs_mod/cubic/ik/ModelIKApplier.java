package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModelIKApplier
{
    private static final int MAX_ITERATIONS = 12;
    private static final float TOLERANCE = 1.0e-4f;

    private ModelIKApplier()
    {
    }

    public static void apply(IModel model, List<ModelIKCache.CompiledChain> chains, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> prevNormals, float hysteresisRad, float singularityRad, Map<String, Float> poseFixByBone)
    {
        if (model == null || chains == null || chains.isEmpty())
        {
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.controller());
            wanted.addAll(chain.chainRootToEffector());
        }

        if (wanted.isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            applyChain(model, chain, frames, controllerTargets, prevNormals, hysteresisRad, singularityRad, poseFixByBone);
        }
    }

    private static void applyChain(IModel model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> prevNormals, float hysteresisRad, float singularityRad, Map<String, Float> poseFixByBone)
    {
        float poseFix = getChainPoseFix(chain, poseFixByBone);
        float weight = chain.weight() * (1F - poseFix);

        if (weight <= 0F)
        {
            return;
        }

        PivotFrame controllerFrame = frames.get(chain.controller());

        if (controllerFrame == null)
        {
            return;
        }

        List<String> chainIds = chain.chainRootToEffector();
        List<Vector3f> currentPositions = new ArrayList<>(chainIds.size());
        Quaternionf rootParentRotation = null;

        for (String id : chainIds)
        {
            PivotFrame frame = frames.get(id);

            if (frame == null)
            {
                return;
            }

            currentPositions.add(new Vector3f(frame.position()));

            if (rootParentRotation == null)
            {
                rootParentRotation = new Quaternionf(frame.parentRotation());
            }
        }

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(chain.controller());
        Vector3f controllerPos = override != null ? new Vector3f(override) : new Vector3f(controllerFrame.position());
        Vector3f target = controllerPos;

        Vector3f pole = null;
        if (chain.poleX() != 0F || chain.poleY() != 0F || chain.poleZ() != 0F)
        {
            pole = new Vector3f(chain.poleX(), chain.poleY(), chain.poleZ()).mul(1F / 16F);
            if (chain.poleSpace() == ModelIKConfig.PoleSpace.CONTROLLER)
            {
                Quaternionf controllerRotation = new Quaternionf(controllerFrame.worldRotation());
                controllerRotation.transform(pole);
            }
            else if (chain.poleSpace() == ModelIKConfig.PoleSpace.ROOT)
            {
                if (rootParentRotation != null)
                {
                    Quaternionf rootSpace = new Quaternionf(rootParentRotation);
                    rootSpace.transform(pole);
                }
            }
            pole.add(controllerPos);
        }

        Vector3f prevNormal = prevNormals == null ? null : prevNormals.get(chain.controller());
        List<Vector3f> solved = FabrikSolver.solve(currentPositions, target, pole, prevNormal, hysteresisRad, singularityRad, MAX_ITERATIONS, TOLERANCE);
        if (rootParentRotation == null)
        {
            return;
        }

        Vector3f[] solvedArray = solved.toArray(new Vector3f[solved.size()]);
        ModelRotationBlender.applyWeightedRotations(model, rootParentRotation, chainIds, solvedArray, weight);

        if (prevNormals != null && solved.size() >= 3)
        {
            Vector3f a = solved.get(0);
            Vector3f b = solved.get(1);
            Vector3f c = solved.get(2);
            Vector3f ba = new Vector3f(b).sub(a);
            Vector3f cb = new Vector3f(c).sub(b);
            Vector3f n = ba.cross(cb);
            if (n.lengthSquared() > 1.0e-8f)
            {
                n.normalize();
                Vector3f store = prevNormals.computeIfAbsent(chain.controller(), (k) -> new Vector3f());
                store.set(n);
            }
        }
    }

    private static float getChainPoseFix(ModelIKCache.CompiledChain chain, Map<String, Float> poseFixByBone)
    {
        if (poseFixByBone == null || poseFixByBone.isEmpty() || chain == null)
        {
            return 0F;
        }

        float maxFix = getFix(poseFixByBone, chain.controller());

        for (String bone : chain.chainRootToEffector())
        {
            maxFix = Math.max(maxFix, getFix(poseFixByBone, bone));

            if (maxFix >= 1F)
            {
                return 1F;
            }
        }

        return maxFix;
    }

    private static float getFix(Map<String, Float> poseFixByBone, String bone)
    {
        Float value = poseFixByBone.get(bone);

        if (value == null)
        {
            return 0F;
        }

        if (value <= 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }
}
