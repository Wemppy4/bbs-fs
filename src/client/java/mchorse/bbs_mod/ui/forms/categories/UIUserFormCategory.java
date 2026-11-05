package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIPromptPopup;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIUserFormCategory extends UIFormCategory
{
    public UIUserFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        this.context((menu) ->
        {
            UserFormSection userForms = BBSModClient.getFormCategories().getUserForms();

            menu.action(Icons.EDIT, UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY, () ->
            {
                UIPromptPopup panel = new UIPromptPopup(
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_TITLE,
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_DESCRIPTION,
                    (str) ->
                    {
                        this.getCategory().title = IKey.constant(str);
                        userForms.writeUserCategories();
                    }
                );

                panel.verb(UIKeys.GENERAL_RENAME);
                panel.text.setText(this.getCategory().title.get());

                this.getContext().replaceContextMenu(panel);
            });

            this.pasteFormAction(menu);

            Form form = this.getContextForm();

            if (form != null)
            {
                this.removeFormAction(menu, form);
            }
            else
            {
                menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_CATEGORY, () ->
                {
                    UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                        UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_TITLE.format(this.category.getProcessedTitle()),
                        UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_DESCRIPTION,
                        (confirm) ->
                        {
                            if (confirm)
                            {
                                userForms.removeUserCategory((UserFormCategory) this.category);

                                UIElement parent = this.getParentContainer();

                                this.removeFromParent();
                                parent.resize();
                            }
                        }
                    ).verb(UIKeys.GENERAL_REMOVE).danger();

                    UIOverlay.addOverlay(this.getContext(), panel);
                });
            }
        });
    }

    private UserFormCategory getCategory()
    {
        return (UserFormCategory) this.category;
    }
}
