package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.LivePlayerItemUse;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Which stack a body is using and how much of the use is left, answered from
 * the film while the player's own body is being drawn.
 *
 * <p>{@code HeldItemRenderer} gates the whole use animation on
 * {@code isUsingItem() && getItemUseTimeLeft() > 0 && getActiveHand() == hand}
 * (javap 1.20.4), and the bow / crossbow / shield / trident model predicates
 * ask for the active stack by identity. The first two of those the player
 * answers themselves - see {@link ClientPlayerEntityFilmUseMixin}; these two
 * they inherit, so they are answered here.</p>
 *
 * <p>Only during the render pass, and only for the local player - see
 * {@link LivePlayerItemUse}. Everything else keeps the honest answer, which is
 * why this cannot leak into gameplay: the input handling and the entity tick
 * run outside that window.</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityFilmUseMixin
{
    @Inject(method = "getActiveItem", at = @At("HEAD"), cancellable = true)
    private void bbsFilmGetActiveItem(CallbackInfoReturnable<ItemStack> info)
    {
        if (LivePlayerItemUse.answersFor((LivingEntity) (Object) this))
        {
            info.setReturnValue(LivePlayerItemUse.getStack());
        }
    }

    @Inject(method = "getItemUseTimeLeft", at = @At("HEAD"), cancellable = true)
    private void bbsFilmGetItemUseTimeLeft(CallbackInfoReturnable<Integer> info)
    {
        if (LivePlayerItemUse.answersFor((LivingEntity) (Object) this))
        {
            info.setReturnValue(LivePlayerItemUse.getTimeLeft());
        }
    }
}
