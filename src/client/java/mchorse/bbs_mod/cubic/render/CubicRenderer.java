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

        if (group.isVisible())
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

    /**
     * @param scale the accumulated scale the bone's frame sits under (its ancestors' bone scales).
     * Rotations are captured normalized, so it drops out of everything the solve does — but a
     * TRANSLATION written back into that frame (the IK stretch offset) is scaled by the renderer,
     * so it has to be divided out to land the bone where the solve put it.
     */
    public static record PivotFrame(Vector3f position, Quaternionf parentRotation, Quaternionf worldRotation, Vector3f scale)
    {
    }

    public static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out)
    {
        collectPivotFrames(model, wanted, out, null, false);
    }

    public static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out, Matrix4f baseTransform)
    {
        collectPivotFrames(model, wanted, out, baseTransform, false);
    }

    /**
     * @param applyStretch when true, each bone's transient {@link ModelGroup#offset} — the IK
     * stretch — is folded into its frame exactly as {@link ICubicRenderer#applyGroupTransformations}
     * folds it, offset first in the parent frame, so a chain collected AFTER an ancestor chain has
     * stretched reads the ancestor at the spot the renderer will draw it. Off (the default) reads
     * the un-stretched pose, which is what the debug overlay and a chain's OWN solve want: a chain
     * writes its offsets only when its own solve runs, so even with this on a chain can only ever
     * inherit ANCESTOR stretch, never its own. The offset is a pure translation, so it moves
     * {@code position} only — {@code parentRotation}/{@code worldRotation} are untouched.
     */
    public static void collectPivotFrames(Model model, Set<String> wanted, Map<String, PivotFrame> out, Matrix4f baseTransform, boolean applyStretch)
    {
        if (model == null || wanted == null || wanted.isEmpty() || out == null)
        {
            return;
        }

        MatrixStack stack = new MatrixStack();

        if (baseTransform != null)
        {
            /* Unnormalized, NOT getNormalizedRotation: the base transform carries the model's and the
             * form's scale, and getNormalizedRotation assumes an already orthonormal 3x3 — it does not
             * divide the scale out, it is simply invalid input for it. Beyond returning a wrong rotation,
             * it is DISCONTINUOUS with scale folded in: it picks a branch off the 3x3's trace, and the
             * branches only agree at their boundary when the axes are unit length. So a scaled model just
             * turning smoothly makes the frame snap as it crosses one (measured on the game's JOML 1.10.5:
             * at scale 2 a quarter-degree step jumped the frame by 2 blocks around 119 degrees of yaw,
             * while scale 1 stayed smooth) — which is the physics twitching on a resized model.
             * getUnnormalizedRotation normalizes the axes first, so scale drops out cleanly. */
            Vector3f t = baseTransform.getTranslation(new Vector3f());
            Quaternionf r = baseTransform.getUnnormalizedRotation(new Quaternionf());
            Matrix4f rigid = new Matrix4f().rotation(r).setTranslation(t);
            stack.peek().getPositionMatrix().set(rigid);
        }

        for (ModelGroup group : model.topGroups)
        {
            collectPivotFramesRec(stack, group, wanted, out, applyStretch);
        }
    }

    private static void collectPivotFramesRec(MatrixStack stack, ModelGroup group, Set<String> wanted, Map<String, PivotFrame> out, boolean applyStretch)
    {
        stack.push();

        if (applyStretch)
        {
            ICubicRenderer.offsetGroup(stack, group);
        }

        ICubicRenderer.translateGroup(stack, group);
        ICubicRenderer.moveToGroupPivot(stack, group);

        boolean store = wanted.contains(group.id);
        Vector3f pos;
        Quaternionf parentRot;
        Vector3f scale;

        if (store)
        {
            /* Unnormalized for the same reason as the base frame above: scaleGroup runs before the children
             * recurse, so any scaled ancestor bone leaves its scale on this stack. */
            Matrix4f mat = stack.peek().getPositionMatrix();
            pos = mat.getTranslation(new Vector3f());
            parentRot = mat.getUnnormalizedRotation(new Quaternionf());
            scale = mat.getScale(new Vector3f());
        }
        else
        {
            pos = null;
            parentRot = null;
            scale = null;
        }

        ICubicRenderer.rotateGroup(stack, group);

        if (store)
        {
            Matrix4f mat = stack.peek().getPositionMatrix();
            Quaternionf worldRot = mat.getUnnormalizedRotation(new Quaternionf());
            out.put(group.id, new PivotFrame(pos, parentRot, worldRot, scale));
        }

        ICubicRenderer.scaleGroup(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);

        for (ModelGroup child : group.children)
        {
            collectPivotFramesRec(stack, child, wanted, out, applyStretch);
        }

        stack.pop();
    }

    /**
     * Directs a solved chain (physics, short IK) onto cubic bones: rebuilds each bone's local
     * rotation from its solved segment (keeping the FK twist about the limb axis), blends it
     * against the evaluated FK base by {@code weight}, and writes the result to
     * {@link ModelGroup#orient} — the euler channels stay read-only FK truth (the constraint-stack
     * contract). The parent frame advances by the applied (blended) rotation, the same frame the
     * renderer establishes for children.
     */
    public static void applyRotations(Model model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float weight)
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

            Quaternionf base = bone.evaluatedRotation();
            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);

            localRot.mul(Matrices.twistAbout(base, restDirLocal));

            Quaternionf applied = weight >= 1F - EPS ? localRot : new Quaternionf(base).slerp(localRot, weight);

            bone.orient = applied;
            parentWorld.mul(applied);
        }
    }
}
