package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.item.ReleaseUseItemActionClip;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin
{
    @Inject(method = "applyDamage", at = @At("HEAD"))
    public void onApplyDamage(ServerWorld world, DamageSource source, float amount, CallbackInfo info)
    {
        Entity attacker = source.getAttacker();

        if (source.isDirect() && attacker != null && attacker.getClass() == ServerPlayerEntity.class)
        {
            BBSMod.getActions().addAction((ServerPlayerEntity) attacker, () ->
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(amount);

                return clip;
            });
        }
    }

    @Inject(method = "getBaseDimensions", at = @At("RETURN"), cancellable = true)
    public void onGetBaseDimensions(CallbackInfoReturnable<EntityDimensions> info)
    {
        if (this instanceof IMorphProvider provider)
        {
            Form form = provider.getMorph().getForm();

            if (form != null && form.hitbox.get())
            {
                LivingEntity entity = (LivingEntity) (Object) this;
                EntityDimensions dimensions = info.getReturnValue();
                float height = form.hitboxHeight.get() * (entity.isSneaking() ? form.hitboxSneakMultiplier.get() : 1F);

                if (dimensions.fixed())
                {
                    info.setReturnValue(EntityDimensions.fixed(form.hitboxWidth.get(), height));
                }
                else
                {
                    info.setReturnValue(EntityDimensions.changing(form.hitboxWidth.get(), height));
                }
            }
        }
    }

    /**
     * The exact vanilla moment a drawn bow fires or a trident launches:
     * stopUsingItem() calls onStoppedUsing() with the remaining use ticks, so
     * this is where the release is recorded - with the very charge the take
     * had. The getClass() check keeps the playback fake player out.
     */
    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    public void onStopUsingItem(CallbackInfo info)
    {
        if ((Object) this instanceof ServerPlayerEntity player && player.getClass() == ServerPlayerEntity.class)
        {
            ItemStack active = player.getActiveItem();

            if (active.isEmpty())
            {
                return;
            }

            boolean mainHand = player.getActiveHand() == Hand.MAIN_HAND;
            int charge = active.getMaxUseTime(player) - player.getItemUseTimeLeft();
            ItemStack stack = active.copy();
            ItemStack recordedProjectile = player.getProjectileType(active).copy();

            /* Creative players shoot without ammo and vanilla substitutes a
             * plain arrow, but the fake player has no creative mode - so the
             * substitute is baked into the clip instead */
            if (recordedProjectile.isEmpty() && stack.getItem() instanceof RangedWeaponItem && player.getAbilities().creativeMode)
            {
                recordedProjectile = new ItemStack(Items.ARROW);
            }

            ItemStack projectile = recordedProjectile;

            /* TridentItem.onStoppedUsing's own fork, evaluated here because it
             * asks the WORLD, not the item: a riptide trident released in water
             * or rain launches its owner and is never thrown. The playback fake
             * player always stands dry, so the answer has to be recorded. */
            boolean riptide = charge >= 10 && EnchantmentHelper.getTridentSpinAttackStrength(active, player) > 0F && player.isTouchingWaterOrRain();

            BBSMod.getActions().addAction(player, () ->
            {
                ReleaseUseItemActionClip clip = new ReleaseUseItemActionClip();

                clip.itemStack.set(stack);
                clip.hand.set(mainHand);
                clip.charge.set(charge);
                clip.projectile.set(projectile);
                clip.riptide.set(riptide);

                return clip;
            });
        }
    }

    /* @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true)
    public void onSwingHand(Hand hand, boolean fromServerPlayer, CallbackInfo info)
    {
        info.cancel();
    } */
}