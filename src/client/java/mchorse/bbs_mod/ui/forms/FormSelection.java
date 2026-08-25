package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.cells.GridSelection;

import java.util.List;

/**
 * The forms picked in a form list. Forms are matched by identity: two equal-looking forms
 * in different categories are different picks. A Shift range runs within one category.
 */
public class FormSelection extends GridSelection<Form>
{
    @Override
    protected boolean same(Form a, Form b)
    {
        return a == b;
    }

    public List<Form> getForms()
    {
        return this.getItems();
    }

    public FormCategory getAnchorCategory()
    {
        return (FormCategory) this.getAnchorScope();
    }

    /** Forget forms that are no longer in any of the given categories. */
    public void retain(List<FormCategory> categories)
    {
        this.retain((form) ->
        {
            for (FormCategory category : categories)
            {
                if (identityIndex(category.getForms(), form) != -1)
                {
                    return true;
                }
            }

            return false;
        });
    }

    public static int identityIndex(List<Form> list, Form form)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == form)
            {
                return i;
            }
        }

        return -1;
    }
}
