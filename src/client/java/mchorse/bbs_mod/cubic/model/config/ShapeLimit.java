package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A shape key's working range. Only the drawn weight is clamped: keyframes and controller handles
 * keep the value as animated, so a handle never jumps when one of its shapes hits the range.
 */
public class ShapeLimit extends ValueGroup
{
    public final ValueString shape = new ValueString("shape", "");
    public final ValueFloat min = new ValueFloat("min", -1F);
    public final ValueFloat max = new ValueFloat("max", 1F);

    public ShapeLimit(String id)
    {
        super(id);
        this.add(this.shape); this.add(this.min); this.add(this.max);
    }

    public static class Limits extends ValueList<ShapeLimit>
    {
        public Limits(String id) { super(id); }
        @Override protected ShapeLimit create(String id) { return new ShapeLimit(id); }

        /** The weights to draw: {@code keys} itself when nothing is out of range, else a clamped copy. */
        public ShapeKeys apply(ShapeKeys keys)
        {
            if (keys == null || keys.shapeKeys.isEmpty()) return keys;
            ShapeKeys result = keys;
            for (ShapeLimit limit : this.getAllTyped())
            {
                Float value = keys.shapeKeys.get(limit.shape.get());
                if (value == null) continue;
                float a = limit.min.get(), b = limit.max.get();
                float clamped = Math.max(Math.min(a, b), Math.min(Math.max(a, b), value));
                if (clamped == value) continue;
                if (result == keys) result = keys.copy();
                result.shapeKeys.put(limit.shape.get(), clamped);
            }
            return result;
        }
    }
}
