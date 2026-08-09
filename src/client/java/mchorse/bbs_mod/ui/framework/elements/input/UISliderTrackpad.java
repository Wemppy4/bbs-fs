package mchorse.bbs_mod.ui.framework.elements.input;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A numeric field for a value with both ends: instead of a relative drag it
 * lays the value out along a track, so where you are within the range is
 * visible at a glance.
 *
 * Pressing the track puts the value under the cursor at once and keeps it there
 * for the rest of the drag, while pressing the handle itself grabs it where it
 * stands. Since the left button is spent on that, the value is typed by hand
 * through the middle button instead, which brings it up selected. Modifiers
 * refine the travel rather than scale a step: shift slows it down, alt slows it
 * further, and ctrl snaps onto {@link #increment}. Arrow keys and the wheel
 * still move by the step fields.
 *
 * A drag lands on a grid rather than wherever the pixels divided out — see
 * {@link #snap}. The grid is absolute (multiples of the step counted from zero)
 * rather than measured from where the gesture began, so dragging a value also
 * heals one that was typed off the grid. Typing, the arrows and the wheel are
 * left alone: the step belongs to the gesture, not to the field, so an exact
 * number stays reachable through the text box.
 *
 * Given no finite limits there is no track to travel along, so the drag falls
 * back to a trackpad's relative one rather than leaving the element inert.
 */
public class UISliderTrackpad extends UINumericInput<UISliderTrackpad>
{
    private static final float VALUE_ALPHA = 0.75F;
    private static final float DRAG_VALUE_ALPHA = 0.92F;
    private static final float HANDLE_ALPHA = 0.8F;
    private static final float HANDLE_HOVER_ALPHA = 0.95F;
    private static final float MARKER_ALPHA = 0.55F;

    /** How much travel the modifiers shave off a positional drag. */
    private static final double SLOW_DRAG = 0.25D;
    private static final double PRECISE_DRAG = 0.05D;

    /** How much finer the same modifiers cut the step they land on. */
    private static final double SLOW_STEPS = 5D;
    private static final double PRECISE_STEPS = 25D;

    /** How many steps a range is cut into when nobody named a step of its own. */
    private static final double AUTO_STEPS = 100D;

    /** Below this a step is too fine for the float dust to be worth sweeping. */
    private static final double CLEAN_LIMIT = 0.000001D;

    /** Dead zone of an unbounded drag, matching {@link UITrackpad}'s. */
    private static final int DRAG_THRESHOLD = 3;

    /**
     * The step a drag lands on, or 0 to cut one out of the range — see
     * {@link #getBaseStep()}. Worth naming only when the value has a unit of
     * its own that the range can't be guessed from (an interface scale that
     * moves in quarters, say).
     */
    public double snap;

    protected final Area handleArea = new Area();

    protected boolean wasInside;
    protected boolean dragging;
    protected int initialX;

    /** The value the gesture began with, restored when it gets cancelled. */
    protected double startValue;

    /** The value the drag travels from — a press on the track moves it under the cursor. */
    protected double anchorValue;

    public UISliderTrackpad()
    {
        this(null);
    }

    public UISliderTrackpad(Consumer<Double> callback)
    {
        super(callback);
    }

    /**
     * Land the drag on multiples of the given number. 0 hands the choice back
     * to the range.
     */
    public UISliderTrackpad snap(double snap)
    {
        this.snap = snap;

        return this;
    }

    @Override
    public boolean isDragging()
    {
        return this.dragging;
    }

    /* Geometry */

    /**
     * Whether the value has both ends, i.e. whether it can be laid out along a
     * track at all.
     */
    protected boolean hasSliderRange()
    {
        return Double.isFinite(this.min) && Double.isFinite(this.max) && this.max > this.min;
    }

    protected int getHandleWidth()
    {
        return Math.min(Math.max(this.area.h / 3, 6), 10);
    }

    protected int getHandlePadding()
    {
        return this.getHandleWidth() / 2;
    }

    protected int getTrackWidth()
    {
        return Math.max(this.area.w - this.getHandlePadding() * 2, 1);
    }

    protected float getProgress()
    {
        if (!this.hasSliderRange())
        {
            return 0F;
        }

        return (float) MathUtils.clamp((this.value - this.min) / (this.max - this.min), 0D, 1D);
    }

    protected int getHandleCenter()
    {
        int handleMinX = this.area.x + this.getHandlePadding();

        return handleMinX + Math.round(this.getTrackWidth() * this.getProgress());
    }

    protected void updateHandleArea()
    {
        if (!this.hasSliderRange())
        {
            this.handleArea.set(this.area.x, this.area.y, 0, this.area.h);

            return;
        }

        int handleWidth = this.getHandleWidth();
        int handleCenter = this.getHandleCenter();

        this.handleArea.set(handleCenter - handleWidth / 2, this.area.y, handleWidth, this.area.h);
    }

    /* Steps */

    /**
     * The nearest number at or above the given one that a person would call
     * round: 1, 2 and 5 in every decade. Everything a step is cut out of goes
     * through here, so a step is never something like 0.036.
     */
    protected static double niceStep(double raw)
    {
        if (!(raw > 0D) || !Double.isFinite(raw))
        {
            return 0D;
        }

        double decade = Math.pow(10D, Math.floor(Math.log10(raw)));
        double mantissa = raw / decade;

        /* A hair of tolerance, or a mantissa that divides out as 1.0000000002
         * gets bumped a whole notch up */
        double nice = mantissa <= 1.000001D ? 1D : (mantissa <= 2.000001D ? 2D : (mantissa <= 5.000001D ? 5D : 10D));

        return nice * decade;
    }

    /**
     * Sweep up the dust a step multiplication leaves behind — 0.1 taken three
     * times is famously 0.30000000000000004 — so a value is as round in the
     * file as it looks in the field.
     */
    protected static double clean(double value)
    {
        if (!Double.isFinite(value) || Math.abs(value) >= 1e9D)
        {
            return value;
        }

        return Math.rint(value * 1e6D) / 1e6D;
    }

    /**
     * The step a drag lands on before the modifiers cut it finer: whatever the
     * caller named, or a round hundredth of the range.
     */
    protected double getBaseStep()
    {
        if (this.snap > 0D)
        {
            return this.snap;
        }

        return this.hasSliderRange() ? niceStep((this.max - this.min) / AUTO_STEPS) : 0D;
    }

    /**
     * The step this very moment lands on. The modifiers that slow the travel
     * divide the step by about as much, so slowing down actually buys
     * precision instead of crawling along the same grid — and by a whole
     * number, so every coarse stop is still a stop of the finer grid.
     */
    protected double getStep()
    {
        double base = this.getBaseStep();

        if (base <= 0D)
        {
            return 0D;
        }

        if (Window.isAltPressed())
        {
            base /= PRECISE_STEPS;
        }
        else if (Window.isShiftPressed())
        {
            base /= SLOW_STEPS;
        }

        /* A whole-number field has nothing to gain from a finer grid, and a
         * step landing on exact integers is what keeps the (int) in
         * normalize() from shaving the value off the cursor */
        return this.integer ? Math.max(1D, Math.rint(base)) : base;
    }

    /**
     * Put the value on the grid. Ctrl asks for the coarse notch instead —
     * {@link #increment} is the deliberate one (15 degrees, a sixteenth of a
     * block), while the step is merely as fine as the track can be aimed.
     */
    protected double snapValue(double value)
    {
        double step = Window.isCtrlPressed() && this.increment > 0D ? this.increment : this.getStep();

        if (step <= 0D)
        {
            return value;
        }

        double snapped = Math.rint(value / step) * step;

        return step >= CLEAN_LIMIT ? clean(snapped) : snapped;
    }

    /* Dragging */

    /**
     * The value the given spot on the track stands for.
     */
    protected double getValueFromMouse(int mouseX)
    {
        int left = this.area.x + this.getHandlePadding();
        double factor = MathUtils.clamp((mouseX - left) / (double) this.getTrackWidth(), 0D, 1D);

        return this.snapValue(this.min + factor * (this.max - this.min));
    }

    /**
     * The value the current drag has travelled to. Bounded drags map the
     * cursor's travel onto the track, unbounded ones multiply it by the step,
     * the way a trackpad does.
     */
    protected double getDraggedValue(int mouseX)
    {
        int dx = mouseX - this.initialX;

        if (!this.hasSliderRange())
        {
            double diff = (Math.abs(dx) - DRAG_THRESHOLD) * this.getValueModifier();

            return diff < 0D ? this.anchorValue : this.snapValue(this.anchorValue + (dx < 0 ? -diff : diff));
        }

        return this.snapValue(this.anchorValue + (dx / (double) this.getTrackWidth()) * (this.max - this.min) * this.getDragPrecision());
    }

    protected double getDragPrecision()
    {
        if (Window.isAltPressed())
        {
            return PRECISE_DRAG;
        }
        else if (Window.isShiftPressed())
        {
            return SLOW_DRAG;
        }

        return 1D;
    }

    /**
     * Move the value, but only when it actually moves — otherwise a drag held
     * still would write it every single frame.
     */
    protected void applyValue(double value)
    {
        if (this.value == this.normalize(value))
        {
            return;
        }

        if (this.delayedInput)
        {
            this.setValue(value);
        }
        else
        {
            this.setValueAndNotify(value);
        }
    }

    protected void updateDragging(int mouseX)
    {
        this.applyValue(this.getDraggedValue(mouseX));
    }

    protected void beginDragging(UIContext context)
    {
        this.dragging = true;
        this.initialX = context.mouseX;
        this.startValue = this.value;

        /* Grabbing the handle takes the value as it stands, pressing anywhere
         * else on the track puts it under the cursor first */
        if (this.hasSliderRange() && !this.handleArea.isInside(context))
        {
            this.applyValue(this.getValueFromMouse(context.mouseX));
        }

        this.anchorValue = this.value;

        this.emitDragStart();
    }

    protected void stopDragging()
    {
        this.dragging = false;
        this.wasInside = false;
    }

    protected void cancelDragging()
    {
        this.setValueAndNotify(this.startValue);
        this.stopDragging();
    }

    /* Input */

    @Override
    public void resize()
    {
        super.resize();
        this.updateHandleArea();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.allowCanceling && context.mouseButton == 1 && this.dragging)
        {
            this.cancelDragging();

            return true;
        }

        /* The left button belongs to the track, so typing the value by hand
         * lives on the middle one. The number comes up selected: reaching for
         * this button means replacing it, not editing a digit of it */
        if (context.mouseButton == 2 && this.area.isInside(context))
        {
            context.focus(this);
            this.selectAll(context);

            return true;
        }

        this.wasInside = this.area.isInside(context);
        this.updateHandleArea();

        if (context.mouseButton == 0)
        {
            if (this.textbox.isFocused())
            {
                if (this.wasInside)
                {
                    /* The track owns the left button even while the value is
                     * being typed — submit the text and take the click */
                    context.focus(null);
                }
                else
                {
                    this.textbox.mouseClicked(context.mouseX, context.mouseY, context.mouseButton);

                    if (!this.textbox.isFocused())
                    {
                        context.focus(null);
                    }
                }
            }

            if (this.wasInside && !this.textbox.isFocused())
            {
                this.beginDragging(context);
            }
        }

        return context.mouseButton == 0 && this.wasInside;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1 && this.dragging)
        {
            this.cancelDragging();

            return true;
        }

        this.textbox.mouseReleased(context.mouseX, context.mouseY, context.mouseButton);

        /* Not gated on drag time like a trackpad's: a press alone already moves
         * the value here, so even the shortest click has something to submit */
        if (this.delayedInput && this.dragging)
        {
            this.setValueAndNotify(this.value);
        }

        if (this.dragging)
        {
            this.emitDragEnd();
        }

        this.stopDragging();

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.dragging)
        {
            updateAmplifier(context);

            return true;
        }

        if (this.area.isInside(context) && context.hasNotScrolledForMore(500) && BBSSettings.enableTrackpadScrolling.get())
        {
            if (context.mouseWheel > 0)
            {
                this.setValueAndNotify(this.value + this.getValueModifier());
            }
            else
            {
                this.setValueAndNotify(this.value - this.getValueModifier());
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (this.dragging && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancelDragging();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            if (this.isFocused())
            {
                context.unfocus();
            }

            this.updateDragging(context.mouseX);
        }

        this.updateHandleArea();

        if (this.textbox.isFocused())
        {
            this.textbox.render(context);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.opaque(BBSSettings.primaryColor.get()));
        }
        else
        {
            int primary = BBSSettings.primaryColor.get();
            int fillX = MathUtils.clamp(this.getHandleCenter(), this.area.x, this.area.ex());
            int fillColor = Colors.setA(primary, this.dragging ? DRAG_VALUE_ALPHA : VALUE_ALPHA);
            int handleColor = this.dragging ? Colors.WHITE : Colors.setA(Colors.WHITE, this.handleArea.isInside(context) ? HANDLE_HOVER_ALPHA : HANDLE_ALPHA);

            this.area.render(context.batcher, BBSSettings.inputSurface());

            if (this.hasSliderRange())
            {
                context.batcher.box(this.area.x, this.area.y, fillX, this.area.ey(), fillColor);
                context.batcher.box(fillX - 1, this.area.y, fillX + 1, this.area.ey(), Colors.setA(primary, MARKER_ALPHA));

                context.batcher.box(this.handleArea.x, this.handleArea.y, this.handleArea.ex(), this.handleArea.ey(), handleColor);
            }

            FontRenderer font = context.batcher.getFont();
            String label = this.forcedLabel == null ? format(this.value) : this.forcedLabel.get();

            /* The value text follows the textbox's color (white by default), so a
             * caller can axis-tint a slider the way transform trackpads are tinted. */
            int base = this.textbox.getColor();
            int textColor = this.dragging ? Colors.opaque(base) : Colors.setA(base, VALUE_ALPHA);
            int lx = this.area.ex() - 6 - font.getWidth(label);
            int ly = this.area.my() - font.getHeight() / 2;

            context.batcher.text(label, lx, ly, textColor);
        }

        this.renderLockedArea(context);

        super.render(context);
    }
}
