package mchorse.bbs_mod.ui.framework.elements.input;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.text.UIBaseTextbox;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Everything a numeric field is besides its mouse gesture: the value with its
 * limits, the drag steps, the text box that spells the number (including math
 * expressions), and the events that bracket a drag.
 *
 * Subclasses add only the way a value is picked with the mouse —
 * {@link UITrackpad} drags it relatively over an infinite range, while
 * {@link UISliderTrackpad} positions it along a bounded track. Everything else
 * lives here so the two can never drift apart again.
 *
 * The self type {@code T} exists so the builder chain keeps returning the
 * concrete class ({@code new UITrackpad().limit(0, 1).integer()} stays a
 * {@code UITrackpad}).
 */
public abstract class UINumericInput <T extends UINumericInput<T>> extends UIBaseTextbox
{
    /** How long a press must be held before it counts as a drag rather than a click. */
    protected static final long DRAG_DELAY = 150L;

    private static final Set<Character> allowedNumberCharacters = ".-+/*^%() ".chars()
        .mapToObj((o) -> (char) o)
        .collect(Collectors.toSet());

    private static final Factor globalFactor = new Factor(20, 1, 40, (x) ->
    {
        if (x <= 10) return x / 100D;
        else if (x <= 20) return (x - 10) / 10D;
        else if (x <= 30) return (x - 20) / 1D;

        return (x - 30) * 10D;
    });

    private static final DecimalFormat FORMAT;

    public Consumer<Double> callback;

    protected double value;

    /* Trackpad options */
    public double strong = 1D;
    public double normal = 0.25D;
    public double weak = 0.05D;
    public double increment = 1D;
    public double min = Float.NEGATIVE_INFINITY;
    public double max = Float.POSITIVE_INFINITY;
    public boolean integer;
    public boolean delayedInput;
    public boolean onlyNumbers;

    public boolean relative;
    public boolean allowCanceling = true;
    public IKey forcedLabel;

    /** When the current gesture began, so a click can be told from a drag. */
    protected long dragTime;

