package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class ActorEntityRenderer extends EntityRenderer<ActorEntity, LivingEntityRenderState>
{
    public static ArmorRenderer armorRenderer;

    /**
     * The form + vanilla-entity context cannot be carried on a vanilla render state, so the actual
     * BBS form rendering still reads off the live {@link ActorEntity}. We stash it here during
     * {@link #updateRenderState} so {@link #render} can route it through the form pipeline.
     *
     * TODO(1.21.11 render): the 1.21.2 render-state split assumes all per-frame data lives on the
     * state object; carrying the live entity here is a build-only bridge until the form pipeline is
     * adapted to the render-state model.
     */
    private ActorEntity renderedEntity;
    private float renderedTickDelta;

    public ActorEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);

        /* 1.21.4+ equipment rewrite: the inner/outer armor layers became per-slot equipment model
         * layers (EntityModelLayers.PLAYER_EQUIPMENT: head/chest/legs/feet). The armor geometry
         * MUST come from these — building the models off the PLAYER layer put the 64x32 armor
         * texture onto player-model UVs, which is exactly the garbled full-body leather look. */
        armorRenderer = new ArmorRenderer(
            EntityModelLayers.PLAYER_EQUIPMENT.map((layer) -> new BipedEntityModel(ctx.getPart(layer))),
            MinecraftClient.getInstance().getBakedModelManager()
        );

        this.shadowRadius = 0.5F;
    }

    @Override
    public LivingEntityRenderState createRenderState()
    {
        return new LivingEntityRenderState();
    }

    @Override
    public void updateRenderState(ActorEntity entity, LivingEntityRenderState state, float tickDelta)
    {
        super.updateRenderState(entity, state, tickDelta);

        this.renderedEntity = entity;
        this.renderedTickDelta = tickDelta;

        state.bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.lastBodyYaw, entity.bodyYaw);
        state.deathTime = entity.deathTime > 0 ? entity.deathTime + tickDelta : 0F;
        state.pose = entity.getPose();
        state.invisible = entity.isInvisible();
    }

    @Override
    public void render(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        super.render(state, matrices, queue, cameraState);

        ActorEntity entity = this.renderedEntity;

        if (entity == null || !this.isVisible(state))
        {
            return;
        }

        matrices.push();

        int overlay = LivingEntityRenderer.getOverlay(state, 0F);

        this.setupTransforms(state, matrices);

        /* TODO(1.21.11 render): blend/depth state is now pipeline-encoded; the explicit
         * RenderSystem.enableBlend/enableDepthTest toggles were removed. */
        Form form = entity.getForm();

        FormUtilsClient.render(form, new FormRenderingContext()
            .set(FormRenderType.ENTITY, entity.getFormEntity(), matrices, state.light, overlay, this.renderedTickDelta)
            .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

        matrices.pop();
    }

    protected boolean isVisible(LivingEntityRenderState state)
    {
        return !state.invisible;
    }

    protected void setupTransforms(LivingEntityRenderState state, MatrixStack matrices)
    {
        if (!state.isInPose(EntityPose.SLEEPING))
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.bodyYaw));
        }

        if (state.deathTime > 0)
        {
            float deathAngle = (state.deathTime - 1F) / 20F * 1.6F;

            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(Math.min(MathHelper.sqrt(deathAngle), 1F) * 90F));
        }
    }
}
