package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
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
 * it (anchored zoom, horizontal pan, middle-drag panning), the marquee band, the pressed-mouse
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

    /** Middle-drag navigation: while pressed, the canvas pans with the cursor. */
    protected boolean navigating;

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
        /* Draw the marker */
        FontRenderer font = context.batcher.getFont();
        int width = font.getWidth(label) + 3;
        int color = BBSSettings.primaryColor.get();

        context.batcher.box(x, area.y, x + 1, area.ey(), color | Colors.A100);

        /* Move the tick line left, so it won't overflow the timeline */
        if (x + 1 + width > area.ex())
        {
            x -= width + 1;
        }

        /* Draw the tick label */
        context.batcher.textCard(label, x + 3, area.ey() - 2 - font.getHeight(), Colors.WHITE, Colors.setA(color, 0.78F), 2);
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

    public boolean isNavigating()
    {
        return this.navigating;
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
        this.xAxis.zoomAnchor(Scale.getAnchorX(context, this.xAxis.area), Math.copySign(this.xAxis.getZoomFactor(), wheel));
    }

    /** Pan the time axis by a cursor movement of {@code dx} pixels (middle-drag navigation). */
    public void dragTimeBy(int dx)
    {
        this.xAxis.setShift(this.xAxis.getShift() - dx / this.xAxis.getZoom());
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
