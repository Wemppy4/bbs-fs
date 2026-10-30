package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authoring data only. Animation remains in the existing shape-key track. */
public class ShapeController extends ValueGroup
{
    public final ValueString name = new ValueString("name", "shape_controller");
    public final ValueString bone = new ValueString("bone", "");
    /** Coordinates in model pixels, relative to the chosen bone. */
    public final ValueVector3f position = new ValueVector3f("position", new Vector3f());
    public final Bindings bindings = new Bindings("bindings");

    public ShapeController(String id)
    {
        super(id);
        this.add(this.name); this.add(this.bone); this.add(this.position); this.add(this.bindings);
    }

    public static final int HEIGHT = 0;
    public static final int TILT = 1;
    /** Sideways along the bone's X, so one handle can travel in any direction with {@link #HEIGHT}. */
    public static final int SHIFT = 2;
    public static final int AXES = 3;

    /** Consolidate repeated targets so an accidental duplicate cannot apply a delta twice. */
    private Map<String, float[]> weights()
    {
        Map<String, float[]> result = new LinkedHashMap<>();
        for (Binding binding : this.bindings.getAllTyped())
        {
            if (binding.shape.get().isBlank()) continue;
            float[] row = result.computeIfAbsent(binding.shape.get(), key -> new float[AXES]);
            row[HEIGHT] += binding.height.get();
            row[TILT] += binding.tilt.get();
            row[SHIFT] += binding.shift.get();
        }
        return result;
    }

    public boolean hasAxis(int axis)
    {
        return this.weights().values().stream().anyMatch(row -> row[axis] != 0F);
    }

    /** Least-squares coordinates; no second animation state that can drift from the shapes. */
    public float[] read(ShapeKeys keys)
    {
        double[][] normal = new double[AXES][AXES];
        double[] projected = new double[AXES];
        for (var entry : this.weights().entrySet())
        {
            float[] w = entry.getValue();
            float y = keys.shapeKeys.getOrDefault(entry.getKey(), 0F);
            for (int i = 0; i < AXES; i++)
            {
                projected[i] += w[i] * y;
                for (int j = 0; j < AXES; j++) normal[i][j] += w[i] * w[j];
            }
        }
        double trace = 0;
        for (int i = 0; i < AXES; i++) trace += normal[i][i];
        float[] result = new float[AXES];
        if (trace < 1E-8) return result;
        double[] solved = solve(normal, projected, trace);
        for (int i = 0; i < AXES; i++)
            result[i] = (float) (solved == null ? projected[i] / trace : solved[i]);
        return result;
    }

    /** Gaussian elimination over the axes that have weights; null when they aren't independent. */
    private static double[] solve(double[][] normal, double[] projected, double trace)
    {
        int[] axes = new int[AXES];
        int n = 0;
        for (int i = 0; i < AXES; i++) if (normal[i][i] > 0) axes[n++] = i;
        double[][] m = new double[n][n + 1];
        for (int r = 0; r < n; r++)
        {
            for (int c = 0; c < n; c++) m[r][c] = normal[axes[r]][axes[c]];
            m[r][n] = projected[axes[r]];
        }
        for (int c = 0; c < n; c++)
        {
            int pivot = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > Math.abs(m[pivot][c])) pivot = r;
            if (Math.abs(m[pivot][c]) <= 1E-6 * trace) return null;
            double[] swap = m[c]; m[c] = m[pivot]; m[pivot] = swap;
            for (int r = 0; r < n; r++)
            {
                if (r == c) continue;
                double f = m[r][c] / m[c][c];
                for (int k = c; k <= n; k++) m[r][k] -= f * m[c][k];
            }
        }
        double[] result = new double[AXES];
        for (int r = 0; r < n; r++) result[axes[r]] = m[r][n] / m[r][r];
        return result;
    }

    /** Keep unbound keys and corrections outside the controller axes intact. */
    public void set(ShapeKeys keys, int axis, float value)
    {
        if (!Float.isFinite(value)) return;
        float delta = value - this.read(keys)[axis];
        Map<String, float[]> weights = this.weights();
        for (var entry : weights.entrySet())
        {
            float gain = entry.getValue()[axis];
            if (gain != 0) keys.shapeKeys.put(entry.getKey(), keys.shapeKeys.getOrDefault(entry.getKey(), 0F) + delta * gain);
        }
    }

    /**
     * The controller on the bone paired with this one's (the bones' "Mirror" in the model config,
     * stored once either way round). Several controllers on one bone pair up by their order on it.
     */
    public ShapeController mirror(List<ShapeController> controllers, Map<String, String> bonePairs)
    {
        String bone = this.bone.get();
        String other = null;
        for (Map.Entry<String, String> pair : bonePairs.entrySet())
        {
            if (pair.getKey().equals(bone)) other = pair.getValue();
            else if (pair.getValue().equals(bone)) other = pair.getKey();
            if (other != null) break;
        }
        if (bone.isEmpty() || other == null || other.equals(bone)) return null;
        String paired = other;
        List<ShapeController> own = controllers.stream().filter(c -> c.bone.get().equals(bone)).toList();
        List<ShapeController> mirrors = controllers.stream().filter(c -> c.bone.get().equals(paired)).toList();
        int index = own.indexOf(this);
        return index >= 0 && index < mirrors.size() ? mirrors.get(index) : null;
    }

    public static class Binding extends ValueGroup
    {
        public final ValueString shape = new ValueString("shape", "");
        public final ValueFloat height = new ValueFloat("height", 1F);
        public final ValueFloat tilt = new ValueFloat("tilt", 0F);
        public final ValueFloat shift = new ValueFloat("shift", 0F);
        public Binding(String id)
        {
            super(id);
            this.add(this.shape); this.add(this.height); this.add(this.tilt); this.add(this.shift);
        }
    }

    public static class Bindings extends ValueList<Binding>
    {
        public Bindings(String id) { super(id); }
        @Override protected Binding create(String id) { return new Binding(id); }
    }

    public static class Controllers extends ValueList<ShapeController>
    {
        public Controllers(String id) { super(id); }
        @Override protected ShapeController create(String id) { return new ShapeController(id); }
    }
}
