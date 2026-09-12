package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.irisshaders.iris.pathways.HandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hand pass answers "no" while a form renders into a framebuffer of its own.
 *
 * <p>Iris picks the program for an entity draw in this order: the shadow pass, then the hand, then
 * block entities, and only last does it ask shouldOverrideShaders() - the flag that goes false
 * around an off-screen render ({@link IrisUtils#renderOffscreen(Runnable)}). So in the hand pass
 * the pack put its gbuffers_hand program on a form's nested parts even though we had told it the
 * main target was gone, and that program's bind() re-bound Iris' own framebuffer: the parts landed
 * in the pack's gbuffer, drawn under the framebuffer form's orthographic projection and its
 * 512-pixel viewport - a flat picture in the corner of the screen - while the form's own buffer
 * stayed empty and its quad showed nothing. Only in first person, only under a pack.</p>
 *
 * <p>Iris' shadow pass is already answered the same way, by clearing ShadowRenderer.ACTIVE for the
 * duration of the off-screen render; the hand keeps its flag private, hence a mixin instead.</p>
 */
@Mixin(HandRenderer.class)
public class HandRendererMixin
{
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsActive(CallbackInfoReturnable<Boolean> info)
    {
        if (IrisUtils.isRenderingOffscreen())
        {
            info.setReturnValue(false);
        }
    }
}
