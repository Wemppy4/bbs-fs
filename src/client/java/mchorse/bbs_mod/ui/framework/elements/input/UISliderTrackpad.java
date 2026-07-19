package mchorse.bbs_mod.ui.framework.elements.input;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

public class UISliderTrackpad extends UIElement
{
    private static final float VALUE_ALPHA = 0.75F;
    private static final float DRAG_VALUE_ALPHA = 0.92F;
    private static final float HANDLE_ALPHA = 0.8F;
    private static final float HANDLE_HOVER_ALPHA = 0.95F;
    private static final float MARKER_ALPHA = 0.55F;
    private static final long DRAG_DELAY = 150L;

    public Consumer<Double> callback;

    protected double value;

    public double strong = 1D;
    public double normal = 0.25D;
    public double weak = 0.05D;
    public double increment = 1D;
    public double min = Float.NEGATIVE_INFINITY;
    public double max = Float.POSITIVE_INFINITY;
    public boolean integer;
    public boolean delayedInput;
    public boolean relative;
    public boolean allowCanceling = true;
    public IKey forcedLabel;

    protected final Area handleArea = new Area();

    protected boolean dragging;
    protected double startValue;
    protected long dragTime;
    protected int dragOffsetX;

    public UISliderTrackpad()
    {
        this(null);
    }

