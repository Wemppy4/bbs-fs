package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import java.util.function.Consumer;

/** Adds panels to existing form editors, before their shared material/general panels.
 * Called once per editor instance, before a form is assigned; filter by editor type here.
 * Panels receive the usual startEdit/finishEdit lifecycle. */
public class RegisterFormPanelsEvent
{
    public void register(Consumer<UIForm<?>> factory)
    {
        UIForm.registerPanelExtension(factory);
    }
}
