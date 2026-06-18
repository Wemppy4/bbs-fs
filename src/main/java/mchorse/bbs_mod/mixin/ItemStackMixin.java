package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin
{
    @Inject(method = "use", at = @At("HEAD"))
    public void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> info)
    {
        if (user instanceof ServerPlayerEntity player)
        {
            BBSMod.getActions().addAction(player, () ->
            {
                UseItemActionClip clip = new UseItemActionClip();

                clip.itemStack.set(user.getStackInHand(hand).copy());
                clip.hand.set(hand == Hand.MAIN_HAND);

                return clip;
            });
        }
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    public void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> info)
    {
        if (context.getPlayer() instanceof ServerPlayerEntity player)
        {
            BBSMod.getActions().addAction(player, () ->
            {
                UseBlockItemActionClip clip = new UseBlockItemActionClip();

                clip.hit.setHitResult(context);
                clip.itemStack.set(context.getStack().copy());
                clip.hand.set(context.getHand() == Hand.MAIN_HAND);

                return clip;
            });
        }
    }
}