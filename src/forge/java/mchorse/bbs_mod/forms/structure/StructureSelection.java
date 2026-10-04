package mchorse.bbs_mod.forms.structure;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3i;
import javax.annotation.Nullable;

/**
 * The region the structure wand is pointing out, purely client-side: the server is only ever told
 * the two finished corners, so nothing about picking them needs to leave this class.
 *
 * <p>Two corners, A and B, each placed by its own mouse button and re-placed any time — there is
 * no phase in which the buttons mean something else, so a wrong corner is one click to fix. On top
 * of that a finished box can be reshaped without touching the corners at all: a face is pushed in
 * or out ({@link #push}), or the whole box slides along a face's normal ({@link #move}). Both keep
 * A and B as the user's corners — a push moves whichever of the two sits on that face — so the
 * markers in the world never swap places under the user.</p>
 */
public class StructureSelection
{
    private static BlockPos a;
    private static BlockPos b;

    @Nullable
    public static BlockPos getA()
    {
        return a;
    }

    @Nullable
    public static BlockPos getB()
    {
        return b;
    }

    /** Both corners are down: there is a box to show, reshape and save. */
    public static boolean isReady()
    {
        return a != null && b != null;
    }

    public static boolean isEmpty()
    {
        return a == null && b == null;
    }

    public static void setA(BlockPos pos)
    {
        a = pos;
    }

    public static void setB(BlockPos pos)
    {
        b = pos;
    }

    public static void clear()
    {
        a = null;
        b = null;
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
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
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
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
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

    /** How many blocks the box covers, air included; 0 while a corner is missing. */
    public static long getVolume()
    {
        Vec3i size = getSize();

        return size == null ? 0 : (long) size.getX() * size.getY() * size.getZ();
    }

    /** The box in world space, block faces included, or null while a corner is missing. */
    @Nullable
    public static AxisAlignedBB getBox()
    {
        BlockPos min = getMin();

        if (min == null)
        {
            return null;
        }

        BlockPos max = getMax();

        return new AxisAlignedBB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /**
     * Push the face on the given side of the box along its normal: a positive amount grows the box
     * outward, a negative one shrinks it. The corner that sits on that face is the one that moves,
     * and it stops at the opposite face — the box never turns inside out.
     *
     * @return whether the box changed
     */
    public static boolean push(EnumFacing face, int amount)
    {
        if (!isReady() || amount == 0)
        {
            return false;
        }

        EnumFacing.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        int av = component(a, axis);
        int bv = component(b, axis);

        /* The corner on that face; when the box is one block thin there, B is the one that goes */
        boolean moveA = positive ? av > bv : av < bv;
        int corner = moveA ? av : bv;
        int other = moveA ? bv : av;
        int moved = corner + (positive ? amount : -amount);

        if (positive ? moved < other : moved > other)
        {
            moved = other;
        }

        if (moved == corner)
        {
            return false;
        }

        if (moveA)
        {
            a = withAxis(a, axis, moved);
        }
        else
        {
            b = withAxis(b, axis, moved);
        }

        return true;
    }

    /** Slide the whole box along a face's normal, shape untouched. */
    public static boolean move(EnumFacing face, int amount)
    {
        if (!isReady() || amount == 0)
        {
            return false;
        }

        a = a.offset(face, amount);
        b = b.offset(face, amount);

        return true;
    }

    private static int component(BlockPos pos, EnumFacing.Axis axis)
    {
        switch (axis)
        {
            case X: return pos.getX();
            case Y: return pos.getY();
            case Z: return pos.getZ();
            default: throw new IllegalArgumentException("Unknown axis " + axis);
        }
    }

    private static BlockPos withAxis(BlockPos pos, EnumFacing.Axis axis, int value)
    {
        switch (axis)
        {
            case X: return new BlockPos(value, pos.getY(), pos.getZ());
            case Y: return new BlockPos(pos.getX(), value, pos.getZ());
            case Z: return new BlockPos(pos.getX(), pos.getY(), value);
            default: throw new IllegalArgumentException("Unknown axis " + axis);
        }
    }
}
