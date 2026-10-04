package mchorse.bbs_mod.cubic.spline;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Geometry shared by IK and path followers; no state from previous frames. */
public final class SplineMath
{
    private static final float EPS = 1E-6F;
    private SplineMath() {}

    public static float[] distances(List<Vector3f> samples)
    {
        float[] distances = new float[samples.size()];
        for (int i = 1; i < samples.size(); i++) distances[i] = distances[i - 1] + samples.get(i).distance(samples.get(i - 1));
        return distances;
    }

    @com.github.bsideup.jabel.Desugar
    public record ArcSample(Vector3f point, int cursor, float parameter)
    {}

    /** Cursor names the next polyline vertex, including the initial/final tangent extensions. */
    public static ArcSample sampleDistance(List<Vector3f> samples, float[] distances,
        Vector3f startTangent, Vector3f endTangent, float distance, int cursor)
    {
        int last = samples.size() - 1;
        if (distance < 0F) return new ArcSample(new Vector3f(samples.get(0)).fma(distance, startTangent), 0, 0F);
        if (distance > distances[last]) return new ArcSample(new Vector3f(samples.get(last)).fma(distance - distances[last], endTangent), samples.size(), 1F);
        cursor = Math.max(1, Math.min(last, cursor));
        while (cursor < last && distances[cursor] < distance) cursor++;
        float span = distances[cursor] - distances[cursor - 1];
        float alpha = span <= EPS ? 0F : (distance - distances[cursor - 1]) / span;
        alpha = Math.max(0F, Math.min(1F, alpha));
        return new ArcSample(new Vector3f(samples.get(cursor - 1)).lerp(samples.get(cursor), alpha), cursor, (cursor - 1F + alpha) / last);
    }

    /** Deterministic normal, independent of the previous frame/tick or another actor. */
    public static Vector3f perpendicular(Vector3f tangent)
    {
        Vector3f axis = Math.abs(tangent.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return axis.fma(-axis.dot(tangent), tangent).normalize();
    }

    /** Parallel transport; at an exact reversal the previous normal supplies the half-turn axis. */
    public static Vector3f transport(Vector3f normal, Vector3f from, Vector3f to)
    {
        Vector3f result = new Vector3f(normal);

        Vector3f axis = new Vector3f(from).cross(to);
        float sine = axis.length();
        /* atan2 remains well-conditioned near a half turn. Do not switch axes at a dot
         * threshold: that used to invert the normal before reaching an actual reversal.
         * At an exact reversal rotating around normal leaves normal itself unchanged. */
        if (sine > EPS)
            new Quaternionf().rotationAxis((float) Math.atan2(sine, from.dot(to)), axis.div(sine)).transform(result);

        result.fma(-result.dot(to), to);
        return result.lengthSquared() <= EPS * EPS ? perpendicular(to) : result.normalize();
    }

    /** A proper basis with the tangent on Z and the transported normal on Y. */
    public static Quaternionf frame(Vector3f tangent, Vector3f normal)
    {
        Vector3f x = new Vector3f(normal).cross(tangent).normalize();
        Vector3f y = new Vector3f(tangent).cross(x).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, tangent));
    }
}
