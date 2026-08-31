package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;

import java.util.function.Supplier;

/**
 * Posted on the client once BBS has registered the editor panels of its own forms.
 *
 * <p>Like the renderer, the panel is found by the form's class and then by its super classes, so
 * a form extending one of BBS's own is editable through its parent's panel out of the box.</p>
 */
public class RegisterFormEditorsEvent
{
    public void register(Class clazz, Supplier<UIForm> supplier)
    {
        UIFormEditor.register(clazz, supplier);
    }
}
