package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
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

    public static void apply(Model model, List<ModelIKCache.CompiledChain> chains)
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
        collectPivotFrames(model, wanted, frames);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            applyChain(model, chain, frames);
        }
    }

    private static void applyChain(Model model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames)
    {
        PivotFrame controllerFrame = frames.get(chain.controller());

        if (controllerFrame == null)
        {
            return;
        }

        List<String> chainIds = chain.chainRootToEffector();
        List<ModelGroup> groups = new ArrayList<>(chainIds.size());
        List<Vector3f> currentPositions = new ArrayList<>(chainIds.size());
        List<Quaternionf> parentWorldRot = new ArrayList<>(chainIds.size());

        for (String id : chainIds)
        {
            ModelGroup g = model.getGroup(id);
            PivotFrame frame = frames.get(id);

            if (g == null || frame == null)
            {
                return;
            }

            groups.add(g);
            currentPositions.add(new Vector3f(frame.position()));
            parentWorldRot.add(new Quaternionf(frame.parentRotation()));
        }

        List<Vector3f> solved = FabrikSolver.solve(currentPositions, new Vector3f(controllerFrame.position()), MAX_ITERATIONS, TOLERANCE);
        applySolvedRotations(groups, solved, parentWorldRot);
    }

    private static void applySolvedRotations(List<ModelGroup> chain, List<Vector3f> solved, List<Quaternionf> parentWorldRot)
    {
        Quaternionf parentWorld = new Quaternionf(parentWorldRot.get(0));

        for (int i = 0; i < chain.size() - 1; i++)
        {
            ModelGroup bone = chain.get(i);
            ModelGroup child = chain.get(i + 1);

            Vector3f restDirLocal = new Vector3f(child.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            Vector3f desiredDirWorld = new Vector3f(solved.get(i + 1)).sub(solved.get(i));

            if (restDirLocal.lengthSquared() < 1.0e-8f || desiredDirWorld.lengthSquared() < 1.0e-8f)
            {
                continue;
            }

            restDirLocal.normalize();
            desiredDirWorld.normalize();

            Quaternionf invParent = new Quaternionf(parentWorld).invert();
            Vector3f desiredDirLocal = new Vector3f(desiredDirWorld);
            invParent.transform(desiredDirLocal);

            if (desiredDirLocal.lengthSquared() < 1.0e-8f)
            {
                continue;
            }

            desiredDirLocal.normalize();

            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);
            Vector3f nextEulerDeg = Matrices.toEulerZYXDegrees(localRot);

            bone.current.rotate.set(nextEulerDeg);
            bone.current.rotate2.set(0F, 0F, 0F);

            parentWorld.mul(Matrices.toQuaternionZYXDegrees(nextEulerDeg.x, nextEulerDeg.y, nextEulerDeg.z));
        }
    }

    private static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out)
    {
        MatrixStack stack = new MatrixStack();

        for (ModelGroup group : model.topGroups)
        {
            collectPivotFramesRec(stack, group, wanted, out);
        }
    }

    private static void collectPivotFramesRec(MatrixStack stack, ModelGroup group, Set<String> wanted, Map<String, PivotFrame> out)
    {
        stack.push();

        ICubicRenderer.translateGroup(stack, group);
        ICubicRenderer.moveToGroupPivot(stack, group);

        if (wanted.contains(group.id))
        {
            Matrix4f mat = stack.peek().getPositionMatrix();
            Vector3f pos = mat.getTranslation(new Vector3f());
            Quaternionf rot = mat.getNormalizedRotation(new Quaternionf());

            out.put(group.id, new PivotFrame(pos, rot));
        }

        ICubicRenderer.rotateGroup(stack, group);
        ICubicRenderer.scaleGroup(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);

        for (ModelGroup child : group.children)
        {
            collectPivotFramesRec(stack, child, wanted, out);
        }

        stack.pop();
    }
}
