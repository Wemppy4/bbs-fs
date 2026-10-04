package mchorse.bbs_mod.ui.forms.editors.utils;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A form picker may run inside another viewport or export framebuffer. */
public final class FormPickingScope implements AutoCloseable
{
    private final int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

    public FormPickingScope()
    {
        GL11.glGetInteger(GL11.GL_VIEWPORT, this.viewport);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, this.clearColor);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override public void close()
    {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GlStateManager.viewport(this.viewport.get(0), this.viewport.get(1), this.viewport.get(2), this.viewport.get(3));
        GlStateManager.clearColor(this.clearColor.get(0), this.clearColor.get(1), this.clearColor.get(2), this.clearColor.get(3));
        if (this.scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
