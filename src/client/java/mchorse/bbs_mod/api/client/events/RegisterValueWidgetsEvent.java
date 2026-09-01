package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/**
 * Posted on the client once BBS has registered the settings widgets of its own value types.
 *
 * <p>This is what draws a value on the settings screen. An addon needs it only for a value type
 * of its own making — the built-in ones already have their widgets.</p>
 */
public class RegisterValueWidgetsEvent
{
    public <T extends BaseValue> void register(Class<T> clazz, UIValueMap.IUIValueFactory<T> factory)
    {
        UIValueMap.register(clazz, factory);
    }
}
