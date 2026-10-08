package mchorse.bbs_mod.film.replays;

import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * The nine hotbar cells at one moment - what a key on a replay's hotbar track holds. A key is
 * the whole row rather than the cells that changed: what the hotbar shows at a tick is simply
 * the key in effect there, nothing is pieced together from earlier ones.
 */
public class Hotbar
{
    private final ItemStack[] stacks = new ItemStack[ReplayKeyframes.HOTBAR_SIZE];

    public Hotbar()
    {
        Arrays.fill(this.stacks, ItemStack.EMPTY);
    }

    /** A row filled with copies of what given source holds in each cell. */
    public static Hotbar of(IntFunction<ItemStack> source)
    {
        Hotbar hotbar = new Hotbar();

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
        {
            hotbar.set(i, source.apply(i).copy());
        }

        return hotbar;
    }

    public ItemStack get(int slot)
    {
        return this.stacks[slot];
    }

    public void set(int slot, ItemStack stack)
    {
        this.stacks[slot] = stack == null ? ItemStack.EMPTY : stack;
    }

    public boolean isEmpty()
    {
        for (ItemStack stack : this.stacks)
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    public boolean isSame(Hotbar hotbar)
    {
        for (int i = 0; i < this.stacks.length; i++)
        {
            if (!ItemStack.areEqual(this.stacks[i], hotbar.stacks[i]))
            {
                return false;
            }
        }

        return true;
    }

    public Hotbar copy()
    {
        return of(this::get);
    }
}
