package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of a model debug overlay (see the IK and physics subclasses),
 * edited through the debug toggle's context menu: the overlay's own switch,
 * whether it draws through the model, dashed chain lines, a global opacity,
 * and an ordered list of its elements. The chain wires and joint dots are
 * shared here but their defaults are supplied per subclass; the accent markers
 * come from the subclasses. The lines element's size is the thickness — zero
 * means hairlines.
 */
public class ValueModelDebug extends ValueGroup
{
    public final ValueBoolean enabled = new ValueBoolean("enabled", false);
    public final ValueBoolean xray = new ValueBoolean("xray", true);
    public final ValueBoolean dashed = new ValueBoolean("dashed", false);
    public final ValueFloat opacity = new ValueFloat("opacity", 0.5F, 0.05F, 1F);

    public final ValueDebugElement lines;
    public final ValueDebugElement joints;

    private final List<ValueDebugElement> elements = new ArrayList<>();

    protected ValueModelDebug(String id, boolean linesVisible, int linesColor, float linesSize, boolean jointsVisible, int jointsColor, float jointsSize)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.xray);
        this.add(this.dashed);
        this.add(this.opacity);

        this.lines = this.line("lines", linesVisible, linesColor, linesSize, 0F, 0.2F);
        this.joints = this.element("joints", jointsVisible, jointsColor, jointsSize, ValueDebugElement.SHAPE_SPHERE);
    }

    /** A line-like element (no shape) — its size is a thickness or a length gain. */
    protected ValueDebugElement line(String id, boolean visible, int color, float size, float min, float max)
    {
        return this.register(new ValueDebugElement(id, visible, color, size, min, max, -1));
    }

    /** A visible marker with a shape. */
    protected ValueDebugElement element(String id, int color, float size, int shape)
    {
        return this.element(id, true, color, size, shape);
    }

    protected ValueDebugElement element(String id, boolean visible, int color, float size, int shape)
    {
        return this.register(new ValueDebugElement(id, visible, color, size, 0.01F, 0.5F, shape));
    }

    private ValueDebugElement register(ValueDebugElement element)
    {
        this.add(element);
        this.elements.add(element);

        return element;
    }

    public List<ValueDebugElement> getElements()
    {
        return this.elements;
    }
}
