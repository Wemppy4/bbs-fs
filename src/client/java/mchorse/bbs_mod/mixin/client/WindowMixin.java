package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin
{
    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private int framebufferWidth;

    @Shadow
    private int framebufferHeight;

    @Shadow
    private int scaledWidth;

    @Shadow
    private int scaledHeight;

    @Shadow
    private double scaleFactor;

    /**
     * While BBS UI is open, its ui_scale setting replaces whatever integer scale
     * vanilla derived from the guiScale option. setScaleFactor() and everything
     * downstream (scaled size, mouse, GUI projection) already operate on doubles,
     * so fractional values work out of the box.
     */
    @ModifyVariable(method = "setScaleFactor", at = @At("HEAD"), argsOnly = true)
    private double bbs$overrideScaleFactor(double scaleFactor)
    {
        float custom = BBSModClient.getCustomGUIScale();

        if (custom > 0F)
        {
            /* Same lower bound vanilla's calculateScaleFactor() enforces: keep at
             * least ~320x240 GUI units on screen, so UI stays usable on small windows */
            double max = Math.max(1D, Math.min(this.framebufferWidth / 320D, this.framebufferHeight / 240D));

            return Math.min(Math.max(custom, 0.5D), max);
        }

        return scaleFactor;
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    public void onGetWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue(BBSRendering.getVideoWidth());
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    public void onGetHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue(BBSRendering.getVideoHeight());
        }
    }

    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    public void onGetFramebufferWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoWidth() * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    public void onGetFramebufferHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoHeight() * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getScaledWidth", at = @At("HEAD"), cancellable = true)
    public void onGetScaledWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoWidth() / this.scaleFactor * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getScaledHeight", at = @At("HEAD"), cancellable = true)
    public void onGetScaledHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoHeight() / this.scaleFactor * BBSModClient.getOriginalFramebufferScale()));
        }
    }
}