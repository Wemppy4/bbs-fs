package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.forms.Form;

public interface IUIFormList
{
    public void exit();

    public void toggleEditor();

    public void accept(Form form);

    /** A form was double-clicked: the user is done choosing. By default the palette closes. */
    public default void confirm()
    {
        this.exit();
    }
}
