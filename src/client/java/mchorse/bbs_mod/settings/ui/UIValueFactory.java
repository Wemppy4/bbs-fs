package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.List;
import java.util.function.Consumer;

public class UIValueFactory
{
    /* Language key factories */

    public static String getTitleKey(BaseValue value)
    {
        return value.getId() + ".config.title";
    }

    public static String getCategoryTitleKey(BaseValue value)
    {
        return getValueLabelKey(value) + ".title";
    }

    public static String getCategoryTooltipKey(BaseValue value)
    {
        return getValueLabelKey(value) + ".tooltip";
    }

    public static String getValueLabelKey(BaseValue value)
    {
        List<String> segments = value.getPathSegments();
        String prefix = segments.remove(0);

        return prefix + ".config." + String.join(".", segments);
    }

    public static String getValueCommentKey(BaseValue value)
    {
        List<String> segments = value.getPathSegments();
        String prefix = segments.remove(0);

        return prefix + ".config." + String.join(".", segments) + "-comment";
    }

    /* UI element factories */

    public static UIToggle booleanUI(ValueBoolean value, Consumer<UIToggle> callback)
    {
        UIToggle booleanToogle = new UIToggle(L10n.lang(getValueLabelKey(value)), value.get(), callback == null ? (toggle) -> value.set(toggle.getValue()) : (toggle) ->
        {
            value.set(toggle.getValue());
            callback.accept(toggle);
        });

        booleanToogle.tooltip(L10n.lang(getValueCommentKey(value)));

        return booleanToogle;
    }

    public static UINumericInput<?> intUI(ValueInt value, Consumer<Double> callback)
    {
        UINumericInput<?> trackpad = numericUI(value, callback == null ? (v) -> value.set(v.intValue()) : (v) ->
        {
            value.set(v.intValue());
            callback.accept(v);
        });

        trackpad.limit(value.getMin(), value.getMax(), true);
        trackpad.delayedInput();
        trackpad.setValue(value.get());
        trackpad.tooltip(L10n.lang(getValueCommentKey(value)));

        return trackpad;
    }

    /**
     * The field a numeric setting is edited with. Values that declare
     * themselves sliders get a track, the rest keep the drag field — see
     * {@link BaseValueNumber#slider()} for what earns one. A step of 0 is the
     * track cutting one out of the range itself.
     */
    private static UINumericInput<?> numericUI(BaseValueNumber<?> value, Consumer<Double> callback)
    {
        return value.isSlider() ? new UISliderTrackpad(callback).snap(value.getSliderStep()) : new UITrackpad(callback);
    }

    public static UIColor colorUI(ValueInt value, Consumer<Integer> callback)
    {
        UIColor color = new UIColor(callback == null ? value::set : (integer) ->
        {
            value.set(integer);
            callback.accept(integer);
        });

        color.tooltip(L10n.lang(getValueCommentKey(value)));

        if (value.getSubtype() == ValueInt.Subtype.COLOR_ALPHA)
        {
            color.withAlpha();
        }

        color.setColor(value.get());

        return color;
    }

    public static UINumericInput<?> floatUI(ValueFloat value, Consumer<Double> callback)
    {
        UINumericInput<?> trackpad = numericUI(value, callback == null ? (v) -> value.set(v.floatValue()) : (v) ->
        {
            value.set(v.floatValue());
            callback.accept(v);
        });

        trackpad.limit(value.getMin(), value.getMax());
        trackpad.delayedInput();
        trackpad.setValue(value.get());
        trackpad.tooltip(L10n.lang(getValueCommentKey(value)));

        return trackpad;
    }

    public static UINumericInput<?> doubleUI(ValueDouble value, Consumer<Double> callback)
    {
        UINumericInput<?> trackpad = numericUI(value, callback == null ? value::set : (v) ->
        {
            value.set(v);
            callback.accept(v);
        });

        trackpad.limit(value.getMin(), value.getMax());
        trackpad.delayedInput();
        trackpad.setValue(value.get().floatValue());
        trackpad.tooltip(L10n.lang(getValueCommentKey(value)));

        return trackpad;
    }

    public static UITextbox stringUI(ValueString value, Consumer<String> callback)
    {
        UITextbox textbox = new UITextbox(10000, callback == null ? value::set : (string) ->
        {
            value.set(string);
            callback.accept(string);
        });

        textbox.setText(value.get());
        textbox.tooltip(L10n.lang(getValueLabelKey(value)));

        return textbox;
    }

    public static UIElement column(UIElement control, BaseValue value)
    {
        UIElement element = new UIElement();

        control.removeTooltip();
        element.row(0).preferred(0).height(20);
        element.add(UIValueFactory.label(value), control);

        return commetTooltip(element, value);
    }

    public static UILabel label(BaseValue value)
    {
        return UI.label(L10n.lang(UIValueFactory.getValueLabelKey(value)), 0).labelAnchor(0, 0.5F);
    }

    public static UIElement commetTooltip(UIElement element, BaseValue value)
    {
        element.tooltip(L10n.lang(UIValueFactory.getValueCommentKey(value)));

        return element;
    }
}
