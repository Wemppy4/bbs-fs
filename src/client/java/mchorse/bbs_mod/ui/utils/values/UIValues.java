package mchorse.bbs_mod.ui.utils.values;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * The right click every property answers with: one verb putting the value back
 * to what its declaration says it should be.
 *
 * <p>The verb is offered only when there is something to undo — a property
 * already sitting at its default gets no entry, and an element left with an
 * otherwise empty menu opens none. The menu is assembled on every right click,
 * so that check is always current.</p>
 *
 * <p>The value arrives as a supplier rather than a reference: panels outlive
 * the object they edit, and a widget that grabbed a value once would keep
 * resetting the form the user has already moved on from.</p>
 */
public class UIValues
{
    public static <T extends UIElement> T resettable(T element, BaseValue value, Runnable refresh)
    {
        return resettable(element, () -> value, refresh);
    }

    public static <T extends UIElement> T resettable(T element, Supplier<? extends BaseValue> value, Runnable refresh)
    {
        element.context((menu) ->
        {
            BaseValue current = value.get();

            if (current == null || current.isDefault())
            {
                return;
            }

            menu.action(Icons.UNDO, UIKeys.VALUE_RESET, () ->
            {
                current.reset();

                if (refresh != null)
                {
                    refresh.run();
                }
            });
        });

        return element;
    }

    /* Widgets bound to a value */

    /**
     * A numeric field writing straight into the value it is pointed at. Range,
     * step and the rest stay the caller's business — chain them onto the field
     * as before.
     */
    public static UITrackpad trackpad(Supplier<? extends BaseValueNumber<?>> value)
    {
        UITrackpad trackpad = new UITrackpad(null);

        trackpad.callback = (v) -> value.get().setNumber(v);

        return resettable(trackpad, value, () -> trackpad.setValue(value.get().get().doubleValue()));
    }

    public static UIToggle toggle(IKey label, Supplier<ValueBoolean> value)
    {
        UIToggle toggle = new UIToggle(label, false, (b) -> value.get().set(b.getValue()));

        return resettable(toggle, value, () -> toggle.setValue(value.get().get()));
    }

    public static UIColor color(Supplier<ValueColor> value)
    {
        UIColor color = new UIColor((v) -> value.get().set(Color.rgba(v)));

        return resettable(color, value, () -> color.setColor(value.get().get().getARGBColor()));
    }

    public static UITextbox textbox(Supplier<ValueString> value)
    {
        UITextbox textbox = new UITextbox((s) -> value.get().set(s));

        return resettable(textbox, value, () -> textbox.setText(value.get().get()));
    }
}
