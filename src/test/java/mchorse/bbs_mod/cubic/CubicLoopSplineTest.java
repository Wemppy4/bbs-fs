package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import org.joml.Vector3d;

/** Standalone checks of Blockbench catmullrom keys across a loop seam; no Minecraft client required. */
public class CubicLoopSplineTest
{
    private static final float H = 0.001F;

    public static void main(String[] args)
    {
        /* One second of catmullrom keys every 5 ticks, the last one back at the first pose
         * (as Blockbench animators build a cycle), uneven so the seam slopes differ. */
        AnimationPart part = part(new double[] {0, 10, 6, -2, 0});

        double start = slope(part, 0F, true);
        double end = slope(part, 20F - H, true);
        check(Math.abs(start - end) < 0.01D, "Looping restart keeps its speed: " + start + " vs " + end);
        /* Blockbench wraps the outer neighbours: (second - next-to-last) / 2 per 5-tick segment. */
        check(Math.abs(start - (10 - -2) / 2D / 5D) < 0.01D, "Seam speed matches Blockbench: " + start);

        double onceStart = slope(part, 0F, false);
        double onceEnd = slope(part, 20F - H, false);
        check(Math.abs(onceStart - 10 / 2D / 5D) < 0.01D, "A one-shot start keeps its clamped speed: " + onceStart);
        check(Math.abs(onceEnd - 2 / 2D / 5D) < 0.01D, "A one-shot end keeps its clamped speed: " + onceEnd);

        for (float tick = 5F; tick < 15F; tick += 0.5F)
        {
            check(value(part, tick, true) == value(part, tick, false), "Inner segments don't change: " + tick);
        }

        /* Two keys have no neighbour to wrap to; the segment stays as it was. */
        AnimationPart pair = part(new double[] {0, 10});
        check(value(pair, 2.5F, true) == value(pair, 2.5F, false), "Two keys don't wrap");

        System.out.println("Cubic loop spline checks passed");
    }

    private static AnimationPart part(double[] values)
    {
        ListType keys = new ListType();

        for (int i = 0; i < values.length; i++)
        {
            ListType key = new ListType();

            key.addDouble(i * 0.25D);
            key.addString("catmullrom");
            key.addDouble(values[i]);
            key.addDouble(0);
            key.addDouble(0);
            keys.add(key);
        }

        MapType data = new MapType();
        AnimationPart part = new AnimationPart(new MolangParser());

        data.put("translate", keys);
        part.fromData(data);

        return part;
    }

    private static double value(AnimationPart part, float tick, boolean looping)
    {
        return CubicModelAnimator.interpolateList(new Vector3d(), part.x, part.y, part.z, tick, 0D, looping).x;
    }

    private static double slope(AnimationPart part, float tick, boolean looping)
    {
        return (value(part, tick + H, looping) - value(part, tick, looping)) / H;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
