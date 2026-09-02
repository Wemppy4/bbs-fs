package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.morphing.MorphHitbox;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The morph's hitbox for players, which {@link LivingEntityMixin}'s hook cannot reach.
 *
 * <p>1.21.9 pushed a {@link PlayerLikeEntity} in between {@code LivingEntity} and
 * {@code PlayerEntity}, and it overrides {@code getBaseDimensions} with a straight lookup in its own
 * pose table — no {@code super} call anywhere in it. So the {@code LivingEntity} injection never
 * runs for a player, and the form's hitbox settings did nothing at all for the one entity they are
 * used on most. On 1.21.1 the same gap existed one class lower and was covered by the same hook in
 * {@code PlayerEntityMixin}.</p>
 */
@Mixin(PlayerLikeEntity.class)
public class PlayerLikeEntityMixin
{
    @Inject(method = "getBaseDimensions", at = @At("RETURN"), cancellable = true)
    public void onGetBaseDimensions(CallbackInfoReturnable<EntityDimensions> info)
    {
        EntityDimensions dimensions = MorphHitbox.override((LivingEntity) (Object) this, info.getReturnValue());

        if (dimensions != null)
        {
            info.setReturnValue(dimensions);
        }
    }
}
