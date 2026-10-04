package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.entity.player.EntityPlayer;

/** Common-side dimension hooks also apply when vanilla chooses sleep or elytra eye heights. */
public final class MorphPlayerHooks
{
    public static float eyeHeight(EntityPlayer player, float vanilla)
    {
        Morph morph = Morph.getMorph(player);
        Form form = morph == null ? null : morph.getForm();
        if (form == null || !form.hitbox.get()) return vanilla;
        return form.hitboxEyeHeight.get() * form.hitboxHeight.get()
            * (player.isSneaking() ? form.hitboxSneakMultiplier.get() : 1F);
    }
    private MorphPlayerHooks() {}
}
