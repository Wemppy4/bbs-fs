package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.ActionActorContext;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

public class UseItemActionClip extends ItemActionClip
{
    @Override public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        EnumHand hand = this.hand.get() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        this.applyPositionRotation(player, replay, tick);
        ItemStack previous = player.getHeldItem(hand);
        try (ActionActorContext context = ActionActorContext.enter(actor))
        {
            player.setHeldItem(hand, this.itemStack.get().copy());
            player.getHeldItem(hand).useItemRightClick(player.world, player, hand);
        }
        finally { player.setHeldItem(hand, previous); }
    }
    @Override protected Clip create() { return new UseItemActionClip(); }
}
