package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import org.lwjgl.glfw.GLFW;

/**
 * Base class for numeric track factories (Double, Float, Integer).
 */
public abstract class UINumericKeyframeFactory <T extends Number> extends UIKeyframeFactory<T>
{
    protected UINumericInput<?> value;

    private int lastMouseX;
    private boolean editingMode;
    private T displayedValue;
    private final UIElement editingOverlay = new AcceptRejectOverlay();

    public UINumericKeyframeFactory(UITrackValue<T> track, UIKeyframes editor)
    {
        super(track, editor);

        this.value = this.createInput(track);
        this.displayedValue = track.getValue();
        this.value.setValue(this.getNumericValue(this.displayedValue));

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, this::startEditingMode).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        this.scroll.add(this.value);
    }

    /**
     * A track whose value declares itself a slider (see {@link BaseValueNumber#slider()}) is edited
     * along its range, the same way settings are; every other numeric track keeps the drag field.
     * Only the field differs: the keyframes are the same numbers either way.
     */
    private UINumericInput<?> createInput(UITrackValue<T> track)
    {
        if (track.sheet.property instanceof BaseValueNumber<?> number && number.isSlider())
        {
            return new UISliderTrackpad((v) -> this.setNumericValue(v))
                .snap(number.getSliderStep())
                .limit(number.getMin().doubleValue(), number.getMax().doubleValue());
        }

        return new UITrackpad((v) -> this.setNumericValue(v));
    }

    /**
     * Convert typed value to double for trackpad display.
     */
    protected abstract double getNumericValue(T value);

    /**
     * Convert double value back to typed value and update the given track.
     */
    protected abstract T convertValue(double value);

    /**
     * Convert the trackpad's Double before writing to a typed numeric track. Keep the name
     * distinct from setValue(Object), which would accept a boxed Double without conversion.
     */
    private void setNumericValue(double value)
    {
        T converted = this.convertValue(value);
        T before = this.displayedValue;
        this.displayedValue = converted;
        this.track.setValue(converted, before);
    }

    private void startEditingMode()
    {
        UIContext context = this.getContext();

        if (context == null || this.editingMode)
        {
            return;
        }

        this.update();
        this.editor.beginValueGesture();
        this.lastMouseX = context.mouseX;
        this.editingMode = true;
        context.menu.overlay.add(this.editingOverlay);
    }

    private void stopEditingMode(boolean accept)
    {
        if (!this.editingMode)
        {
            return;
        }

        this.editingMode = false;
        this.editingOverlay.removeFromParent();

        if (accept) this.editor.endValueGesture();
        else this.editor.cancelValueGesture();
        this.update();
    }

    @Override
    protected void onRemove(UIElement parent)
    {
        this.stopEditingMode(false);
        super.onRemove(parent);
    }

    /** Handle the gesture before fields, timelines and their context menus. */
    private class AcceptRejectOverlay extends UIElement
    {
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 || context.mouseButton == 1)
            {
                UINumericKeyframeFactory.this.stopEditingMode(context.mouseButton == 0);
            }
            return true;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
            {
                UINumericKeyframeFactory.this.stopEditingMode(true);
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                UINumericKeyframeFactory.this.stopEditingMode(false);
            }
            return true;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            UITrackpad.updateAmplifier(context);
            return true;
        }
    }

    /** Nothing is refreshed under the user's hands: not while typing, dragging or grabbing. */
    private boolean isBusy()
    {
        return this.editingMode || this.value.isDragging() || this.value.textbox.isFocused();
    }

    @Override
    public boolean isUserEditing()
    {
        return this.editingMode || super.isUserEditing();
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.editingMode)
        {
            int dx = context.mouseX - this.lastMouseX;

            if (dx != 0)
            {
                double modifier = this.value.getValueModifier();
                double newValue = MathUtils.clamp(this.value.getValue() + dx * modifier, this.value.min, this.value.max);

                if (this.value.integer)
                {
                    newValue = (int) newValue;
                }

                this.value.setValue(newValue);
                this.setNumericValue(newValue);
                this.lastMouseX = context.mouseX;
            }

            String label = UIKeys.TRANSFORMS_EDITING.get();
            FontRenderer font = context.batcher.getFont();
            int x = this.area.mx(font.getWidth(label));
            int y = this.area.my(font.getHeight());

            context.batcher.textCard(label, x, y, Colors.WHITE, Colors.A50);
        }
    }

    @Override
    public void update()
    {
        super.update();

        if (!this.isBusy())
        {
            this.displayedValue = this.getDisplayValue();
            this.value.setValue(this.getNumericValue(this.displayedValue));
        }

    }
}
