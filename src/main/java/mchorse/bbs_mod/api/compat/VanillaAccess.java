package mchorse.bbs_mod.api.compat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * The bits of vanilla BBS had to widen access to, offered as plain methods.
 *
 * <p>An access widener applies to Minecraft, and only for the mod that declares it — Loom never
 * applies one mod's widener to another's build. So an addon that needed the same fields had to
 * keep a second copy of BBS's widener, word for word, and the two copies drifted apart the moment
 * one of them was touched. Calling a method needs no widener at all.</p>
 *
 * <p>These are the fields the item-model predicates read: a bow bending, a shield blocking, a
 * trident lifting all ask an entity what it is using, and a film has no such entity — the
 * stand-in answers for it.</p>
 */
public final class VanillaAccess
{
    private VanillaAccess()
    {}

    public static ItemStack getActiveItemStack(LivingEntity entity)
    {
        return entity.activeItemStack;
    }

    public static void setActiveItemStack(LivingEntity entity, ItemStack stack)
    {
        entity.activeItemStack = stack;
    }

    public static int getItemUseTimeLeft(LivingEntity entity)
    {
        return entity.itemUseTimeLeft;
    }

    public static void setItemUseTimeLeft(LivingEntity entity, int ticks)
    {
        entity.itemUseTimeLeft = ticks;
    }

    /**
     * Raises or drops one of the living flags — flag 1 is "using an item".
     *
     * <p>On the client {@code setCurrentHand} never raises it, so anything standing in for a user
     * of an item has to raise it by hand.</p>
     */
    public static void setLivingFlag(LivingEntity entity, int index, boolean value)
    {
        entity.setLivingFlag(index, value);
    }

    public static int getRiptideTicks(LivingEntity entity)
    {
        return entity.riptideTicks;
    }

    /**
     * Spins the body the way a riptide does. {@code useRiptide()} is the player's own method, and
     * an actor is not a player.
     */
    public static void setRiptideTicks(LivingEntity entity, int ticks)
    {
        entity.riptideTicks = ticks;
    }
}
