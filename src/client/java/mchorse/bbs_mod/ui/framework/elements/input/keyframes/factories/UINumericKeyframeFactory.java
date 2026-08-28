package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.lwjgl.glfw.GLFW;

/**
 * Base class for numeric keyframe factories (Double, Float, Integer).
 */
public abstract class UINumericKeyframeFactory <T extends Number> extends UIKeyframeFactory<T>
{
    protected UITrackpad value;
    protected UIBezierHandles handles;

    private int lastMouseX;
    private boolean editingMode;
    private double editingInitialValue;

    public UINumericKeyframeFactory(Keyframe<T> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.value = new UITrackpad((v) -> this.setValue(v));
        this.value.setValue(this.getNumericValue(keyframe.getValue()));
        this.handles = new UIBezierHandles(keyframe);

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, this::startEditingMode).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        this.scroll.add(this.value, this.handles.createColumn());
    }

    /**
     * Convert typed value to double for trackpad display.
     */
    protected abstract double getNumericValue(T value);

    /**
     * Convert double value back to typed value and update the given keyframe.
     */
    protected abstract void setKeyframeValue(Keyframe<T> keyframe, double value);

    /**
     * Override parent's setValue to handle numeric conversion. With auto-keyframing on the edit
     * lands on the keyframe at the playhead instead of the one this panel was opened for.
     */
    private void setValue(double value)
    {
        Keyframe<T> target = this.getEditTarget();

        this.setKeyframeValue(target, value);
        this.editor.getGraph().setValue(target.getValue(), true, true);
    }

    private void startEditingMode()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        this.editingInitialValue = this.value.getValue();
        this.lastMouseX = context.mouseX;
        this.editingMode = true;
    }

    private void stopEditingMode(boolean accept)
    {
        if (!this.editingMode)
        {
            return;
        }

        this.editingMode = false;

        if (!accept)
        {
            this.value.setValue(this.editingInitialValue);
            this.setValue(this.editingInitialValue);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.editingMode)
        {
            if (context.mouseButton == 0)
            {
                this.stopEditingMode(true);

                return true;
            }
            else if (context.mouseButton == 1)
            {
                this.stopEditingMode(false);

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.editingMode)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                this.stopEditingMode(true);

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.stopEditingMode(false);

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /** Nothing is refreshed under the user's hands: not while typing, dragging or grabbing. */
    private boolean isBusy()
    {
        return this.editingMode || this.value.isDragging() || this.value.textbox.isFocused();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.followsPlayhead() && !this.isBusy())
        {
            this.value.setValue(this.getNumericValue(this.getDisplayValue()));
        }

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
                this.setValue(newValue);
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
            this.value.setValue(this.getNumericValue(this.getDisplayValue()));
        }

        this.handles.update();
    }
}
