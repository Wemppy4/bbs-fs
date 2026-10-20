package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pbr.TextureInfoCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderTargets.class, remap = false)
public class RenderTargetsMixin
{
    @Shadow private int currentDepthTexture;
    @Shadow private int cachedDepthBufferVersion;
    @Shadow private DepthBufferFormat currentDepthFormat;
    @Shadow private DepthTexture noHand;
    @Shadow private DepthTexture noTranslucents;
    @Shadow private GlFramebuffer noHandDestFb;
    @Shadow private GlFramebuffer noTranslucentsDestFb;
    @Unique private boolean bbs$depthFormatChanged;

    /** Independent framebuffers can have the same allocation version. When BBS
     *  switches the main target, Iris must reattach its new depth texture too. */
    @Inject(method = "resizeIfNeeded", at = @At("HEAD"))
    private void onDepthTargetChanged(int depthBufferVersion, int depthTexture, int width, int height,
        DepthBufferFormat depthFormat, PackDirectives directives, CallbackInfoReturnable<Boolean> info)
    {
        this.bbs$depthFormatChanged = this.currentDepthFormat != depthFormat;
        if (this.currentDepthTexture != depthTexture)
        {
            this.cachedDepthBufferVersion = depthBufferVersion - 1;
        }
    }

    /** Iris reallocates these textures on format changes, but the attachment
     *  points must change too when switching between packed and plain depth. */
    @Inject(method = "resizeIfNeeded", at = @At("RETURN"))
    private void refreshDepthCopyAttachments(int depthBufferVersion, int depthTexture, int width, int height,
        DepthBufferFormat depthFormat, PackDirectives directives, CallbackInfoReturnable<Boolean> info)
    {
        if (this.bbs$depthFormatChanged)
        {
            /* DSA allocation bypasses TextureInfoCache's texImage2D hook.
             * Evict the old format before choosing the new attachment points. */
            TextureInfoCache.INSTANCE.onDeleteTexture(this.noHand.getTextureId());
            TextureInfoCache.INSTANCE.onDeleteTexture(this.noTranslucents.getTextureId());
            this.noHandDestFb.addDepthAttachment(this.noHand.getTextureId());
            this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents.getTextureId());
        }
    }
}
