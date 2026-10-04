package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.forms.FormArchitect;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.function.Consumer;

/**
 * Posted on both sides right after the forms are registered, for an addon that wants to add
 * something of its own to forms it did not write.
 *
 * <p>A modifier adds values to a form as it is made. Everything else follows from that on its
 * own: the value is saved and read like any other, the timeline collects a track for it because
 * the track catalogue walks the form's values rather than a list of names, and the keyframes work
 * because the value carries its own factory. That is the whole reason this is one call rather
 * than a family of them.</p>
 *
 * <pre>
 * event.register((form) -&gt; form.add(new ValueFloat("myaddon:wobble", 0F)));
 * </pre>
 *
 * <p>Name the value with your namespace. It is what keeps the value in the data when your addon
 * is not loaded — see the note on naming in {@code ADDONS.md}.</p>
 */
public class RegisterFormModifiersEvent
{
    public void register(Consumer<Form> modifier)
    {
        FormArchitect.registerModifier(modifier);
    }
}
