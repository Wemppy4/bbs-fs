package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.utils.MathUtils;

/**
 * Auto bezier interpolation, a port of Blender's automatic F-curve handles
 * (calchandleNurb_intern). Handles aren't stored: each keyframe's tangent is a
 * length-weighted blend of its two neighbouring segment slopes.
 *
 * <p>{@code clamped} selects Blender's two handle types: plain "Auto" carries
 * the tangent through every keyframe (smooth flow, overshoots at extrema), while
 * "Auto Clamped" flattens the tangent at extrema so the curve never overshoots
 * (no flow-through at sharp turns). Lets multi-value tracks interpolate smoothly
 * per number with no curve editor.
 */
public class AutoBezier
{
    private static final double NORM = 2.5614D;

    public static double get(double preA, double a, double b, double postB, float preATick, float aTick, float bTick, float postBTick, boolean clamped, float x)
    {
        if (x <= 0) return a;
        if (x >= 1) return b;

        double w = bTick - aTick;
        double h = b - a;

        if (h == 0) h = 0.00001;

        double[] ah = new double[4];
        double[] bh = new double[4];

        handles(preATick, preA, aTick, a, bTick, b, preATick != aTick, true, clamped, ah);
        handles(aTick, a, bTick, b, postBTick, postB, true, postBTick != bTick, clamped, bh);

        /* a's right handle (ah[2..3]) and b's left handle (bh[0..1]) drive this segment */
        double x1 = MathUtils.clamp((ah[2] - aTick) / w, 0, 1);
        double y1 = (ah[3] - a) / h;
        double x2 = MathUtils.clamp((bh[0] - aTick) / w, 0, 1);
        double y2 = (bh[1] - a) / h;

        return Lerps.bezier(0, y1, y2, 1, Lerps.bezierX(x1, x2, x)) * h + a;
    }

    /**
     * Auto handles of a keyframe into out = {leftTick, leftValue, rightTick, rightValue}.
     * Missing neighbours (curve ends) are reflected, matching Blender. When {@code clamped},
     * handles are clamped/flattened at extrema so the curve doesn't overshoot.
     */
    public static void handles(double prevT, double prevV, double curT, double curV, double nextT, double nextV, boolean hasPrev, boolean hasNext, boolean clamped, double[] out)
    {
        if (!hasPrev)
        {
            prevT = 2 * curT - nextT;
            prevV = 2 * curV - nextV;
        }

        if (!hasNext)
        {
            nextT = 2 * curT - prevT;
            nextV = 2 * curV - prevV;
        }

        double dax = curT - prevT;
        double day = curV - prevV;
        double dbx = nextT - curT;
        double dby = nextV - curV;

        double lenA = dax == 0 ? 1 : dax;
        double lenB = dbx == 0 ? 1 : dbx;

        double tx = dbx / lenB + dax / lenA;
        double ty = dby / lenB + day / lenA;
        double len = tx * NORM;

        out[0] = out[2] = curT;
        out[1] = out[3] = curV;

        if (len == 0)
        {
            return;
        }

        lenA = Math.min(lenA, 5 * lenB);
        lenB = Math.min(lenB, 5 * lenA);

        double la = lenA / len;
        double lb = lenB / len;

        out[0] = curT - tx * la;
        out[1] = curV - ty * la;
        out[2] = curT + tx * lb;
        out[3] = curV + ty * lb;

        if (!clamped || !hasPrev || !hasNext)
        {
            return;
        }

        double yd1 = prevV - curV;
        double yd2 = nextV - curV;

        if ((yd1 <= 0 && yd2 <= 0) || (yd1 >= 0 && yd2 >= 0))
        {
            /* Local extremum — flatten both handles so the curve doesn't overshoot */
            out[1] = curV;
            out[3] = curV;

            return;
        }

        boolean leftviolate = false;
        boolean rightviolate = false;

        if (yd1 <= 0 ? prevV > out[1] : prevV < out[1])
        {
            out[1] = prevV;
            leftviolate = true;
        }

        if (yd1 <= 0 ? nextV < out[3] : nextV > out[3])
        {
            out[3] = nextV;
            rightviolate = true;
        }

        if (leftviolate)
        {
            out[3] = curV + (curV - out[1]) / (out[0] - curT) * (curT - out[2]);
        }
        else if (rightviolate)
        {
            out[1] = curV + (curV - out[3]) / (curT - out[2]) * (out[0] - curT);
        }
    }
}
