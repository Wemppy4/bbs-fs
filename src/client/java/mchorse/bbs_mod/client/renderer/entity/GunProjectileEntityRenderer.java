package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class GunProjectileEntityRenderer extends EntityRenderer<GunProjectileEntity, GunProjectileEntityRenderer.GunProjectileRenderState>
{
    public GunProjectileEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);
    }

    @Override
    public GunProjectileRenderState createRenderState()
    {
        return new GunProjectileRenderState();
    }

    @Override
    public void updateRenderState(GunProjectileEntity entity, GunProjectileRenderState state, float tickDelta)
    {
        super.updateRenderState(entity, state, tickDelta);

        GunProperties properties = entity.getProperties();
        int out = properties.lifeSpan - 2;

        state.entity = entity;
        state.tickDelta = tickDelta;
        state.bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.lastYaw, entity.getYaw());
        state.entityPitch = MathHelper.lerpAngleDegrees(tickDelta, entity.lastPitch, entity.getPitch());
        state.fadeScale = Lerps.envelope(entity.age + tickDelta, 0, properties.fadeIn, out - properties.fadeOut, out);
    }

    @Override
    public void render(GunProjectileRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        super.render(state, matrices, queue, cameraState);

        GunProjectileEntity projectile = state.entity;

        if (projectile == null)
        {
            return;
        }

        GunProperties properties = projectile.getProperties();

        matrices.push();

        if (properties.yaw) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.bodyYaw));
        if (properties.pitch) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.entityPitch));
        matrices.scale(state.fadeScale, state.fadeScale, state.fadeScale);
        MatrixStackUtils.applyTransform(matrices, properties.projectileTransform);

        /* TODO(1.21.11 render): depth state is now pipeline-encoded; RenderSystem.enableDepthTest was removed. */

        /* World draw, so it needs the world-forms span — same rule (and same ghosting under a
         * shaderpack without it) as the actor next door. */
        boolean prevWorldForms = BBSRendering.beginWorldForms();

        try
        {
            FormUtilsClient.render(projectile.getForm(), new FormRenderingContext()
                .set(FormRenderType.ENTITY, projectile.getFormEntity(), matrices, state.light, OverlayTexture.DEFAULT_UV, state.tickDelta)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
        }
        finally
        {
            BBSRendering.endWorldForms(prevWorldForms);

            matrices.pop();
        }
    }

    /**
     * Carries the projectile-specific per-frame data + the live entity through the render-state model.
     *
     * TODO(1.21.11 render): carrying the live entity is a build-only bridge until the BBS form
     * pipeline is adapted to read off the render state.
     */
    public static class GunProjectileRenderState extends EntityRenderState
    {
        public GunProjectileEntity entity;
        public float tickDelta;
        public float bodyYaw;
        public float entityPitch;
        public float fadeScale;
    }
}
