package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * This class represents a scale of an axis
 */
public class Scale
{
    private static final long ZOOM_DURATION_NS = 130_000_000L;

    private boolean zoomAnimating;
    private long zoomStarted;
    private long zoomDuration;
    private double zoomStart;
    private double zoomTarget;
    private float zoomAnchor;
    private double zoomDirection;
    private int zoomAreaPosition;
    private int zoomAreaSize;

    protected double shift = 0;
    protected double zoom = 1;
    protected int mult = 1;
    public boolean inverse;

    public Area area;
    public ScrollDirection direction = ScrollDirection.HORIZONTAL;
    public float anchor;

    protected boolean lockViewport;
    protected double lockMin;
    protected double lockMax;

    public static float getAnchorX(UIContext context, Area area)
    {
        return (context.mouseX - area.x) / (float) area.w;
    }

    public static float getAnchorY(UIContext context, Area area)
    {
        return (context.mouseY - area.y) / (float) area.h;
    }

    public Scale(Area area, ScrollDirection direction)
    {
        this(area);

        this.direction = direction;
    }

    public Scale(Area area)
    {
        super();

        this.area = area;
    }

    public Scale inverse()
    {
        this.stopZoom();
        this.inverse = true;

        return this;
    }

    public void copy(Scale scale)
    {
        this.stopZoom();
        this.shift = scale.shift;
        this.zoom = scale.zoom;
        this.mult = scale.mult;
        this.anchor = scale.anchor;
    }

    /* Convenience methods */

    public void set(double shift, double zoom)
    {
        this.setShift(shift);
        this.setZoom(zoom);
    }

    public void anchor(float anchor)
    {
        this.stopZoom();
        this.anchor = anchor;
    }

    public void lock(double min, double max)
    {
        this.stopZoom();
        this.lockViewport = true;
        this.lockMin = Math.min(min, max);
        this.lockMax = Math.max(min, max);
    }

    public void unlock()
    {
        this.stopZoom();
        this.lockViewport = false;
    }

    public void calculateMultiplier()
    {
        this.mult = this.recalcMultiplier(this.getZoom());
    }

    protected int recalcMultiplier(double zoom)
    {
        int factor = (int) (60F / zoom);

        /* Hardcoded caps */
        if (factor > 10000) factor = 10000;
        else if (factor > 5000) factor = 5000;
        else if (factor > 2500) factor = 2500;
        else if (factor > 1000) factor = 1000;
        else if (factor > 500) factor = 500;
        else if (factor > 250) factor = 250;
        else if (factor > 100) factor = 100;
        else if (factor > 50) factor = 50;
        else if (factor > 25) factor = 25;
        else if (factor > 10) factor = 10;
        else if (factor > 5) factor = 5;

        return factor <= 0 ? 1 : factor;
    }

    /* Getters/setters */

    public void setShift(double shift)
    {
        this.stopZoom();
        if (this.lockViewport)
        {
            double distance = this.getMaxValue() - this.getMinValue();

            this.shift = shift;

            double min = this.getMinValue();
            double max = this.getMaxValue();

            if (min < this.lockMin)
            {
                this.shift(this.lockMin, this.lockMin + distance);
            }
            else if (max > this.lockMax)
            {
                this.shift(this.lockMax - distance, this.lockMax);
            }

            min = this.getMinValue();
            max = this.getMaxValue();

            if (min < this.lockMin || max > this.lockMax)
            {
                double lockMin = Math.max(this.lockMin, min);
                double lockMax = Math.min(this.lockMax, max);

                this.view(lockMin, lockMax);
            }
        }
        else
        {
            this.shift = shift;
        }
    }

    public double getShift()
    {
        return this.shift;
    }

    public void setZoom(double zoom)
    {
        this.stopZoom();
        if (this.lockViewport)
        {
            this.zoom = zoom;

            double min = this.getMinValue();
            double max = this.getMaxValue();

            if (min < this.lockMin || max > this.lockMax)
            {
                this.view(Math.max(min, this.lockMin), Math.min(max, this.lockMax));
            }
        }
        else
        {
            this.zoom = zoom;
        }

        this.calculateMultiplier();
    }

    public double getZoom()
    {
        return this.zoom == 0 ? 1D : this.zoom;
    }

    public int getMult()
    {
        return this.mult;
    }

