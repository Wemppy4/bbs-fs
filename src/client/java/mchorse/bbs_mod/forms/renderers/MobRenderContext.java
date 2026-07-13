package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The pose state scoped to one MobForm entity render.
 *
 * <p>Contexts restore the previous value when closed so nested entity renders cannot leak pose
 * state into their caller. The part map is identity based and lives only for the duration of the
 * render, matching the renderer/model instances discovered for that call.</p>
 */
public final class MobRenderContext implements AutoCloseable
{
    private static final ThreadLocal<MobRenderContext> CURRENT = new ThreadLocal<>();

    private final MobRenderContext previous;
    private final IdentityHashMap<ModelPart, PoseTransform> transforms;
    private boolean closed;

    private MobRenderContext(Object renderer, Pose pose, Pose overlay)
    {
        this.previous = CURRENT.get();
        this.transforms = resolveTransforms(renderer, mergePose(pose, overlay));

        CURRENT.set(this);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay)
    {
        return new MobRenderContext(renderer, pose, overlay);
    }

    public static PoseTransform getTransform(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context == null ? null : context.transforms.get(part);
    }

    private static Pose mergePose(Pose pose, Pose overlay)
    {
        Pose result = pose.copy();

        for (Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform transform = result.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0F)
            {
                transform.translate.lerp(value.translate, value.fix);
                transform.scale.lerp(value.scale, value.fix);
                transform.rotate.lerp(value.rotate, value.fix);
                transform.rotate2.lerp(value.rotate2, value.fix);
            }
            else
            {
                transform.translate.add(value.translate);
                transform.scale.add(value.scale).sub(1F, 1F, 1F);
                transform.rotate.add(value.rotate);
                transform.rotate2.add(value.rotate2);
            }
        }

        return result;
    }

    private static IdentityHashMap<ModelPart, PoseTransform> resolveTransforms(Object renderer, Pose pose)
    {
        IdentityHashMap<ModelPart, PoseTransform> transforms = new IdentityHashMap<>();
        VanillaRendererBones.Discovery discovery = VanillaRendererBones.discover(renderer);

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            ModelPart part = discovery.resolve(entry.getKey())
                .map(VanillaBoneHierarchy.Bone::getPart)
                .orElse(null);

            if (part != null)
            {
                transforms.put(part, entry.getValue());
            }
        }

        return transforms;
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.closed = true;

        if (CURRENT.get() == this)
        {
            if (this.previous == null)
            {
                CURRENT.remove();
            }
            else
            {
                CURRENT.set(this.previous);
            }
        }
    }
}
