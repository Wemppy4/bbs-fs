package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** The film's item use during drawing only. Forge has no vanilla query mixin: the
 * actual item-use fields and tracked byte are borrowed for one render frame, then
 * restored before gameplay input or entity ticks can observe them. */
public class LivePlayerItemUse
{
    private static ItemUsePose.Use use;
    private static EnumHand hand = EnumHand.MAIN_HAND;

    /** Whether the film's answer is the one being drawn right now (see class doc). */
    private static boolean drawing;
    private static NativeItemUseScope scope;

    /** Called after controllers publish this frame's use and before any vanilla draw. */
    public static void beginFrame()
    {
        if (scope != null) { scope.close(); scope = null; }
        EntityLivingBase player = Minecraft.getMinecraft().player;
        if (drawing && use != null && player != null)
            scope = NativeItemUseScope.open(player, hand, getStack(), getTimeLeft());
    }

    public static void apply(EntityLivingBase player, ItemUsePose.Use mainUse, ItemUsePose.Use offUse)
    {
        if (player != Minecraft.getMinecraft().player)
        {
            return;
        }

        ItemUsePose.Use active = mainUse == null ? offUse : mainUse;
        EnumHand activeHand = mainUse == null ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;

        if (active == null || player.getHeldItem(activeHand).isEmpty())
        {
            clear();

            return;
        }

        use = active;
        hand = activeHand;
        drawing = true;
    }

    /** The render pass is over - from here to the next frame the player is themselves again. */
    public static void endFrame()
    {
        if (scope != null) { scope.close(); scope = null; }
        drawing = false;
    }

    public static void clear()
    {
        endFrame();
        use = null;
    }

    /** Whether this entity's use is the film's to answer at this moment. */
    public static boolean answersFor(EntityLivingBase entity)
    {
        return drawing && use != null && entity == Minecraft.getMinecraft().player;
    }

    public static EnumHand getHand()
    {
        return hand;
    }

    /**
     * The very instance in the hand: vanilla's model predicates compare the
     * active stack to the one being drawn by identity.
     */
    public static ItemStack getStack()
    {
        EntityLivingBase player = Minecraft.getMinecraft().player;

        return player == null ? ItemStack.EMPTY : player.getHeldItem(hand);
    }

    /**
     * Vanilla counts the use DOWN from the item's max, which is what every
     * renderer reads - and it counts in WHOLE ticks, adding the frame's own
     * fraction back itself: {@code maxUseTime - (left - tickDelta + 1)} is the
     * smooth elapsed time the hand is posed by (javap 1.20.4).
     *
     * <p>Hence the floor: the film's elapsed already carries the fraction of
     * the frame, and handing it over rounded would let vanilla add that
     * fraction a second time - the pose then jumped by up to a whole tick back
     * and forth as the rounding flipped, and the drawn bow trembled. Floored,
     * the value holds still for the whole tick and vanilla's own tickDelta
     * makes it smooth again.</p>
     */
    public static int getTimeLeft()
    {
        return Math.max(0, getStack().getMaxItemUseDuration() - (int) Math.floor(use.elapsed()) - 1);
    }
}