    static
    {
        FORMAT = new DecimalFormat("#.###");
        FORMAT.setRoundingMode(RoundingMode.HALF_EVEN);
        FORMAT.setGroupingUsed(false);
        FORMAT.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.ENGLISH));
    }

    public static void updateAmplifier(UIContext context)
    {
        globalFactor.addX((int) context.mouseWheel);
        context.notifyOrUpdate(UIKeys.TRACKPAD_GLOBAL_AMPLIFIER.format(globalFactor.getValue()), Colors.BLUE);
    }

    public static String format(double number)
    {
        return FORMAT.format(number).replace(',', '.');
    }

    public UINumericInput()
    {
        this(null);
    }

    public UINumericInput(Consumer<Double> callback)
    {
        super();

        this.callback = callback;

        this.setValue(0);
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    @SuppressWarnings("unchecked")
    protected T self()
    {
        return (T) this;
    }

    /* Builders */

    public T max(double max)
    {
        this.max = max;

        return this.self();
    }

    public T limit(double min)
    {
        this.min = min;

        return this.self();
    }

    public T limit(double min, double max)
    {
        this.min = min;
        this.max = max;

        return this.self();
    }

    public T limit(ValueInt value)
    {
        return this.limit(value.getMin(), value.getMax(), true);
    }

    public T limit(ValueFloat value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public T limit(ValueDouble value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public T limit(double min, double max, boolean integer)
    {
        this.integer = integer;

        return this.limit(min, max);
    }

    public T integer()
    {
        this.integer = true;

        return this.self();
    }

    public T increment(double increment)
    {
        this.increment = increment;

        return this.self();
    }

    public T values(double normal)
    {
        this.normal = normal;
        this.weak = normal / 5F;
        this.strong = normal * 5F;

        return this.self();
    }

    public T values(double normal, double weak, double strong)
    {
        this.normal = normal;
        this.weak = weak;
        this.strong = strong;

        return this.self();
    }

    public T delayedInput()
    {
        this.delayedInput = true;

        return this.self();
    }

    public T onlyNumbers()
    {
        this.onlyNumbers = true;

        return this.self();
    }

    public T relative(boolean relative)
    {
        this.relative = relative;

        return this.self();
    }

    public T forcedLabel(IKey label)
    {
        this.forcedLabel = label;

        return this.self();
    }

    public T disableCanceling()
    {
        this.allowCanceling = false;

        return this.self();
    }

    /* Values presets */

    public T degrees()
    {
        return this.increment(15D).values(1D, 0.1D, 5D);
    }

    public T block()
    {
        return this.increment(1 / 16D).values(1 / 32D, 1 / 128D, 1 / 2D);
    }

    /**
     * Steps for a multiplier that lives around 1 (a scale). Without a preset
     * these fields fall back to the generic default, which is tuned for
     * whole-number values and moves a scale about eight times faster than the
     * same drag moves a translate — both in the field itself and in the
     * hotkey lever that borrows the field's step ({@code additiveFactor}).
     * Landed between the two: a hair over the translate step, since a scale
     * covers its useful range in far less travel than a position does.
     */
    public T factor()
    {
        return this.increment(0.25D).values(0.05D, 0.01D, 0.25D);
    }

    public T metric()
    {
        return this.values(0.1D, 0.01D, 1);
    }

    /**
     * An angle bounded to half a turn either way — a rotation limit, a pole
     * angle, a cone half-angle. Every such field in the mod goes through here,
     * so they all drag and scroll by the same amount and none of them accepts
     * letters: the same knob has to feel the same wherever the animator meets
     * it, whether it is on the constraints tab, in an IK joint or in physics.
     */
    public T angle180()
    {
        return this.degrees().onlyNumbers().limit(-180D, 180D);
    }

    /**
     * A normalized 0..1 factor — weight, softness, stiffness, damping. Same
     * reasoning as {@link #angle180()}: one set of steps for the whole family.
     */
    public T normalized()
    {
        return this.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
    }

    /* Value */

    /**
     * Whether this field is being dragged
     */
    public abstract boolean isDragging();

    public boolean isDraggingTime()
    {
        return this.isDragging() && System.currentTimeMillis() - this.dragTime > DRAG_DELAY;
    }

    public double getValue()
    {
        return this.value;
    }

    /**
     * Set the value of the field. The input value would be rounded up to 3
     * decimal places.
     */
    public void setValue(double value)
    {
        this.setValueInternal(value);

        if (!this.textbox.isFocused())
        {
            this.updateTextField();
        }
    }

    /**
     * Set value of this field and also notify the listener so it could detect
     * the value change.
     */
    public void setValueAndNotify(double value)
    {
        double oldValue = this.value;

        this.setValue(value);
        this.accept(value, oldValue);
    }

    protected void setValueInternal(double value)
    {
        this.value = this.normalize(value);
    }

    /**
     * What this field would actually store for the given input. Drag code
     * compares against this rather than the raw value, so a gesture that lands
     * outside the limits (or between two integers) stops notifying every frame.
     */
    protected double normalize(double value)
    {
        value = MathUtils.clamp(value, this.min, this.max);

        return this.integer ? (int) value : value;
    }

    protected void accept(double value, double oldValue)
    {
        if (this.callback != null)
        {
            this.callback.accept(this.relative ? value - oldValue : this.value);
        }
    }

    public double getValueModifier()
    {
        double value = this.normal;

        if (Window.isShiftPressed())
        {
            value = this.strong;
        }
        else if (Window.isAltPressed())
        {
            value = this.weak;
        }
        else if (Window.isCtrlPressed())
        {
            value = this.increment;
        }

        return value * globalFactor.getValue();
    }

    /* Drag gesture */

    protected void emitDragStart()
    {
        this.dragTime = System.currentTimeMillis();
        this.getEvents().emit(new UITrackpadDragStartEvent(this));
    }

    protected void emitDragEnd()
    {
        this.getEvents().emit(new UITrackpadDragEndEvent(this));
    }

    /* Text input */

    @Override
    public void focus(UIContext context)
    {
        super.focus(context);

        this.updateTextField();
        this.textbox.setFocused(true);
        this.textbox.moveCursorToEnd();
    }

    @Override
    public void unfocus(UIContext context)
    {
        this.evaluate();

        super.unfocus(context);

        this.textbox.setFocused(false);

        /* Reset the value in case it's out of range */
        if (this.delayedInput)
        {
            this.setValueAndNotify(this.value);
        }
        else
        {
            this.setValue(this.value);
        }
    }

    /**
     * Set the value from one of this field's own gestures — an arrow key, the
     * scroll wheel, the negate shortcut — any of which can happen while the
     * field is being typed into.
     *
     * While the field is focused, the text box is what spells the value out,
     * and {@link #setValue(double)} deliberately refuses to respell a focused
     * box (an update coming from the outside must not eat what is half typed).
     * These gestures are not such an outside update — they are this field's own
     * edit — so they respell the box themselves, otherwise the number visibly
     * stops moving until the field is left.
     *
     * Not named {@code applyValue}: {@link UISliderTrackpad} already has a method
     * by that name for its drag, and a base-class twin would be silently
     * overridden by it — the arrow keys on a slider would then stop respelling
     * the box, which is the very bug this exists to fix.
     */
    protected void setValueFromGesture(double value)
    {
        this.setValueAndNotify(value);

        if (this.textbox.isFocused())
        {
            this.updateTextField();
            this.textbox.moveCursorToEnd();
        }
    }

    protected void updateTextField()
    {
        if (Window.isAltPressed())
        {
            this.textbox.setText(this.integer ? String.valueOf((int) this.value) : String.valueOf(this.value));
        }
        else
        {
            this.textbox.setText(this.integer ? format((int) this.value) : format(this.value));
        }
    }

    protected void evaluate()
    {
        String text = this.textbox.getText().trim();

        try
        {
            Float.parseFloat(text);

            return;
        }
        catch (Exception e)
        {}

        try
        {
            MathBuilder builder = new MathBuilder();

            this.setValueAndNotify(builder.parse(text).get().doubleValue());
            this.textbox.moveCursorToEnd();
        }
        catch (Exception e)
        {}
    }

    /**
     * Feed an event into the text box and adopt whatever number it now spells.
     */
    protected boolean editText(BooleanSupplier input)
    {
        String old = this.textbox.getText();
        boolean result = input.getAsBoolean();
        String text = this.textbox.getText();

        if (this.textbox.isFocused() && !text.equals(old))
        {
            try
            {
                double oldValue = this.value;

                this.setValueInternal(text.isEmpty() ? 0 : Double.parseDouble(text));

                if (!this.delayedInput)
                {
                    this.accept(this.value, oldValue);
                }
            }
            catch (Exception e)
            {}
        }

        return result;
    }

    private boolean numberCharacterAllowed(char character)
    {
        return Character.isDigit(character) || allowedNumberCharacters.contains(character);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.textbox.area.copy(this.area);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (this.isFocused())
        {
            if (context.isHeld(GLFW.GLFW_KEY_UP))
            {
                this.setValueFromGesture(this.value + this.getValueModifier());

                return true;
            }
            else if (context.isHeld(GLFW.GLFW_KEY_DOWN))
            {
                this.setValueFromGesture(this.value - this.getValueModifier());

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_TAB))
            {
                context.focus(this, Window.isShiftPressed() ? -1 : 1);

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                context.unfocus();

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                context.focus(null);
            }
        }
        else if (this.area.isInside(context))
        {
            /* Not with ctrl — Ctrl+minus is the global GUI scale shortcut */
            if (!context.isFocused() && !Window.isCtrlPressed() && (context.isPressed(GLFW.GLFW_KEY_MINUS) || context.isPressed(GLFW.GLFW_KEY_KP_SUBTRACT)))
            {
                this.setValueAndNotify(-this.value);

                return true;
            }
        }

        return this.editText(() -> this.textbox.keyPressed(context));
    }

    @Override
    public boolean subTextInput(UIContext context)
    {
        char inputCharacter = context.getInputCharacter();

        if (this.onlyNumbers && this.isFocused() && !this.numberCharacterAllowed(inputCharacter))
        {
            context.unfocus();

            return false;
        }

        return this.editText(() -> this.textbox.textInput(inputCharacter));
    }
}
