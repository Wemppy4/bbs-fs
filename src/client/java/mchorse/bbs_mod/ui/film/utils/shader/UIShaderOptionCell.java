package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One option of the shader pack's settings: a cell of the pack's menu with the name the pack's
 * translations give the option, and at the right edge a control that suits it, the way Iris picks
 * one.
 *
 * <ul>
 *     <li>on/off &mdash; a switch;</li>
 *     <li>an option the pack lists under {@code sliders=} &mdash; a slider over the values it
 *     allows;</li>
 *     <li>any other list of values &mdash; a button that steps through them (left click forward,
 *     right click back).</li>
 * </ul>
 *
 * <p>A change that waits for the pack to reload turns the name orange. Right click on the cell
 * offers a reset to the pack's default.</p>
 */
public class UIShaderOptionCell extends UIShaderMenuCell
{
    /** Width of a control; narrower than a usual value field, so the names of the pack's options fit */
    static final int CONTROL_WIDTH = 64;

    private static final int TOGGLE_WIDTH = 24;

    private final UIShaderOptionsPanel panel;
    private final ShaderMenu.Cell cell;
    private final String optionId;
    private final String label;
    private final List<String> values;

    private UIToggle toggle;
    private UISliderTrackpad slider;
    private UICirculate circulate;

    public UIShaderOptionCell(UIShaderOptionsPanel panel, ShaderMenu.Cell cell)
    {
        this(panel, cell, panel.getBrowser().optionLabel(cell.optionId));
    }

    private UIShaderOptionCell(UIShaderOptionsPanel panel, ShaderMenu.Cell cell, String label)
    {
        super(Kind.OPTION, label, null);

        this.panel = panel;
        this.cell = cell;
        this.optionId = cell.optionId;
        this.label = label;
        this.values = new ArrayList<>(cell.allowedValues);

        UIElement control;
        int width = CONTROL_WIDTH;

        if (cell.booleanOption)
        {
            control = this.toggle = new UIToggle(IKey.EMPTY, (b) -> panel.set(this.optionId, Boolean.toString(b.getValue())));
            width = TOGGLE_WIDTH;
        }
        else if (cell.slider && this.values.size() > 1)
        {
            this.slider = new ValueSlider((v) -> this.pick((int) Math.round(v)));
            this.slider.limit(0, this.values.size() - 1, true);
            this.slider.snap(1);
            this.slider.forcedLabel(this::valueText);
            control = this.slider;
        }
        else
        {
            this.circulate = new UICirculate((b) -> this.pick(b.getValue()));

            for (String value : this.values)
            {
                this.circulate.addLabel(IKey.constant(this.valueLabel(value)));
            }

            if (this.values.isEmpty())
            {
                this.circulate.addLabel(this::valueText);
                this.circulate.setEnabled(false);
            }

            control = this.circulate;
        }

        this.tooltip((IKey) this::tooltip);
        this.context((menu) ->
        {
            if (this.cell.defaultValue != null && !this.cell.defaultValue.equals(this.panel.value(this.optionId)))
            {
                menu.action(Icons.REFRESH, UIKeys.GENERAL_RESET, () -> this.panel.set(this.optionId, this.cell.defaultValue));
            }
        });

        this.control(control, width);
        this.valueBinding(this::update);
    }

    private void pick(int index)
    {
        if (index >= 0 && index < this.values.size())
        {
            this.panel.set(this.optionId, this.values.get(index));
        }
    }

    /** Puts the control on the option's value. */
    private void update()
    {
        String value = this.panel.value(this.optionId);
        int index = this.values.indexOf(value);

        if (this.toggle != null)
        {
            this.toggle.setValue("true".equals(value));
        }
        else if (this.slider != null)
        {
            if (!this.slider.isUserEditing() && index >= 0)
            {
                this.slider.setValue(index);
            }
        }
        else if (this.circulate != null && !this.values.isEmpty())
        {
            if (index >= 0)
            {
                this.circulate.setValue(index);
            }
            else
            {
                this.circulate.label = IKey.constant(this.valueText());
            }
        }
    }

    /** The name is orange while a change waits for a reload */
    @Override
    protected int labelColor(boolean lit)
    {
        return this.panel.isPending(this.optionId) ? Colors.A100 | Colors.ORANGE : super.labelColor(lit);
    }

    private String valueLabel(String value)
    {
        return this.panel.getBrowser().valueLabel(this.optionId, value, this.cell.booleanOption);
    }

    /** What the control reads: the option's value */
    private String valueText()
    {
        return this.valueLabel(this.panel.value(this.optionId));
    }

    private String tooltip()
    {
        StringBuilder builder = new StringBuilder(this.label).append(" (").append(this.optionId).append(")");
        String comment = this.panel.getBrowser().optionComment(this.optionId);

        if (!comment.isEmpty())
        {
            builder.append("\n").append(comment);
        }

        if (!this.panel.isCurvable(this.optionId))
        {
            builder.append("\n").append(UIKeys.FILM_SHADER_RELOAD.get());
        }

        return builder.toString();
    }

    /**
     * A slider over the positions of the values an option allows. It moves by position, so there
     * is no number to type in: the middle button that would bring up the text field does nothing.
     */
    private static class ValueSlider extends UISliderTrackpad
    {
        public ValueSlider(Consumer<Double> callback)
        {
            super(callback);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 2)
            {
                return this.area.isInside(context);
            }

            return super.subMouseClicked(context);
        }
    }
}
