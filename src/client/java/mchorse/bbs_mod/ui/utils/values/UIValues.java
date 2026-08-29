package mchorse.bbs_mod.ui.utils.values;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;

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
}
