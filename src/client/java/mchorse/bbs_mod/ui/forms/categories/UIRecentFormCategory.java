package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIRecentFormCategory extends UIFormCategory
{
    public UIRecentFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        this.context((menu) ->
        {
            this.pasteFormAction(menu);

            Form form = this.getContextForm();

            if (form != null)
            {
                menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_ALL_FORM, Colors.RED, () ->
                {
                    this.category.clearForms();
                    this.select(null, false);
                    this.list.reconcile();
                });

                this.removeFormAction(menu, form);
            }
        });
    }
}
