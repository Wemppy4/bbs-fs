package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin
{
    @Inject(method = "dropItem", at = @At("RETURN"))
    public void onDropItem(CallbackInfoReturnable<ItemEntity> info)
    {
        ItemEntity entity = info.getReturnValue();

        if (entity != null)
        {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            BBSMod.getActions().addAction(player, () ->
            {
                ItemDropActionClip actionClip = new ItemDropActionClip();
                Vec3d velocity = entity.getVelocity();
                Vec3d pos = entity.getEntityPos();

                actionClip.velocityX.set((float) velocity.x);
                actionClip.velocityY.set((float) velocity.y);
                actionClip.velocityZ.set((float) velocity.z);
                actionClip.posX.set(pos.x);
                actionClip.posY.set(pos.y);
                actionClip.posZ.set(pos.z);
                actionClip.itemStack.set(entity.getStack().copy());

                return actionClip;
            });
        }
    }
}