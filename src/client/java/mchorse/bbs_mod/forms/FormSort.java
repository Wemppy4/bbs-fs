package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How a category shows its forms. Only the view is ordered: the category's own list keeps the
 * order the user arranged by hand, so switching back to {@link #MANUAL} brings it back intact.
 */
public enum FormSort
{
    MANUAL("manual", Icons.ALL_DIRECTIONS, UIKeys.FORMS_CATEGORIES_SORT_MANUAL, null),
    NAME("name", Icons.FONT, UIKeys.FORMS_CATEGORIES_SORT_NAME, Comparator.comparing((Form f) -> f.getDisplayName().toLowerCase())),
    TYPE("type", Icons.SHAPES, UIKeys.FORMS_CATEGORIES_SORT_TYPE, Comparator.comparing(Form::getFormId).thenComparing((Form f) -> f.getDisplayName().toLowerCase()));

    public final String id;
    public final Icon icon;
    public final IKey label;

    private final Comparator<Form> comparator;

    FormSort(String id, Icon icon, IKey label, Comparator<Form> comparator)
    {
        this.id = id;
        this.icon = icon;
        this.label = label;
        this.comparator = comparator;
    }

    public static FormSort byId(String id)
    {
        for (FormSort sort : values())
        {
            if (sort.id.equals(id))
            {
                return sort;
            }
        }

        return MANUAL;
    }

    /**
     * Whether forms can be dragged around inside a category shown this way. Dropping a form
     * into a spot of an alphabetical list would change nothing visible, so it's refused.
     */
    public boolean isRearrangeable()
    {
        return this == MANUAL;
    }

    public List<Form> sorted(List<Form> forms)
    {
        List<Form> view = new ArrayList<>(forms);

        if (this.comparator != null)
        {
            view.sort(this.comparator);
        }

        return view;
    }
}
