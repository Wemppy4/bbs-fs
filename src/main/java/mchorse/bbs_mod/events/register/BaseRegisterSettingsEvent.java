package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.io.File;
import java.util.function.Consumer;

/**
 * The shared body of the settings registration events.
 *
 * <p>{@link RegisterSettingsEvent} and {@link RegisterClientSettingsEvent} are siblings rather
 * than one extending the other on purpose: the bus hands an event to the subscribers of its
 * super classes as well, so an inheritance between the two would call a subscriber of the common
 * event a second time on the client. Subscribe to this class to be called by both.</p>
 */
public abstract class BaseRegisterSettingsEvent
{
    public void register(Icon icon, String id, Consumer<SettingsBuilder> consumer)
    {
        BBSMod.setupConfig(icon, id, new File(BBSMod.getSettingsFolder(), id + ".json"), consumer);
    }
}