    public UISliderTrackpad(Consumer<Double> callback)
    {
        this.callback = callback;

        this.setValue(0D);
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    public UISliderTrackpad max(double max)
    {
        this.max = max;

        return this;
    }

    public UISliderTrackpad limit(double min)
    {
        this.min = min;

        return this;
    }

    public UISliderTrackpad limit(double min, double max)
    {
        this.min = min;
        this.max = max;

        return this;
    }

    public UISliderTrackpad limit(ValueInt value)
    {
        return this.limit(value.getMin(), value.getMax(), true);
    }

    public UISliderTrackpad limit(ValueFloat value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public UISliderTrackpad limit(ValueDouble value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public UISliderTrackpad limit(double min, double max, boolean integer)
    {
        this.integer = integer;

        return this.limit(min, max);
    }

    public UISliderTrackpad integer()
    {
        this.integer = true;

        return this;
    }

    public UISliderTrackpad increment(double increment)
    {
        this.increment = increment;

        return this;
    }

    public UISliderTrackpad values(double normal)
    {
        this.normal = normal;
        this.weak = normal / 5F;
        this.strong = normal * 5F;

        return this;
    }

    public UISliderTrackpad values(double normal, double weak, double strong)
    {
        this.normal = normal;
        this.weak = weak;
        this.strong = strong;

        return this;
    }

    public UISliderTrackpad delayedInput()
    {
        this.delayedInput = true;

        return this;
    }

    public UISliderTrackpad relative(boolean relative)
    {
        this.relative = relative;

        return this;
    }

    public UISliderTrackpad forcedLabel(IKey label)
    {
        this.forcedLabel = label;

        return this;
    }

    public UISliderTrackpad disableCanceling()
    {
        this.allowCanceling = false;

        return this;
    }

    public UISliderTrackpad degrees()
    {
        return this.increment(15D).values(1D, 0.1D, 5D);
    }

    public UISliderTrackpad block()
    {
        return this.increment(1 / 16D).values(1 / 32D, 1 / 128D, 1 / 2D);
    }

    public UISliderTrackpad metric()
    {
        return this.values(0.1D, 0.01D, 1D);
    }

    public boolean isDragging()
    {
        return this.dragging;
    }

    public boolean isDraggingTime()
    {
        return this.dragging && System.currentTimeMillis() - this.dragTime > DRAG_DELAY;
    }

    public double getValue()
    {
        return this.value;
    }

    public void setValue(double value)
    {
        this.setValueInternal(value);
    }

    public void setValueAndNotify(double value)
    {
        double oldValue = this.value;

        this.setValue(value);
        this.accept(value, oldValue);
    }

    protected void setValueInternal(double value)
    {
        value = MathUtils.clamp(value, this.min, this.max);

        if (this.integer)
        {
            value = (int) value;
        }

        this.value = value;
    }

    protected void accept(double value, double oldValue)
    {
        if (this.callback != null)
        {
            this.callback.accept(this.relative ? value - oldValue : this.value);
        }
    }

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
        int handlePadding = this.getHandlePadding();
        int handleMinX = this.area.x + handlePadding;
        int handleMaxX = this.area.ex() - handlePadding;
        int handleRange = Math.max(handleMaxX - handleMinX, 0);

        return handleMinX + Math.round(handleRange * this.getProgress());
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

    protected double getValueFromMouse(int mouseX)
    {
        int centerX = mouseX - this.dragOffsetX;
        int handlePadding = this.getHandlePadding();
        int left = this.area.x + handlePadding;
        int width = Math.max(this.area.w - handlePadding * 2, 1);
        double factor = MathUtils.clamp((centerX - left) / (double) width, 0D, 1D);

        return this.min + factor * (this.max - this.min);
    }

    protected void applySliderValue(double value)
    {
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
        if (this.hasSliderRange())
        {
            this.applySliderValue(this.getValueFromMouse(mouseX));
        }
    }

    protected void stopDragging()
    {
        this.dragging = false;
        this.dragOffsetX = 0;
    }

    protected void cancelDragging()
    {
        this.setValueAndNotify(this.startValue);
        this.stopDragging();
    }

    protected void finishDragging(int mouseX)
    {
        this.updateDragging(mouseX);
        this.updateHandleArea();

        if (this.delayedInput)
        {
            this.setValueAndNotify(this.value);
        }

        this.stopDragging();
    }

    protected void beginDragging(UIContext context)
    {
        this.dragging = true;
        this.startValue = this.value;
        this.dragTime = System.currentTimeMillis();
        this.dragOffsetX = this.handleArea.isInside(context) ? context.mouseX - this.handleArea.mx() : 0;

        this.updateDragging(context.mouseX);
        this.updateHandleArea();
    }

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

        if (context.mouseButton == 2 && this.area.isInside(context))
        {
            this.setValueAndNotify(-this.value);

            return true;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        this.updateHandleArea();

        if (this.hasSliderRange() && this.area.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.setValueAndNotify(Math.round(this.value));

                return true;
            }

            this.beginDragging(context);

            return true;
        }

        return false;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1 && this.dragging)
        {
            this.cancelDragging();

            return true;
        }

        if (context.mouseButton == 0 && this.dragging)
        {
            this.finishDragging(context.mouseX);

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.dragging)
        {
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

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (this.dragging && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancelDragging();

            return true;
        }

        if (this.area.isInside(context))
        {
            if (context.isHeld(GLFW.GLFW_KEY_UP))
            {
                this.setValueAndNotify(this.value + this.getValueModifier());

                return true;
            }
            else if (context.isHeld(GLFW.GLFW_KEY_DOWN))
            {
                this.setValueAndNotify(this.value - this.getValueModifier());

                return true;
            }
            /* Not with ctrl — Ctrl+minus is the global GUI scale shortcut */
            else if (!Window.isCtrlPressed() && (context.isPressed(GLFW.GLFW_KEY_MINUS) || context.isPressed(GLFW.GLFW_KEY_KP_SUBTRACT)))
            {
                this.setValueAndNotify(-this.value);

                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean subTextInput(UIContext context)
    {
        return false;
    }

    protected double getValueModifier()
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

        return value;
    }

    @Override
    public void render(UIContext context)
    {
        this.updateHandleArea();

        if (this.dragging)
        {
            this.updateDragging(context.mouseX);
            this.updateHandleArea();
        }

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
        String label = this.forcedLabel == null ? UITrackpad.format(this.value) : this.forcedLabel.get();
        int textColor = this.dragging ? Colors.WHITE : Colors.setA(Colors.WHITE, VALUE_ALPHA);
        int lx = this.area.ex() - 6 - font.getWidth(label);
        int ly = this.area.my() - font.getHeight() / 2;

        context.batcher.text(label, lx, ly, textColor);

        this.renderLockedArea(context);

        super.render(context);
    }
}
