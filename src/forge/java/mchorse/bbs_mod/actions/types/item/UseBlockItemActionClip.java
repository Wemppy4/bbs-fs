package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.values.ValueBlockHitResult;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.actions.ActionActorContext;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;

public class UseBlockItemActionClip extends ItemActionClip
{
    public final ValueBlockHitResult hit = new ValueBlockHitResult("hit");

    public UseBlockItemActionClip()
    {
        super();

        this.add(this.hit);
    }

    @Override
    public void shift(double dx, double dy, double dz)
    {
        super.shift(dx, dy, dz);

        this.hit.shift(dx, dy, dz);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        EnumHand hand = this.hand.get() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        ItemStack copy = this.itemStack.get().copy();

        this.applyPositionRotation(player, replay, tick);
        ItemStack previous = player.getHeldItem(hand);
        try (ActionActorContext context = ActionActorContext.enter(actor))
        {
            player.setHeldItem(hand, copy);
            RayTraceResult hit = this.hit.getHitResult();
            BlockPos pos = hit.getBlockPos();
            copy.onItemUse(player, player.world, pos, hand, hit.sideHit,
                (float) (hit.hitVec.x-pos.getX()), (float) (hit.hitVec.y-pos.getY()), (float) (hit.hitVec.z-pos.getZ()));
        }
        finally { player.setHeldItem(hand, previous); }
    }

    @Override
    protected Clip create()
    {
        return new UseBlockItemActionClip();
    }
}