package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.mixin.client.LivingEntityRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Vanilla's own posing of a living entity's model, without a render: the flags the living entity
 * renderer sets before drawing, then {@code animateModel} and {@code setAngles} with the numbers it
 * would pass. The mob form's offline matrices ({@link MobRigMatrices}) and the CEM stage
 * ({@code CemVanillaStage}) both pose the model this way, from one place, so the two cannot drift apart.
 *
 * <p>Model parts are shared with the world's real entities of that kind. The pose left here is the
 * one every render of them overwrites first thing, so it costs nobody anything — except while an
 * entity render is in flight, which is the one moment the writes would be seen: {@link #renderer}
 * answers null then.</p>
 */
public class VanillaPose
{
    /**
     * The renderer a living entity draws through, or null when there is nothing to pose: an entity that
     * is not living, one no living renderer takes, or an entity render in flight.
     */
    public static LivingEntityRenderer renderer(Entity entity)
    {
        if (MobRenderContext.current() != null || !(entity instanceof LivingEntity))
        {
            return null;
        }

        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer renderer ? renderer : null;
    }

    /** Pose the renderer's model for this frame of the entity, the way {@code LivingEntityRenderer.render} does before it draws. */
    public static void animate(LivingEntityRenderer renderer, LivingEntity living, float transition)
    {
        EntityModel model = renderer.getModel();
        LivingEntityRendererInvoker invoker = (LivingEntityRendererInvoker) renderer;

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

        model.animateModel(living, limbAngle, limbDistance, transition);
        model.setAngles(living, limbAngle, limbDistance, animationProgress, headYaw - bodyYaw, pitch);
    }
}