    /* Graphing code */

    /**
     * Convert the value to on-screen coordinate
     */
    public double to(double value)
    {
        double factor = (this.inverse
            ? -value + this.shift
            : value - this.shift
        ) * this.getZoom();

        if (this.area != null)
        {
            factor += this.direction.getPosition(this.area, this.anchor);
        }

        return factor;
    }

    /**
     * Convert on-screen coordinate to value
     */
    public double from(double mouse)
    {
        if (this.area != null)
        {
            mouse -= this.direction.getPosition(this.area, this.anchor);
        }

        return this.inverse
            ? -(mouse / this.getZoom() - this.shift)
            : mouse / this.getZoom() + this.shift;
    }

    public double getMinValue()
    {
        this.assertArea();

        return this.from(this.direction.getPosition(this.area, this.inverse ? 1 : 0));
    }

    public double getMaxValue()
    {
        this.assertArea();

        return this.from(this.direction.getPosition(this.area, this.inverse ? 0 : 1));
    }

    /* Viewport manipulation methods */

    public boolean isInView(double value)
    {
        return value >= this.getMinValue() && value <= this.getMaxValue();
    }

    public void view(double min, double max)
    {
        this.assertArea();
        this.view(min, max, this.direction.getSide(this.area));
    }

    public void view(double min, double max, double length)
    {
        this.viewOffset(min, max, length, 0);
    }

    public void viewOffset(double min, double max, double offset)
    {
        this.assertArea();
        this.viewOffset(min, max, this.direction.getSide(this.area), offset);
    }

    public void viewOffset(double min, double max, double length, double offset)
    {
        this.stopZoom();
        if (length <= 0)
        {
            return;
        }

        this.zoom = 1 / ((max - min) / length);

        if (offset != 0)
        {
            min -= offset / this.getZoom();
            max += offset / this.getZoom();
        }

        if (this.lockViewport && (min < this.lockMin || max > this.lockMax))
        {
            min = Math.max(min, this.lockMin);
            max = Math.min(max, this.lockMax);
        }

        this.zoom = 1 / ((max - min) / length);
        this.shift(min, max);

        this.calculateMultiplier();
    }

    public void shift(double min, double max)
    {
        this.stopZoom();
        this.shift = Lerps.lerp(min, max, this.inverse ? 1 - this.anchor : this.anchor);
    }

    public void shiftIntoMiddle(double x)
    {
        if (!this.isInView(x))
        {
            this.setShift(x - (this.getMaxValue() - this.getMinValue()) / 2);
        }
    }

    public void shiftInto(double value)
    {
        this.shiftInto(value, 0);
    }

    public void shiftInto(double value, double offset)
    {
        double min = this.getMinValue();
        double max = this.getMaxValue();
        double distance = max - min;

        if (value < min)
        {
            this.shift(value, value + distance);
        }
        else if (value > max)
        {
            value -= offset;

            this.shift(value - distance, value);
        }
    }

    /**
     * Follow a moving playhead: the view holds while {@code value} is short of {@code ahead} (a
     * fraction of the width), then moves with it so it stays there. A value behind the view is
     * caught only when it {@code jumped} back there (a loop restarting) and lands on the left
     * edge; otherwise the view was scrolled ahead on purpose and waits for the value to come.
     * A running zoom animation is left to finish.
     */
    public void follow(double value, double ahead, boolean jumped)
    {
        if (this.zoomAnimating || this.area == null || this.direction.getSide(this.area) <= 0)
        {
            return;
        }

        double min = this.getMinValue();
        double distance = this.getMaxValue() - min;

        if (value > min + distance * ahead)
        {
            this.shift(value - distance * ahead, value + distance * (1 - ahead));
        }
        else if (value < min && jumped)
        {
            this.shift(value, value + distance);
        }
    }

    public void zoom(double amount, double min, double max)
    {
        this.setZoom(MathUtils.clamp(this.getZoom() + amount, min, max));
    }

    public void zoomAnchor(float newAnchor, double amount)
    {
        this.zoomAnchor(newAnchor, amount, 0.01D, 1000D);
    }

