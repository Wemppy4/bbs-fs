package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.FramebufferPool;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;

/** Scoped offscreen projection, FBO and queues shared by labels and framebuffer forms. */
final class NativeOffscreen implements AutoCloseable
{
    private final NativeFormDraw.State state=new NativeFormDraw.State();
    private final mchorse.bbs_mod.graphics.OptiFineShaders.LocalPass local=mchorse.bbs_mod.graphics.OptiFineShaders.localPass();
    private final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final IntBuffer viewport=BufferUtils.createIntBuffer(16),scissor=BufferUtils.createIntBuffer(16);
    private final FloatBuffer clear=BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection=BufferUtils.createFloatBuffer(16),modelView=BufferUtils.createFloatBuffer(16);
    private final boolean scissored=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean queue=FormTranslucentQueue.suspend(),last=FormRenderLast.suspend();
    private boolean closed;
    final FramebufferPool pool=BBSModClient.getFramebuffers().getFormFramebuffers();
    final Framebuffer framebuffer;
    NativeOffscreen(int width,int height,double left,double right,double bottom,double top)
    {
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);GL11.glGetInteger(GL11.GL_SCISSOR_BOX,scissor);GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,clear);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,modelView);
        framebuffer=pool.get(width,height);framebuffer.apply();
        /* The native projection stack is commonly only four entries deep. A framebuffer
         * containing another framebuffer and a label already exceeds it under the UI. */
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(left,right,bottom,top,-500,500);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.clearColor(0,0,0,0);framebuffer.clear();
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    /** Restore the caller before drawing the resulting texture; release it after the quad draw. */
    void restore()
    {
        if(closed)return;closed=true;
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrix(modelView);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GL11.glLoadMatrix(projection);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
        GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
        GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
        GL11.glScissor(scissor.get(0),scissor.get(1),scissor.get(2),scissor.get(3));if(scissored)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        FormTranslucentQueue.restore(queue);FormRenderLast.restore(last);local.close();state.close();
    }
    @Override public void close(){try{restore();}finally{pool.release(framebuffer);}}
}
