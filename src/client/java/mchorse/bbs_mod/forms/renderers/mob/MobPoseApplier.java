package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.model.ModelPart;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Writes a pose onto a vanilla model's parts, and takes it back off again.
 *
 * <p>Vanilla model parts are shared by every entity of that kind in the world, so the pose is
 * ADDED on top of the angles {@code setAngles} just computed and the originals are put aside to be
 * restored the moment the render is over. Both halves live here because the film's offline matrix
 * evaluation needs exactly the same pair, and a second copy of this arithmetic is how the two
 * would quietly drift apart.</p>
 */
public class MobPoseApplier
{
    /**
     * The pose stack of a mob form: its pose with the overlay folded in. Same rule as the model
     * form's merge — a non-zero {@code fix} lerps toward the overlay, otherwise it sums.
     */
    public static Pose merge(Pose pose, Pose overlay)
    {
        Pose merged = pose.copy();

        if (overlay == null)
        {
            return merged;
        }

        for (Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform poseTransform = merged.getOrCreate(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0)
            {
                poseTransform.translate.lerp(value.translate, value.fix);
                poseTransform.scale.lerp(value.scale, value.fix);
                poseTransform.lerpRotation(value, value.fix);
            }
            else
            {
                poseTransform.translate.add(value.translate);
                poseTransform.scale.add(value.scale).sub(1, 1, 1);
                poseTransform.addRotation(value);
            }
        }

        return merged;
    }

    public static void apply(MobRig rig, Pose pose, Map<ModelPart, Transform> saved)
    {
        if (rig == null || pose == null)
        {
            return;
        }

        for (ModelPart part : rig.ordered())
        {
            PoseTransform poseTransform = rig.resolve(pose, rig.name(part));

            if (poseTransform == null)
            {
                continue;
            }

            Transform transform = new Transform();

            transform.translate.set(part.pivotX, part.pivotY, part.pivotZ);
            transform.rotate.set(part.pitch, part.yaw, part.roll);
            transform.scale.set(part.xScale, part.yScale, part.zScale);

            /* Vanilla ModelPart holds euler pitch/yaw/roll only, so a quaternion pose bone is
             * decomposed to its euler equivalent here instead of reading the stale rotate triple. */
            Vector3f rotation = poseTransform.getEulerRotation(new Vector3f());

            part.pivotX += poseTransform.translate.x;
            part.pivotY += poseTransform.translate.y;
            part.pivotZ += poseTransform.translate.z;
            part.pitch += rotation.x;
            part.yaw += rotation.y;
            part.roll += rotation.z;
            part.xScale += poseTransform.scale.x - 1F;
            part.yScale += poseTransform.scale.y - 1F;
            part.zScale += poseTransform.scale.z - 1F;

            saved.putIfAbsent(part, transform);
        }
    }

    public static void restore(Map<ModelPart, Transform> saved)
    {
        for (Map.Entry<ModelPart, Transform> entry : saved.entrySet())
        {
            ModelPart part = entry.getKey();
            Transform transform = entry.getValue();

            part.pivotX = transform.translate.x;
            part.pivotY = transform.translate.y;
            part.pivotZ = transform.translate.z;
            part.pitch = transform.rotate.x;
            part.yaw = transform.rotate.y;
            part.roll = transform.rotate.z;
            part.xScale = transform.scale.x;
            part.yScale = transform.scale.y;
            part.zScale = transform.scale.z;
        }

        saved.clear();
    }
}
