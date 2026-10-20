package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlFramebuffer.class, remap = false)
public class GlFramebufferMixin
{
    /** A depth-only camera target must not retain the window's packed stencil
     *  attachment: separate depth and stencil textures make the FBO unsupported. */
    @Inject(method = "addDepthAttachment", at = @At("HEAD"))
    private void clearPreviousStencil(int depthTexture, CallbackInfo info)
    {
        GlFramebuffer framebuffer = (GlFramebuffer) (Object) this;
        IrisRenderSystem.framebufferTexture2D(framebuffer.getId(), GL30.GL_FRAMEBUFFER,
            GL30.GL_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
    }
}
