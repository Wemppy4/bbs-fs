package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.LivePlayerItemUse;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two questions the player answers for THEMSELVES rather than from the
 * living entity's tracked flags.
 *
 * <p>{@code ClientPlayerEntity} overrides {@code isUsingItem} and
 * {@code getActiveHand} with its own prediction fields ({@code usingItem},
 * {@code activeHand}, javap 1.20.4) - the tracked byte every other body reads
 * is not consulted at all. So the film has to be heard here too, or the whole
 * use animation stays gated shut on the very body it is meant to drive; the
 * other two questions ({@code getActiveItem}, {@code getItemUseTimeLeft}) are
 * inherited untouched and are answered in {@link LivingEntityFilmUseMixin}.</p>
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityFilmUseMixin
{
    @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
    private void bbsFilmIsUsingItem(CallbackInfoReturnable<Boolean> info)
    {
        if (LivePlayerItemUse.answersFor((LivingEntity) (Object) this))
        {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "getActiveHand", at = @At("HEAD"), cancellable = true)
    private void bbsFilmGetActiveHand(CallbackInfoReturnable<Hand> info)
    {
        if (LivePlayerItemUse.answersFor((LivingEntity) (Object) this))
        {
            info.setReturnValue(LivePlayerItemUse.getHand());
        }
    }
}
