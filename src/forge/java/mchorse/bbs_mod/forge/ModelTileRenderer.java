package mchorse.bbs_mod.forge;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
public class ModelTileRenderer extends TileEntitySpecialRenderer<ModelTileEntity> {
    public void render(ModelTileEntity tile, double x, double y, double z, float partialTicks, int stage, float alpha) {
        mchorse.bbs_mod.client.BBSRendering.capturedModelBlocks.add(tile);
        StubEntity entity=tile.getRenderEntity();
        entity.setWorld(tile.getWorld());entity.setForm(tile.form);
        entity.setPosition(tile.getPos().getX()+0.5,tile.getPos().getY(),tile.getPos().getZ()+0.5);
        entity.setPrevX(entity.getX());entity.setPrevY(entity.getY());entity.setPrevZ(entity.getZ());
        entity.setAge((int)tile.getWorld().getTotalWorldTime());
        mchorse.bbs_mod.graphics.WorldRenderContext frame = mchorse.bbs_mod.graphics.WorldRenderContext.capture(partialTicks);
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(mchorse.bbs_mod.forge.studio.NativeTextureRenderer.currentMatrix())
            .translate((float)x + 0.5F, (float)y, (float)z + 0.5F);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        int mode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            FormRenderingContext context=new FormRenderingContext().set(FormRenderType.MODEL_BLOCK,entity,matrices,
                tile.getWorld().getCombinedLight(tile.getPos(),0),10<<16,partialTicks).camera(frame.camera());
            FormUtilsClient.render(tile.form,context);
        } finally {
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(mode);
        }
    }
}
