package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

public class GunProjectileEntityRenderer extends Render<GunProjectileEntity> {
    public GunProjectileEntityRenderer(RenderManager manager) { super(manager);this.shadowSize=0; }
    @Override protected ResourceLocation getEntityTexture(GunProjectileEntity entity) { return new ResourceLocation("textures/entity/steve.png"); }
    @Override public void doRender(GunProjectileEntity projectile,double x,double y,double z,float yaw,float transition) {
        GunProperties properties=projectile.getProperties();
        if(projectile.getForm()==null)return;
        WorldRenderContext frame=WorldRenderContext.capture(transition);
        MatrixStack matrices=new MatrixStack();
        matrices.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix()).translate((float)x,(float)y,(float)z);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        float bodyYaw=projectile.prevRotationYaw+MathHelper.wrapDegrees(projectile.rotationYaw-projectile.prevRotationYaw)*transition;
        float pitch=projectile.prevRotationPitch+MathHelper.wrapDegrees(projectile.rotationPitch-projectile.prevRotationPitch)*transition;
        int out=properties.lifeSpan-2;
        float scale=Lerps.envelope(projectile.ticksExisted+transition,0,properties.fadeIn,out-properties.fadeOut,out);
        if(properties.yaw)matrices.multiply(new Quaternionf().rotationY((float)Math.toRadians(bodyYaw)));
        if(properties.pitch)matrices.multiply(new Quaternionf().rotationX((float)Math.toRadians(-pitch)));
        MatrixStackUtils.scaleStack(matrices,scale,scale,scale);
        MatrixStackUtils.applyTransform(matrices,properties.projectileTransform);
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try {
            GlStateManager.enableDepth();
            FormUtilsClient.render(projectile.getForm(),new FormRenderingContext().set(FormRenderType.ENTITY,projectile.getEntity(),matrices,projectile.getBrightnessForRender(),10<<16,transition).camera(frame.camera()));
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(mode);
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
        }
        super.doRender(projectile,x,y,z,yaw,transition);
    }
}
