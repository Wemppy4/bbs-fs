package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.mixin.client.LivingEntityRendererInvoker;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where a mob form's bones are, in the form's own space.
 *
 * <p>The one place the space convention lives. Vanilla walks from the form's stack through
 * {@code setupTransforms} (which yaws by {@code 180 - bodyYaw}, and the form pins body yaw to zero)
 * and then {@code scale(-1, -1, 1)}, so the model's frame ends up turned 180 degrees about X
 * relative to the form: Y down, Z backwards. Turning it back by 180 degrees about Z lands it where
 * the model form's bones already are — the same frame {@code renderBodyParts} falls back to when it
 * has no matrix for a bone (a plain 180 degree yaw), so a body part behaves the same whichever kind
 * of form it hangs off.</p>
 *
 * <p>The correction is written as a {@code scale(-1, -1, 1)} rather than a {@code rotateZ(PI)}
 * because it is exactly that matrix, with no sine-of-pi residue. Its determinant is +1: this is a
 * rotation, not a mirror, so an attached form is not flipped.</p>
 */
public class MobRigMatrices
{
    /**
     * Records one part, given the frame vanilla was on BEFORE the part posed itself (the parent's
     * frame, i.e. the stack at the head of {@code ModelPart.rotate}). The part's own pivot,
     * rotation and scale are re-applied here instead of being read back out of a captured matrix,
     * which keeps both flavours the cache wants — the frame at the pivot before the bone rotates,
     * and the full one — exact and inverse-free.
     */
    public static void put(MatrixCache cache, Matrix4f baseInverse, String bone, ModelPart part, Matrix4f parent)
    {
        Matrix4f origin = new Matrix4f(baseInverse).mul(parent);

        origin.translate(part.pivotX / 16F, part.pivotY / 16F, part.pivotZ / 16F);

        Matrix4f matrix = new Matrix4f(origin);

        if (part.pitch != 0F || part.yaw != 0F || part.roll != 0F)
        {
            matrix.rotate(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch));
        }

        if (part.xScale != 1F || part.yScale != 1F || part.zScale != 1F)
        {
            matrix.scale(part.xScale, part.yScale, part.zScale);
        }

        matrix.scale(-1F, -1F, 1F);
        origin.scale(-1F, -1F, 1F);

        cache.put(bone, matrix, origin);
    }

    /**
     * Where every bone sits WITHOUT rendering anything.
     *
     * <p>The gizmo, the anchor system, trackers and the motion path all ask a form for its bone
     * matrices several times a frame, at arbitrary ticks, and treat the call as a question rather
     * than a draw. So this replays vanilla's own preamble — angles, then the transform chain the
     * living entity renderer sets up — and walks the part tree for matrices, instead of running a
     * second {@code EntityRenderDispatcher.render} that would re-enter item and armor rendering
     * and touch GL state from inside what the callers think is a read.</p>
     *
     * <p>Model parts are shared with the world's real entities, so everything written here is put
     * back in a finally. It also refuses to run while an entity render is in flight, which is the
     * one moment those writes would be seen by someone else.</p>
     */
    public static void evaluate(Entity entity, MobRig rig, Pose pose, Pose poseOverlay, float transition, MatrixCache cache)
    {
        if (rig == null || MobRenderContext.current() != null
            || !(entity instanceof LivingEntity living)
            || !(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer renderer))
        {
            return;
        }

        EntityModel model = renderer.getModel();
        LivingEntityRendererInvoker invoker = (LivingEntityRendererInvoker) renderer;
        Map<ModelPart, Transform> saved = new IdentityHashMap<>();

        model.handSwingProgress = invoker.bbs$getHandSwingProgress(living, transition);
        model.riding = living.hasVehicle();
        model.child = living.isBaby();

        float bodyYaw = MathHelper.lerpAngleDegrees(transition, living.prevBodyYaw, living.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(transition, living.prevHeadYaw, living.headYaw);
        float pitch = MathHelper.lerp(transition, living.prevPitch, living.getPitch());
        float animationProgress = invoker.bbs$getAnimationCounter(living, transition);
        float limbDistance = 0F;
        float limbAngle = 0F;

        if (!living.hasVehicle() && living.isAlive())
        {
            limbDistance = Math.min(living.limbAnimator.getSpeed(transition), 1F);
            limbAngle = living.limbAnimator.getPos(transition) * (living.isBaby() ? 3F : 1F);
        }

        try
        {
            model.animateModel(living, limbAngle, limbDistance, transition);
            model.setAngles(living, limbAngle, limbDistance, animationProgress, headYaw - bodyYaw, pitch);

            MobPoseApplier.apply(rig, MobPoseApplier.merge(pose, poseOverlay), saved);

            MatrixStack stack = new MatrixStack();

            /* Since 1.21.1 the entity carries a scale attribute: vanilla scales the stack by it
             * and hands it to setupTransforms, so the rig has to walk the same chain. */
            float entityScale = living.getScale();

            stack.scale(entityScale, entityScale, entityScale);
            invoker.bbs$setupTransforms(living, stack, animationProgress, bodyYaw, transition, entityScale);
            stack.scale(-1F, -1F, 1F);
            invoker.bbs$scale(living, stack, transition);
            stack.translate(0F, -1.501F, 0F);

            Matrix4f baseInverse = new Matrix4f();

            for (String root : rig.getRootGroupKeys())
            {
                walk(cache, baseInverse, rig, stack, rig.part(root));
            }
        }
        finally
        {
            MobPoseApplier.restore(saved);
        }
    }

    /**
     * The same descent {@code ModelPart.render} makes, minus the drawing — including its two
     * early-outs, so a bone that would not be drawn does not get a matrix here either and the two
     * paths agree on which bones exist.
     */
    private static void walk(MatrixCache cache, Matrix4f baseInverse, MobRig rig, MatrixStack stack, ModelPart part)
    {
        if (part == null || !part.visible)
        {
            return;
        }

        Map<String, ModelPart> children = IBBSModelPart.of(part).bbs$children();

        if (part.isEmpty() && children.isEmpty())
        {
            return;
        }

        String bone = rig.name(part);

        if (bone != null)
        {
            put(cache, baseInverse, bone, part, stack.peek().getPositionMatrix());
        }

        stack.push();
        part.rotate(stack);

        for (ModelPart child : children.values())
        {
            walk(cache, baseInverse, rig, stack, child);
        }

        stack.pop();
    }
}
