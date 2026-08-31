package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;

/**
 * Posted on the client before the keybind settings file is built.
 *
 * <p>BBS reads its key combos out of the fields of a class, so an addon hands over a class of its
 * own rather than the combos one by one. Its categories can carry an icon of their own too.</p>
 */
public class RegisterKeybindsEvent
{
    public void register(Class clazz)
    {
        KeybindSettings.register(clazz);
    }

    public void registerCategoryIcon(String category, Icon icon)
    {
        KeybindSettings.registerCategoryIcon(category, icon);
    }
}
