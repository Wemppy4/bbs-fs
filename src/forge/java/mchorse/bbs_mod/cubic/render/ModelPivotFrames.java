package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;

public final class ModelPivotFrames
{
    private ModelPivotFrames()
    {
    }

    public static void collect(IModel model, Set<String> wanted, Map<String, CubicRenderer.PivotFrame> out)
    {
        collect(model, wanted, out, null);
    }

    public static void collect(IModel model, Set<String> wanted, Map<String, CubicRenderer.PivotFrame> out, Matrix4f baseTransform)
    {
        collect(model, wanted, out, baseTransform, FormPoseEvents.PIVOT_OFFSETS.invoker().include(model));
    }

    /**
     * @param applyStretch see {@link CubicRenderer#collectPivotFrames(Model, Set, Map, Matrix4f,
     * boolean)}. Ignored for BOBJ: a BOBJ stretch rides the skinning matrix and leaves the skeleton
     * frames ({@code originMat}/{@code mat}) nominal by construction, so there is nothing to fold in.
     */
    public static void collect(IModel model, Set<String> wanted, Map<String, CubicRenderer.PivotFrame> out, Matrix4f baseTransform, boolean applyStretch)
    {
        if (model == null || wanted == null || wanted.isEmpty() || out == null)
        {
            return;
        }

        if (model instanceof Model cubic)
        {
            CubicRenderer.collectPivotFrames(cubic, wanted, out, baseTransform, applyStretch);
            return;
        }

        if (model instanceof BOBJModel bobj)
        {
            collectBobjPivotFrames(bobj, wanted, out, baseTransform);
        }
    }

    private static void collectBobjPivotFrames(BOBJModel model, Set<String> wanted, Map<String, CubicRenderer.PivotFrame> out, Matrix4f baseTransform)
    {
        Vector3f baseTranslation = null;
        Quaternionf baseRotation = null;

        if (baseTransform != null)
        {
            /* Unnormalized: the base transform carries the model's and the form's scale, which
             * getNormalizedRotation is not valid for — see the note in {@link
             * CubicRenderer#collectPivotFrames}. */
            baseTranslation = baseTransform.getTranslation(new Vector3f());
            baseRotation = baseTransform.getUnnormalizedRotation(new Quaternionf());
        }

        model.getArmature().setupMatrices();

        for (BOBJBone bone : model.getArmature().orderedBones)
        {
            if (bone == null || !wanted.contains(bone.name))
            {
                continue;
            }

            /* Unnormalized: a scaled bone leaves its scale on these skeleton matrices. */
            Vector3f position = bone.originMat.getTranslation(new Vector3f());
            Quaternionf parentRotation = bone.originMat.getUnnormalizedRotation(new Quaternionf());
            Quaternionf worldRotation = bone.mat.getUnnormalizedRotation(new Quaternionf());
            Vector3f scale = bone.originMat.getScale(new Vector3f());

            if (baseRotation != null && baseTranslation != null)
            {
                baseRotation.transform(position);
                position.add(baseTranslation);

                parentRotation = new Quaternionf(baseRotation).mul(parentRotation);
                worldRotation = new Quaternionf(baseRotation).mul(worldRotation);
            }

            out.put(bone.name, new CubicRenderer.PivotFrame(position, parentRotation, worldRotation, scale));
        }
    }
}
