package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Marquee;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Common ancestor of the two time canvases — the film's clip timeline ({@code UIClips}) and the
 * keyframe editor ({@code UIKeyframes}). It owns what the canvases share by NATURE, not by
 * accident: the horizontal time axis with its pixel↔tick conversions, the wheel navigation over
 * it (anchored zoom, horizontal pan, middle-drag panning, Ctrl+middle-drag zoom), the marquee band, the pressed-mouse
 * bookkeeping and the look of the time cursor.
 *
 * <p>Deliberately NOT here: what an element of the timeline is and how it moves. A clip has a
 * duration, a layer and collisions; a keyframe is a point with a value axis, scaling and
 * stacking — their dragging, selection semantics and clipboard formats are per-canvas logic,
 * and pulling them up would make a third system out of two.</p>
 */
public abstract class UITimelineCanvas extends UIElement
{
    /**
     * The time axis: what tick a pixel column means. Bound to this element's own area; a canvas
     * whose time strip is narrower (the dope sheet's label column) re-points {@link Scale#area}
     * at its actual strip once it exists.
     */
    protected final Scale xAxis = new Scale(this.area, ScrollDirection.HORIZONTAL);

    /** The Shift-drag selection band. Subclasses decide what falls into it on release. */
    protected final Marquee marquee = new Marquee();

    /** How much a Ctrl+middle-drag zooms per pixel: about 2.7× per 100 pixels. */
    private static final double DRAG_ZOOM_RATE = 0.01D;

    /** Middle-drag navigation: while pressed, the canvas pans with the cursor. */
    protected boolean navigating;

    /** Ctrl held at the middle press: the drag zooms around the press point instead (like Blender). */
    protected boolean zoomNavigating;

    /* Where the pressed mouse was last frame and where the press began, for every gesture
     * that measures its own movement. */
    protected int lastX;
    protected int lastY;
    protected int initialX;
    protected int initialY;

    /**
     * Render the time cursor: a vertical line at {@code x} with the tick label on a card at its
     * foot, nudged left when it would poke out of the area.
     */
    public static void renderCursor(UIContext context, String label, Area area, int x)
    {
        renderCursor(context, label, area, x, area.ey(), BBSSettings.primaryColor.get(), false);
    }

    /**
     * The cursor's look in any colour, shared with the film's markers: a vertical line at
     * {@code x} from the top of the area down to {@code bottom}, with the label on a card at its
     * foot or, {@code onTop}, at its head. An empty label leaves the line alone.
     */
    public static void renderCursor(UIContext context, String label, Area area, int x, int bottom, int color, boolean onTop)
    {
        FontRenderer font = context.batcher.getFont();
        int width = font.getWidth(label) + 3;

        context.batcher.box(x, area.y, x + 1, bottom, color | Colors.A100);

        if (label.isEmpty())
        {
            return;
        }

        /* Move the tick line left, so it won't overflow the timeline */
        if (x + 1 + width > area.ex())
        {
            x -= width + 1;
        }

        int y = onTop ? area.y + 2 : bottom - 2 - font.getHeight();

        context.batcher.textCard(label, x + 3, y, Colors.WHITE, Colors.setA(color, 0.78F), 2);
    }

    public Scale getXAxis()
    {
        return this.xAxis;
    }

    public int toGraphX(double value)
    {
        return (int) this.xAxis.to(value);
    }

    public double fromGraphX(int mouseX)
    {
        return this.xAxis.from(mouseX);
    }

    public boolean isSnappingToTicks()
    {
        return BBSSettings.editorSnapToTicks.get() && !Window.isShiftPressed();
    }

    /** Mouse authoring uses the shared tick grid, with Shift temporarily bypassing it. */
    public float fromGraphCursor(int mouseX)
    {
        double tick = this.fromGraphX(mouseX);

        return (float) (this.isSnappingToTicks() ? Math.round(tick) : tick);
    }

    public boolean isNavigating()
    {
        return this.navigating;
    }

    public boolean isZoomNavigating()
    {
        return this.navigating && this.zoomNavigating;
    }

    /** Begin middle-drag navigation at the press remembered by {@link #setMouse}; Ctrl makes it zoom. */
    protected void startNavigating()
    {
        this.navigating = true;
        this.zoomNavigating = Window.isCtrlPressed();
        this.xAxis.stopZoom();
    }

    /** Remember where a press landed (and start measuring movement from there). */
    protected void setMouse(int x, int y)
    {
        this.lastX = this.initialX = x;
        this.lastY = this.initialY = y;
    }

    /** Pan the time axis by a horizontal wheel step, zoom-compensated. */
    public void panTime(double wheelHorizontal)
    {
        this.xAxis.setShift(this.xAxis.getShift() - (25F * BBSSettings.scrollingSensitivityHorizontal.get() * wheelHorizontal) / this.xAxis.getZoom());
    }

    /** Zoom the time axis one step in the wheel's direction, anchored under the cursor. */
    public void zoomTimeAt(UIContext context, double wheel)
    {
        this.xAxis.animateZoom(Scale.getAnchorX(context, this.xAxis.area), wheel, this.getZoomSpeed());
    }

    public double getZoomSpeed()
    {
        return Window.isShiftPressed() && Window.isCtrlPressed() ? 3D : 1D;
    }

    /** Pan the time axis by a cursor movement of {@code dx} pixels (middle-drag navigation). */
    public void dragTimeBy(int dx)
    {
        this.xAxis.setShift(this.xAxis.getShift() - dx / this.xAxis.getZoom());
    }

    /** Follow a middle-drag along the time axis: pan with the cursor, or with Ctrl zoom (right zooms in). */
    public void navigateTime(int dx)
    {
        if (this.zoomNavigating)
        {
            this.zoomByDrag(this.xAxis, dx);
        }
        else
        {
            this.dragTimeBy(dx);
        }
    }

    /**
     * Zoom an axis by a drag of {@code pixels} (positive zooms in), keeping the value under the
     * point where the drag began in place.
     */
    public void zoomByDrag(Scale scale, double pixels)
    {
        int side = scale.direction.getSide(scale.area);

        if (pixels == 0 || side <= 0)
        {
            return;
        }

        int pressed = scale.direction == ScrollDirection.HORIZONTAL ? this.initialX : this.initialY;
        float anchor = (pressed - scale.direction.getPosition(scale.area, 0F)) / (float) side;
        double zoom = scale.getZoom();

        scale.zoomAnchor(anchor, zoom * Math.exp(pixels * DRAG_ZOOM_RATE) - zoom);
    }

    /** Keep the marquee's far corner on the cursor and draw the band while it is pressed. */
    protected void renderMarquee(UIContext context)
    {
        if (this.marquee.isPressed())
        {
            this.marquee.update(context.mouseX, context.mouseY);
            this.marquee.render(context, 0, 0);
        }
    }
}
