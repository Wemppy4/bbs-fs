package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import java.util.function.BooleanSupplier;

/** The original morph body and first-person arm pipelines at native Forge render boundaries. */
public final class MorphRenderer
{
    public static boolean hidePlayer;
    private static BooleanSupplier visibility = () -> true;

    /** Installed by the real morphing panel; editing its preview hides the world body. */
    public static void setVisibility(BooleanSupplier value) { visibility = value; }

    @SubscribeEvent
    public void renderPlayer(RenderPlayerEvent.Pre event)
    {
        if (hidePlayer && FormUtilsClient.getCurrentForm() instanceof MobForm
            && !((MobForm) FormUtilsClient.getCurrentForm()).isPlayer())
        {
            event.setCanceled(true);
            return;
        }
        Morph morph = Morph.getMorph(event.getEntityPlayer());
        if (morph == null || morph.getForm() == null) return;
        event.setCanceled(true);
        if (!visibility.getAsBoolean()) return;

        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntityPlayer();
        float transition = event.getPartialRenderTick();
        WorldRenderContext frame = WorldRenderContext.capture(transition);
        MatrixStack stack = currentStack();
        stack.translate(event.getX(), event.getY(), event.getZ());
        float yaw = player.prevRenderYawOffset + transition * MathHelper.wrapDegrees(player.renderYawOffset - player.prevRenderYawOffset);
        stack.multiply(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
        DeathPose.apply(stack, player.deathTime, transition);
        try (NativeScope ignored = new NativeScope())
        {
            boolean renderLast = FormRenderLast.open();
            try
            {
                FormUtilsClient.render(morph.getForm(), new FormRenderingContext()
                    .set(FormRenderType.ENTITY, morph.entity, stack, player.getBrightnessForRender(),
                        player.hurtTime > 0 || player.deathTime > 0 ? 3 << 16 : 10 << 16, transition)
                    .camera(frame.camera()));
            }
            finally { FormRenderLast.close(renderLast); }
        }
    }

    /** Called at RenderPlayer.renderRightArm/renderLeftArm HEAD, before vanilla changes the arm frame. */
    public static boolean renderArm(AbstractClientPlayer player, boolean right)
    {
        Morph morph = Morph.getMorph(player);
        if (morph == null || morph.getForm() == null) return false;
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(morph.getForm());
        if (renderer == null) return false;
        if (renderer instanceof ModelFormRenderer)
            ((ModelFormRenderer) renderer).ensureAnimator(Minecraft.getMinecraft().getRenderPartialTicks());
        MatrixStack stack = currentStack();
        try (NativeScope ignored = new NativeScope())
        {
            return renderer.renderArm(stack, player.getBrightnessForRender(), player, right ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND);
        }
    }

    private static MatrixStack currentStack()
    {
        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix());
        stack.peek().getNormalMatrix().set(stack.peek().getPositionMatrix()).invert().transpose();
        return stack;
    }

    private static final class NativeScope implements AutoCloseable
    {
        private final int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        NativeScope()
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.enableDepth();
        }
        public void close()
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(mode);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
    }
}
