package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.FormRenderCapture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEMPORARY (1.21.11 items diagnosis) — remove once the third-person held-item bug is closed.
 *
 * <p>The special-renderer probe proved third person never reaches BBS code at all, and the
 * vanilla chain (ArmedEntityRenderState.updateRenderState → ItemModelManager → SpecialItemModel →
 * HeldItemFeatureRenderer → ItemRenderState.render) is intact in the bytecode. So the break is
 * either above the feature (the whole player render suppressed — e.g. the morph hook) or inside
 * the state (hand item state empty). This logs which: a feature call that never happens and a
 * feature call that arrives with an empty state are different bugs.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin
{
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void bbs$probeRenderItem(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info)
    {
        FormRenderCapture.probeStage("held-feature " + arm, "empty=" + itemState.isEmpty() + " stack=" + (stack == null || stack.isEmpty() ? "EMPTY" : stack.getItem().toString()));
    }
}
