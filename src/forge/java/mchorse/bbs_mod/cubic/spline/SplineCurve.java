package mchorse.bbs_mod.cubic.spline;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Shared interpolating curve for the solver and its editor overlay. Coordinates are blocks. */
public final class SplineCurve
{
    private SplineCurve()
    {}

    /** Uniform Catmull-Rom with linear endpoint tangents. Two controls describe an exact line. */
    public static Vector3f evaluate(List<Vector3f> points, float t)
    {
        return evaluate(points, t, false);
    }

    public static Vector3f evaluate(List<Vector3f> points, float t, boolean closed)
    {
        int count = points.size();

        if (count == 0) return new Vector3f();
        if (count == 1) return new Vector3f(points.get(0));
        float scaled = (closed ? t - (float) Math.floor(t) : Math.max(0F, Math.min(1F, t))) * (closed ? count : count - 1);
        int i = Math.min(closed ? count - 1 : count - 2, (int) scaled);
        float u = scaled - i;
        Vector3f b = points.get(i);
        Vector3f c = points.get((i + 1) % count);
        Vector3f a = closed ? points.get((i + count - 1) % count) : i == 0 ? new Vector3f(b).mul(2F).sub(c) : points.get(i - 1);
        Vector3f d = closed ? points.get((i + 2) % count) : i + 2 == count ? new Vector3f(c).mul(2F).sub(b) : points.get(i + 2);
        float u2 = u * u;
        float u3 = u2 * u;

        return new Vector3f(b).mul(2F)
            .fma(u, new Vector3f(c).sub(a))
            .fma(u2, new Vector3f(a).mul(2F).fma(-5F, b).fma(4F, c).sub(d))
            .fma(u3, new Vector3f(a).negate().fma(3F, b).fma(-3F, c).add(d))
            .mul(0.5F);
    }

    /** Derivative with respect to a segment parameter; callers normalize when needed. */
    public static Vector3f tangent(List<Vector3f> points, float t)
    {
        return tangent(points, t, false);
    }

    public static Vector3f tangent(List<Vector3f> points, float t, boolean closed)
    {
        int count = points.size();
        if (count < 2) return new Vector3f(0, 0, 1);
        float scaled = (closed ? t - (float) Math.floor(t) : Math.max(0F, Math.min(1F, t))) * (closed ? count : count - 1);
        int i = Math.min(closed ? count - 1 : count - 2, (int) scaled);
        float u = scaled - i;
        Vector3f b = points.get(i);
        Vector3f c = points.get((i + 1) % count);
        Vector3f a = closed ? points.get((i + count - 1) % count) : i == 0 ? new Vector3f(b).mul(2F).sub(c) : points.get(i - 1);
        Vector3f d = closed ? points.get((i + 2) % count) : i + 2 == count ? new Vector3f(c).mul(2F).sub(b) : points.get(i + 2);

        return new Vector3f(c).sub(a)
            .fma(2F * u, new Vector3f(a).mul(2F).fma(-5F, b).fma(4F, c).sub(d))
            .fma(3F * u * u, new Vector3f(a).negate().fma(3F, b).fma(-3F, c).add(d))
            .mul(0.5F);
    }

    public static List<Vector3f> sample(List<Vector3f> points, int subdivisionsPerSegment)
    {
        return sample(points, subdivisionsPerSegment, false);
    }

    public static List<Vector3f> sample(List<Vector3f> points, int subdivisionsPerSegment, boolean closed)
    {
        if (points.isEmpty()) return java.util.Collections.emptyList();

        int steps = Math.max(1, subdivisionsPerSegment) * Math.max(1, closed ? points.size() : points.size() - 1);
        List<Vector3f> result = new ArrayList<>(steps + 1);

        for (int i = 0; i <= steps; i++) result.add(evaluate(points, i / (float) steps, closed));

        return result;
    }
}
