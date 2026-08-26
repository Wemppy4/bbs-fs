package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
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
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_TITLE,
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_DESCRIPTION,
                    (str) ->
                    {
                        this.getCategory().title = IKey.constant(str);
                        userForms.writeUserCategories();
                    }
                );

                panel.text.setText(this.getCategory().title.get());

                UIOverlay.addOverlay(this.getContext(), panel);
            });

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
                /* With several picked, the group menu already offers their removal */
                if (!this.isGroupContext())
                {
                    menu.icon(MenuVerb.REMOVE, () ->
                    {
                        this.category.removeForm(form);
                        this.list.reconcile();
                    }).label(UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FORM);
                }
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
                    );

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
