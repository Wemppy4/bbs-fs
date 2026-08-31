package mchorse.bbs_mod.forms.structure;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * The region the structure wand is pointing out, purely client-side: the server is only ever told
 * the two finished corners, so nothing about picking them needs to leave this class.
 *
 * <p>Two phases, and the HUD hint names whichever one is current. While {@link #isReady()} is
 * false the mouse buttons place corners; once both are down they mean save and cancel instead.
 * That costs the ability to nudge one corner — a correction is a fresh pair of clicks — which is
 * the deal we took to keep the two buttons enough on their own, with no modifier to remember.</p>
 */
public class StructureSelection
{
    private static BlockPos first;
    private static BlockPos second;

    @Nullable
    public static BlockPos getFirst()
    {
        return first;
    }

    @Nullable
    public static BlockPos getSecond()
    {
        return second;
    }

    /** Both corners are down: the buttons now mean save/cancel. */
    public static boolean isReady()
    {
        return first != null && second != null;
    }

    public static boolean isEmpty()
    {
        return first == null && second == null;
    }

    public static void setFirst(BlockPos pos)
    {
        first = pos;
    }

    public static void setSecond(BlockPos pos)
    {
        second = pos;
    }

    public static void clear()
    {
        first = null;
        second = null;
    }

    /** Lowest corner of the inclusive box, or null while a corner is missing. */
    @Nullable
    public static BlockPos getMin()
    {
        if (!isReady())
        {
            return null;
        }

        return new BlockPos(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ())
        );
    }

    /** Highest corner of the inclusive box, or null while a corner is missing. */
    @Nullable
    public static BlockPos getMax()
    {
        if (!isReady())
        {
            return null;
        }

        return new BlockPos(
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ())
        );
    }

    /** Size in blocks, both corners included, or null while a corner is missing. */
    @Nullable
    public static Vec3i getSize()
    {
        BlockPos min = getMin();

        if (min == null)
        {
            return null;
        }

        return getMax().subtract(min).add(1, 1, 1);
    }
}
