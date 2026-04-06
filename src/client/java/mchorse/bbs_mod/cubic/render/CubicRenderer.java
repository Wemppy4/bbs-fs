package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CubicRenderer
{
    private static final float EPS = 1.0e-6f;
    /**
     * Process/render given model
     *
     * This method recursively goes through all groups in the model, and
     * applies given render processor. Processor may return true from its
     * sole method which means that iteration should be halted.
     */
    public static boolean processRenderModel(ICubicRenderer renderProcessor, BufferBuilder builder, MatrixStack stack, Model model)
    {
        for (ModelGroup group : model.topGroups)
        {
            if (processRenderRecursively(renderProcessor, builder, stack, model, group))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Apply the render processor, recursively
     */
    private static boolean processRenderRecursively(ICubicRenderer renderProcessor, BufferBuilder builder, MatrixStack stack, Model model, ModelGroup group)
    {
        stack.push();
        renderProcessor.applyGroupTransformations(stack, group);

        if (group.visible)
        {
            if (renderProcessor.renderGroup(builder, stack, group, model))
            {
                stack.pop();

                return true;
            }
        }

        for (ModelGroup childGroup : group.children)
        {
            if (processRenderRecursively(renderProcessor, builder, stack, model, childGroup))
            {
                stack.pop();

                return true;
            }
        }

        stack.pop();

        return false;
    }

    public static record PivotFrame(Vector3f position, Quaternionf parentRotation, Quaternionf worldRotation)
    {
    }

    public static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out)
    {
        collectPivotFrames(model, wanted, out, null);
    }

    public static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out, Matrix4f baseTransform)
    {
        if (model == null || wanted == null || wanted.isEmpty() || out == null)
        {
            return;
        }

        MatrixStack stack = new MatrixStack();

        if (baseTransform != null)
        {
            Vector3f t = baseTransform.getTranslation(new Vector3f());
            Quaternionf r = baseTransform.getNormalizedRotation(new Quaternionf());
            Matrix4f rigid = new Matrix4f().rotation(r).setTranslation(t);
            stack.peek().getPositionMatrix().set(rigid);
        }

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

        boolean store = wanted.contains(group.id);
        Vector3f pos;
        Quaternionf parentRot;

        if (store)
        {
            Matrix4f mat = stack.peek().getPositionMatrix();
            pos = mat.getTranslation(new Vector3f());
            parentRot = mat.getNormalizedRotation(new Quaternionf());
        }
        else
        {
            pos = null;
            parentRot = null;
        }

        ICubicRenderer.rotateGroup(stack, group);

        if (store)
        {
            Matrix4f mat = stack.peek().getPositionMatrix();
            Quaternionf worldRot = mat.getNormalizedRotation(new Quaternionf());
            out.put(group.id, new PivotFrame(pos, parentRot, worldRot));
        }

        ICubicRenderer.scaleGroup(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);

        for (ModelGroup child : group.children)
        {
            collectPivotFramesRec(stack, child, wanted, out);
        }

        stack.pop();
    }

    public static void applyRotations(Model model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions)
    {
        if (model == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        Quaternionf parentWorld = new Quaternionf(rootParentRotation);
        int boneCount = ids.size();
        boolean hasTip = positions.length >= boneCount + 1;
        int rotCount = boneCount - 1 + (hasTip ? 1 : 0);

        for (int i = 0; i < rotCount; i++)
        {
            ModelGroup bone = model.getGroup(ids.get(i));
            ModelGroup child = i + 1 < boneCount ? model.getGroup(ids.get(i + 1)) : null;

            if (bone == null)
            {
                return;
            }

            Vector3f restDirLocal;

            if (child != null)
            {
                restDirLocal = new Vector3f(child.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            }
            else
            {
                if (boneCount >= 2)
                {
                    ModelGroup parent = model.getGroup(ids.get(i - 1));

                    if (parent == null)
                    {
                        return;
                    }

                    restDirLocal = new Vector3f(bone.initial.translate).sub(parent.initial.translate).mul(1.0f / 16.0f);
                }
                else if (bone.children != null && !bone.children.isEmpty())
                {
                    ModelGroup firstChild = bone.children.get(0);
                    restDirLocal = new Vector3f(firstChild.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
                }
                else
                {
                    restDirLocal = new Vector3f(0F, -1F, 0F);
                }
            }

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
            Vector3f eulerDeg = Matrices.toEulerZYXDegrees(localRot);

            bone.current.rotate.set(eulerDeg);
            bone.current.rotate2.set(0F, 0F, 0F);

            parentWorld.mul(Matrices.toQuaternionZYXDegrees(eulerDeg.x, eulerDeg.y, eulerDeg.z));
        }
    }
}
