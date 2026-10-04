package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayPropertiesPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Additional controls in actor properties. The supplier returns the currently displayed
 * actor (the first actor for a multiple selection), or null. Resolve it when acting;
 * do not capture its initial value while constructing the control. */
public class RegisterReplayActionsEvent
{
    public void register(BiFunction<UIFilmPanel, Supplier<Replay>, UIElement> factory)
    {
        UIReplayPropertiesPanel.registerAction(factory);
    }
}
