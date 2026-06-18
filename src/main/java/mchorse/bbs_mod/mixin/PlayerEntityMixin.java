package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For some unknown reason to me, if these methods are used in {@link PlayerEntityMorphMixin}
 * then the world will be locked for some reason... by extracting write/read NBT method to
 * a separate mixin fixes it...
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityMixin
{
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    public void onWriteCustomDataToNbt(WriteView view, CallbackInfo info)
    {
        if (this instanceof IMorphProvider provider)
        {
            view.put("BBSMorph", NbtCompound.CODEC, (NbtCompound) provider.getMorph().toNbt());
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    public void onReadCustomDataFromNbt(ReadView view, CallbackInfo info)
    {
        if (this instanceof IMorphProvider provider)
        {
            view.read("BBSMorph", NbtCompound.CODEC).ifPresent((nbt) -> provider.getMorph().fromNbt(nbt));
        }
    }

    @Inject(method = "getBaseDimensions", at = @At("RETURN"), cancellable = true)
    public void onGetDimensions(CallbackInfoReturnable<EntityDimensions> info)
    {
        if (this instanceof IMorphProvider provider)
        {
            Form form = provider.getMorph().getForm();

            if (form != null && form.hitbox.get())
            {
                PlayerEntity player = (PlayerEntity) (Object) this;
                EntityDimensions dimensions = info.getReturnValue();
                float height = form.hitboxHeight.get() * (player.isSneaking() ? form.hitboxSneakMultiplier.get() : 1F);

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

}