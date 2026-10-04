package mchorse.bbs_mod.film;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.nio.FloatBuffer;

/** Native ground projection and billboard labels for replay bodies. The placement
 * is supplied by FilmEntityRenderer, including anchor and shadow-follow displacement. */
public final class FilmOverlayRenderer
{
    private static final ResourceLocation SHADOW = new ResourceLocation("textures/misc/shadow.png");

    public static void renderShadow(World world, MatrixStack matrices, Camera camera, double x, double y, double z, float radius, float opacity)
    {
        if (world == null || radius <= 0F) return;
        opacity *= Math.max(0D, 1D - camera.position.distanceSquared(x, y, z) / 256D);
        if (opacity <= 0F) return;
        try (DrawState state = new DrawState(matrices.peek().getPositionMatrix()))
        {
            Minecraft.getMinecraft().getTextureManager().bindTexture(SHADOW);
            GlStateManager.depthMask(false);
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            for (BlockPos cursor : BlockPos.getAllInBoxMutable(
                new BlockPos(MathHelper.floor(x-radius), MathHelper.floor(y-radius), MathHelper.floor(z-radius)),
                new BlockPos(MathHelper.floor(x+radius), MathHelper.floor(y), MathHelper.floor(z+radius))))
            {
                BlockPos ground = cursor.down();
                IBlockState block = world.getBlockState(ground);
                if (block.getRenderType() == EnumBlockRenderType.INVISIBLE || world.getLightFromNeighbors(cursor) <= 3) continue;
                AxisAlignedBB box = block.getCollisionBoundingBox(world, ground);
                if (box == null || box.maxY <= box.minY) continue;
                double top = ground.getY() + box.maxY;
                float alpha = (float) ((opacity - (y - top) / 2D) * 0.5D * world.getLightBrightness(cursor));
                if (alpha <= 0F) continue;
                alpha = Math.min(1F, alpha);
                double x1 = ground.getX()+box.minX-x, x2 = ground.getX()+box.maxX-x;
                double z1 = ground.getZ()+box.minZ-z, z2 = ground.getZ()+box.maxZ-z;
                double sy = top-y+0.015625D;
                float u1=(float)(-x1/(2D*radius)+0.5D), u2=(float)(-x2/(2D*radius)+0.5D);
                float v1=(float)(-z1/(2D*radius)+0.5D), v2=(float)(-z2/(2D*radius)+0.5D);
                buffer.pos(x1,sy,z1).tex(u1,v1).color(1F,1F,1F,alpha).endVertex();
                buffer.pos(x1,sy,z2).tex(u1,v2).color(1F,1F,1F,alpha).endVertex();
                buffer.pos(x2,sy,z2).tex(u2,v2).color(1F,1F,1F,alpha).endVertex();
                buffer.pos(x2,sy,z1).tex(u2,v1).color(1F,1F,1F,alpha).endVertex();
            }
            Tessellator.getInstance().draw();
        }
    }

    static void renderNameTag(IEntity entity, String text, MatrixStack matrices, Camera camera)
    {
        Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix())
            .translate(0F, (float) entity.getPickingHitbox().h + 0.5F, 0F)
            .mul(new Matrix4f(camera.view).setTranslation(0,0,0).invert())
            .scale(-0.025F,-0.025F,0.025F);
        try (DrawState state = new DrawState(model))
        {
            FontRenderer font = mchorse.bbs_mod.fonts.nativefonts.NativeDefaultFont.renderer();
            float left = -font.getStringWidth(text) / 2F;
            if (!entity.isSneaking()) GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableTexture2D();
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(left-1,-1,0).color(0F,0F,0F,0.25F).endVertex();
            buffer.pos(left-1,8,0).color(0F,0F,0F,0.25F).endVertex();
            buffer.pos(-left+1,8,0).color(0F,0F,0F,0.25F).endVertex();
            buffer.pos(-left+1,-1,0).color(0F,0F,0F,0.25F).endVertex();
            Tessellator.getInstance().draw();
            GlStateManager.enableTexture2D();
            font.drawString(text,left,0F,0x20ffffff,false);
            if (!entity.isSneaking())
            {
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                font.drawString(text,left,0F,0xffffffff,false);
            }
        }
    }

    /** Scope every native state touched here; matrices already contain the camera view. */
    private static final class DrawState implements AutoCloseable
    {
        private final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND), depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean lighting=GL11.glIsEnabled(GL11.GL_LIGHTING), alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE), program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), texture;
        private final boolean textured;
        /* LWJGL2 glGetFloat requires room for a 4x4 value even for GL_CURRENT_COLOR. */
        private final FloatBuffer color=BufferUtils.createFloatBuffer(16);
        DrawState(Matrix4f model)
        {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); textured=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL20.glUseProgram(0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
            FloatBuffer values=BufferUtils.createFloatBuffer(16); model.get(values); GlStateManager.loadIdentity(); GL11.glMultMatrix(values);
            GlStateManager.disableLighting(); GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
            GlStateManager.color(1F,1F,1F,1F);
        }
        public void close()
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(mode);
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.depthMask(mask); GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.bindTexture(texture);
            if(textured)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(active);GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            GL20.glUseProgram(program);
        }
    }
    private FilmOverlayRenderer() {}
}
