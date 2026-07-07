package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * One element of a model debug overlay (chain lines, a marker, wind arrows):
 * whether it is drawn, its colour, its size — relative to the chain's segment
 * length, so it fits any rig — and, for markers, its shape. Built through
 * {@link ValueModelDebug}'s {@code line}/{@code element} helpers, never
 * directly.
 */
public class ValueDebugElement extends ValueGroup
{
    public static final int SHAPE_SPHERE = 0;
    public static final int SHAPE_CUBE = 1;
    public static final int SHAPE_DIAMOND = 2;
    public static final int SHAPE_RING = 3;
    public static final int SHAPE_CROSS = 4;
    public static final int SHAPES = 5;

    public final ValueBoolean visible;
    public final ValueInt color;
    public final ValueFloat size;

    /** Marker shape, one of the SHAPE_* constants; null for line-like elements. */
    public final ValueInt shape;

    public ValueDebugElement(String id, boolean visible, int color, float size, float min, float max, int shape)
    {
        super(id);

        this.visible = new ValueBoolean("visible", visible);
        this.color = new ValueInt("color", color).color();
        this.size = new ValueFloat("size", size, min, max);
        this.shape = shape < 0 ? null : new ValueInt("shape", shape, 0, SHAPES - 1);

        this.add(this.visible);
        this.add(this.color);
        this.add(this.size);

        if (this.shape != null)
        {
            this.add(this.shape);
        }
    }
}
