package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/**
 * Vanilla's "released the use button" moment: a drawn bow firing its arrow, a
 * trident flying off, a crossbow snapping loaded. Playback re-runs
 * ItemStack.onStoppedUsing() on the fake player, so the projectile is a real
 * server entity with real physics, aimed by the replay's recorded rotations.
 */
public class ReleaseUseItemActionClip extends ItemActionClip
{
    /** Ticks the item was held drawn; restores the vanilla charge on playback. */
    public final ValueInt charge = new ValueInt("charge", 0, 0, 72000);

    /**
     * The ammo vanilla picked during the take. Lent to the fake player's other
     * hand, because its own inventory has no arrows to shoot.
     */
    public final ValueItemStack projectile = new ValueItemStack("projectile");

    /**
     * The take riptided instead of throwing: a riptide trident released while
     * touching water or rain launches its owner and stays in their hand. That
     * condition is the WORLD's, not the item's, and the fake player replaying
     * the release stands dry in whatever world the film plays in - so it's
     * decided at record time and carried here.
     */
    public final ValueBoolean riptide = new ValueBoolean("riptide", false);

    public ReleaseUseItemActionClip()
    {
        super();

        this.add(this.charge);
        this.add(this.projectile);
        this.add(this.riptide);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        EnumHand hand = this.hand.get() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        EnumHand other = this.hand.get() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        ItemStack stack = this.itemStack.get().copy();
        ItemStack projectile = this.projectile.get();

        this.applyPositionRotation(player, replay, tick);

        if (this.riptide.get())
        {
            throw new IllegalArgumentException("Riptide requires a trident provider; vanilla Minecraft 1.12 has no tridents");
        }

        /* Use clips leave the fake player's "using an item" flag raised, and a
         * raised flag makes setCurrentHand() refuse to work */
        player.resetActiveHand();
        player.setHeldItem(hand, stack);

        if (!projectile.isEmpty())
        {
            player.setHeldItem(other, projectile.copy());
        }

        player.setActiveHand(hand);
        stack.onPlayerStoppedUsing(player.world, player, Math.max(0, stack.getMaxItemUseDuration() - this.charge.get()));
        player.resetActiveHand();
        player.setHeldItem(hand, ItemStack.EMPTY);
        player.setHeldItem(other, ItemStack.EMPTY);
    }

    @Override
    protected Clip create()
    {
        return new ReleaseUseItemActionClip();
    }
}
