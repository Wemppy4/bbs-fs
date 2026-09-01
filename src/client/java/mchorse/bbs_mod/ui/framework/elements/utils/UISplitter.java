package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * The grip between a sidebar and the pane beside it: the same 6×40 {@link UIDraggable} strip
 * every editor used to hand-roll, with the parts they all copied &mdash; the drag-to-size math,
 * the clamp, the remembered size &mdash; kept here once.
 *
 * <p>It is a handle, not a container. The owner still places its panes and this grip (with
 * {@code relative(...)} on the pane's edge, as before) and re-applies its own flex in
 * {@link #onChange}; the splitter only tells it what the size is now. Sizes are read from and
 * saved to {@link BBSSettings#editorLayoutSettings} under {@code key}, so they survive a restart,
 * and a double click on the grip puts the default back.</p>
 *
 * <p>The size is in the unit the owner already lays out in: {@link #fraction} of the extent
 * element's width, or {@link #pixels} from the origin element's edge. Which edge is measured from
 * is {@link #fromEnd()} for sidebars on the right.</p>
 */
public class UISplitter extends UIDraggable
{
    /** Same window as the dock's seams, so a double click feels alike everywhere. */
    public static final long DOUBLE_CLICK_MS = 300;

    private final String key;
    private final boolean fraction;
    private final float defaultValue;
    private float value;

    private float min;
    private Supplier<Float> max;

    private UIElement origin;
    private Supplier<UIElement> extent;
    private boolean fromEnd;
    private boolean vertical;
    private Runnable onChange;

    private long lastClickTime;

    /** A splitter whose size is a share (0..1) of {@code extent}'s width, measured from the edge of {@code origin}. */
    public static UISplitter fraction(String key, float defaultValue, float min, float max)
    {
        return new UISplitter(key, true, defaultValue).range(min, max);
    }

    /** A splitter whose size is pixels from the edge of {@code origin}. */
    public static UISplitter pixels(String key, int defaultValue, int min, int max)
    {
        return new UISplitter(key, false, defaultValue).range(min, max);
    }

    public UISplitter(String key, boolean fraction, float defaultValue)
    {
        super(null);

        this.key = key;
        this.fraction = fraction;
        this.defaultValue = defaultValue;
        this.value = BBSSettings.editorLayoutSettings.getSplitSize(key, defaultValue);
        this.min = 0F;
        this.max = () -> fraction ? 1F : Float.MAX_VALUE;

        this.callback(this::drag);
        /* The live value is applied every frame of the drag; the setting is written once it ends. */
        this.dragEnd(this::persist);
        this.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
    }

    /* Configuration */

    /** A fixed range; the stored size is clamped into it right away, so a stale setting can't overflow. */
    public UISplitter range(float min, float max)
    {
        this.min = min;
        this.max = () -> max;
        this.value = MathUtils.clamp(this.value, min, max);

        return this;
    }

    /**
     * A range whose top depends on the live layout (a sibling sidebar's size, the window width).
     * It is only applied on drag: at construction the areas it reads are still empty.
     */
    public UISplitter range(float min, Supplier<Float> max)
    {
        this.min = min;
        this.max = max;
        this.value = Math.max(this.value, min);

        return this;
    }

    /** The element the size is measured from, and the one a fraction is a share of. */
    public UISplitter measure(UIElement origin, UIElement extent)
    {
        return this.measure(origin, () -> extent);
    }

    /** Same, with the extent looked up at drag time &mdash; for an element that isn't there yet, like the owner's parent. */
    public UISplitter measure(UIElement origin, Supplier<UIElement> extent)
    {
        this.origin = origin;
        this.extent = extent;

        return this;
    }

    public UISplitter measure(UIElement element)
    {
        return this.measure(element, element);
    }

    /** Measure from the far edge (right, or bottom when vertical) &mdash; for a sidebar on that side. */
    public UISplitter fromEnd()
    {
        this.fromEnd = true;

        return this;
    }

    /** Size along Y instead of X. */
    public UISplitter vertical()
    {
        this.vertical = true;
        this.cursors(GLFW.GLFW_VRESIZE_CURSOR, GLFW.GLFW_VRESIZE_CURSOR);

        return this;
    }

    /** Runs after the size changed (a drag, a reset): the owner re-applies its flex and resizes. */
    public UISplitter onChange(Runnable onChange)
    {
        this.onChange = onChange;

        return this;
    }

    /* Value */

    public float getValue()
    {
        return this.value;
    }

    public int getPixels()
    {
        return Math.round(this.value);
    }

    /** Sets the size as if the user had dragged it there: clamped, remembered, and reported. */
    public void setValue(float value)
    {
        this.apply(value);
        this.persist();
    }

    public void reset()
    {
        this.setValue(this.defaultValue);
    }

    private void apply(float value)
    {
        this.value = MathUtils.clamp(value, this.min, this.max.get());

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    private void persist()
    {
        BBSSettings.editorLayoutSettings.setSplitSize(this.key, this.value);
    }

    /* Interaction */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            long now = System.currentTimeMillis();
            boolean paired = now - this.lastClickTime <= DOUBLE_CLICK_MS;

            /* Reset rather than keep the time, so a third click starts a fresh pair. */
            this.lastClickTime = paired ? 0 : now;

            if (paired)
            {
                this.reset();

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    private void drag(UIContext context)
    {
        UIElement extent = this.extent == null ? null : this.extent.get();

        if (this.origin == null || extent == null)
        {
            return;
        }

        float distance;

        if (this.vertical)
        {
            distance = this.fromEnd ? this.origin.area.ey() - context.mouseY : context.mouseY - this.origin.area.y;
        }
        else
        {
            distance = this.fromEnd ? this.origin.area.ex() - context.mouseX : context.mouseX - this.origin.area.x;
        }

        if (this.fraction)
        {
            int size = this.vertical ? extent.area.h : extent.area.w;

            if (size <= 0)
            {
                return;
            }

            distance = distance / (float) size;
        }

        this.apply(distance);
    }
}
