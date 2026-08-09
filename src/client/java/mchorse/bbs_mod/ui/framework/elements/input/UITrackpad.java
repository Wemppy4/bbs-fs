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
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

/**
 * A numeric field whose value is dragged relatively: the cursor's horizontal
 * travel is multiplied by the current step, and the cursor wraps around the
 * screen so an unbounded range stays reachable.
 *
 * The value itself, its limits and the text editing all live in
 * {@link UINumericInput}. For values with a finite range see
 * {@link UISliderTrackpad}, which positions the value along a track instead.
 */
public class UITrackpad extends UINumericInput<UITrackpad>
{
    /* Value dragging fields */
    private boolean wasInside;
    private boolean dragging;
    private int shiftX;
    private int initialX;
    private int initialY;
    private double lastValue;

    private Timer changed = new Timer(30);

    private Area plusOne = new Area();
    private Area minusOne = new Area();

    public UITrackpad()
    {
        this(null);
    }

    public UITrackpad(Consumer<Double> callback)
    {
        super(callback);
    }

    /* Builders
     *
     * Every one of these already works through {@link UINumericInput}, and for
     * source code the inherited version would do. They are restated here for
     * the sake of the byte code: the self type {@code T} erases to
     * {@code UINumericInput}, so the inherited methods carry that in their
     * descriptor, while add-ons compiled against the older UITrackpad — where
     * these lived directly — look up a descriptor ending in {@code UITrackpad}
     * and die with a NoSuchMethodError. A covariant override makes javac emit
     * exactly that old descriptor again (plus a bridge to the base one), so
     * both the old and the new binaries resolve.
     *
     * Nothing may be dropped from this list without breaking somebody's jar.
     */

    @Override
    public UITrackpad max(double max)
    {
        return super.max(max);
    }

    @Override
    public UITrackpad limit(double min)
    {
        return super.limit(min);
    }

    @Override
    public UITrackpad limit(double min, double max)
    {
        return super.limit(min, max);
    }

    @Override
    public UITrackpad limit(ValueInt value)
    {
        return super.limit(value);
    }

    @Override
    public UITrackpad limit(ValueFloat value)
    {
        return super.limit(value);
    }

    @Override
    public UITrackpad limit(ValueDouble value)
    {
        return super.limit(value);
    }

    @Override
    public UITrackpad limit(double min, double max, boolean integer)
    {
        return super.limit(min, max, integer);
    }

    @Override
    public UITrackpad integer()
    {
        return super.integer();
    }

    @Override
    public UITrackpad increment(double increment)
    {
        return super.increment(increment);
    }

    @Override
    public UITrackpad values(double normal)
    {
        return super.values(normal);
    }

    @Override
    public UITrackpad values(double normal, double weak, double strong)
    {
        return super.values(normal, weak, strong);
    }

    @Override
    public UITrackpad delayedInput()
    {
        return super.delayedInput();
    }

    @Override
    public UITrackpad onlyNumbers()
    {
        return super.onlyNumbers();
    }

    @Override
    public UITrackpad relative(boolean relative)
    {
        return super.relative(relative);
    }

    @Override
    public UITrackpad forcedLabel(IKey label)
    {
        return super.forcedLabel(label);
    }

    @Override
    public UITrackpad disableCanceling()
    {
        return super.disableCanceling();
    }

    @Override
    public UITrackpad degrees()
    {
        return super.degrees();
    }

    @Override
    public UITrackpad block()
    {
        return super.block();
    }

    @Override
    public UITrackpad factor()
    {
        return super.factor();
    }

    @Override
    public UITrackpad metric()
    {
        return super.metric();
    }

    @Override
    public boolean isDragging()
    {
        return this.dragging;
    }

    /**
     * Update the bounding box of this GUI field
     */
    @Override
    public void resize()
    {
        super.resize();

        int w = this.area.w < 60 ? 12 : 20;

        this.plusOne.copy(this.area);
        this.minusOne.copy(this.area);
        this.plusOne.w = this.minusOne.w = w;
        this.plusOne.x = this.area.ex() - w;
    }

