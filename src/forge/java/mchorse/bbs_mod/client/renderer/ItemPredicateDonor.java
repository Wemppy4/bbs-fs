package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/** A real 1.12 entity supplying the film's use state to vanilla item model predicates. */
public final class ItemPredicateDonor
{
    private static Donor donor;

    public static EntityLivingBase get(ItemStack stack, ItemUsePose.Use use)
    {
        World world = Minecraft.getMinecraft().world;
        if (world == null || stack == null || stack.isEmpty() || use == null) return null;
        if (donor == null || donor.world != world) donor = new Donor(world);
        donor.stack = stack;
        donor.remaining = Math.max(0, Math.round(stack.getMaxItemUseDuration() - use.elapsed()));
        donor.setHeldItem(EnumHand.MAIN_HAND, stack);
        return donor;
    }

    private static final class Donor extends EntityArmorStand
    {
        private ItemStack stack = ItemStack.EMPTY;
        private int remaining;
        private Donor(World world) { super(world); }
        @Override public boolean isHandActive() { return !this.stack.isEmpty(); }
        @Override public EnumHand getActiveHand() { return EnumHand.MAIN_HAND; }
        @Override public ItemStack getActiveItemStack() { return this.stack; }
        @Override public int getItemInUseCount() { return this.remaining; }
        @Override public int getItemInUseMaxCount() { return this.stack.getMaxItemUseDuration() - this.remaining; }
    }
    private ItemPredicateDonor() {}
}
