package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.DeathPose;
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

public class ActorEntityRenderer extends EntityRenderer<ActorEntity, ActorEntityRenderer.ActorRenderState>
{
    public static ArmorRenderer armorRenderer;

    /**
     * The form + vanilla-entity context cannot be carried on a vanilla render state, so the actual
     * BBS form rendering still reads off the live {@link ActorEntity}. It rides on the state the
     * frame is drawn from and never on the renderer: 1.21.11 fills the render states of every entity
     * first (WorldRenderer#fillEntityRenderStates) and draws them only afterwards
     * (WorldRenderer#pushEntityRenders), so a field on the renderer holds whichever actor was
     * extracted last - and every actor of the film gets drawn with that one's form and pose.
     */
    public static class ActorRenderState extends LivingEntityRenderState
    {
        public ActorEntity entity;
        public float tickDelta;
    }

    public ActorEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);

        /* 1.21.4+ equipment rewrite: the inner/outer armor layers became per-slot equipment model
         * layers (EntityModelLayers.PLAYER_EQUIPMENT: head/chest/legs/feet). The armor geometry
         * MUST come from these — building the models off the PLAYER layer put the 64x32 armor
         * texture onto player-model UVs, which is exactly the garbled full-body leather look. */
        armorRenderer = new ArmorRenderer(
            EntityModelLayers.PLAYER_EQUIPMENT.map((layer) -> new BipedEntityModel(ctx.getPart(layer))),
            ctx.getPart(EntityModelLayers.ELYTRA),
            ctx.getEquipmentModelLoader()
        );

        this.shadowRadius = 0.5F;
    }

    @Override
    public ActorRenderState createRenderState()
    {
        return new ActorRenderState();
    }

    @Override
    public void updateRenderState(ActorEntity entity, ActorRenderState state, float tickDelta)
    {
        super.updateRenderState(entity, state, tickDelta);

        state.entity = entity;
        state.tickDelta = tickDelta;

        state.bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.lastBodyYaw, entity.bodyYaw);
        state.deathTime = entity.deathTime > 0 ? entity.deathTime + tickDelta : 0F;

        /* The red damage flash, exactly as LivingEntityRenderer derives it: a blow OR a death. The
         * death half is what keeps the body red for the whole fall - this renderer extends
         * EntityRenderer and fills the living state itself, so nothing else was setting it. */
        state.hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        state.pose = entity.getPose();
        state.invisible = entity.isInvisible();
    }

    @Override
    public void render(ActorRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        super.render(state, matrices, queue, cameraState);

        ActorEntity entity = state.entity;

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

        /* An actor standing in the world is a world draw, so it goes inside the world-forms span —
         * the same rule the morph's first-person arm and a held model item already follow. Outside it
         * the draw takes the shared {@code bbs:pipeline/model}, which carries no shaderpack program
         * assignment ("Missing program bbs:pipeline/model in override list" in the log), so under a
         * pack the actor came out ghosted while every replay the film editor draws itself — those go
         * through the span — stayed solid. That asymmetry was the whole bug report: the actor turns
         * see-through the moment shaders are on. */
        boolean prevWorldForms = BBSRendering.beginWorldForms();

        try
        {
            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, entity.getFormEntity(), matrices, state.light, overlay, state.tickDelta)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
        }
        finally
        {
            BBSRendering.endWorldForms(prevWorldForms);

            matrices.pop();
        }
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

        DeathPose.apply(matrices, state.deathTime);
    }
}
