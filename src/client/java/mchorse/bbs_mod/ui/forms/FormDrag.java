package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.utils.DragGesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One drag-and-drop in progress inside a form list: what's being carried and where it would
 * land. Either a handful of forms or a whole user category — never both. The categories
 * report the drop target while they paint (they're the ones that know their local
 * geometry), and the list resolves the drop on release.
 */
public class FormDrag extends DragGesture
{
    public enum Kind
    {
        NONE, FORMS, CATEGORY
    }

    private Kind kind = Kind.NONE;

    private final List<Form> forms = new ArrayList<>();
    private FormCategory source;
    private UserFormCategory category;

    private UIFormCategory target;
    private int insertion;

    public void pressForms(List<Form> forms, FormCategory source, int x, int y)
    {
        this.reset();
        this.press(x, y);

        this.kind = Kind.FORMS;
        this.forms.addAll(forms);
        this.source = source;
    }

    public void pressCategory(UserFormCategory category, int x, int y)
    {
        this.reset();
        this.press(x, y);

        this.kind = Kind.CATEGORY;
        this.category = category;
    }

    @Override
    public void reset()
    {
        super.reset();

        this.kind = Kind.NONE;
        this.forms.clear();
        this.source = null;
        this.category = null;
        this.clearTarget();
    }

    public Kind getKind()
    {
        return this.kind;
    }

    public boolean isDragging(Form form)
    {
        return this.isActive() && this.kind == Kind.FORMS && FormSelection.identityIndex(this.forms, form) != -1;
    }

    public boolean isDragging(FormCategory category)
    {
        return this.isActive() && this.kind == Kind.CATEGORY && this.category == category;
    }

    public List<Form> getForms()
    {
        return Collections.unmodifiableList(this.forms);
    }

    /** The category the pressed form was in — the one a same-category drop rearranges. */
    public FormCategory getSource()
    {
        return this.source;
    }

    public UserFormCategory getCategory()
    {
        return this.category;
    }

    /* Drop target, reported by categories while painting */

    public void setTarget(UIFormCategory target, int insertion)
    {
        this.target = target;
        this.insertion = insertion;
    }

    public void clearTarget()
    {
        this.target = null;
        this.insertion = -1;
    }

    public UIFormCategory getTarget()
    {
        return this.target;
    }

    public boolean isTarget(UIFormCategory category)
    {
        return this.isActive() && this.target == category;
    }

    /**
     * For forms: the slot in the target's displayed order. For a category: 0 to land above
     * the target category, 1 to land below it.
     */
    public int getInsertion()
    {
        return this.insertion;
    }
}
