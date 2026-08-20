package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * The take's item use answered to whoever DRAWS the real player, so the first
 * person hand plays it.
 *
 * <p>The hand you see in first person is drawn by vanilla's own
 * {@code HeldItemRenderer}, and it asks the LIVE player what it is using - a
 * film raises nothing there, so a take that drew a bow used to hold it limp.
 * The same question decides the item's model, in the hand and in the hotbar
 * alike (a drawn bow is the {@code bow_pulling} model, picked by a predicate
 * that asks the holder).</p>
 *
 * <p>Nothing is written onto the player. Writing was tried first - the two use
 * fields take, but the "using an item" living flag does not survive the write
 * (the client's own tick owns that tracked byte), and forcing it would also
 * make vanilla's {@code handleInputEvents} fire a release packet at the server
 * every tick. The film's use is not something the player DOES, it is something
 * their hand is drawn as - so it lives here and is handed out on the way to the
 * screen, through {@code LivingEntityFilmUseMixin}.</p>
 *
 * <p>Which is also why it only answers during the render pass: the frame opens
 * it (the film controller publishes the state at the start of the world render)
 * and the next client tick closes it, so everything gameplay-side - the input
 * handling, the entity tick - keeps seeing the honest "not using anything".</p>
 */
public class LivePlayerItemUse
{
    private static ItemUsePose.Use use;
    private static Hand hand = Hand.MAIN_HAND;

    /** Whether the film's answer is the one being drawn right now (see class doc). */
    private static boolean drawing;

    public static void apply(LivingEntity player, ItemUsePose.Use mainUse, ItemUsePose.Use offUse)
    {
        if (player != MinecraftClient.getInstance().player)
        {
            return;
        }

        ItemUsePose.Use active = mainUse == null ? offUse : mainUse;
        Hand activeHand = mainUse == null ? Hand.OFF_HAND : Hand.MAIN_HAND;

        if (active == null || player.getStackInHand(activeHand).isEmpty())
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
        drawing = false;
    }

    public static void clear()
    {
        use = null;
        drawing = false;
    }

    /** Whether this entity's use is the film's to answer at this moment. */
    public static boolean answersFor(LivingEntity entity)
    {
        return drawing && use != null && entity == MinecraftClient.getInstance().player;
    }

    public static Hand getHand()
    {
        return hand;
    }

    /**
     * The very instance in the hand: vanilla's model predicates compare the
     * active stack to the one being drawn by identity.
     */
    public static ItemStack getStack()
    {
        LivingEntity player = MinecraftClient.getInstance().player;

        return player == null ? ItemStack.EMPTY : player.getStackInHand(hand);
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
        /* The hand being drawn is the client player's own, and 1.21.1 asks the
         * holder for the max use time (the stack's enchantments read its Random). */
        return Math.max(0, getStack().getMaxUseTime(MinecraftClient.getInstance().player) - (int) Math.floor(use.elapsed()) - 1);
    }
}
