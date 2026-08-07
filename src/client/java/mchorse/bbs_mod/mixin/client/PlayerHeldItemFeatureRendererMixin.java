package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.FormRenderCapture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEMPORARY (1.21.11 items diagnosis) — remove with {@link HeldItemFeatureRendererMixin}.
 *
 * <p>The player renderer OVERRIDES renderItem (empty-state early return + spyglass split) and only
 * then calls super — so the base-class hook never fires when the player's hand state is empty.
 * This hook sits at the override's head and reports the state as the feature receives it.
 */
@Mixin(PlayerHeldItemFeatureRenderer.class)
public abstract class PlayerHeldItemFeatureRendererMixin
{
    @Inject(
        method = "renderItem(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
        at = @At("HEAD")
    )
    private void bbs$probePlayerRenderItem(PlayerEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo info)
    {
        FormRenderCapture.probeStage("player-held-feature " + arm, "empty=" + itemState.isEmpty() + " stack=" + (stack == null || stack.isEmpty() ? "EMPTY" : stack.getItem().toString()));
    }
}
