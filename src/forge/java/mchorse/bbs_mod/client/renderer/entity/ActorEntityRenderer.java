package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.renderer.DeathPose;
import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** Remote actors use the same complete form pipeline as a film's local actors. */
public class ActorEntityRenderer extends Render<ActorEntity>
{
    public static final ArmorRenderer armorRenderer = new ArmorRenderer();
    public ActorEntityRenderer(RenderManager manager) { super(manager); this.shadowSize = 0; }
    @Override protected ResourceLocation getEntityTexture(ActorEntity entity) { return new ResourceLocation("textures/entity/steve.png"); }

    @Override public void doRender(ActorEntity entity, double x, double y, double z, float yaw, float transition)
    {
        if (BBSModClient.getFilms().isActorDrawn(entity.getEntityId()) || entity.isInvisible()) return;
        WorldRenderContext frame = WorldRenderContext.capture(transition);
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix()).translate((float)x, (float)y, (float)z);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        float bodyYaw = entity.prevRenderYawOffset + transition * MathHelper.wrapDegrees(entity.renderYawOffset - entity.prevRenderYawOffset);
        matrices.multiply(new Quaternionf().rotationY((float)Math.toRadians(-bodyYaw)));
        DeathPose.apply(matrices, entity.deathTime, transition);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        try
        {
            GlStateManager.enableBlend(); GlStateManager.enableDepth();
            FormUtilsClient.render(entity.getForm(), new FormRenderingContext()
                .set(FormRenderType.ENTITY, entity.getEntity(), matrices, entity.getBrightnessForRender(), entity.hurtTime > 0 || entity.deathTime > 0 ? 3 << 16 : 10 << 16, transition)
                .camera(frame.camera()));
        }
        finally
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(mode);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
        super.doRender(entity, x, y, z, yaw, transition);
    }
}
