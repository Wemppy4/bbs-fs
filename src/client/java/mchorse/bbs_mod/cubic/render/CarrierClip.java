package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The bone a body part is worn on, as a box its model's marked bones stay inside (a face rig's skin
 * on a head whose face ends right under the eye). Pushed by the carrier around each part it draws;
 * render thread only.
 *
 * @param frame the carrier bone's frame (its captured matrix) on the render stack
 * @param min,max the bone's own cubes in that frame, inflated layers left out
 */
public record CarrierClip(Matrix4f frame, Vector3f min, Vector3f max)
{
    /** Pulled in a hair, so a face edge on the carrier's edge doesn't z-fight with it. */
    private static final float MARGIN = 0.005F / 16F;

    private static final Deque<CarrierClip> STACK = new ArrayDeque<>();
    private static final CarrierClip NONE = new CarrierClip(null, null, null);

    public static void push(CarrierClip clip)
    {
        STACK.push(clip == null ? NONE : clip);
    }

    public static void pop()
    {
        STACK.poll();
    }

    /** The carrier of the part being drawn, or null when it isn't worn on a cubic bone. */
    public static CarrierClip current()
    {
        CarrierClip clip = STACK.peek();

        return clip == NONE ? null : clip;
    }

    /**
     * The box of a group's plain cubes in its captured frame ({@code ModelInstance.captureMatrices}:
     * the pivot, turned half a turn around Y). Rotated and inflated cubes (an outer skin layer) don't count.
     */
    public static CarrierClip of(Model model, String bone, Matrix4f stack, Matrix4f boneMatrix)
    {
        ModelGroup group = model == null || boneMatrix == null ? null : model.getGroup(bone);

        if (group == null)
        {
            return null;
        }

        Vector3f pivot = group.initial.translate;
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

        for (ModelCube cube : group.cubes)
        {
            if (cube.inflate != 0F || cube.rotate.lengthSquared() != 0F)
            {
                continue;
            }

            for (int i = 0; i < 2; i++)
            {
                float x = cube.origin.x + cube.size.x * i, y = cube.origin.y + cube.size.y * i, z = cube.origin.z + cube.size.z * i;

                /* Half a turn around Y: X and Z flip. */
                Vector3f local = new Vector3f(pivot.x - x, y - pivot.y, pivot.z - z).div(16F);

                min.min(local);
                max.max(local);
            }
        }

        if (min.x > max.x)
        {
            return null;
        }

        min.add(MARGIN, MARGIN, MARGIN);
        max.sub(MARGIN, MARGIN, MARGIN);

        return new CarrierClip(new Matrix4f(stack).mul(boneMatrix), min, max);
    }
}
