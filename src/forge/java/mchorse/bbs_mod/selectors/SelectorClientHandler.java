package mchorse.bbs_mod.selectors;

import mchorse.bbs_mod.client.renderer.DeathPose;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** Native update/render boundaries; selector forms never replace the stand-in mobs they render. */
public final class SelectorClientHandler
{
    private boolean rendering;

    @SubscribeEvent
    public void attach(AttachCapabilitiesEvent<Entity> event)
    {
        Entity entity = event.getObject();
        if (entity instanceof EntityLivingBase && entity.world != null && entity.world.isRemote)
            event.addCapability(new ResourceLocation("bbs", "selector_owner"), new SelectorOwnerCapability((EntityLivingBase) entity));
    }

    @SubscribeEvent
    public void update(LivingEvent.LivingUpdateEvent event)
    {
        if (!event.getEntityLiving().world.isRemote) return;
        SelectorOwner owner = SelectorOwnerCapability.get(event.getEntityLiving());
        if (owner != null) owner.update();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(RenderLivingEvent.Pre<?> event)
    {
        if (this.rendering || FormUtilsClient.getCurrentForm() != null) return;
        EntityLivingBase entity = event.getEntity();
        SelectorOwner owner = SelectorOwnerCapability.get(entity);
        if (owner == null) return;
        owner.check();
        if (owner.getForm() == null) return;
        event.setCanceled(true);

        float transition = event.getPartialRenderTick();
        WorldRenderContext frame = WorldRenderContext.capture(transition);
        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix());
        stack.peek().getNormalMatrix().set(stack.peek().getPositionMatrix()).invert().transpose();
        stack.translate(event.getX(), event.getY(), event.getZ());
        float yaw = entity.prevRenderYawOffset + transition * MathHelper.wrapDegrees(entity.renderYawOffset - entity.prevRenderYawOffset);
        stack.multiply(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
        DeathPose.apply(stack, entity.deathTime, transition);

        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.enableDepth();
        this.rendering = true;
        boolean renderLast = FormRenderLast.open();
        try
        {
            FormUtilsClient.render(owner.getForm(), new FormRenderingContext()
                .set(FormRenderType.ENTITY, owner.entity, stack, entity.getBrightnessForRender(),
                    entity.hurtTime > 0 || entity.deathTime > 0 ? 3 << 16 : 10 << 16, transition)
                .camera(frame.camera()));
        }
        finally
        {
            FormRenderLast.close(renderLast);
            this.rendering = false;
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(mode);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
    }
}