    public void zoomAnchor(float newAnchor, double amount, double min, double max)
    {
        this.stopZoom();
        if (this.area != null)
        {
            if (this.inverse)
            {
                double shift = this.direction.getPosition(this.area, this.anchor) - this.direction.getPosition(this.area, newAnchor);

                this.shift += shift / this.getZoom();
            }
            else
            {
                double shift = this.direction.getPosition(this.area, this.anchor) - this.direction.getPosition(this.area, newAnchor);

                this.shift -= shift / this.getZoom();
            }
        }

        this.anchor = newAnchor;

        this.zoom(amount, min, max);

        double diff = this.from(this.direction.getPosition(this.area, newAnchor)) - this.from(this.direction.getPosition(this.area, 0F));

        this.shift -= diff;
        this.anchor = 0F;
    }

    /** Queue a wheel step from the last displayed scale, keeping its point under the cursor. */
    public void animateZoom(float anchor, double wheel)
    {
        this.animateZoom(anchor, wheel, 1D);
    }

    public void animateZoom(float anchor, double wheel, double speed)
    {
        if (wheel == 0 || this.area == null || this.direction.getSide(this.area) <= 0)
        {
            return;
        }

        double direction = Math.signum(wheel);
        /* Repeated steps accumulate; reversing the wheel responds immediately instead of
         * first finishing the queued movement in the opposite direction. */
        double base = this.zoomAnimating && this.zoomDirection == direction && this.hasZoomArea()
            ? this.zoomTarget : this.getZoom();
        this.zoomStart = this.getZoom();
        this.zoomTarget = MathUtils.clamp(base + Math.copySign(this.getZoomFactor(base) * speed, wheel), 0.01D, 1000D);
        this.zoomAnchor = anchor;
        this.zoomDirection = direction;
        this.zoomStarted = this.zoomTime();
        this.zoomAreaPosition = this.direction.getPosition(this.area, 0F);
        this.zoomAreaSize = this.direction.getSide(this.area);
        this.zoomDuration = (long) (ZOOM_DURATION_NS * (double) BBSSettings.getScrollSmoothingIntensity());

        if (this.zoomDuration <= 0L)
        {
            this.zoomAnchor(anchor, this.zoomTarget - this.getZoom());
            return;
        }

        this.zoomAnimating = this.zoomStart > 0 && this.zoomTarget != this.zoomStart;
    }

    /** Advance once before rendering and hit testing; direct viewport changes cancel the animation. */
    public void updateZoom()
    {
        if (!this.zoomAnimating) return;
        if (!this.hasZoomArea())
        {
            this.stopZoom();
            return;
        }

        double progress = BBSSettings.getScrollSmoothingIntensity() <= 0F ? 1D
            : MathUtils.clamp((this.zoomTime() - this.zoomStarted) / (double) this.zoomDuration, 0D, 1D);
        double eased = 1D - Math.pow(1D - progress, 3D);
        double zoom = progress == 1D ? this.zoomTarget
            : Math.exp(Math.log(this.zoomStart) + (Math.log(this.zoomTarget) - Math.log(this.zoomStart)) * eased);

        this.zoomAnchor(this.zoomAnchor, zoom - this.getZoom());
        /* zoomAnchor uses the immediate setters, which cancel pending animation. */
        this.zoomAnimating = progress < 1D;
    }

    /** Freeze at the displayed scale so a new gesture starts exactly where the user clicked. */
    public void stopZoom()
    {
        this.zoomAnimating = false;
    }

    protected long zoomTime()
    {
        return System.nanoTime();
    }

    private boolean hasZoomArea()
    {
        return this.area != null && this.direction.getPosition(this.area, 0F) == this.zoomAreaPosition
            && this.direction.getSide(this.area) == this.zoomAreaSize;
    }

    public double getZoomFactor()
    {
        return this.getZoomFactor(this.getZoom());
    }

    public double getZoomFactor(double zoom)
    {
        double factor = 25D;

        if (zoom < 0.2D) factor = 0.005D;
        else if (zoom < 1) factor = 0.025D;
        else if (zoom < 2) factor = 0.1D;
        else if (zoom < 15) factor = 0.5D;
        else if (zoom <= 50) factor = 2.5D;
        else if (zoom <= 100) factor = 5D;

        return factor;
    }

    protected void assertArea()
    {
        if (this.area == null)
        {
            throw new IllegalStateException("This operation isn't possible without area present!");
        }
    }
}
