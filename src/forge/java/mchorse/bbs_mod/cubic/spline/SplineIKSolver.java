package mchorse.bbs_mod.cubic.spline;

import org.joml.Vector3f;

import java.util.List;
import mchorse.bbs_mod.cubic.spline.SplineMath.ArcSample;
import static mchorse.bbs_mod.cubic.spline.SplineMath.sampleDistance;

/** Stateless spline sampling and rotation-minimizing frames; no renderer or game state. */
public final class SplineIKSolver
{
    private static final float EPS = 1E-6F;

    private SplineIKSolver()
    {}

    @com.github.bsideup.jabel.Desugar
    public record Result(Vector3f[] joints, Vector3f[] tangents)
    {}

    /**
     * There is one length between adjacent joints. Preserve mode follows arc-length guides
     * with rigid links. Joints may leave the curve where a link cannot fit a tight bend;
     * searching for exact curve/sphere intersections would jump between solution branches.
     * Past the end, the last tangent continues the curve. Fit distributes joints by arc length;
     * it changes their separation, and does not promise to scale the model's geometry.
     */
    public static Result solve(List<Vector3f> controls, float[] lengths, boolean fit)
    {
        return solve(controls, lengths, fit, 0F);
    }

    /**
     * Progress moves the whole chain along the curve, measured in multiples of its arc length.
     * Zero starts at the first control, one at the last; values outside that range continue
     * along the endpoint tangents. Fit shifts its entire interval without shortening it.
     */
    public static Result solve(List<Vector3f> controls, float[] lengths, boolean fit, float progress)
    {
        if (controls.size() < 2 || lengths.length == 0 || !Float.isFinite(progress)) return null;

        for (Vector3f point : controls) if (point == null || !point.isFinite()) return null;
        for (float length : lengths) if (!Float.isFinite(length) || length <= EPS) return null;

        List<Vector3f> samples = SplineCurve.sample(controls, 48);
        float[] distances = SplineMath.distances(samples);

        float total = distances[distances.length - 1];
        if (!Float.isFinite(total) || total <= EPS) return null;

        Vector3f startTangent = new Vector3f();
        for (int i = 1; i < samples.size(); i++)
        {
            startTangent.set(samples.get(i)).sub(samples.get(0));
            if (startTangent.lengthSquared() > EPS * EPS) break;
        }
        startTangent.normalize();

        Vector3f endTangent = new Vector3f();
        for (int i = samples.size() - 2; i >= 0; i--)
        {
            endTangent.set(samples.get(samples.size() - 1)).sub(samples.get(i));
            if (endTangent.lengthSquared() > EPS * EPS) break;
        }
        endTangent.normalize();

        Vector3f[] joints = new Vector3f[lengths.length + 1];
        Vector3f[] tangents = new Vector3f[joints.length];
        float startDistance = progress * total;
        if (!Float.isFinite(startDistance)) return null;
        ArcSample first = sampleDistance(samples, distances, startTangent, endTangent, startDistance, 1);
        joints[0] = first.point();
        int cursor = first.cursor();
        if (!joints[0].isFinite()) return null;
        float sum = 0F;
        for (float length : lengths) sum += length;
        if (!Float.isFinite(sum)) return null;
        float travelled = 0F;
        Vector3f previousGuide = new Vector3f(joints[0]);

        for (int i = 1; i < joints.length; i++)
        {
            float length = lengths[i - 1];

            if (fit)
            {
                travelled += length;
                float distance = startDistance + total * travelled / sum;
                if (!Float.isFinite(distance)) return null;
                ArcSample sample = sampleDistance(samples, distances, startTangent, endTangent, distance, cursor);
                joints[i] = sample.point();
                cursor = sample.cursor();
            }
            else
            {
                float middleDistance = startDistance + travelled + length * 0.5F;
                travelled += length;
                float distance = startDistance + travelled;
                if (!Float.isFinite(distance) || !Float.isFinite(middleDistance)) return null;
                ArcSample guide = sampleDistance(samples, distances, startTangent, endTangent, distance, cursor);
                Vector3f forward = new Vector3f(guide.point()).sub(previousGuide);
                if (forward.lengthSquared() <= EPS * EPS)
                {
                    ArcSample middle = sampleDistance(samples, distances, startTangent, endTangent, middleDistance, cursor);
                    forward = tangent(controls, middle, startTangent, endTangent);
                }
                else forward.normalize();
                Vector3f direction = new Vector3f(guide.point()).sub(joints[i - 1]);

                /* A guide can pass behind an off-curve joint. Keep a positive component along
                 * this interval's direction, so projecting to the rigid length cannot collapse
                 * or flip through zero. This is a spatial constraint, not frame-history smoothing. */
                float along = direction.dot(forward);
                direction.fma(Math.max(0F, length * 0.25F - along), forward).normalize();
                joints[i] = new Vector3f(joints[i - 1]).fma(length, direction);
                cursor = guide.cursor();
                previousGuide.set(guide.point());
            }

            Vector3f tangent = new Vector3f(joints[i]).sub(joints[i - 1]);
            if (!joints[i].isFinite() || !tangent.isFinite() || tangent.lengthSquared() <= EPS * EPS) return null;
            tangents[i - 1] = tangent.normalize();
        }

        ArcSample terminal = sampleDistance(samples, distances, startTangent, endTangent,
            startDistance + (fit ? total : travelled), cursor);
        tangents[tangents.length - 1] = tangent(controls, terminal, startTangent, endTangent);
        if (!tangents[tangents.length - 1].isFinite()) tangents[tangents.length - 1] = new Vector3f(tangents[tangents.length - 2]);

        return new Result(joints, tangents);
    }

    private static Vector3f tangent(List<Vector3f> controls, ArcSample sample, Vector3f start, Vector3f end)
    {
        if (sample.parameter() <= 0F) return new Vector3f(start);
        if (sample.parameter() >= 1F) return new Vector3f(end);
        Vector3f tangent = SplineCurve.tangent(controls, sample.parameter());
        /* At a stationary curve point use its outgoing side. Fully collapsed curves have
         * already been rejected; ordinary smooth bends use the analytic derivative. */
        if (tangent.lengthSquared() <= EPS * EPS)
            tangent.set(SplineCurve.tangent(controls, Math.min(1F, sample.parameter() + 1E-3F)));
        return tangent.lengthSquared() <= EPS * EPS ? new Vector3f(start) : tangent.normalize();
    }

}
