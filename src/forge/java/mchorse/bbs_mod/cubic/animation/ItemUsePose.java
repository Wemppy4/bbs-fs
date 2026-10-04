package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.item.EnumAction;

/**
 * The item use state of a body, as everything that poses or dresses it needs it:
 * which vanilla use branch is running in a hand, for how long, and on which stack.
 *
 * <p>A film's actor never ticks an item use - the use is an action clip, and
 * {@link mchorse.bbs_mod.film.replays.ReplayItemUse} reads the state off it. Only
 * the client knows which replay drives which body, so the film controllers publish
 * the states through a {@link Source} they install, and everyone else asks here.</p>
 */
public class ItemUsePose
{
    /**
     * Which vanilla animation branch runs, how long it has been running
     * (fractional ticks), the stack driving it and how long the whole use lasts.
     */
    @com.github.bsideup.jabel.Desugar
    public record Use(EnumAction action, float elapsed, ItemStack stack, float window)
    {}

    public interface Source
    {
        public Use get(IEntity entity, boolean mainHand);
    }

    private static Source source;
    private static boolean suppressed;

    public static void setSource(Source source)
    {
        ItemUsePose.source = source;
    }

    /**
     * The first person arm is drawn from the very same bones, and vanilla poses
     * it flat (its renderArm zeroes the arm's pitch) - so the use poses must not
     * leak into it.
     */
    public static void setSuppressed(boolean suppressed)
    {
        ItemUsePose.suppressed = suppressed;
    }

    public static boolean isSuppressed()
    {
        return ItemUsePose.suppressed;
    }

    public static Use get(IEntity entity, boolean mainHand)
    {
        if (suppressed || entity == null)
        {
            return null;
        }

        Use use = source == null ? null : source.get(entity, mainHand);

        return use == null ? live(entity, mainHand) : use;
    }

    /**
     * Whatever the entity is doing right now, for everyone the film doesn't
     * drive: a player morphed into a form outside of a film, and the player
     * being recorded (the clips that would answer for them are only written
     * when the take ends).
     */
    private static Use live(IEntity entity, boolean mainHand)
    {
        if (!(entity instanceof MCEntity mc) || !(mc.getMcEntity() instanceof EntityLivingBase living) || !living.isHandActive())
        {
            return null;
        }

        if ((living.getActiveHand() == EnumHand.MAIN_HAND) != mainHand)
        {
            return null;
        }

        ItemStack stack = living.getActiveItemStack();
        EnumAction action = stack.getItemUseAction();

        return action == EnumAction.NONE ? null : new Use(action, living.getItemInUseMaxCount(), stack, stack.getMaxItemUseDuration());
    }
}
