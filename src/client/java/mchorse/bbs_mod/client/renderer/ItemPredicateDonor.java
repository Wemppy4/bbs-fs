package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * A stand-in entity for vanilla's item model predicates.
 *
 * <p>A drawn bow, a blocking shield and a charged crossbow are different MODELS,
 * picked by predicates that ask an entity whether it is using this very stack.
 * A film's actor is no such entity - it never uses anything - so the predicates
 * get answered from the film's data instead, through this one.</p>
 */
public class ItemPredicateDonor
{
    private static ArmorStandEntity donor;

    /**
     * An entity that reports "using this stack, this much time left" so that
     * vanilla's model predicates (bow pull, crossbow pulling/charged, shield
     * blocking, trident throwing) pick the right model. The predicates compare
     * the active stack by identity, so the caller must hand in the very
     * instance it renders.
     */
    public static LivingEntity get(ItemStack stack, ItemUsePose.Use use)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || stack.isEmpty())
        {
            return null;
        }

        if (donor == null || donor.getEntityWorld() != mc.world)
        {
            donor = new ArmorStandEntity(mc.world, 0D, 0D, 0D);

            donor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BOW));
            donor.setCurrentHand(Hand.MAIN_HAND);

            /* setCurrentHand only raises the "using item" flag on the SERVER
             * (its isClient branch skips setLivingFlag - 1.20.4 bytecode), and
             * the bow's "pulling", the shield's "blocking" and the trident's
             * "throwing" predicates all demand that flag: without it the bow
             * never bends and never shows its arrow. Raise it by hand, once -
             * on the client nothing ever lowers it again. */
            donor.setLivingFlag(1, true);
        }

        donor.activeItemStack = stack;
        donor.itemUseTimeLeft = Math.max(0, Math.round(ItemUsePose.maxUseTime(stack, use.user()) - use.elapsed()));

        return donor;
    }
}
