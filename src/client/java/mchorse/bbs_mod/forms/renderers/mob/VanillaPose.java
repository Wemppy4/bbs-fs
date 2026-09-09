package mchorse.bbs_mod.forms.renderers.mob;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Vanilla's own posing of a living entity's model, without a render: the render state the living
 * entity renderer would draw from, and {@code setAngles} with it. The mob form's offline matrices
 * ({@link MobRigMatrices}) and the CEM stage ({@code CemVanillaStage}) both pose the model this way,
 * from one place, so the two cannot drift apart.
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

    /**
     * Pose the renderer's model for this frame of the entity, the way {@code LivingEntityRenderer.render}
     * does before it draws, and hand back the state it posed from — null when the entity has none.
     *
     * <p>Since 1.21.2 the model is posed from a render STATE rather than from the entity: setAngles
     * takes one, and everything the 1.21.1 call assembled by hand — hand swing, riding, baby, the
     * limb swing, the interpolated yaws and pitch, the scale attribute — is a field on it.
     * getAndUpdateRenderState is the one supported way to fill one, and it is what MobFormRenderer's
     * own draw already uses, so a caller walks exactly the pose the entity is drawn in rather than a
     * second, hand-built approximation of it.</p>
     */
    public static LivingEntityRenderState animate(LivingEntityRenderer renderer, LivingEntity living, float transition)
    {
        EntityRenderState renderState = MinecraftClient.getInstance().getEntityRenderDispatcher().getAndUpdateRenderState(living, transition);

        if (!(renderState instanceof LivingEntityRenderState state))
        {
            return null;
        }

        renderer.getModel().setAngles(state);

        return state;
    }
}
