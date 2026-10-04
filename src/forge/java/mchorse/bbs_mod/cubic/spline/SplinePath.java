package mchorse.bbs_mod.cubic.spline;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** One evaluated path in the space in which distance is measured. No global or temporal cache. */
public final class SplinePath
{
    private final List<Vector3f> controls;
    private final List<Vector3f> samples;
    private final float[] distances;
    private final List<Vector3f> tangents = new ArrayList<>();
    private final List<Vector3f> normals = new ArrayList<>();
    private final boolean closed;
    private final float length;
    private float seam;

    public float length()
    {
        return this.length;
    }

    public static SplinePath create(SplineSource source, Matrix4f matrix)
    {
        if (matrix == null || !matrix.isFinite() || source.points().getAllTyped().isEmpty()) return null;
        List<Vector3f> controls = new ArrayList<>();
        for (SplinePoint point : source.points().getAllTyped())
        {
            Vector3f position = matrix.transformPosition(new Vector3f(source.position(point.getId()).translate));
            if (!position.isFinite()) return null;
            controls.add(position);
        }
        return new SplinePath(controls, source.closed());
    }

    public SplinePath(List<Vector3f> controls, boolean closed)
    {
        this.controls = controls;
        this.closed = closed;
        this.samples = SplineCurve.sample(controls, 48, closed);
        this.distances = SplineMath.distances(this.samples);
        this.length = this.distances.length == 0 ? 0 : this.distances[this.distances.length - 1];
        for (int i = 0; i < this.samples.size(); i++)
        {
            Vector3f tangent = this.tangent(i / (float) Math.max(1, this.samples.size() - 1));
            this.tangents.add(tangent);
            this.normals.add(i == 0 ? SplineMath.perpendicular(tangent)
                : SplineMath.transport(this.normals.get(i - 1), this.tangents.get(i - 1), tangent));
        }
        if (closed && this.normals.size() > 1)
        {
            Vector3f end = this.normals.get(this.normals.size() - 1), start = this.normals.get(0);
            this.seam = (float) Math.atan2(this.tangents.get(0).dot(new Vector3f(end).cross(start)), end.dot(start));
        }
    }

    private Vector3f tangent(float t)
    {
        Vector3f tangent = SplineCurve.tangent(this.controls, t, this.closed);
        if (tangent.lengthSquared() < 1E-12F)
            tangent = SplineCurve.tangent(this.controls, t + (t >= 1 && !this.closed ? -1E-3F : 1E-3F), this.closed);
        return !tangent.isFinite() || tangent.lengthSquared() < 1E-12F ? new Vector3f(0, 0, 1) : tangent.normalize();
    }

    /** +Z follows increasing progress. Offsets are in this orthonormal frame, in world blocks. */
    public Matrix4f frame(float progress, boolean horizontal)
    {
        if (this.samples.isEmpty() || !Float.isFinite(progress) || !Float.isFinite(this.length)) return null;
        float fraction = progress / 100F;
        fraction = this.closed ? fraction - (float) Math.floor(fraction) : Math.max(0, Math.min(1, fraction));
        if (this.length < 1E-6F) return new Matrix4f().translation(this.samples.get(0));
        SplineMath.ArcSample sample = SplineMath.sampleDistance(this.samples, this.distances,
            this.tangents.get(0), this.tangents.get(this.tangents.size() - 1), this.length * fraction, 1);
        Vector3f tangent = this.tangent(sample.parameter());
        Vector3f normal;
        if (horizontal)
        {
            tangent.y = 0;
            if (tangent.lengthSquared() < 1E-10F)
            {
                // Resolve a vertical section spatially, never from the last displayed frame.
                for (int i = sample.cursor() - 1; i >= 0; i--)
                {
                    tangent.set(this.tangents.get(i));
                    tangent.y = 0;
                    if (tangent.lengthSquared() >= 1E-10F) break;
                }
                if (tangent.lengthSquared() < 1E-10F) tangent.set(0, 0, 1);
            }
            tangent.normalize();
            normal = new Vector3f(0, 1, 0);
        }
        else
        {
            int i = Math.max(0, sample.cursor() - 1);
            normal = SplineMath.transport(this.normals.get(i), this.tangents.get(i), tangent);
            if (this.closed) new Quaternionf().rotationAxis(this.seam * fraction, tangent).transform(normal);
        }
        return new Matrix4f().translationRotate(sample.point(), SplineMath.frame(tangent, normal));
    }
}
