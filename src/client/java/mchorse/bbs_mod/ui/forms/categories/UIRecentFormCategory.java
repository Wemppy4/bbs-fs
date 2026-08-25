package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
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
            try
            {
                MapType data = Window.getClipboardMap();
                Form form = FormUtils.fromData(data);

                menu.action(Icons.PASTE, UIKeys.FORMS_CATEGORIES_CONTEXT_PASTE_FORM, () -> this.category.addForm(form));
            }
            catch (Exception e)
            {}

            Form form = this.getContextForm();

            if (form != null)
            {
                menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_ALL_FORM, Colors.RED, () ->
                {
                    this.category.clearForms();
                    this.select(null, false);
                    this.list.reconcile();
                });

                /* With several picked, the group menu already offers their removal */
                if (!this.isGroupContext())
                {
                    menu.action(Icons.REMOVE, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FORM, Colors.RED, () ->
                    {
                        this.category.removeForm(form);
                        this.list.reconcile();
                    });
                }
            }
        });
    }
}
