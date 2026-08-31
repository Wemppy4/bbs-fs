package com.example.bbsaddon;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A form of this addon's own, built on top of one of BBS's.
 *
 * <p>Extending a BBS form is worth doing: the renderer and the editor panel are looked up by the
 * form's own class first and then by the classes it extends, so this draws and edits like a
 * billboard without registering anything, and only what it actually changes needs writing.</p>
 */
public class GadgetForm extends BillboardForm
{
    public final ValueFloat spin = new ValueFloat("spin", 0F);

    public GadgetForm()
    {
        super();

        /* Visible values become timeline tracks on their own. */
        this.add(this.spin);
    }
}
