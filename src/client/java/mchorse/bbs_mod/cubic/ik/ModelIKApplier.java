package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig.BoneConstraint;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
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

    public static void apply(IModel model, List<ModelIKCache.CompiledChain> chains, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, IKControl> controlOverrides, Map<String, BoneConstraint> boneLimits)
    {
        if (model == null || chains == null || chains.isEmpty())
        {
            return;
        }

        /* Apply ancestor chains (shallower root) first, and re-collect frames per
         * chain, so a child chain (e.g. an arm) sees the pose its parent chain
         * (e.g. the body) already produced and rides along with it. */
        List<ModelIKCache.CompiledChain> ordered = new ArrayList<>(chains);
        ordered.sort(Comparator.comparingInt((ModelIKCache.CompiledChain chain) -> rootDepth(model, chain)));

        for (ModelIKCache.CompiledChain chain : ordered)
        {
            Set<String> wanted = new HashSet<>();
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }

            Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
            ModelPivotFrames.collect(model, wanted, frames);

            applyChain(model, chain, frames, controllerTargets, poleTargets, controlOverrides, boneLimits);
        }
    }

    /** Depth of the chain's root bone from the model root, for ancestor-first ordering. */
    private static int rootDepth(IModel model, ModelIKCache.CompiledChain chain)
    {
        List<String> ids = chain.chainRootToEffector();
        String group = ids.isEmpty() ? chain.tip() : ids.get(0);
        int depth = 0;

        while (group != null && !group.isEmpty() && depth < 256)
        {
            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
            depth++;
        }

        return depth;
    }

    private static void applyChain(IModel model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, IKControl> controlOverrides, Map<String, BoneConstraint> boneLimits)
    {
        /* The film's `ik` track may override the chain's static config scalars.
         * IK weight is independent of pose `fix` — freezing a bone pins it to rest
         * (changing the FK pose IK reads from) but no longer gates IK weight, which
         * comes only from the config and the `ik` track. */
        IKControl control = controlOverrides == null ? null : controlOverrides.get(chain.tip());

        if (control != null && !control.enabled)
        {
            return;
        }

        boolean pole = control != null ? control.pole : chain.pole();
        float softness = control != null ? control.softness : chain.softness();
        float weight = control != null ? control.weight : chain.weight();

        if (weight <= 0F)
        {
            return;
        }

        PivotFrame targetFrame = frames.get(chain.target());

        if (targetFrame == null)
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

        if (rootParentRotation == null)
        {
            return;
        }

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(chain.target());
        Vector3f target = override != null ? new Vector3f(override) : new Vector3f(targetFrame.position());

        Vector3f polePoint = resolvePolePoint(pole, chain.poleTarget(), frames, poleTargets);
        IKSolver.Limit[] limits = buildLimits(model, chainIds, boneLimits);

        IKSolver.Solution solution = IKSolver.solve(currentPositions, target, pole, polePoint, softness, MAX_ITERATIONS, TOLERANCE, limits, limits == null ? null : rootParentRotation);
        List<Vector3f> solved = solution.positions();

        Vector3f[] solvedArray = solved.toArray(new Vector3f[solved.size()]);
        ModelRotationBlender.applyWeightedRotations(model, rootParentRotation, chainIds, solvedArray, weight);
        applyChainRoll(model, chainIds, solved, rootParentRotation, solution.roll(), weight);
    }

    /**
     * Stores the pole roll as a transient rigid rotation on the chain's root bone.
     * Applied raw in the render matrix (never written back to a euler bone), it
     * rolls the whole chain about the root-to-tip axis the way Blender's pole does —
     * geometry included — without the +-180 instability of carrying a roll through
     * the swing/twist euler reconstruction. The axis passes through the root pivot,
     * so the tip stays put and the hierarchy rolls rigidly. Cubic only; cleared each
     * frame by {@link ModelGroup#reset()}.
     */
    private static void applyChainRoll(IModel model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float roll, float weight)
    {
        if (roll == 0F || chainIds.isEmpty() || !(model instanceof Model cubic))
        {
            return;
        }

        ModelGroup rootBone = cubic.getGroup(chainIds.get(0));

        if (rootBone == null)
        {
            return;
        }

        Vector3f axis = new Vector3f(solved.get(solved.size() - 1)).sub(solved.get(0));

        if (axis.lengthSquared() < 1.0e-12f)
        {
            return;
        }

        /* The renderer multiplies ikRoll in the root bone's PARENT frame (before
         * the bone's own rotation), so express the world root->tip axis there. */
        axis.normalize();
        new Quaternionf(rootParentRotation).conjugate().transform(axis);

        rootBone.ikRoll = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, roll * weight);
    }

    /**
     * Resolves the pole target into a model-space point the bend aims at: the
     * film override position if the chain's pole bone is being driven, otherwise
     * the pole bone's current position. Returns {@code null} (automatic hinge)
     * when the chain has no pole or no pole target.
     */
    private static Vector3f resolvePolePoint(boolean pole, String poleTarget, Map<String, PivotFrame> frames, Map<String, Vector3f> poleTargets)
    {
        if (!pole || poleTarget == null || poleTarget.isEmpty())
        {
            return null;
        }

        Vector3f override = poleTargets == null ? null : poleTargets.get(poleTarget);

        if (override != null)
        {
            return new Vector3f(override);
        }

        PivotFrame frame = frames.get(poleTarget);

        return frame == null ? null : new Vector3f(frame.position());
    }

    /**
     * Builds per-bone rotation limits for the chain's directed bones (root..tip-1),
     * matching the renderer's reconstruction so the clamp is exact. The solver math
     * is in degrees and the {@link BoneConstraint} limits are degrees, so the same
     * path serves cubic {@link Model} and {@link BOBJModel} — only the rest
     * direction differs (each taken the way that model's renderer takes it).
     * Returns {@code null} when no bone in the chain is constrained (fast path) or
     * the model is neither type. Every entry carries the bone's local rest
     * direction (needed to advance the parent frame during the clamp pass);
     * {@code enabled} is set only where a constraint exists.
     */
    private static IKSolver.Limit[] buildLimits(IModel model, List<String> chainIds, Map<String, BoneConstraint> boneLimits)
    {
        if (boneLimits == null || boneLimits.isEmpty())
        {
            return null;
        }

        int directed = chainIds.size() - 1;

        if (directed < 1)
        {
            return null;
        }

        boolean any = false;

        for (int i = 0; i < directed; i++)
        {
            BoneConstraint c = boneLimits.get(chainIds.get(i));

            if (c != null && c.enabled())
            {
                any = true;
                break;
            }
        }

        if (!any)
        {
            return null;
        }

        IKSolver.Limit[] limits = new IKSolver.Limit[directed];

        for (int i = 0; i < directed; i++)
        {
            String id = chainIds.get(i);
            Vector3f restDir = restDirection(model, chainIds, i);

            if (restDir == null)
            {
                return null;
            }

            BoneConstraint c = boneLimits.get(id);
            boolean enabled = c != null && c.enabled();

            limits[i] = enabled
                ? new IKSolver.Limit(true, restDir, c.minX(), c.minY(), c.minZ(), c.maxX(), c.maxY(), c.maxZ())
                : new IKSolver.Limit(false, restDir, 0F, 0F, 0F, 0F, 0F, 0F);
        }

        return limits;
    }

    /**
     * The bone's local rest direction towards its child, taken exactly as that
     * model's renderer takes it (cubic: pivot difference; BOBJ: the renderer's
     * own {@link ModelRotationBlender#getBobjRestDirection}), so the limit clamp
     * reconstructs the same swing the renderer applies.
     */
    private static Vector3f restDirection(IModel model, List<String> chainIds, int i)
    {
        String id = chainIds.get(i);
        String childId = chainIds.get(i + 1);

        if (model instanceof Model cubic)
        {
            ModelGroup bone = cubic.getGroup(id);
            ModelGroup child = cubic.getGroup(childId);

            if (bone == null || child == null)
            {
                return null;
            }

            return normalizeRest(new Vector3f(child.initial.translate).sub(bone.initial.translate));
        }

        if (model instanceof BOBJModel bobj)
        {
            BOBJBone bone = bobj.getArmature().bones.get(id);
            BOBJBone child = bobj.getArmature().bones.get(childId);

            if (bone == null)
            {
                return null;
            }

            return normalizeRest(ModelRotationBlender.getBobjRestDirection(bobj, bone, child, chainIds, i));
        }

        return null;
    }

    private static Vector3f normalizeRest(Vector3f restDir)
    {
        if (restDir.lengthSquared() < 1.0e-12f)
        {
            restDir.set(0F, -1F, 0F);
        }

        restDir.normalize();

        return restDir;
    }
}
