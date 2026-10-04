package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.forms.FormArchitect;

/**
 * Posted on both sides once BBS has registered its own forms.
 *
 * <p>A form registered here can be read, saved, animated and put into a body part like any of
 * BBS's own. Two more things belong with it on the client — a renderer
 * ({@code RegisterFormRenderersEvent}) and an editor panel ({@code RegisterFormEditorsEvent}) —
 * and neither is required: a form without them is invisible and uneditable, but its data still
 * survives the round trip.</p>
 */
public class RegisterFormsEvent
{
    public final FormArchitect forms;

    public RegisterFormsEvent(FormArchitect forms)
    {
        this.forms = forms;
    }
}
