package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.graphics.window.InputCodes;

/**
 * Blender-style numeric input captured while a hotkey-driven G/S/R operation
 * is live. {@link #display()}'s dividend holds the typed digits and decimal
 * point; the sign lives separately so '-' can flip it at any moment, and a
 * '/' opens a divisor so fractions can be typed exactly. While active the
 * cursor drives nothing — the transform is recomputed purely from the edit's
 * start snapshot plus the typed amount.
 *
 * <p>This class owns only the buffers; feeding the parsed value back into the
 * transform stays with the host editor and its active {@link DragStrategy}.
 */
public class TransformNumericInput
{
    /** What a fed key did to the buffers, so the host knows how to react. */
    public enum Result
    {
        /** Not a numeric key — let it fall through to other handlers. */
        IGNORED,
        /** Consumed but nothing changed (e.g. a second decimal point). */
        CONSUMED,
        /** The typed amount changed — re-apply it to the transform. */
        CHANGED,
        /** The whole buffer was erased — numeric mode should end. */
        EMPTIED;
    }

    private final StringBuilder input = new StringBuilder();
    private final StringBuilder divisor = new StringBuilder();
    private boolean negative;
    private boolean active;
    private boolean dividing;

    public boolean isActive()
    {
        return this.active;
    }

    /**
     * Feed one key into the live buffer: digits and the decimal point extend
     * it, {@code -} flips the sign, {@code /} opens (or drops) the divisor,
     * backspace trims — and once everything is erased the host should hand
     * control back to the cursor ({@link Result#EMPTIED}).
     */
    public Result feedKey(int key)
    {
        int digit = digit(key);

        if (digit >= 0)
        {
            this.activeBuffer().append((char) ('0' + digit));
            this.active = true;

            return Result.CHANGED;
        }

        if (key == InputCodes.KEY_PERIOD || key == InputCodes.KEY_KP_DECIMAL)
        {
            StringBuilder buffer = this.activeBuffer();

            if (buffer.indexOf(".") < 0)
            {
                if (buffer.length() == 0)
                {
                    buffer.append('0');
                }

                buffer.append('.');
                this.active = true;

                return Result.CHANGED;
            }

            return Result.CONSUMED;
        }

        if (key == InputCodes.KEY_MINUS || key == InputCodes.KEY_KP_SUBTRACT)
        {
            this.negative = !this.negative;
            this.active = true;

            return Result.CHANGED;
        }

        if (key == InputCodes.KEY_SLASH || key == InputCodes.KEY_KP_DIVIDE)
        {
            /* First '/' opens a divisor that subsequent typing feeds; a second
             * '/' drops it and returns to editing the dividend as usual. */
            if (this.dividing)
            {
                this.divisor.setLength(0);
                this.dividing = false;
            }
            else
            {
                this.dividing = true;
            }

            this.active = true;

            return Result.CHANGED;
        }

        if (key == InputCodes.KEY_BACKSPACE)
        {
            if (!this.active)
            {
                return Result.IGNORED;
            }

            if (this.dividing)
            {
                /* Editing the divisor: trim it, and once it is empty drop the
                 * '/' so editing falls back to the dividend. */
                if (this.divisor.length() > 0)
                {
                    this.divisor.deleteCharAt(this.divisor.length() - 1);
                }
                else
                {
                    this.dividing = false;
                }

                return Result.CHANGED;
            }

            if (this.input.length() > 0)
            {
                this.input.deleteCharAt(this.input.length() - 1);
            }
            else
            {
                this.negative = false;
            }

            return this.input.length() == 0 && !this.negative ? Result.EMPTIED : Result.CHANGED;
        }

        return Result.IGNORED;
    }

    public double value()
    {
        double value = parse(this.input);

        if (this.negative)
        {
            value = -value;
        }

        /* A zero or empty divisor leaves the dividend untouched, so the value
         * stays sensible until a non-zero divisor is typed. */
        if (this.dividing)
        {
            double divisor = parse(this.divisor);

            if (divisor != 0D)
            {
                value /= divisor;
            }
        }

        return value;
    }

    public String display()
    {
        String display = (this.negative ? "-" : "") + (this.input.length() > 0 ? this.input.toString() : "0");

        if (this.dividing)
        {
            display += " / " + this.divisor;
        }

        return display;
    }

    public void clear()
    {
        this.input.setLength(0);
        this.divisor.setLength(0);
        this.negative = false;
        this.dividing = false;
        this.active = false;
    }

    /** The buffer keystrokes currently feed: the divisor while dividing, else the dividend. */
    private StringBuilder activeBuffer()
    {
        return this.dividing ? this.divisor : this.input;
    }

    private static int digit(int key)
    {
        if (key >= InputCodes.KEY_0 && key <= InputCodes.KEY_9)
        {
            return key - InputCodes.KEY_0;
        }

        if (key >= InputCodes.KEY_KP_0 && key <= InputCodes.KEY_KP_9)
        {
            return key - InputCodes.KEY_KP_0;
        }

        return -1;
    }

    private static double parse(CharSequence buffer)
    {
        if (buffer.length() == 0)
        {
            return 0D;
        }

        try
        {
            return Double.parseDouble(buffer.toString());
        }
        catch (NumberFormatException e)
        {
            return 0D;
        }
    }
}