    /**
     * Delegates mouse click to text field and initiate value dragging if the
     * cursor inside of trackpad's bounding box.
     */
    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.allowCanceling && context.mouseButton == 1 && this.isDragging())
        {
            this.setValueAndNotify(this.lastValue);

            this.wasInside = false;
            this.dragging = false;
            this.shiftX = 0;

            return true;
        }

        if (context.mouseButton == 2 && this.area.isInside(context))
        {
            this.setValueAndNotify(-this.value);

            return true;
        }

        this.wasInside = this.area.isInside(context);

        if (context.mouseButton == 0)
        {
            if (this.textbox.isFocused())
            {
                this.textbox.mouseClicked(context.mouseX, context.mouseY, context.mouseButton);

                if (!this.textbox.isFocused())
                {
                    context.focus(null);
                }
            }

            if (this.wasInside && !this.textbox.isFocused())
            {
                if (Window.isCtrlPressed())
                {
                    this.setValueAndNotify(Math.round(this.value));
                    this.wasInside = false;

                    return true;
                }

                this.dragging = true;
                this.initialX = context.mouseX;
                this.initialY = context.mouseY;
                this.lastValue = this.value;

                this.emitDragStart();
            }
        }

        return context.mouseButton == 0 && this.wasInside;
    }

    /**
     * Reset value dragging
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1 && this.isDragging())
        {
            this.setValueAndNotify(this.lastValue);

            this.wasInside = false;
            this.dragging = false;
            this.shiftX = 0;

            return true;
        }

        this.textbox.mouseReleased(context.mouseX, context.mouseY, context.mouseButton);

        if (context.mouseButton == 0 && !this.isDraggingTime() && !this.textbox.isFocused())
        {
            if (this.wasInside)
            {
                if (this.plusOne.isInside(context))
                {
                    this.setValueAndNotify(this.value + this.increment);
                }
                else if (this.minusOne.isInside(context))
                {
                    this.setValueAndNotify(this.value - this.increment);
                }
                else
                {
                    context.focus(this);
                }
            }
        }

        if (this.delayedInput && this.isDraggingTime())
        {
            this.setValueAndNotify(this.value);
        }

        if (this.dragging)
        {
            this.emitDragEnd();
        }

        this.wasInside = false;
        this.dragging = false;
        this.shiftX = 0;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = new Area();
        int w = this.area.w / 2;

        area.copy(this.area);
        area.x = area.mx() - w / 2;
        area.w = w;

        if (this.dragging)
        {
            updateAmplifier(context);

            return true;
        }
        else if (area.isInside(context) && context.hasNotScrolledForMore(500) && BBSSettings.enableTrackpadScrolling.get())
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

    /**
     * Draw the trackpad
     *
     * This method will not only render the text box, background and title label,
     * but also dragging the numerical value based on the mouse input.
     */
    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        int padding = 0;

        boolean dragging = this.isDraggingTime();
        boolean plus = !dragging && this.plusOne.isInside(context);
        boolean minus = !dragging && this.minusOne.isInside(context);

        if (this.isEnabled() && (this.textbox.isFocused() || (!dragging && this.area.isInside(context))))
        {
            context.requestCursor(GLFW.GLFW_IBEAM_CURSOR);
        }

        if (this.textbox.isFocused())
        {
            this.textbox.render(context);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.opaque(BBSSettings.primaryColor.get()));
        }
        else
        {
            this.area.render(context.batcher, BBSSettings.inputSurface());

            if (dragging)
            {
                /* Draw filling background */
                int color = BBSSettings.primaryColor.get();
                int fx = MathUtils.clamp(context.mouseX, this.area.x + padding, this.area.ex() - padding);

                context.batcher.box(Math.min(fx, this.initialX), this.area.y + padding, Math.max(fx, this.initialX), this.area.ey() - padding, Colors.A100 | color);
            }

            FontRenderer font = context.batcher.getFont();
            String label = this.forcedLabel == null ? format(this.value) : this.forcedLabel.get();
            int lx = this.area.mx(font.getWidth(label));
            int ly = this.area.my() - font.getHeight() / 2;

            context.batcher.text(label, lx, ly, this.textbox.getColor());

            if (BBSSettings.enableTrackpadIncrements.get() || this.area.isInside(context))
            {
                this.plusOne.render(context.batcher, plus ? 0x22ffffff : 0x0affffff, padding);
                this.minusOne.render(context.batcher, minus ? 0x22ffffff : 0x0affffff, padding);

                context.batcher.icon(Icons.MOVE_LEFT, minus ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.5F), x + (this.plusOne.w - Icons.MOVE_LEFT.w) / 2, y + (h - 16) / 2);
                context.batcher.icon(Icons.MOVE_RIGHT, plus ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.5F), x + w - this.minusOne.w + (this.minusOne.w - Icons.MOVE_RIGHT.w) / 2, y + (h - 16) / 2);
            }
        }

        if (dragging)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            int ww = mc.getWindow().getWidth();

            double factor = Math.ceil(ww / (double) context.menu.width);
            int mouseX = context.globalX(context.mouseX);

            /* Mouse doesn't change immediately the next frame after Mouse.setCursorPosition(),
             * so this is a hack that stops for double shifting */
            if (this.changed.isTime())
            {
                final int border = 5;
                final int borderPadding = border + 1;
                boolean stop = false;

                if (mouseX <= border)
                {
                    Window.moveCursor(ww - (int) (factor * borderPadding), (int) mc.mouse.getY());

                    this.shiftX -= context.menu.width - borderPadding * 2;
                    this.changed.mark();
                    stop = true;
                }
                else if (mouseX >= context.menu.width - border)
                {
                    Window.moveCursor((int) (factor * borderPadding), (int) mc.mouse.getY());

                    this.shiftX += context.menu.width - borderPadding * 2;
                    this.changed.mark();
                    stop = true;
                }

                if (!stop)
                {
                    if (this.isFocused())
                    {
                        context.unfocus();
                    }

                    int dx = (this.shiftX + context.mouseX) - this.initialX;

                    if (dx != 0)
                    {
                        double value = this.getValueModifier();

                        double diff = (Math.abs(dx) - 3) * value;
                        double newValue = this.lastValue + (dx < 0 ? -diff : diff);

                        newValue = diff < 0 ? this.lastValue : newValue;

                        if (this.value != this.normalize(newValue))
                        {
                            if (this.delayedInput)
                            {
                                this.setValue(newValue);
                            }
                            else
                            {
                                this.setValueAndNotify(newValue);
                            }
                        }
                    }
                }
            }

            /* Draw active element */
            context.batcher.outlineCenter(this.initialX, this.initialY, 4, Colors.WHITE);
        }

        this.renderLockedArea(context);

        super.render(context);
    }
}
