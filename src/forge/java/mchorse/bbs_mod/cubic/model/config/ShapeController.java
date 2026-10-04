package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
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

    /** Consolidate repeated targets so an accidental duplicate cannot apply a delta twice. */
    private Map<String, float[]> weights()
    {
        Map<String, float[]> result = new LinkedHashMap<>();
        for (Binding binding : this.bindings.getAllTyped())
        {
            if (binding.shape.get().codePoints().allMatch(Character::isWhitespace)) continue;
            float[] row = result.computeIfAbsent(binding.shape.get(), key -> new float[2]);
            row[0] += binding.height.get();
            row[1] += binding.tilt.get();
        }
        return result;
    }

    /** Least-squares coordinates; no second animation state that can drift from the shapes. */
    public float[] read(ShapeKeys keys)
    {
        double aa = 0, ab = 0, bb = 0, ay = 0, by = 0;
        for (var entry : this.weights().entrySet())
        {
            float[] w = entry.getValue();
            float y = keys.shapeKeys.getOrDefault(entry.getKey(), 0F);
            aa += w[0] * w[0]; ab += w[0] * w[1]; bb += w[1] * w[1];
            ay += w[0] * y; by += w[1] * y;
        }
        double det = aa * bb - ab * ab;
        if (det > 1E-8 * Math.max(1, aa * bb))
            return new float[] {(float) ((ay * bb - by * ab) / det), (float) ((by * aa - ay * ab) / det)};
        double trace = aa + bb;
        return trace < 1E-8 ? new float[2] : new float[] {(float) (ay / trace), (float) (by / trace)};
    }

    /** Keep unbound keys and corrections outside the two controller axes intact. */
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

    public static class Binding extends ValueGroup
    {
        public final ValueString shape = new ValueString("shape", "");
        public final ValueFloat height = new ValueFloat("height", 1F);
        public final ValueFloat tilt = new ValueFloat("tilt", 0F);
        public Binding(String id)
        {
            super(id);
            this.add(this.shape); this.add(this.height); this.add(this.tilt);
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
