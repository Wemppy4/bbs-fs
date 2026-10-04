package mchorse.bbs_mod.cubic.spline;

import mchorse.bbs_mod.forms.forms.SplineForm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Placement only: copies never own forms, entities, animation clocks or saved data. */
public final class SplineArray
{
    public static final int MAX_COPIES = 1024;

    private SplineArray()
    {}

    /** Distances and vertical are in the spline's local space, before its parent transform. */
    public static List<Matrix4f> frames(SplineForm form)
    {
        if (!form.repeatEnabled.get()) return java.util.Collections.singletonList(new Matrix4f());

        SplinePath path = SplinePath.create(form, new Matrix4f());
        if (path == null || !Float.isFinite(path.length())) return java.util.Collections.emptyList();

        double start = form.repeatStart.get();
        double end = form.repeatEnd.get();
        double offset = form.repeatOffset.get();
        double step = form.repeatDistance.get();
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(offset)) return java.util.Collections.emptyList();
        start = Math.max(0, Math.min(100, start));
        end = Math.max(0, Math.min(100, end));

        double length = path.length();
        if (length < 1E-6) return java.util.Collections.singletonList(path.frame(0, false));

        double span = Math.abs(end - start) * length / 100;
        double direction = end < start ? -1 : 1;
        boolean loop = form.closed.get() && Math.abs(end - start) >= 100;
        int count;
        if (span < 1E-6)
        {
            count = 1;
            step = 0;
        }
        else if (form.repeatMode.get() == 1)
        {
            if (!Double.isFinite(step) || step <= 0) return java.util.Collections.emptyList();
            // Closed full laps exclude the endpoint, including exact multiples of the step.
            double intervals = span / step;
            count = (int) Math.max(1, Math.min(MAX_COPIES, loop ? Math.ceil(intervals - 1E-7) : Math.floor(intervals + 1E-7) + 1));
        }
        else
        {
            count = Math.max(1, Math.min(MAX_COPIES, form.repeatCount.get()));
            step = count == 1 ? 0 : span / (loop ? count : count - 1);
        }

        List<Matrix4f> frames = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            double distance = start * length / 100 + direction * i * step + offset;
            if (form.closed.get()) distance = distance - Math.floor(distance / length) * length;
            else if (distance < -1E-7 || distance > length + 1E-7) continue;

            Matrix4f frame = path.frame((float) (distance / length * 100), form.repeatRotation.get() == 2);
            if (frame == null) continue;
            if (form.repeatRotation.get() == 0) frame = new Matrix4f().translation(frame.getTranslation(new Vector3f()));
            frames.add(frame);
        }
        return frames;
    }
}
