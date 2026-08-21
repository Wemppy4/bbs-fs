package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
    private int scaleFactor;

    /**
     * While BBS UI is open, its ui_scale setting replaces whatever scale vanilla derived from the
     * guiScale option.
     *
     * <p>On 1.21.1 {@code Window.scaleFactor} was a double and one override was the whole story. Here
     * it is an {@code int} again, so the fractional scale cannot ride this argument: the window is
     * given the nearest whole step — which is only ever read as a resolution hint (the GUI item atlas
     * re-renders when it changes) — and the fractional value is applied to the two things that decide
     * how big a GUI pixel actually is, the scaled size below and, in {@code GuiRendererMixin}, the GUI
     * projection and scissor. Anything else that maps GUI units to pixels must go through
     * {@link BBSModClient#getGUIScale()} rather than read the window's rounded factor.</p>
     */
    @ModifyVariable(method = "setScaleFactor", at = @At("HEAD"), argsOnly = true)
    private int bbs$overrideScaleFactor(int scaleFactor)
    {
        float custom = BBSModClient.getCustomGUIScale();

        if (custom > 0F)
        {
            return Math.max(1, Math.round(BBSModClient.clampGUIScale(custom, this.framebufferWidth, this.framebufferHeight)));
        }

        return scaleFactor;
    }

    /**
     * The size of the GUI in its own units, recomputed against the FRACTIONAL scale.
     *
     * <p>Vanilla has just divided the framebuffer by the rounded factor. These two fields are what
     * every screen lays itself out in and what the mouse position is converted through, so writing
     * them here is what actually makes ui_scale 1.5 mean 1.5 — rounded up, exactly as vanilla rounds
     * its own division, so the last partial GUI pixel still exists.</p>
     */
    @Inject(method = "setScaleFactor", at = @At("TAIL"))
    private void bbs$fractionalScaledSize(int scaleFactor, CallbackInfo info)
    {
        float custom = BBSModClient.getCustomGUIScale();

        if (custom <= 0F)
        {
            return;
        }

        float scale = BBSModClient.clampGUIScale(custom, this.framebufferWidth, this.framebufferHeight);

        this.scaledWidth = (int) Math.ceil(this.framebufferWidth / scale);
        this.scaledHeight = (int) Math.ceil(this.framebufferHeight / scale);
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
            /* The same fractional scale the GUI projection uses (see bbs$fractionalScaledSize) — the
             * window's rounded factor here would lay the interface out at one scale while it is drawn
             * at another for every exported frame. */
            info.setReturnValue((int) (BBSRendering.getVideoWidth() / (double) BBSModClient.getGUIScale() * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getScaledHeight", at = @At("HEAD"), cancellable = true)
    public void onGetScaledHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoHeight() / (double) BBSModClient.getGUIScale() * BBSModClient.getOriginalFramebufferScale()));
        }
    }
}